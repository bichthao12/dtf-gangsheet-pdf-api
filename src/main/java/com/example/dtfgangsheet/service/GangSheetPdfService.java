package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.*;
import com.example.dtfgangsheet.exception.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final double EPSILON_INCH = 0.0001;

    private final AssetStorageService assetStorageService;

    private final String outputDir;

    private final double sheetWidthInch;

    private final double bottomPaddingInch;

    private final int imageRenderDpi;

    private final long maxRasterPixels;

    private final long maxTotalBytesPerRequest;

    private final int maxItemsPerRequest;

    private final int maxItemSizeInch;

    private final int maxPositionInch;

    private final int warningDpi;

    public GangSheetPdfService(
            AssetStorageService assetStorageService,
            ImageProperties imageProps,
            PdfProperties pdfProps
    ) {
        this.assetStorageService = assetStorageService;
        this.outputDir = pdfProps.outputDir();
        this.sheetWidthInch = pdfProps.sheetWidthInch();
        this.bottomPaddingInch = pdfProps.bottomPaddingInch();
        this.imageRenderDpi = imageProps.renderDpi();
        this.maxRasterPixels = imageProps.maxRasterPixels();
        this.maxTotalBytesPerRequest = imageProps.maxTotalBytesPerRequest();
        this.maxItemsPerRequest = imageProps.maxItemsPerRequest();
        this.maxItemSizeInch = imageProps.maxItemSizeInch();
        this.maxPositionInch = imageProps.maxPositionInch();
        warningDpi = imageProps.warningDpi();
    }

    /**
     * Generates a gang sheet PDF from the requested items.
     */
    public GeneratePdfResponse generate(List<GangSheetItemRequest> items) {
        long tStart = System.nanoTime();

        validateInputs(items);
        SheetLayout layout = computeLayout(items);

        String id = UUID.randomUUID().toString();
        String name = buildOutputFileName(id);
        Path output = Path.of(outputDir).resolve(name).normalize();

        List<ImageAsset> assets = null;
        try {
            assets = loadImages(items);
            enforceLoadedSizeBudget(assets);
            long tLoaded = System.nanoTime();

            List<String> warnings = new ArrayList<>();
            buildPdf(output, layout, items, assets, warnings);

            log.debug("PDF: load={}ms build={}ms total={}ms items={}",
                    ms(tLoaded - tStart),
                    ms(System.nanoTime() - tLoaded),
                    ms(System.nanoTime() - tStart),
                    items.size());

            return new GeneratePdfResponse(
                    id, name, buildDownloadUrl(id), items.size(),
                    new PdfSheetResponse(layout.widthInch(), layout.heightInch(), "INCH"),
                    warnings.isEmpty() ? null : warnings
            );
        } catch (IOException e) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException cleanupError) {
                log.warn("Failed to delete partial PDF: id={} outputPath={}",
                        id, output, cleanupError);
            }

            log.error("PDF generation failed: id={} outputPath={} items={}",
                    id, output, items.size(), e);

            throw new ServerException(ApiResultCode.PDF_GENERATION_FAILED);

        } finally {
            try {
                assetStorageService.cleanupTempAssets(assets);
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup temp image assets: id={}", id, cleanupError);
            }
        }
    }

    /**
     * Validates request-level limits before doing any image I/O.
     */
    private void validateInputs(List<GangSheetItemRequest> items) {
        validateRequest(items);
        validateItemsForSheetCalculation(items);
        enforceLocalSizeBudget(items);
    }

    /**
     * Computes sheet height and validates that all items fit the sheet.
     */
    private SheetLayout computeLayout(List<GangSheetItemRequest> items) {
        double heightInch = calculateSheetHeight(items);
        validateItemsInsideSheet(items, sheetWidthInch, heightInch);
        return new SheetLayout(sheetWidthInch, heightInch);
    }

    private void buildPdf(
            Path outputPath,
            SheetLayout layout,
            List<GangSheetItemRequest> items,
            List<ImageAsset> assets,
            List<String> warnings
    ) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempOutput = outputPath.resolveSibling(
                outputPath.getFileName() + ".tmp"
        );

        long t0 = System.nanoTime();

        try {
            try (PDDocument doc = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                PDPage page = new PDPage(
                        new PDRectangle(layout.widthPt(), layout.heightPt())
                );
                doc.addPage(page);

                Map<String, PDImageXObject> imageCache = new HashMap<>();

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    embedItems(cs, doc, layout, items, assets, imageCache, warnings);
                }

                long tEmbedded = System.nanoTime();

                doc.save(tempOutput.toFile());

                Files.move(
                        tempOutput,
                        outputPath,
                        StandardCopyOption.REPLACE_EXISTING
                );

                long tSaved = System.nanoTime();

                log.debug("PDF build: embed={}ms save={}ms total={}ms items={} outputPath={}",
                        ms(tEmbedded - t0),
                        ms(tSaved - tEmbedded),
                        ms(tSaved - t0),
                        items.size(),
                        outputPath);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempOutput);
            throw e;
        }
    }

    private void embedItems(PDPageContentStream cs, PDDocument doc, SheetLayout layout, List<GangSheetItemRequest> items,
                            List<ImageAsset> assets, Map<String, PDImageXObject> imageCache, List<String> warnings) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            GangSheetItemRequest item = items.get(i);
            ImageAsset asset = assets.get(i);

            addDpiWarningIfNeeded(warnings, asset, item, i);

            long t0 = System.nanoTime();
            PDImageXObject pdfImage = getOrCreatePdfImage(doc, imageCache, asset, item);
            log.debug("embedded image {}/{}: {}ms src={}", i + 1, items.size(), ms(System.nanoTime() - t0), asset.source());

            drawItemExactlyByFeLayout(cs, pdfImage, item, layout.heightInch());
        }
    }

    private String buildDownloadUrl(String id) {
        return "/api/gang-sheets/" + id + "/download";
    }

    /**
     * Draws one image in PDF coordinates using the exact FE layout.
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

    private double normalizeRotation(double rotationDegree) {
        double normalized = rotationDegree % 360;

        if (normalized < 0) {
            normalized += 360;
        }

        return normalized;
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
        DecodeSize decodeSize = resolveDecodeSize(imageAsset, item);
        BufferedImage decoded = assetStorageService.decodeForRender(
                imageAsset,
                decodeSize.width(),
                decodeSize.height()
        );

        try {
            BufferedImage normalized = toRgbOrArgb(decoded);
            try {
                return LosslessFactory.createFromImage(document, normalized);
            } finally {
                if (normalized != decoded) normalized.flush();
            }
        } finally {
            decoded.flush();
        }
    }

    private BufferedImage toRgbOrArgb(BufferedImage src) {
        int targetType = src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;

        if (src.getType() == targetType) {
            return src;
        }

        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), targetType);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /**
     * Chooses a bounded decode size for PDF rendering.
     */
    private DecodeSize resolveDecodeSize(ImageAsset imageAsset, GangSheetItemRequest item) {
        int targetWidth = Math.clamp((int) Math.ceil(item.width() * imageRenderDpi), 1,
                imageAsset.width());
        int targetHeight = Math.clamp((int) Math.ceil(item.height() * imageRenderDpi), 1,
                imageAsset.height());

        long targetPixels = (long) targetWidth * targetHeight;

        if (targetPixels > maxRasterPixels) {
            double scale = Math.sqrt((double) maxRasterPixels / targetPixels);
            targetWidth = Math.max(1, (int) Math.floor(targetWidth * scale));
            targetHeight = Math.max(1, (int) Math.floor(targetHeight * scale));
        }

        return new DecodeSize(targetWidth, targetHeight);
    }

    private record DecodeSize(int width, int height) {
    }

    private String buildPdfImageCacheKey(ImageAsset imageAsset, GangSheetItemRequest item) {
        String pathKey = imageAsset.path()
                .toAbsolutePath()
                .normalize()
                .toString();

        DecodeSize renderSize = resolveDecodeSize(imageAsset, item);

        return "%s|%s#%dx%d".formatted(
                imageAsset.format().name(), pathKey,
                renderSize.width(), renderSize.height()
        );
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

        if (actualDpi < warningDpi) {
            warnings.add(
                    "Item at index " + index
                            + " has low DPI. actualDpi="
                            + String.format("%.2f", actualDpi)
                            + ", recommendedDpi>=" + warningDpi
            );
        }
    }

    private void enforceLoadedSizeBudget(List<ImageAsset> imageAssets) {
        Set<String> seen = new HashSet<>();
        long totalBytes = 0;

        for (ImageAsset asset : imageAssets) {
            if (asset == null || asset.source() == null || asset.source().isBlank()) {
                continue;
            }

            if (!seen.add(asset.source())) {
                continue;
            }

            long sizeBytes = asset.sizeBytes();

            if (sizeBytes <= 0) {
                continue;
            }

            totalBytes += sizeBytes;

            if (totalBytes > maxTotalBytesPerRequest) {
                log.warn("Loaded image size budget exceeded: totalBytes={} maxBytes={}",
                        totalBytes, maxTotalBytesPerRequest);

                throw new ImageSizeExceededException(
                        "Total image size exceeds limit. totalBytes=%d, maxBytes=%d"
                                .formatted(totalBytes, maxTotalBytesPerRequest)
                );
            }
        }
    }

    private List<ImageAsset> loadImages(List<GangSheetItemRequest> items) {
        Map<String, List<Integer>> indicesBySource = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            indicesBySource.computeIfAbsent(items.get(i).img(), k -> new ArrayList<>()).add(i);
        }

        LinkedHashMap<String, CompletableFuture<ImageAsset>> futuresBySource = new LinkedHashMap<>();
        List<ImageAsset> loadedUniqueAssets = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (GangSheetItemRequest item : items) {
                futuresBySource.computeIfAbsent(
                        item.img(),
                        source -> CompletableFuture.supplyAsync(
                                () -> loadAssetWithTiming(source), executor));
            }

            Map<String, ImageAsset> imagesBySource = new HashMap<>(futuresBySource.size());
            loadedUniqueAssets = new ArrayList<>();
            List<ApiErrorDetail> imageErrors = new ArrayList<>();

            for (Map.Entry<String, CompletableFuture<ImageAsset>> entry : futuresBySource.entrySet()) {
                String source = entry.getKey();
                try {
                    ImageAsset asset = joinImageFuture(entry.getValue(), source);
                    imagesBySource.put(source, asset);
                    loadedUniqueAssets.add(asset);
                } catch (ImageLoadException ex) {
                    log.warn("failed to load image src={}", source, ex);
                    ApiResultCode errorCode = resolveImageErrorCode(ex);
                    for (int idx : indicesBySource.getOrDefault(source, List.of(-1))) {
                        imageErrors.add(ApiErrorDetail.imageError(
                                idx,
                                source,
                                errorCode.getCode(),
                                errorCode.getMessage()
                        ));
                    }
                }
            }

            if (!imageErrors.isEmpty()) {
                assetStorageService.cleanupTempAssets(loadedUniqueAssets);
                log.warn("Image batch load failed: {}/{} source(s) could not be loaded",
                        imageErrors.stream().map(ApiErrorDetail::source).distinct().count(),
                        futuresBySource.size());
                throw new ImageBatchLoadException(imageErrors);
            }

            List<ImageAsset> sourceImages = new ArrayList<>(items.size());
            for (GangSheetItemRequest item : items) {
                sourceImages.add(imagesBySource.get(item.img()));
            }
            return sourceImages;
        } catch (RuntimeException e) {
            assetStorageService.cleanupTempAssets(loadedUniqueAssets);
            throw e;
        }
    }

    private ApiResultCode resolveImageErrorCode(Exception ex) {
        if (ex instanceof ImageNotFoundException) return ApiResultCode.IMAGE_NOT_FOUND;
        if (ex instanceof ImageSizeExceededException) return ApiResultCode.IMAGE_SIZE_EXCEEDED;
        if (ex instanceof UnsupportedImageFormatException) return ApiResultCode.UNSUPPORTED_IMAGE_FORMAT;
        if (ex instanceof TransientImageLoadException) return ApiResultCode.IMAGE_FETCH_ERROR;
        return ApiResultCode.BAD_REQUEST;
    }

    private ImageAsset joinImageFuture(CompletableFuture<ImageAsset> future, String source) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new ImageLoadException("Failed to load image: " + source, cause);
        }
    }

    private ImageAsset loadAssetWithTiming(String source) {
        long t0 = System.nanoTime();
        ImageAsset image = assetStorageService.loadAsset(source);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        log.debug("loaded image: {}ms bytes={} {}x{} format={} src={}",
                elapsedMs, image.sizeBytes(), image.width(), image.height(),
                image.format(), source);
        return image;
    }

    private String buildOutputFileName(String id) {
        return "gang-sheet-" + id + ".pdf";
    }

    private float inchToPoint(double inch) {
        return (float) (inch * POINT_PER_INCH);
    }

    private void validateItemsInsideSheet(
            List<GangSheetItemRequest> items,
            double sheetWidth,
            double sheetHeight
    ) {
        List<ApiErrorDetail> details = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            collectItemLayoutErrors(
                    items.get(i),
                    sheetWidth,
                    sheetHeight,
                    i,
                    details
            );
        }

        if (!details.isEmpty()) {
            throw new GangSheetLayoutException(details);
        }
    }

    private void collectItemLayoutErrors(
            GangSheetItemRequest item,
            double sheetWidth,
            double sheetHeight,
            int index,
            List<ApiErrorDetail> details
    ) {
        if (item.width() <= 0 || item.height() <= 0) {
            details.add(new ApiErrorDetail(
                    "INVALID_ITEM_SIZE",
                    "items[" + index + "]",
                    "Item at index " + index + " has invalid size. "
                            + "width=" + item.width()
                            + ", height=" + item.height()
            ));
            return;
        }

        RotatedBounds bounds = calculateRotatedBounds(item);

        if (bounds.left() < -EPSILON_INCH) {
            details.add(new ApiErrorDetail(
                    "ITEM_EXCEEDS_SHEET_LEFT",
                    "items[" + index + "].x",
                    "Item at index " + index
                            + " exceeds sheet left boundary. left=" + bounds.left()
            ));
        }

        if (bounds.top() < -EPSILON_INCH) {
            details.add(new ApiErrorDetail(
                    "ITEM_EXCEEDS_SHEET_TOP",
                    "items[" + index + "].y",
                    "Item at index " + index
                            + " exceeds sheet top boundary. top=" + bounds.top()
            ));
        }

        if (bounds.right() > sheetWidth + EPSILON_INCH) {
            details.add(new ApiErrorDetail(
                    "ITEM_EXCEEDS_SHEET_RIGHT",
                    "items[" + index + "].x",
                    "Item at index " + index
                            + " exceeds sheet right boundary. right=" + bounds.right()
                            + ", sheetWidth=" + sheetWidth
            ));
        }

        if (bounds.bottom() > sheetHeight + EPSILON_INCH) {
            details.add(new ApiErrorDetail(
                    "ITEM_EXCEEDS_SHEET_BOTTOM",
                    "items[" + index + "].y",
                    "Item at index " + index
                            + " exceeds sheet bottom boundary. bottom=" + bounds.bottom()
                            + ", sheetHeight=" + sheetHeight
            ));
        }
    }

    private double calculateSheetHeight(List<GangSheetItemRequest> items) {
        double maxBottom = 0.0;

        for (GangSheetItemRequest item : items) {
            RotatedBounds bounds = calculateRotatedBounds(item);
            maxBottom = Math.max(maxBottom, bounds.bottom());
        }

        return maxBottom + bottomPaddingInch;
    }

    private RotatedBounds calculateRotatedBounds(GangSheetItemRequest item) {
        double width = item.width();
        double height = item.height();

        double centerX = item.x() + width / 2.0;
        double centerY = item.y() + height / 2.0;

        double rotationRadians = Math.toRadians(normalizeRotation(item.rotation()));

        double sin = Math.abs(Math.sin(rotationRadians));
        double cos = Math.abs(Math.cos(rotationRadians));

        double rotatedWidth = width * cos + height * sin;
        double rotatedHeight = width * sin + height * cos;

        double left = centerX - rotatedWidth / 2.0;
        double top = centerY - rotatedHeight / 2.0;
        double right = centerX + rotatedWidth / 2.0;
        double bottom = centerY + rotatedHeight / 2.0;

        return new RotatedBounds(left, top, right, bottom);
    }

    private record RotatedBounds(
            double left,
            double top,
            double right,
            double bottom
    ) {
    }

    private void enforceLocalSizeBudget(List<GangSheetItemRequest> items) {
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
            log.warn("Local image size budget exceeded: totalBytes={} maxBytes={}",
                    totalLocalBytes, maxTotalBytesPerRequest);
            throw new ImageSizeExceededException(
                    "Total image size exceeds limit. totalBytes=%d, maxBytes=%d"
                            .formatted(totalLocalBytes, maxTotalBytesPerRequest)
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
            throw new IllegalArgumentException(
                    "Item at index " + index + " must not be null"
            );
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

        if (item.width() > maxItemSizeInch || item.height() > maxItemSizeInch) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " exceeds max item size"
            );
        }

        if (item.x() > maxPositionInch || item.y() > maxPositionInch) {
            throw new IllegalArgumentException(
                    "Item at index " + index + " exceeds max position"
            );
        }
    }

    private void validateRequest(List<GangSheetItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        if (items.size() > maxItemsPerRequest) {
            throw new IllegalArgumentException(
                    "Too many items in request. count=" + items.size() + ", max=" + maxItemsPerRequest
            );

        }
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    private record SheetLayout(double widthInch, double heightInch) {
        float widthPt() {
            return (float) (widthInch * POINT_PER_INCH);
        }

        float heightPt() {
            return (float) (heightInch * POINT_PER_INCH);
        }
    }
}
