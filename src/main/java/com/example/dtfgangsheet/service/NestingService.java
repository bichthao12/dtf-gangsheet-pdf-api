package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.request.NestingRequest;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.response.NestingResponse;
import com.example.dtfgangsheet.mapper.NestingInputMapper;
import com.example.dtfgangsheet.model.ImageAsset;
import com.example.dtfgangsheet.model.NestingInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NestingService {

    private static final Logger log = LoggerFactory.getLogger(NestingService.class);

    private final ImageLoadingService    imageLoadingService;
    private final MaxRectsNestingService nestingEngine;
    private final GangSheetPdfService    pdfService;

    public NestingService(ImageLoadingService imageLoadingService,
                          MaxRectsNestingService nestingEngine,
                          GangSheetPdfService pdfService) {
        this.imageLoadingService = imageLoadingService;
        this.nestingEngine       = nestingEngine;
        this.pdfService          = pdfService;
    }

    public GeneratePdfResponse nestAndGenerate(List<NestingRequest> requests) {
        long tApi = System.nanoTime();

        List<NestingInput> items = NestingInputMapper.toModels(requests);
        logStats(items);

        List<String> sources = items.stream().map(NestingInput::img).toList();

        long tLoad = System.nanoTime();
        List<ImageAsset> assets = imageLoadingService.loadOrdered(sources);
        log.debug("load: {}ms count={}", ms(System.nanoTime() - tLoad), sources.stream().distinct().count());

        try {
            long tNest = System.nanoTime();
            NestingResponse layout = nestingEngine.nest(items, assets);
            log.debug("nesting: {}ms placed={}/{} usage={}% rotated={}",
                    ms(System.nanoTime() - tNest),
                    layout.stats().totalPlaced(),
                    layout.stats().totalRequested(),
                    String.format("%.1f", layout.stats().usagePercent()),
                    layout.stats().rotatedCount());

            GeneratePdfResponse response = pdfService.generateWithAssets(layout.items(), assets);

            log.debug("api total [POST /api/nesting]: {}ms slots={} pdfId={}",
                    ms(System.nanoTime() - tApi),
                    items.stream().mapToInt(NestingInput::quantity).sum(),
                    response.id());

            return response;
        } finally {
            imageLoadingService.cleanup(assets);
        }
    }

    private long ms(long nanos) { return nanos / 1_000_000; }

    private void logStats(List<NestingInput> requests) {
        long unique     = requests.stream().map(NestingInput::img).distinct().count();
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