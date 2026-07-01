package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.mapper.GangSheetItemMapper;
import com.example.dtfgangsheet.model.GangSheetItem;
import com.example.dtfgangsheet.dto.request.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.response.PdfSheetResponse;
import com.example.dtfgangsheet.model.SheetSpec;
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
        long tApi = System.nanoTime();

        List<GangSheetItem> items = GangSheetItemMapper.toModels(requests);
        layoutService.validateRequest(items);

        List<String> sources = items.stream().map(GangSheetItem::img).toList();
        enforceLocalSizeBudget(sources);

        SheetLayout layout = layoutService.computePrintLayout(items);

        // Image load failures → ImageBatchLoadException → GlobalExceptionHandler (no catch here).
        // Cleanup on load failure is done inside ImageLoadingService before throw.
        long tLoad = System.nanoTime();
        List<ImageAsset> assets = imageLoadingService.loadOrdered(sources);
        log.debug("load: {}ms count={}", ms(System.nanoTime() - tLoad), sources.stream().distinct().count());

        try {
            imageLoadingService.enforceLoadedSizeBudget(assets, maxTotalBytesPerRequest);

            long tRender = System.nanoTime();
            GeneratePdfResponse response = doRender(items, assets, layout);
            log.debug("render: {}ms", ms(System.nanoTime() - tRender));
            log.debug("api total [POST /api/gang-sheets/pdf]: {}ms items={} pdfId={}",
                    ms(System.nanoTime() - tApi), items.size(), response.id());

            return response;
        } catch (IOException e) {
            log.error("PDF generation failed: items={}", items.size(), e);
            throw new ServerException(ApiResultCode.PDF_GENERATION_FAILED);
        } finally {
            imageLoadingService.cleanup(assets);
        }
    }

    public GeneratePdfResponse generateWithAssets(List<GangSheetItem> items,
                                                  List<ImageAsset> assets) {
        // assets từ NestingService chỉ chứa unique assets (deduplicated).
        // items có thể nhiều hơn assets do quantity expand.
        // Cần map lại: mỗi item → asset theo img source.
        Map<String, ImageAsset> assetBySource = new LinkedHashMap<>();
        for (ImageAsset asset : assets) {
            assetBySource.put(asset.source(), asset);
        }

        // Expand assets theo thứ tự items
        List<ImageAsset> expandedAssets = items.stream()
                .map(item -> assetBySource.get(item.img()))
                .toList();

        layoutService.validateRequest(items);
        imageLoadingService.enforceLoadedSizeBudget(expandedAssets, maxTotalBytesPerRequest);
        SheetLayout layout = layoutService.computePrintLayout(items);

        try {
            return doRender(items, expandedAssets, layout);
        } catch (IOException e) {
            log.error("PDF generation failed: items={}", items.size(), e);
            throw new ServerException(ApiResultCode.PDF_GENERATION_FAILED);
        }
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
        try {
            List<String> warnings = renderService.render(output, layout, items, assets);
            log.debug("PDF rendered: {}ms items={}", ms(System.nanoTime() - tStart), items.size());

            return new GeneratePdfResponse(
                    id, name, "/api/gang-sheets/" + id + "/download", items.size(),
                    new PdfSheetResponse(layout.widthInch(), layout.heightInch(), SheetSpec.UNIT_INCH),
                    warnings.isEmpty() ? null : warnings
            );
        } catch (IOException e) {
            // Xóa file partial ngay tại đây
            try { Files.deleteIfExists(output); }
            catch (IOException ignored) {
                log.warn("Failed to delete partial PDF: {}", output);
            }
            throw e;
        }
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

    private long ms(long nanos) { return nanos / 1_000_000; }
}