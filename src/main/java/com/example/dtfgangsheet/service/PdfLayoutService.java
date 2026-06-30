package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.dto.response.GangSheetBuilderConfigResponse;
import com.example.dtfgangsheet.exception.AppException;
import com.example.dtfgangsheet.exception.GangSheetLayoutException;
import com.example.dtfgangsheet.exception.ServerException;
import com.example.dtfgangsheet.model.GangSheetItem;
import com.example.dtfgangsheet.model.SheetLayout;
import com.example.dtfgangsheet.model.SheetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validate request items và tính toán layout của sheet.
 *
 * <p>{@link #computeBuilderLayout} — save draft / canvas (padding nhỏ, không QR).<br>
 * {@link #computePrintLayout} — gen PDF / sản xuất (có chừa chỗ QR).</p>
 */
@Service
public class PdfLayoutService {

    private static final Logger log = LoggerFactory.getLogger(PdfLayoutService.class);
    private static final double EPSILON_INCH = 0.0001;

    private final double sheetWidthInch;
    private final double rightPaddingInch;
    private final double bottomPaddingInch;
    private final double qrCodeSizeInch;
    private final double qrCodeMarginInch;
    private final int    maxItemsPerRequest;
    private final int    maxItemSizeInch;
    private final int    maxPositionInch;

    public PdfLayoutService(PdfProperties pdfProps, ImageProperties imageProps) {
        this.sheetWidthInch     = pdfProps.sheetWidthInch();
        this.rightPaddingInch   = pdfProps.rightPaddingInch();
        this.bottomPaddingInch  = pdfProps.bottomPaddingInch();
        this.qrCodeSizeInch     = pdfProps.qrCodeSizeInch();
        this.qrCodeMarginInch   = pdfProps.qrCodeMarginInch();
        this.maxItemsPerRequest = imageProps.maxItemsPerRequest();
        this.maxItemSizeInch    = imageProps.maxItemSizeInch();
        this.maxPositionInch    = imageProps.maxPositionInch();
    }

    public GangSheetBuilderConfigResponse builderConfig() {
        return new GangSheetBuilderConfigResponse(
                sheetWidthInch,
                rightPaddingInch,
                bottomPaddingInch,
                SheetSpec.UNIT_INCH
        );
    }

    public void validateRequest(List<GangSheetItem> items) {
        if (items == null || items.isEmpty()) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "items must not be empty");
        }
        if (items.size() > maxItemsPerRequest) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "Too many items in request. count=" + items.size() + ", max=" + maxItemsPerRequest);
        }
        for (int i = 0; i < items.size(); i++) {
            validateItem(items.get(i), i);
        }
    }

    /**
     * Layout cho builder / save draft — không chừa QR.
     */
    public SheetLayout computeBuilderLayout(List<GangSheetItem> items) {
        return computeLayoutWithPadding(items, bottomPaddingInch);
    }

    /**
     * Layout cho in PDF — tự mở rộng chiều cao nếu cần chỗ QR.
     */
    public SheetLayout computePrintLayout(List<GangSheetItem> items) {
        List<RotatedBounds> allBounds = boundsOf(items);
        double printableWidth = printableWidthInch();
        double maxBottom = allBounds.stream().mapToDouble(RotatedBounds::bottom).max().orElse(0);
        double maxRight = allBounds.stream().mapToDouble(RotatedBounds::right).max().orElse(0);
        double effectiveBottomPadding = resolvePrintBottomPadding(maxBottom, maxRight);
        double sheetHeight = maxBottom + effectiveBottomPadding;

        log.debug("Print layout: maxBottom={}in effectiveBottomPadding={}in sheetHeight={}in",
                fmt(maxBottom), fmt(effectiveBottomPadding), fmt(sheetHeight));

        validateItemsInsideSheet(items, allBounds, printableWidth, sheetHeight);
        return new SheetLayout(sheetWidthInch, sheetHeight);
    }

    /** @deprecated dùng {@link #computePrintLayout} hoặc {@link #computeBuilderLayout} */
    @Deprecated
    public SheetLayout computeLayout(List<GangSheetItem> items) {
        return computePrintLayout(items);
    }

    public RotatedBounds rotatedBounds(GangSheetItem item) {
        double w = item.width(), h = item.height();
        double cx = item.x() + w / 2.0;
        double cy = item.y() + h / 2.0;

        double rad = Math.toRadians(normalizeRotation(item.rotation()));
        double sin = Math.abs(Math.sin(rad));
        double cos = Math.abs(Math.cos(rad));

        double rw = w * cos + h * sin;
        double rh = w * sin + h * cos;

        return new RotatedBounds(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2);
    }

    private SheetLayout computeLayoutWithPadding(List<GangSheetItem> items, double bottomPadding) {
        List<RotatedBounds> allBounds = boundsOf(items);
        double printableWidth = printableWidthInch();
        double sheetHeight = allBounds.stream()
                .mapToDouble(RotatedBounds::bottom)
                .max().orElse(0) + bottomPadding;

        validateItemsInsideSheet(items, allBounds, printableWidth, sheetHeight);
        return new SheetLayout(sheetWidthInch, sheetHeight);
    }

    private List<RotatedBounds> boundsOf(List<GangSheetItem> items) {
        return items.stream().map(this::rotatedBounds).toList();
    }

    /**
     * Cùng logic QR với {@code MaxRectsNestingService}.
     */
    private double resolvePrintBottomPadding(double maxBottom, double maxRight) {
        double actualRightGap = sheetWidthInch - maxRight;
        double minQrSpace = qrCodeSizeInch + qrCodeMarginInch * 2;

        if (actualRightGap >= minQrSpace) {
            double minHeightForQr = qrCodeSizeInch + qrCodeMarginInch;
            double currentHeight = maxBottom + bottomPaddingInch;
            if (currentHeight < minHeightForQr) {
                return minHeightForQr - maxBottom;
            }
            return bottomPaddingInch;
        }
        return Math.max(bottomPaddingInch, minQrSpace);
    }

    private void validateItem(GangSheetItem item, int index) {
        if (item == null) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "Item at index " + index + " must not be null");
        }
        if (!Double.isFinite(item.x()) || !Double.isFinite(item.y())
                || !Double.isFinite(item.width()) || !Double.isFinite(item.height())
                || !Double.isFinite(item.rotation())) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "Item at index " + index + " has non-finite geometry");
        }
        if (item.width() > maxItemSizeInch || item.height() > maxItemSizeInch) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "Item at index " + index + " exceeds max item size. max=" + maxItemSizeInch + "in");
        }
        if (item.x() > maxPositionInch || item.y() > maxPositionInch) {
            throw new AppException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "Item at index " + index + " exceeds max position. max=" + maxPositionInch + "in");
        }
    }

    private void validateItemsInsideSheet(List<GangSheetItem> items,
                                          List<RotatedBounds> allBounds,
                                          double sheetWidth, double sheetHeight) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            collectLayoutErrors(items.get(i), allBounds.get(i), sheetWidth, sheetHeight, i, details);
        }
        if (!details.isEmpty()) {
            throw new GangSheetLayoutException(details);
        }
    }

    private void collectLayoutErrors(GangSheetItem item,
                                     RotatedBounds bounds,
                                     double sheetWidth, double sheetHeight,
                                     int index, List<ApiErrorDetail> details) {
        if (item.width() <= 0 || item.height() <= 0) {
            details.add(ApiErrorDetail.of("INVALID_ITEM_SIZE", "items[" + index + "]",
                    "Item at index " + index + " has invalid size. width=" + item.width()
                            + ", height=" + item.height()));
            return;
        }

        if (bounds.left() < -EPSILON_INCH) {
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_LEFT", "items[" + index + "].x",
                    "Item at index " + index + " exceeds sheet left boundary. left=" + bounds.left()));
        }

        if (bounds.top() < -EPSILON_INCH) {
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_TOP", "items[" + index + "].y",
                    "Item at index " + index + " exceeds sheet top boundary. top=" + bounds.top()));
        }

        if (bounds.right() > sheetWidth + EPSILON_INCH) {
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_RIGHT", "items[" + index + "].x",
                    "Item at index " + index + " exceeds sheet right boundary. right=" + bounds.right()
                            + ", sheetWidth=" + sheetWidth));
        }

        if (bounds.bottom() > sheetHeight + EPSILON_INCH) {
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_BOTTOM", "items[" + index + "].y",
                    "Item at index " + index + " exceeds sheet bottom boundary. bottom=" + bounds.bottom()
                            + ", sheetHeight=" + sheetHeight));
        }
    }

    private double normalizeRotation(double deg) {
        double n = deg % 360;
        return n < 0 ? n + 360 : n;
    }

    private double printableWidthInch() {
        double w = sheetWidthInch - rightPaddingInch;
        if (w <= 0) {
            throw new ServerException(ApiResultCode.INTERNAL_ERROR,
                    new IllegalStateException(
                            "rightPaddingInch must be smaller than sheetWidthInch. "
                                    + "sheetWidthInch=" + sheetWidthInch
                                    + ", rightPaddingInch=" + rightPaddingInch));
        }
        return w;
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }

    public record RotatedBounds(double left, double top, double right, double bottom) {}
}
