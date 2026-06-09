package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.exception.*;
import com.example.dtfgangsheet.model.ImageAsset;
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

/**
 * Load ảnh parallel bằng virtual threads, deduplicate theo source URL/path.
 *
 * <p>Được dùng chung bởi {@link GangSheetPdfService} và {@link NestingService}
 * — trước đây mỗi service có logic load riêng, nay gom về đây.</p>
 *
 * <h3>Kết quả trả về</h3>
 *  trả về List có cùng size và thứ tự với input —
 * cùng source → cùng {@link ImageAsset} object (immutable record, thread-safe).
 *
 * <h3>Error handling</h3>
 * Nếu bất kỳ source nào fail, cleanup toàn bộ assets đã load rồi throw
 * {@link ImageBatchLoadException} với chi tiết từng lỗi.
 */
@Service
public class ImageLoadingService {

    private static final Logger log = LoggerFactory.getLogger(ImageLoadingService.class);

    private final AssetStorageService assetStorageService;

    public ImageLoadingService(AssetStorageService assetStorageService) {
        this.assetStorageService = assetStorageService;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Load ảnh theo thứ tự của {@code sources}, deduplicate — cùng source chỉ load 1 lần.
     *
     * @param sources  danh sách source URL/path, có thể trùng lặp
     * @param indices  map từ source → list index trong sources (dùng để build error detail)
     * @return List<ImageAsset> cùng size/thứ tự với sources
     * @throws ImageBatchLoadException nếu có bất kỳ source nào fail
     */
    public List<ImageAsset> loadOrdered(List<String> sources,
                                        Map<String, List<Integer>> indices) {
        List<String> unique = sources.stream().distinct().toList();

        Map<String, CompletableFuture<ImageAsset>> futures = submitAll(unique);
        LoadResult result = collectResults(futures, indices);

        if (!result.errors().isEmpty()) {
            assetStorageService.cleanup(result.loaded());
            throw new ImageBatchLoadException(result.errors());
        }

        // Map lại theo thứ tự sources — cùng source → cùng ImageAsset object
        return sources.stream()
                .map(src -> result.bySource().get(src))
                .toList();
    }

    /**
     * Overload tiện lợi — tự build indices từ sources.
     * Dùng khi caller không cần indices cho mục đích khác.
     */
    public List<ImageAsset> loadOrdered(List<String> sources) {
        Map<String, List<Integer>> indices = buildIndices(sources);
        return loadOrdered(sources, indices);
    }

    private static Map<String, List<Integer>> buildIndices(List<String> sources) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            map.computeIfAbsent(sources.get(i), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    /**
     * Cleanup temp assets đã load — dùng khi caller muốn cleanup thủ công.
     * Guard bằng Set<Path> trong AssetStorageService nên an toàn khi có duplicate.
     */
    public void cleanup(List<ImageAsset> assets) {
        if (assets == null || assets.isEmpty()) return;
        try {
            assetStorageService.cleanup(assets.stream().distinct().toList());
        } catch (Exception e) {
            log.warn("Failed to cleanup temp assets", e);
        }
    }

    /**
     * Enforce total size budget sau khi load.
     * Deduplicate theo source trước khi tính tổng.
     */
    public void enforceLoadedSizeBudget(List<ImageAsset> assets, long maxTotalBytes) {
        long totalBytes = 0;
        var seen = new java.util.HashSet<String>();

        for (ImageAsset asset : assets) {
            if (asset == null || asset.source() == null || asset.source().isBlank()) continue;
            if (!seen.add(asset.source())) continue;
            if (asset.sizeBytes() <= 0) continue;

            totalBytes += asset.sizeBytes();

            if (totalBytes > maxTotalBytes) {
                log.warn("Loaded image size budget exceeded: totalBytes={} maxBytes={}",
                        totalBytes, maxTotalBytes);
                throw new ImageSizeExceededException(
                        "Total image size exceeds limit. totalBytes=%d, maxBytes=%d"
                                .formatted(totalBytes, maxTotalBytes));
            }
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private Map<String, CompletableFuture<ImageAsset>> submitAll(List<String> sources) {
        Map<String, CompletableFuture<ImageAsset>> futures = new LinkedHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String src : sources) {
                futures.put(src, CompletableFuture.supplyAsync(
                        () -> loadWithTiming(src), executor));
            }
        }
        return futures;
    }

    private LoadResult collectResults(Map<String, CompletableFuture<ImageAsset>> futures,
                                      Map<String, List<Integer>> indices) {
        Map<String, ImageAsset> bySource = new LinkedHashMap<>();
        List<ImageAsset>        loaded   = new ArrayList<>();
        List<ApiErrorDetail>    errors   = new ArrayList<>();

        for (var entry : futures.entrySet()) {
            String src = entry.getKey();
            try {
                ImageAsset asset = joinFuture(entry.getValue(), src);
                bySource.put(src, asset);
                loaded.add(asset);
            } catch (ImageLoadException ex) {
                log.warn("Failed to load image: src={}", src, ex);
                ApiResultCode code = resolveErrorCode(ex);
                for (int idx : indices.getOrDefault(src, List.of(-1))) {
                    errors.add(ApiErrorDetail.imageError(idx, src, code.getCode(), code.getMessage()));
                }
            }
        }

        return new LoadResult(bySource, loaded, errors);
    }

    private ImageAsset loadWithTiming(String source) {
        long t0    = System.nanoTime();
        ImageAsset asset = assetStorageService.load(source);
        log.debug("Loaded: {}ms bytes={} {}x{} format={} src={}",
                (System.nanoTime() - t0) / 1_000_000,
                asset.sizeBytes(), asset.width(), asset.height(),
                asset.format(), source);
        return asset;
    }

    private ImageAsset joinFuture(CompletableFuture<ImageAsset> future, String source) {
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

    private record LoadResult(
            Map<String, ImageAsset> bySource,
            List<ImageAsset>        loaded,
            List<ApiErrorDetail>    errors
    ) {}
}