package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.ImageAsset;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GangSheetPdfService {

    private static final Logger log = LoggerFactory.getLogger(GangSheetPdfService.class);
    private static final double POINT_PER_INCH = 72.0;
    private static final int DEFAULT_WARNING_DPI = 150;

    private final AssetStorageService assetStorageService;

    @Value("${app.pdf.output-dir:data/output}")
    private String outputDir;

    @Value("${app.pdf.sheet-width-inch:22}")
    private double sheetWidthInch;

    @Value("${app.pdf.bottom-padding-inch:0}")
    private double bottomPaddingInch;

    @Value("${app.image.render-dpi:300}")
    private int imageRenderDpi;

    @Value("${app.image.max-raster-pixels:40000000}")
    private long maxRasterPixels;

    @Value("${app.image.max-total-bytes-per-request:524288000}")
    private long maxTotalBytesPerRequest;

    @Value("${app.image.max-items-per-request:20}")
    private int maxItemsPerRequest;

    public GangSheetPdfService(AssetStorageService assetStorageService) {
        this.assetStorageService = assetStorageService;
    }

    public GeneratePdfResponse generate(List<GangSheetItemRequest> items) throws IOException {
        long tStart = System.nanoTime();

        validateRequest(items);
        validateItemsForSheetCalculation(items);
        enforceLocalSizeBudget(items);

        double sheetHeightInch = calculateSheetHeight(items);
        validateItemsInsideSheet(items, sheetWidthInch, sheetHeightInch);

        float pageWidthPt = inchToPoint(sheetWidthInch);
        float pageHeightPt = inchToPoint(sheetHeightInch);

        String fileName = buildOutputFileName();

        Path outputDirectory = Path.of(outputDir);
        Files.createDirectories(outputDirectory);

        Path outputPath = outputDirectory.resolve(fileName).normalize();

        List<String> warnings = new ArrayList<>();
        List<ImageAsset> imageAssets = loadImages(items);
        enforceLoadedSizeBudget(imageAssets);
        long tLoaded = System.nanoTime();

        long tSaved;
        try (PDDocument document = new PDDocument(IOUtils.createMemoryOnlyStreamCache())) {
            PDPage page = new PDPage(new PDRectangle(pageWidthPt, pageHeightPt));
            document.addPage(page);
            Map<String, PDImageXObject> pdfImagesBySource = new HashMap<>();

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                for (int i = 0; i < items.size(); i++) {
                    GangSheetItemRequest item = items.get(i);
                    ImageAsset imageAsset = imageAssets.get(i);

                    addDpiWarningIfNeeded(
                            warnings,
                            imageAsset,
                            item,
                            i
                    );

                    long tEmbedItem = System.nanoTime();
                    PDImageXObject pdfImage = getOrCreatePdfImage(
                            document,
                            pdfImagesBySource,
                            imageAsset,
                            item
                    );
                    long embedItemMs = (System.nanoTime() - tEmbedItem) / 1_000_000;
                    log.info(
                            "embedded image {}/{}: {}ms src={}",
                            i + 1,
                            items.size(),
                            embedItemMs,
                            imageAsset.source()
                    );

                    drawItemExactlyByFeLayout(
                            contentStream,
                            pdfImage,
                            item,
                            sheetHeightInch
                    );
                }
            }
            long tEmbedded = System.nanoTime();

            document.save(outputPath.toFile());
            tSaved = System.nanoTime();

            log.info(
                    "PDF timing: load={}ms embed={}ms save={}ms total={}ms items={}",
                    (tLoaded - tStart) / 1_000_000,
                    (tEmbedded - tLoaded) / 1_000_000,
                    (tSaved - tEmbedded) / 1_000_000,
                    (tSaved - tStart) / 1_000_000,
                    items.size()
            );
        }

        return new GeneratePdfResponse(
                "PDF created successfully",
                fileName,
                outputPath.toAbsolutePath().toString(),
                items.size(),
                sheetWidthInch,
                sheetHeightInch,
                "INCH",
                warnings
        );
    }

    /**
     * FE chịu trách nhiệm layout và giữ aspect ratio.
     * BE chỉ render đúng tuyệt đối theo:
     * x, y, width, height, rotation.
     * x, y, width, height tính theo inch.
     * x, y là top-left.
     * rotation xoay quanh tâm box.
     */
    private void drawItemExactlyByFeLayout(
            PDPageContentStream contentStream,
            PDImageXObject pdfImage,
            GangSheetItemRequest item,
            double sheetHeightInch
    ) throws IOException {

        float xPt = inchToPoint(item.x());
        float topYFromTopPt = inchToPoint(item.y());
        float widthPt = inchToPoint(item.width());
        float heightPt = inchToPoint(item.height());

        float centerXPt = xPt + widthPt / 2.0f;

        float centerYPt = inchToPoint(sheetHeightInch)
                - topYFromTopPt
                - heightPt / 2.0f;

        double rotationRadians = Math.toRadians(-normalizeRotation(item.rotation()));

        contentStream.saveGraphicsState();

        try {
            contentStream.transform(
                    Matrix.getTranslateInstance(centerXPt, centerYPt)
            );

            if (rotationRadians != 0) {
                contentStream.transform(
                        Matrix.getRotateInstance(rotationRadians, 0, 0)
                );
            }

            contentStream.drawImage(
                    pdfImage,
                    -widthPt / 2.0f,
                    -heightPt / 2.0f,
                    widthPt,
                    heightPt
            );
        } finally {
            contentStream.restoreGraphicsState();
        }
    }

    private PDImageXObject getOrCreatePdfImage(
            PDDocument document,
            Map<String, PDImageXObject> pdfImagesBySource,
            ImageAsset imageAsset,
            GangSheetItemRequest item
    ) throws IOException {
        String cacheKey = buildPdfImageCacheKey(imageAsset, item);
        PDImageXObject cachedPdfImage = pdfImagesBySource.get(cacheKey);

        if (cachedPdfImage != null) {
            return cachedPdfImage;
        }

        PDImageXObject pdfImage = createPdfImage(document, imageAsset, item);
        pdfImagesBySource.put(cacheKey, pdfImage);
        return pdfImage;
    }

    private PDImageXObject createPdfImage(
            PDDocument document,
            ImageAsset imageAsset,
            GangSheetItemRequest item
    ) throws IOException {
        if (imageAsset.format() == ImageAsset.ImageFormat.JPEG) {
            return JPEGFactory.createFromByteArray(document, imageAsset.bytes());
        }

        RenderSize renderSize = calculateRenderSize(imageAsset, item);
        BufferedImage image = assetStorageService.decodeForRender(
                imageAsset,
                renderSize.width(),
                renderSize.height()
        );

        return LosslessFactory.createFromImage(document, image);
    }

    private String buildPdfImageCacheKey(ImageAsset imageAsset, GangSheetItemRequest item) {
        if (imageAsset.format() == ImageAsset.ImageFormat.JPEG) {
            return imageAsset.source();
        }

        RenderSize renderSize = calculateRenderSize(imageAsset, item);
        return imageAsset.source()
                + "#"
                + renderSize.width()
                + "x"
                + renderSize.height();
    }

    private RenderSize calculateRenderSize(ImageAsset imageAsset, GangSheetItemRequest item) {
        int targetWidth = Math.min(
                imageAsset.width(),
                Math.max(1, (int) Math.ceil(item.width() * imageRenderDpi))
        );
        int targetHeight = Math.min(
                imageAsset.height(),
                Math.max(1, (int) Math.ceil(item.height() * imageRenderDpi))
        );

        long targetPixels = (long) targetWidth * targetHeight;

        if (targetPixels > maxRasterPixels) {
            double scale = Math.sqrt((double) maxRasterPixels / targetPixels);
            targetWidth = Math.max(1, (int) Math.floor(targetWidth * scale));
            targetHeight = Math.max(1, (int) Math.floor(targetHeight * scale));
        }

        return new RenderSize(targetWidth, targetHeight);
    }

    private List<ImageAsset> loadImages(List<GangSheetItemRequest> items) throws IOException {
        LinkedHashMap<String, CompletableFuture<ImageAsset>> futuresByPath = new LinkedHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (GangSheetItemRequest item : items) {
                futuresByPath.computeIfAbsent(
                        item.img(),
                        src -> CompletableFuture.supplyAsync(() -> loadAssetWithTiming(src), executor)
                );
            }
        }

        Map<String, ImageAsset> imagesByPath = new HashMap<>(futuresByPath.size());
        for (Map.Entry<String, CompletableFuture<ImageAsset>> entry : futuresByPath.entrySet()) {
            imagesByPath.put(entry.getKey(), joinImageFuture(entry.getValue(), entry.getKey()));
        }

        List<ImageAsset> sourceImages = new ArrayList<>(items.size());
        for (GangSheetItemRequest item : items) {
            sourceImages.add(imagesByPath.get(item.img()));
        }
        return sourceImages;
    }

    private ImageAsset loadAssetWithTiming(String source) {
        try {
            long t0 = System.nanoTime();
            ImageAsset image = assetStorageService.loadAsset(source);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            int components = image.format() == ImageAsset.ImageFormat.JPEG
                    ? assetStorageService.tryReadJpegComponents(image.bytes())
                    : -1;

            log.info(
                    "loaded image: {}ms bytes={} {}x{} format={} components={} src={}",
                    elapsedMs,
                    image.bytes().length,
                    image.width(),
                    image.height(),
                    image.format(),
                    components,
                    source
            );
            return image;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private ImageAsset joinImageFuture(CompletableFuture<ImageAsset> future, String source) throws IOException {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof UncheckedIOException uio) {
                throw uio.getCause();
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException("Failed to load image: " + source, cause);
        }
    }

    private double calculateSheetHeight(List<GangSheetItemRequest> items) {
        double maxBottom = 0;

        for (GangSheetItemRequest item : items) {
            double bottom = item.y() + item.height();
            maxBottom = Math.max(maxBottom, bottom);
        }

        return maxBottom + bottomPaddingInch;
    }

    private void validateRequest(List<GangSheetItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        if (items.size() > maxItemsPerRequest) {
            throw new IllegalArgumentException(
                    "Too many items in request. count=" + items.size()
                            + ", max=" + maxItemsPerRequest
            );
        }

        if (sheetWidthInch <= 0) {
            throw new IllegalArgumentException("sheetWidthInch must be greater than 0");
        }

        if (bottomPaddingInch < 0) {
            throw new IllegalArgumentException("bottomPaddingInch must be greater than or equal to 0");
        }

        if (imageRenderDpi <= 0) {
            throw new IllegalArgumentException("imageRenderDpi must be greater than 0");
        }

        if (maxRasterPixels <= 0) {
            throw new IllegalArgumentException("maxRasterPixels must be greater than 0");
        }
    }

    private void enforceLocalSizeBudget(List<GangSheetItemRequest> items) throws IOException {
        Set<String> seen = new HashSet<>();
        long totalLocalBytes = 0;

        for (GangSheetItemRequest item : items) {
            if (!seen.add(item.img())) {
                continue;
            }
            long size = assetStorageService.peekLocalSize(item.img());
            if (size > 0) {
                totalLocalBytes += size;
            }
        }

        if (totalLocalBytes > maxTotalBytesPerRequest) {
            throw new IllegalArgumentException(
                    "Total local image size exceeds limit. totalBytes=" + totalLocalBytes
                            + ", maxBytes=" + maxTotalBytesPerRequest
            );
        }
    }

    private void enforceLoadedSizeBudget(List<ImageAsset> imageAssets) {
        Set<String> seen = new HashSet<>();
        long totalBytes = 0;

        for (ImageAsset asset : imageAssets) {
            if (!seen.add(asset.source())) {
                continue;
            }
            totalBytes += asset.bytes().length;
        }

        if (totalBytes > maxTotalBytesPerRequest) {
            throw new IllegalArgumentException(
                    "Total image size exceeds limit. totalBytes=" + totalBytes
                            + ", maxBytes=" + maxTotalBytesPerRequest
            );
        }
    }

    private void validateItemsForSheetCalculation(List<GangSheetItemRequest> items) {
        for (int i = 0; i < items.size(); i++) {
            validateItemForSheetCalculation(items.get(i), i);
        }
    }

    private void validateItemForSheetCalculation(GangSheetItemRequest item, int index) {
        if (item == null) {
            throw new IllegalArgumentException("Item at index " + index + " must not be null");
        }

        if (item.img() == null || item.img().isBlank()) {
            throw new IllegalArgumentException("img at index " + index + " must not be blank");
        }

        if (!Double.isFinite(item.x())
                || !Double.isFinite(item.y())
                || !Double.isFinite(item.width())
                || !Double.isFinite(item.height())
                || !Double.isFinite(item.rotation())) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " has non-finite geometry"
            );
        }

        if (item.x() < 0 || item.y() < 0) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " has negative x or y"
            );
        }

        if (item.width() <= 0 || item.height() <= 0) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " must have width/height > 0"
            );
        }
    }

    private void validateItemsInsideSheet(
            List<GangSheetItemRequest> items,
            double sheetWidth,
            double sheetHeight
    ) {
        for (int i = 0; i < items.size(); i++) {
            validateItemInsideSheet(items.get(i), sheetWidth, sheetHeight, i);
        }
    }

    private void validateItemInsideSheet(
            GangSheetItemRequest item,
            double sheetWidth,
            double sheetHeight,
            int index
    ) {
        if (item.x() + item.width() > sheetWidth) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " exceeds sheet width. "
                            + "x + width = " + (item.x() + item.width())
                            + ", sheetWidth = " + sheetWidth
            );
        }

        if (item.y() + item.height() > sheetHeight) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " exceeds sheet height. "
                            + "y + height = " + (item.y() + item.height())
                            + ", sheetHeight = " + sheetHeight
            );
        }
    }

    private void addDpiWarningIfNeeded(
            List<String> warnings,
            ImageAsset imageAsset,
            GangSheetItemRequest item,
            int index
    ) {
        double actualDpiX = imageAsset.width() / item.width();
        double actualDpiY = imageAsset.height() / item.height();

        double actualDpi = Math.min(actualDpiX, actualDpiY);

        if (actualDpi < DEFAULT_WARNING_DPI) {
            warnings.add(
                    "Item at index " + index
                            + " has low DPI. actualDpi="
                            + String.format("%.2f", actualDpi)
                            + ", recommendedDpi>=" + DEFAULT_WARNING_DPI
            );
        }
    }

    private float inchToPoint(double inch) {
        return (float) (inch * POINT_PER_INCH);
    }

    private double normalizeRotation(double rotationDegree) {
        double normalized = rotationDegree % 360;

        if (normalized < 0) {
            normalized += 360;
        }

        return normalized;
    }

    private String buildOutputFileName() {
        return "gang-sheet-" + UUID.randomUUID() + ".pdf";
    }

    private record RenderSize(int width, int height) {
    }
}
