package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact Java port of the layout constants and formulas in generatePaper.js. */
public final class SourcePaperLayoutResolver {

    private static final int HEADER = 851;
    private static final int FOOTER = 692;
    private static final int GUTTER = 0;
    private static final int DOCUMENT_GRID_LINE_PITCH = 312;
    public ResolvedPageLayout resolve(PaperLayoutConfig config) {
        validate(config);

        PageDimensions base = dimensions(config.getPageSize());
        boolean landscape = config.getOrientation() == Orientation.LANDSCAPE;
        int width = landscape ? base.width() : base.height();
        int height = landscape ? base.height() : base.width();
        Margins margins = margins(config);
        int columns = config.getColumnsCount();
        int columnSpace = config.getColumnSpace();

        return new ResolvedPageLayout(
                width,
                height,
                sourcePageHeightMm(config.getPageSize(), config.getOrientation()),
                config.getOrientation(),
                margins.top(),
                margins.right(),
                margins.bottom(),
                margins.left(),
                HEADER,
                FOOTER,
                GUTTER,
                columns,
                columnSpace,
                columns > 1,
                DOCUMENT_GRID_LINE_PITCH,
                config.getHasBindingLine()
        );
    }

    public BindingLayoutTokens bindingTokens(PaperLayoutConfig config) {
        ResolvedPageLayout layout = resolve(config);
        int pageHeightMm = layout.pageHeightMm();
        long lineHeightEmu = Math.round(pageHeightMm * 36000d);
        String lineHeightPt = Math.round(pageHeightMm * 2.835d) + "pt";
        String lineTopPt = Math.round((pageHeightMm - 100) * 2.835d) + "pt";

        Map<String, String> values = new LinkedHashMap<>();
        values.put("%h1LineHeight%", lineHeightPt);
        values.put("%h2LineHeight%", lineHeightPt);
        values.put("%h1LineWidth%", "53pt");
        values.put("%h2LineWidth%", "53pt");
        values.put("%h1LineTop%", lineTopPt);
        values.put("%h2LineTop%", lineTopPt);
        values.put("%h1MarginLeftIn%", "-99pt");
        values.put("%h1MarginLeftInside%", "-46pt");
        values.put("%h1MarginLeftOutside%", "-125pt");
        values.put("%h1wordUpAndDown1%", "-584200");
        values.put("%h1wordAbout1%", "-546100");
        values.put("%h1wordUpAndDown2%", "-1257300");
        values.put("%h1wordAbout2%", "-546100");
        values.put("%h1wordUpAndDown3%", "-1257300");
        values.put("%h1wordAbout3%", "-546100");
        values.put("%h1wordUpAndDown4%", "-1257300");
        values.put("%h1wordAbout4%", Long.toString(lineHeightEmu - 1270000));
        values.put("%h1wordUpAndDown5%", "-1587500");
        values.put("%h1wordAbout5%", "-546100");
        values.put("%h2wordUpAndDown1%", Long.toString(lineHeightEmu + 2451100));
        values.put("%h2wordAbout1%", "-546100");
        values.put("%h2wordUpAndDown2%", Long.toString(lineHeightEmu + 2781300));
        values.put("%h2wordAbout2%", "-546100");
        values.put("%h2wordUpAndDown3%", Long.toString(lineHeightEmu + 2781300));
        values.put("%h2wordAbout3%", "-546100");
        values.put("%h2wordUpAndDown4%", Long.toString(lineHeightEmu + 2781300));
        values.put("%h2wordAbout4%", Long.toString(lineHeightEmu - 1270000));
        values.put("%h2wordUpAndDown5%", Long.toString(lineHeightEmu + 3454400));
        values.put("%h2wordAbout5%", "-546100");
        values.put("%information%", Objects.requireNonNullElse(config.getHeaderInfo(), ""));
        return new BindingLayoutTokens(Collections.unmodifiableMap(values));
    }

