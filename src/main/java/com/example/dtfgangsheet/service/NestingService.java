package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.request.NestingRequest;
import com.example.dtfgangsheet.dto.response.GeneratePdfResponse;
import com.example.dtfgangsheet.dto.response.NestingResponse;
import com.example.dtfgangsheet.mapper.GangSheetItemMapper;
import com.example.dtfgangsheet.mapper.NestingInputMapper;
import com.example.dtfgangsheet.model.GangSheetItem;
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
        List<NestingInput> items = NestingInputMapper.toModels(requests);

        logStats(items);

        List<String> sources = items.stream().map(NestingInput::img).toList();
        Map<String, List<Integer>> indices = buildIndices(items);

        List<ImageAsset> assets = imageLoadingService.loadOrdered(sources, indices);
        try {
            NestingResponse layout = nestingEngine.nest(items, assets);

            log.info("Nesting done — generating PDF: placed={} height={}in usage={}%",
                    layout.stats().totalPlaced(),
                    String.format("%.3f", layout.stats().sheetHeightInch()),
                    String.format("%.1f", layout.stats().usagePercent()));

            // Dùng generateWithAssets() — tái dụng assets đã load, không load lại lần 2
            return pdfService.generateWithAssets(layout.items(), assets);
        } finally {
            // NestingService chịu trách nhiệm cleanup — generateWithAssets() không cleanup
            imageLoadingService.cleanup(assets);
        }
    }

    private Map<String, List<Integer>> buildIndices(List<NestingInput> requests) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            map.computeIfAbsent(requests.get(i).img(), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

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