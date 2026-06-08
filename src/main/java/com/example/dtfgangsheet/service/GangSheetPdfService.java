package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.mapper.GangSheetItemMapper;
import com.example.dtfgangsheet.model.GangSheetItem;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.response.PdfSheetResponse;
import com.example.dtfgangsheet.exception.ImageSizeExceededException;
import com.example.dtfgangsheet.exception.ServerException;
import com.example.dtfgangsheet.model.ImageAsset;
import com.example.dtfgangsheet.model.SheetLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GangSheetPdfService {

    private static final Logger log = LoggerFactory.getLogger(GangSheetPdfService.class);

    private final AssetStorageService assetStorageService;
    private final ImageLoadingService imageLoadingService;
    private final PdfLayoutService    layoutService;
    private final PdfRenderService    renderService;

    private final String outputDir;
    private final long   maxTotalBytesPerRequest;

    public GangSheetPdfService(AssetStorageService assetStorageService,
                               ImageLoadingService imageLoadingService,
                               PdfLayoutService    layoutService,
                               PdfRenderService    renderService,
                               PdfProperties       pdfProps,
                               ImageProperties     imageProps) {
        this.assetStorageService     = assetStorageService;
        this.imageLoadingService     = imageLoadingService;
        this.layoutService           = layoutService;
        this.renderService           = renderService;
        this.outputDir               = pdfProps.outputDir();
        this.maxTotalBytesPerRequest = imageProps.maxTotalBytesPerRequest();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Entry point từ GangSheetController — tự load ảnh từ items.img.
     */
    public GeneratePdfResponse generate(List<GangSheetItemRequest> requests) {
        List<GangSheetItem> items = GangSheetItemMapper.toModels(requests);
        layoutService.validateRequest(items);

        // Extract sources 1 lần thay vì 3 lần
        List<String> sources = items.stream().map(GangSheetItem::img).toList();
        enforceLocalSizeBudget(sources);

        SheetLayout layout = layoutService.computeLayout(items);

        Map<String, List<Integer>> indices = buildIndices(sources);
        List<ImageAsset> assets = imageLoadingService.loadOrdered(sources, indices);

        String id     = UUID.randomUUID().toString();
        String name   = "gang-sheet-" + id + ".pdf";
        Path   output = Path.of(outputDir).resolve(name).normalize();

        try {
            imageLoadingService.enforceLoadedSizeBudget(assets, maxTotalBytesPerRequest);
            return doRender(items, assets, layout);
        } catch (IOException e) {
            deleteQuietly(output);   // ← thêm dòng này
            log.error("PDF generation failed: items={}", items.size(), e);
            throw new ServerException(ApiResultCode.PDF_GENERATION_FAILED);
        } finally {
            imageLoadingService.cleanup(assets);
        }
    }
    /**
     * Entry point từ NestingService — assets đã load sẵn, không load lại.
     * Tránh double-load ảnh khi nesting.
     */
    public GeneratePdfResponse generateWithAssets(List<GangSheetItem> items,
                                                  List<ImageAsset> assets) {
        // Convert GangSheetItem → GangSheetItemRequest để tái dụng layout/render
        List<GangSheetItem> requests = items.stream()
                .map(i -> new GangSheetItem(
                        i.img(), i.x(), i.y(), i.width(), i.height(), i.rotation(), i.dpi()))
                .toList();

        layoutService.validateRequest(requests);
        imageLoadingService.enforceLoadedSizeBudget(assets, maxTotalBytesPerRequest);

        SheetLayout layout = layoutService.computeLayout(requests);

        try {
            return doRender(requests, assets, layout);
        } catch (IOException e) {
            log.error("PDF generation failed: items={}", items.size(), e);
            throw new ServerException(ApiResultCode.PDF_GENERATION_FAILED);
        }
        // Không cleanup assets ở đây — NestingService chịu trách nhiệm cleanup
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private GeneratePdfResponse doRender(List<GangSheetItem> items,
                                         List<ImageAsset> assets,
                                         SheetLayout layout) throws IOException {
        String id     = UUID.randomUUID().toString();
        String name   = "gang-sheet-" + id + ".pdf";
        Path   output = Path.of(outputDir).resolve(name).normalize();

        long tStart = System.nanoTime();
        List<String> warnings = renderService.render(output, layout, items, assets);
        log.debug("PDF rendered: {}ms items={}", ms(System.nanoTime() - tStart), items.size());

        return new GeneratePdfResponse(
                id, name, "/api/gang-sheets/" + id + "/download", items.size(),
                new PdfSheetResponse(layout.widthInch(), layout.heightInch(), "INCH"),
                warnings.isEmpty() ? null : warnings
        );
    }

    private void enforceLocalSizeBudget(List<String> sources) {
        var seen  = new HashSet<String>();
        long total = 0;

        for (String src : sources) {
            if (!seen.add(src)) continue;
            long size = assetStorageService.peekLocalSize(src);
            if (size <= 0) continue;
            total += size;
            if (total > maxTotalBytesPerRequest) {
                log.warn("Local size budget exceeded: total={} max={}", total, maxTotalBytesPerRequest);
                throw new ImageSizeExceededException(
                        "Total image size exceeds limit. totalBytes=%d, maxBytes=%d"
                                .formatted(total, maxTotalBytesPerRequest));
            }
        }
    }

    private Map<String, List<Integer>> buildIndices(List<String> sources) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            map.computeIfAbsent(sources.get(i), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException e) { log.warn("Failed to delete partial PDF: {}", path); }
    }

    private long ms(long nanos) { return nanos / 1_000_000; }
}