package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.config.PdfProperties;
import com.example.dtfgangsheet.dto.common.ApiErrorDetail;
import com.example.dtfgangsheet.dto.common.ApiResultCode;
import com.example.dtfgangsheet.exception.AppException;
import com.example.dtfgangsheet.exception.GangSheetLayoutException;
import com.example.dtfgangsheet.exception.ServerException;
import com.example.dtfgangsheet.model.GangSheetItem;
import com.example.dtfgangsheet.model.SheetLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validate request items và tính toán layout của sheet PDF.
 *
 * <p>Tách ra từ {@link GangSheetPdfService} để tuân thủ SRP:
 * class này chỉ làm toán hình học và validate — không đụng PDFBox, không I/O.</p>
 */
@Service
public class PdfLayoutService {

    private static final Logger log = LoggerFactory.getLogger(PdfLayoutService.class);
    private static final double EPSILON_INCH = 0.0001;

    private final double sheetWidthInch;
    private final double rightPaddingInch;
    private final double bottomPaddingInch;
    private final int    maxItemsPerRequest;
    private final int    maxItemSizeInch;
    private final int    maxPositionInch;

    public PdfLayoutService(PdfProperties pdfProps, ImageProperties imageProps) {
        this.sheetWidthInch     = pdfProps.sheetWidthInch();
        this.rightPaddingInch   = pdfProps.rightPaddingInch();
        this.bottomPaddingInch  = pdfProps.bottomPaddingInch();
        this.maxItemsPerRequest = imageProps.maxItemsPerRequest();
        this.maxItemSizeInch    = imageProps.maxItemSizeInch();
        this.maxPositionInch    = imageProps.maxPositionInch();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Validate request-level limits (count, geometry, position).
     * Gọi trước khi load ảnh để fail-fast mà không tốn I/O.
     */
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
     * Tính sheet height và validate tất cả items nằm trong sheet.
     *
     * @return SheetLayout chứa widthInch và heightInch để render PDF
     */
    public SheetLayout computeLayout(List<GangSheetItem> items) {
        double printableWidth = printableWidthInch();
        double sheetHeight    = calculateSheetHeight(items);
        validateItemsInsideSheet(items, printableWidth, sheetHeight);
        return new SheetLayout(sheetWidthInch, sheetHeight);
    }

    // =========================================================================
    // Validate items
    // =========================================================================

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
                                          double sheetWidth, double sheetHeight) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            collectLayoutErrors(items.get(i), sheetWidth, sheetHeight, i, details);
        }
        if (!details.isEmpty()) {
            throw new GangSheetLayoutException(details);
        }
    }

    private void collectLayoutErrors(GangSheetItem item,
                                     double sheetWidth, double sheetHeight,
                                     int index, List<ApiErrorDetail> details) {
        if (item.width() <= 0 || item.height() <= 0) {
            details.add(ApiErrorDetail.of("INVALID_ITEM_SIZE", "items[" + index + "]",
                    "Item at index " + index + " has invalid size. width=" + item.width()
                            + ", height=" + item.height()));
            return;
        }

        RotatedBounds bounds = rotatedBounds(item);

        if (bounds.left() < -EPSILON_INCH)
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_LEFT", "items[" + index + "].x",
                    "Item at index " + index + " exceeds sheet left boundary. left=" + bounds.left()));

        if (bounds.top() < -EPSILON_INCH)
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_TOP", "items[" + index + "].y",
                    "Item at index " + index + " exceeds sheet top boundary. top=" + bounds.top()));

        if (bounds.right() > sheetWidth + EPSILON_INCH)
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_RIGHT", "items[" + index + "].x",
                    "Item at index " + index + " exceeds sheet right boundary. right=" + bounds.right()
                            + ", sheetWidth=" + sheetWidth));

        if (bounds.bottom() > sheetHeight + EPSILON_INCH)
            details.add(ApiErrorDetail.of("ITEM_EXCEEDS_SHEET_BOTTOM", "items[" + index + "].y",
                    "Item at index " + index + " exceeds sheet bottom boundary. bottom=" + bounds.bottom()
                            + ", sheetHeight=" + sheetHeight));
    }

    // =========================================================================
    // Geometry
    // =========================================================================

    private double calculateSheetHeight(List<GangSheetItem> items) {
        double maxBottom = items.stream()
                .mapToDouble(item -> rotatedBounds(item).bottom())
                .max().orElse(0);
        return maxBottom + bottomPaddingInch;
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

    // =========================================================================
    // Public records — dùng bởi GangSheetPdfService và PdfRenderService
    // =========================================================================

    public record RotatedBounds(double left, double top, double right, double bottom) {}
}