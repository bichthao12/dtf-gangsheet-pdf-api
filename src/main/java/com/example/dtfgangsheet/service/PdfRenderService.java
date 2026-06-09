package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.model.GangSheetItem;
import com.example.dtfgangsheet.model.ImageAsset;
import com.example.dtfgangsheet.model.SheetLayout;
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
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDFBox render — nhận items đã có tọa độ + assets đã load, xuất file PDF ra disk.
 *
 * <p>Tách ra từ {@link GangSheetPdfService} để tuân thủ SRP:
 * class này chỉ làm PDFBox operations — không validate, không load ảnh.</p>
 */
@Service
public class PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);
    private static final double POINT_PER_INCH = 72.0;

    private final AssetStorageService assetStorageService;
    private final int    imageRenderDpi;
    private final long   maxRasterPixels;
    private final int    warningDpi;

    public PdfRenderService(AssetStorageService assetStorageService,
                            ImageProperties imageProps) {
        this.assetStorageService = assetStorageService;
        this.imageRenderDpi      = imageProps.renderDpi();
        this.maxRasterPixels     = imageProps.maxRasterPixels();
        this.warningDpi          = imageProps.warningDpi();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Render PDF ra {@code outputPath}.
     * Dùng write-then-move để đảm bảo atomic — không bao giờ để file partial.
     *
     * @return danh sách warning (DPI thấp, v.v.)
     */
    public List<String> render(Path outputPath,
                               SheetLayout layout,
                               List<GangSheetItem> items,
                               List<ImageAsset> assets) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tempOutput = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
        List<String> warnings = new ArrayList<>();
        long t0 = System.nanoTime();

        try {
            try (PDDocument doc = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                PDPage page = new PDPage(new PDRectangle(layout.widthPt(), layout.heightPt()));
                doc.addPage(page);

                Map<String, PDImageXObject> imageCache = new HashMap<>();

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    embedItems(cs, doc, layout, items, assets, imageCache, warnings);
                }

                long tEmbedded = System.nanoTime();
                doc.save(tempOutput.toFile());
                Files.move(tempOutput, outputPath, StandardCopyOption.REPLACE_EXISTING);

                log.debug("PDF render: embed={}ms save={}ms items={} outputPath={}",
                        ms(tEmbedded - t0),
                        ms(System.nanoTime() - tEmbedded),
                        items.size(), outputPath);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempOutput);
            throw e;
        }

        return warnings;
    }

    // =========================================================================
    // Embed items
    // =========================================================================

    private void embedItems(PDPageContentStream cs, PDDocument doc,
                            SheetLayout layout,
                            List<GangSheetItem> items,
                            List<ImageAsset> assets,
                            Map<String, PDImageXObject> imageCache,
                            List<String> warnings) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            GangSheetItem item  = items.get(i);
            ImageAsset           asset = assets.get(i);

            addDpiWarningIfNeeded(warnings, asset, item, i);

            long t0 = System.nanoTime();
            PDImageXObject pdfImage = getOrCreatePdfImage(doc, imageCache, asset, item);
            log.debug("embedded {}/{}: {}ms src={}", i + 1, items.size(),
                    ms(System.nanoTime() - t0), asset.source());

            drawItem(cs, pdfImage, item, layout.heightInch());
        }
    }

    private PDImageXObject getOrCreatePdfImage(PDDocument doc,
                                               Map<String, PDImageXObject> cache,
                                               ImageAsset asset,
                                               GangSheetItem item) throws IOException {
        DecodeSize decodeSize = resolveDecodeSize(asset, item);   // tính 1 lần
        String key = cacheKey(asset, decodeSize);                 // dùng decodeSize đã có
        PDImageXObject cached = cache.get(key);
        if (cached != null) return cached;

        PDImageXObject pdfImage = createPdfImage(doc, asset, item, decodeSize);  // truyền xuống
        cache.put(key, pdfImage);
        return pdfImage;
    }

    private PDImageXObject createPdfImage(PDDocument doc, ImageAsset asset,
                                          GangSheetItem item, DecodeSize decodeSize) throws IOException {
        log.debug("decodeSize: {}x{}px from {}x{}px src={}",
                decodeSize.width(), decodeSize.height(),
                asset.width(), asset.height(), asset.source());

        long tDecode = System.nanoTime();
        BufferedImage decoded = assetStorageService.decodeForRender(
                asset, decodeSize.width(), decodeSize.height());
        log.debug("decode: {}ms {}x{}px",
                ms(System.nanoTime() - tDecode),
                decoded.getWidth(), decoded.getHeight());

        try {
            long tFactory = System.nanoTime();
            PDImageXObject result;

            if (decoded.getColorModel().hasAlpha()) {
                // Có alpha (transparent PNG) → bắt buộc dùng LosslessFactory để giữ transparency
                BufferedImage normalized = toRgbOrArgb(decoded);
                try {
                    result = LosslessFactory.createFromImage(doc, normalized);
                } finally {
                    if (normalized != decoded) normalized.flush();
                }
            } else {
                // Không có alpha → JPEGFactory nhanh hơn ~10x
                // quality 0.95 = không phân biệt được bằng mắt ở 300 DPI
                result = JPEGFactory.createFromImage(doc, decoded, 0.95f);
            }

            log.debug("factory: {}ms hasAlpha={}",
                    ms(System.nanoTime() - tFactory),
                    decoded.getColorModel().hasAlpha());
            return result;
        } finally {
            decoded.flush();
        }
    }

    private void drawItem(PDPageContentStream cs,
                          PDImageXObject pdfImage,
                          GangSheetItem item,
                          double sheetHeightInch) throws IOException {
        float xPt          = pt(item.x());
        float topYPt       = pt(item.y());
        float widthPt      = pt(item.width());
        float heightPt     = pt(item.height());
        float centerXPt    = xPt + widthPt / 2.0f;
        float centerYPt    = pt(sheetHeightInch) - topYPt - heightPt / 2.0f;
        double rotRad      = Math.toRadians(-normalizeRotation(item.rotation()));

        cs.saveGraphicsState();
        try {
            cs.transform(Matrix.getTranslateInstance(centerXPt, centerYPt));
            if (rotRad != 0) cs.transform(Matrix.getRotateInstance(rotRad, 0, 0));
            cs.drawImage(pdfImage, -widthPt / 2.0f, -heightPt / 2.0f, widthPt, heightPt);
        } finally {
            cs.restoreGraphicsState();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private DecodeSize resolveDecodeSize(ImageAsset asset, GangSheetItem item) {
        int tw = Math.clamp((int) Math.ceil(item.width()  * imageRenderDpi), 1, asset.width());
        int th = Math.clamp((int) Math.ceil(item.height() * imageRenderDpi), 1, asset.height());

        long pixels = (long) tw * th;
        if (pixels > maxRasterPixels) {
            double scale = Math.sqrt((double) maxRasterPixels / pixels);
            tw = Math.max(1, (int) Math.floor(tw * scale));
            th = Math.max(1, (int) Math.floor(th * scale));
        }
        return new DecodeSize(tw, th);
    }

    private String cacheKey(ImageAsset asset, DecodeSize decodeSize) {
        return "%s|%s#%dx%d".formatted(
                asset.format().name(),
                asset.path().toAbsolutePath().normalize(),
                decodeSize.width(), decodeSize.height());
    }


    private BufferedImage toRgbOrArgb(BufferedImage src) {
        int type = src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (src.getType() == type) return src;

        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = dst.createGraphics();
        try { g.drawImage(src, 0, 0, null); } finally { g.dispose(); }
        return dst;
    }

    private void addDpiWarningIfNeeded(List<String> warnings,
                                       ImageAsset asset,
                                       GangSheetItem item,
                                       int index) {
        double dpi = Math.min(asset.width() / item.width(), asset.height() / item.height());
        if (dpi < warningDpi) {
            warnings.add("Item at index " + index + " has low DPI. actualDpi="
                    + String.format("%.2f", dpi) + ", recommendedDpi>=" + warningDpi);
        }
    }

    private double normalizeRotation(double deg) {
        double n = deg % 360;
        return n < 0 ? n + 360 : n;
    }

    private float pt(double inch) { return (float) (inch * POINT_PER_INCH); }
    private long  ms(long nanos)  { return nanos / 1_000_000; }

    private record DecodeSize(int width, int height) {}
}