    private void validate(PaperLayoutConfig config) {
        Objects.requireNonNull(config, "layout config must not be null");
        require(config.getPageSize() != null, "pageSize is required");
        require(config.getOrientation() != null, "orientation is required");
        require(config.getMarginPreset() != null, "marginPreset is required");
        requireRange(config.getColumnsCount(), 1, 2, "columnsCount");
        requireRange(config.getColumnSpace(), 0, 2880, "columnSpace");
        requireRange(config.getTitleFontSize(), 10, 120, "titleFontSize");
        requireRange(config.getSubtitleFontSize(), 10, 72, "subtitleFontSize");
        requireRange(config.getBodyFontSize(), 10, 72, "bodyFontSize");
        require(config.getHasBindingLine() != null, "hasBindingLine is required");
        if (config.getMarginPreset() == MarginPreset.CUSTOM) {
            requireRange(config.getCustomMarginTop(), 0, 7200, "customMarginTop");
            requireRange(config.getCustomMarginRight(), 0, 7200, "customMarginRight");
            requireRange(config.getCustomMarginBottom(), 0, 7200, "customMarginBottom");
            requireRange(config.getCustomMarginLeft(), 0, 7200, "customMarginLeft");
        }
    }

    private static void requireRange(Integer value, int min, int max, String name) {
        require(value != null && value >= min && value <= max,
                name + " must be between " + min + " and " + max);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static PageDimensions dimensions(PageSize pageSize) {
        return switch (pageSize) {
            case A3 -> new PageDimensions(23814, 16840);
            case A4 -> new PageDimensions(16840, 11907);
            case B4 -> new PageDimensions(20639, 14572);
        };
    }

    private static Margins margins(PaperLayoutConfig config) {
        return switch (config.getMarginPreset()) {
            case NORMAL -> new Margins(1134, 1134, 1134, 1134);
            case NARROW -> new Margins(720, 720, 720, 720);
            case WIDE -> new Margins(1134, 1440, 1134, 1440);
            case BINDING -> new Margins(1134, 998, 1134, 2500);
            case CUSTOM -> new Margins(
                    config.getCustomMarginTop(),
                    config.getCustomMarginRight(),
                    config.getCustomMarginBottom(),
                    config.getCustomMarginLeft()
            );
        };
    }

    // This deliberately preserves the source's A3-vs-non-A3 formula, including B4 behavior.
    private static int sourcePageHeightMm(PageSize pageSize, Orientation orientation) {
        if (orientation == Orientation.LANDSCAPE) {
            return pageSize == PageSize.A3 ? 297 : 210;
        }
        return pageSize == PageSize.A3 ? 420 : 297;
    }

    private record PageDimensions(int width, int height) {}

    private record Margins(int top, int right, int bottom, int left) {}

    public record BindingLayoutTokens(Map<String, String> values) {}

    public record ResolvedPageLayout(
            int pageWidth,
            int pageHeight,
            int pageHeightMm,
            Orientation orientation,
            int marginTop,
            int marginRight,
            int marginBottom,
            int marginLeft,
            int header,
            int footer,
            int gutter,
            int columnsCount,
            int columnSpace,
            boolean columnSeparator,
            int documentGridLinePitch,
            boolean hasBindingLine
    ) {
        public String pageSizeXml() {
            return "<w:pgSz w:w=\"" + pageWidth + "\" w:h=\"" + pageHeight
                    + "\" w:orient=\"" + orientation.name().toLowerCase() + "\"/>";
        }

        public String pageMarginsXml() {
            return "<w:pgMar w:top=\"" + marginTop + "\" w:right=\"" + marginRight
                    + "\" w:bottom=\"" + marginBottom + "\" w:left=\"" + marginLeft
                    + "\" w:header=\"" + header + "\" w:footer=\"" + footer
                    + "\" w:gutter=\"" + gutter + "\"/>";
        }

        public String pageNumberingXml() {
            return "<w:pgNumType w:start=\"1\"/>";
        }

        public String columnsXml() {
            if (columnsCount <= 1) {
                return "";
            }
            return "<w:cols w:num=\"" + columnsCount + "\" w:space=\"" + columnSpace
                    + "\" w:sep=\"1\"/>";
        }

        public String titlePageXml() {
            return hasBindingLine ? "<w:titlePg/>" : "";
        }

        public String documentGridXml() {
            return "<w:docGrid w:linePitch=\"" + documentGridLinePitch + "\"/>";
        }
    }
}
