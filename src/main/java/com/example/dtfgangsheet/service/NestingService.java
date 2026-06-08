package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.ApiErrorDetail;
import com.example.dtfgangsheet.dto.ApiResultCode;
import com.example.dtfgangsheet.dto.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.ImageAsset;
import com.example.dtfgangsheet.dto.NestingRequest;
import com.example.dtfgangsheet.dto.NestingResponse;
import com.example.dtfgangsheet.exception.ImageBatchLoadException;
import com.example.dtfgangsheet.exception.ImageLoadException;
import com.example.dtfgangsheet.exception.ImageNotFoundException;
import com.example.dtfgangsheet.exception.ImageSizeExceededException;
import com.example.dtfgangsheet.exception.TransientImageLoadException;
import com.example.dtfgangsheet.exception.UnsupportedImageFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

@Service
public class NestingService {

    private static final Logger log = LoggerFactory.getLogger(NestingService.class);

    private final AssetStorageService   assetStorageService;
    private final MaxRectsNestingService maxRectsNestingService;
    private final GangSheetPdfService   pdfService;

    public NestingService(AssetStorageService assetStorageService,
                          MaxRectsNestingService nestingEngine,
                          GangSheetPdfService pdfService) {
        this.assetStorageService = assetStorageService;
        this.maxRectsNestingService = nestingEngine;
        this.pdfService          = pdfService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public GeneratePdfResponse nestAndGenerate(List<NestingRequest> requests) {
        logStats(requests);
        List<ImageAsset> assets = loadUniqueAssets(requests);
        try {
            NestingResponse layout = maxRectsNestingService.nest(requests, assets);

            log.info("Nesting done — generating PDF: placed={} height={}in usage={}%",
                    layout.stats().totalPlaced(),
                    String.format("%.3f", layout.stats().sheetHeightInch()),
                    String.format("%.1f", layout.stats().usagePercent()));

            return pdfService.generate(layout.items());
        } finally {
            cleanup(assets);
        }
    }

    // -------------------------------------------------------------------------
    // Load ảnh — deduplicate, virtual threads
    // Pattern giống GangSheetPdfService.loadImages() nhưng keyed bằng source string
    // -------------------------------------------------------------------------

    private List<ImageAsset> loadUniqueAssets(List<NestingRequest> requests) {
        // Unique sources theo thứ tự xuất hiện đầu tiên
        List<String> uniqueSources = requests.stream()
                .map(NestingRequest::img)
                .distinct()
                .toList();

        // index trong requests của mỗi source (để build ApiErrorDetail đúng index)
        Map<String, List<Integer>> indicesBySource = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            indicesBySource
                    .computeIfAbsent(requests.get(i).img(), k -> new ArrayList<>())
                    .add(i);
        }

        // Submit song song bằng virtual threads
        Map<String, CompletableFuture<ImageAsset>> futures = new LinkedHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String src : uniqueSources) {
                futures.put(src, CompletableFuture.supplyAsync(
                        () -> loadWithTiming(src), executor));
            }
        }

        // Collect kết quả
        Map<String, ImageAsset> loaded           = new LinkedHashMap<>();
        List<ImageAsset>        loadedForCleanup = new ArrayList<>();
        List<ApiErrorDetail>    errors           = new ArrayList<>();

        for (var entry : futures.entrySet()) {
            String src = entry.getKey();
            try {
                ImageAsset asset = join(entry.getValue(), src);
                loaded.put(src, asset);
                loadedForCleanup.add(asset);
            } catch (ImageLoadException ex) {
                log.warn("Failed to load image: src={}", src, ex);
                ApiResultCode code = resolveErrorCode(ex);
                for (int idx : indicesBySource.getOrDefault(src, List.of(-1))) {
                    errors.add(ApiErrorDetail.imageError(idx, src, code.getCode(), code.getMessage()));
                }
            }
        }

        if (!errors.isEmpty()) {
            assetStorageService.cleanupTempAssets(loadedForCleanup);
            throw new ImageBatchLoadException(errors);
        }

        // Cùng img → cùng ImageAsset object (immutable record, safe)
        return requests.stream()
                .map(req -> loaded.get(req.img()))
                .toList();
    }

    private ImageAsset loadWithTiming(String source) {
        long t0 = System.nanoTime();
        ImageAsset asset = assetStorageService.loadAsset(source);
        log.debug("Loaded: {}ms bytes={} {}x{} format={} src={}",
                (System.nanoTime() - t0) / 1_000_000,
                asset.sizeBytes(), asset.width(), asset.height(),
                asset.format(), source);
        return asset;
    }

    private ImageAsset join(CompletableFuture<ImageAsset> future, String source) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new ImageLoadException("Failed to load image: " + source, cause);
        }
    }

    private ApiResultCode resolveErrorCode(Exception ex) {
        if (ex instanceof ImageNotFoundException)          return ApiResultCode.IMAGE_NOT_FOUND;
        if (ex instanceof ImageSizeExceededException)      return ApiResultCode.IMAGE_SIZE_EXCEEDED;
        if (ex instanceof UnsupportedImageFormatException) return ApiResultCode.UNSUPPORTED_IMAGE_FORMAT;
        if (ex instanceof TransientImageLoadException)     return ApiResultCode.IMAGE_FETCH_ERROR;
        return ApiResultCode.IMAGE_LOAD_ERROR;
    }

    private void cleanup(List<ImageAsset> assets) {
        if (assets == null) return;
        try {
            // distinct() tránh log warning thừa khi có duplicate img
            // AssetStorageService.cleanupTempAssets() đã guard bằng Set<Path>
            assetStorageService.cleanupTempAssets(assets.stream().distinct().toList());
        } catch (Exception e) {
            log.warn("Failed to cleanup nesting temp assets", e);
        }
    }

    private void logStats(List<NestingRequest> requests) {
        long unique     = requests.stream().map(NestingRequest::img).distinct().count();
        int  total      = requests.size();
        int  totalSlots = requests.stream().mapToInt(r -> Math.max(1, r.quantity())).sum();
        if (unique < total) {
            log.info("Nesting: {} requests ({} unique imgs, {} total slots, {} reuses)",
                    total, unique, totalSlots, total - unique);
        } else {
            log.info("Nesting: {} requests ({} total slots)", total, totalSlots);
        }
    }
}