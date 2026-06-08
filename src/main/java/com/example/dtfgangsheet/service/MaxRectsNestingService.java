package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.GangSheetItemRequest;
import com.example.dtfgangsheet.dto.ImageAsset;
import com.example.dtfgangsheet.dto.NestingRequest;
import com.example.dtfgangsheet.dto.NestingResponse;
import com.example.dtfgangsheet.dto.PdfSheetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MaxRectsNestingService {

    private static final Logger log = LoggerFactory.getLogger(MaxRectsNestingService.class);
    private static final double STRIP_INIT_HEIGHT = 1_000_000.0;

    private final double sheetWidthInch;
    private final double usableWidthInch;
    private final double bottomPaddingInch;
    private final double itemGapInch;
    private final int    renderDpi;

    public MaxRectsNestingService(PdfProperties pdfProps, ImageProperties imageProps) {
        this.sheetWidthInch    = pdfProps.sheetWidthInch();
        this.usableWidthInch   = pdfProps.sheetWidthInch() - pdfProps.rightPaddingInch();
        this.bottomPaddingInch = pdfProps.bottomPaddingInch();
        this.itemGapInch       = pdfProps.itemGapInch();
        this.renderDpi         = imageProps.renderDpi();
    }

    public NestingResponse nest(List<NestingRequest> requests, List<ImageAsset> assets) {
        List<Slot> slots = buildSlots(requests, assets);

        log.debug("Nesting: {} slots from {} requests, usableWidth={}in sheetWidth={}in gap={}in",
                slots.size(), requests.size(), usableWidthInch, sheetWidthInch, itemGapInch);

        slots.sort(Comparator.comparingDouble(Slot::area).reversed());

        List<FreeRect> freeRects = new ArrayList<>();
        freeRects.add(new FreeRect(0, 0, usableWidthInch, STRIP_INIT_HEIGHT));

        List<Placed> placed  = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Slot slot : slots) {
            Placement best = findBestPlacement(freeRects, slot.slotW(), slot.slotH());

            if (best == null) {
                log.warn("Cannot place: source={} slot={}x{}in",
                        slot.source(), slot.slotW(), slot.slotH());
                skipped.add(slot.source());
                continue;
            }

            double gap      = slot.gap();
            double imgOrigW = slot.slotW() - gap * 2;
            double imgOrigH = slot.slotH() - gap * 2;

            double bboxW = best.rotated() ? imgOrigH : imgOrigW;
            double bboxH = best.rotated() ? imgOrigW : imgOrigH;

            double imgX = best.x() + gap + bboxW / 2.0 - imgOrigW / 2.0;
            double imgY = best.y() + gap + bboxH / 2.0 - imgOrigH / 2.0;

            double placedSlotW = best.rotated() ? slot.slotH() : slot.slotW();
            double placedSlotH = best.rotated() ? slot.slotW() : slot.slotH();

            placed.add(new Placed(slot.source(), imgX, imgY, imgOrigW, imgOrigH, best.rotated()));
            splitAndPrune(freeRects, best.x(), best.y(), placedSlotW, placedSlotH);
        }

        return buildResponse(placed, skipped, slots.size());
    }

    // -------------------------------------------------------------------------
    // Build slots
    // -------------------------------------------------------------------------

    private List<Slot> buildSlots(List<NestingRequest> requests, List<ImageAsset> assets) {
        List<Slot> slots = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            NestingRequest req   = requests.get(i);
            ImageAsset     asset = assets.get(i);

            double w = req.width();
            double h = req.height();

            // Gap đồng nhất toàn sheet — từ config, không từ client
            double gap = itemGapInch;

            // Clamp: slot (ảnh + 2*gap) phải vừa usableWidthInch, giữ tỷ lệ
            if (w + gap * 2 > usableWidthInch) {
                double scale = usableWidthInch / (w + gap * 2);
                w = w * scale;
                h = h * scale;
                log.debug("Clamped to usableWidth: source={} -> {}x{}in", req.img(), w, h);
            }

            double slotW = w + gap * 2;
            double slotH = h + gap * 2;

            int qty = Math.max(1, req.quantity());
            for (int q = 0; q < qty; q++) {
                slots.add(new Slot(req.img(), slotW, slotH, gap));
            }
        }

        return slots;
    }

    // -------------------------------------------------------------------------
    // MAXRECTS BSSF
    // -------------------------------------------------------------------------

    private Placement findBestPlacement(List<FreeRect> freeRects, double w, double h) {
        Placement best      = null;
        double    bestScore = Double.MAX_VALUE;

        for (FreeRect fr : freeRects) {
            if (fr.w() >= w && fr.h() >= h) {
                double score = bssf(fr, w, h);
                if (score < bestScore) {
                    bestScore = score;
                    best = new Placement(fr.x(), fr.y(), false);
                }
            }
            if (Math.abs(w - h) > 1e-9 && fr.w() >= h && fr.h() >= w) {
                double score = bssf(fr, h, w);
                if (score < bestScore) {
                    bestScore = score;
                    best = new Placement(fr.x(), fr.y(), true);
                }
            }
        }

        return best;
    }

    private double bssf(FreeRect fr, double w, double h) {
        return Math.min(fr.w() - w, fr.h() - h);
    }

    // -------------------------------------------------------------------------
    // Guillotine split + prune dominated
    // -------------------------------------------------------------------------

    private void splitAndPrune(List<FreeRect> freeRects,
                               double px, double py, double pw, double ph) {
        List<FreeRect> toAdd    = new ArrayList<>();
        List<FreeRect> toRemove = new ArrayList<>();

        for (FreeRect fr : freeRects) {
            if (!overlaps(fr, px, py, pw, ph)) continue;
            toRemove.add(fr);

            if (px > fr.x())
                toAdd.add(new FreeRect(fr.x(), fr.y(), px - fr.x(), fr.h()));
            if (px + pw < fr.x() + fr.w())
                toAdd.add(new FreeRect(px + pw, fr.y(), fr.x() + fr.w() - (px + pw), fr.h()));
            if (py > fr.y())
                toAdd.add(new FreeRect(fr.x(), fr.y(), fr.w(), py - fr.y()));
            if (py + ph < fr.y() + fr.h())
                toAdd.add(new FreeRect(fr.x(), py + ph, fr.w(), fr.y() + fr.h() - (py + ph)));
        }

        freeRects.removeAll(toRemove);
        freeRects.addAll(toAdd);

        pruneDominated(freeRects);
    }

    private boolean overlaps(FreeRect fr, double px, double py, double pw, double ph) {
        return px < fr.x() + fr.w() && px + pw > fr.x()
                && py < fr.y() + fr.h() && py + ph > fr.y();
    }

    private void pruneDominated(List<FreeRect> rects) {
        List<FreeRect> dominated = new ArrayList<>();
        int n = rects.size();
        for (int i = 0; i < n; i++) {
            FreeRect a = rects.get(i);
            if (dominated.contains(a)) continue;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                FreeRect b = rects.get(j);
                if (b.x() <= a.x() && b.y() <= a.y()
                        && b.x() + b.w() >= a.x() + a.w()
                        && b.y() + b.h() >= a.y() + a.h()) {
                    dominated.add(a);
                    break;
                }
            }
        }
        rects.removeAll(dominated);
    }

    // -------------------------------------------------------------------------
    // Build response
    // -------------------------------------------------------------------------

    private NestingResponse buildResponse(List<Placed> placed, List<String> skipped, int totalRequested) {
        if (placed.isEmpty()) {
            return new NestingResponse(
                    List.of(),
                    new PdfSheetResponse(sheetWidthInch, bottomPaddingInch, "INCH"),
                    new NestingResponse.NestingStats(0, totalRequested, 0.0, bottomPaddingInch, 0, skipped)
            );
        }

        double maxBottom = placed.stream()
                .mapToDouble(p -> p.y() + p.h())
                .max().orElse(0);
        double sheetHeight = maxBottom + bottomPaddingInch;

        long   rotatedCount = placed.stream().filter(Placed::rotated).count();
        double usedArea     = placed.stream().mapToDouble(p -> p.w() * p.h()).sum();
        double usagePct     = (usedArea / (usableWidthInch * sheetHeight)) * 100.0;

        log.info("Nesting done: placed={}/{} height={}in usage={}% rotated={}",
                placed.size(), totalRequested,
                String.format("%.3f", sheetHeight),
                String.format("%.1f", usagePct),
                rotatedCount);

        List<GangSheetItemRequest> items = placed.stream()
                .map(p -> new GangSheetItemRequest(
                        p.source(),
                        p.x(),
                        p.y(),
                        p.w(),
                        p.h(),
                        p.rotated() ? 90.0 : 0.0,
                        null
                ))
                .toList();

        return new NestingResponse(
                items,
                new PdfSheetResponse(sheetWidthInch, sheetHeight, "INCH"),
                new NestingResponse.NestingStats(
                        placed.size(), totalRequested,
                        usagePct, sheetHeight,
                        (int) rotatedCount, skipped
                )
        );
    }

    // -------------------------------------------------------------------------
    // Internal records
    // -------------------------------------------------------------------------

    private record FreeRect(double x, double y, double w, double h) {}

    private record Slot(String source, double slotW, double slotH, double gap) {
        double area() { return slotW * slotH; }
    }

    private record Placement(double x, double y, boolean rotated) {}

    private record Placed(String source, double x, double y, double w, double h, boolean rotated) {}
}