package com.example.appbackend.service.exampaper;

import com.example.appbackend.dto.ExamPaperDTO.MarginPreset;
import com.example.appbackend.dto.ExamPaperDTO.Orientation;
import com.example.appbackend.dto.ExamPaperDTO.PageSize;
import com.example.appbackend.dto.ExamPaperDTO.PaperLayoutConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static java.util.Map.entry;

import static org.junit.jupiter.api.Assertions.*;

class SourcePaperLayoutResolverTest {

    private final SourcePaperLayoutResolver resolver = new SourcePaperLayoutResolver();

    @ParameterizedTest
    @MethodSource("pageDimensions")
    void resolvesSourcePageDimensions(PageSize pageSize, Orientation orientation, int width, int height,
                                      int heightMm) {
        PaperLayoutConfig config = defaults();
        config.setPageSize(pageSize);
        config.setOrientation(orientation);

        var layout = resolver.resolve(config);

        assertEquals(width, layout.pageWidth());
        assertEquals(height, layout.pageHeight());
        assertEquals(heightMm, layout.pageHeightMm());
    }

    static Stream<Arguments> pageDimensions() {
        return Stream.of(
                Arguments.of(PageSize.A3, Orientation.LANDSCAPE, 23814, 16840, 297),
                Arguments.of(PageSize.A3, Orientation.PORTRAIT, 16840, 23814, 420),
                Arguments.of(PageSize.A4, Orientation.LANDSCAPE, 16840, 11907, 210),
                Arguments.of(PageSize.A4, Orientation.PORTRAIT, 11907, 16840, 297),
                Arguments.of(PageSize.B4, Orientation.LANDSCAPE, 20639, 14572, 210),
                Arguments.of(PageSize.B4, Orientation.PORTRAIT, 14572, 20639, 297)
        );
    }

    @ParameterizedTest
    @MethodSource("marginPresets")
    void resolvesSourceMarginPresets(MarginPreset preset, int top, int right, int bottom, int left) {
        PaperLayoutConfig config = defaults();
        config.setMarginPreset(preset);

        var layout = resolver.resolve(config);

        assertAll(
                () -> assertEquals(top, layout.marginTop()),
                () -> assertEquals(right, layout.marginRight()),
                () -> assertEquals(bottom, layout.marginBottom()),
                () -> assertEquals(left, layout.marginLeft()),
                () -> assertEquals(851, layout.header()),
                () -> assertEquals(692, layout.footer()),
                () -> assertEquals(0, layout.gutter()),
                () -> assertEquals(312, layout.documentGridLinePitch())
        );
    }

    static Stream<Arguments> marginPresets() {
        return Stream.of(
                Arguments.of(MarginPreset.NORMAL, 1134, 1134, 1134, 1134),
                Arguments.of(MarginPreset.NARROW, 720, 720, 720, 720),
                Arguments.of(MarginPreset.WIDE, 1134, 1440, 1134, 1440),
                Arguments.of(MarginPreset.BINDING, 1134, 998, 1134, 2500)
        );
    }

    @Test
    void resolvesCustomMarginsAndColumnSettings() {
        PaperLayoutConfig config = defaults();
        config.setMarginPreset(MarginPreset.CUSTOM);
        config.setCustomMarginTop(101);
        config.setCustomMarginRight(202);
        config.setCustomMarginBottom(303);
        config.setCustomMarginLeft(404);
        config.setColumnsCount(1);
        config.setColumnSpace(0);

        var layout = resolver.resolve(config);

        assertAll(
                () -> assertEquals(101, layout.marginTop()),
                () -> assertEquals(202, layout.marginRight()),
                () -> assertEquals(303, layout.marginBottom()),
                () -> assertEquals(404, layout.marginLeft()),
                () -> assertEquals(1, layout.columnsCount()),
                () -> assertEquals(0, layout.columnSpace()),
                () -> assertFalse(layout.columnSeparator())
        );
    }

    @Test
    void sourceDefaultsResolveToExactTemplateFragments() {
        var layout = resolver.resolve(defaults());

        assertEquals("<w:pgSz w:w=\"23814\" w:h=\"16840\" w:orient=\"landscape\"/>", layout.pageSizeXml());
        assertEquals("<w:pgMar w:top=\"1134\" w:right=\"998\" w:bottom=\"1134\" w:left=\"2500\" w:header=\"851\" w:footer=\"692\" w:gutter=\"0\"/>", layout.pageMarginsXml());
        assertEquals("<w:pgNumType w:start=\"1\"/>", layout.pageNumberingXml());
        assertEquals("<w:cols w:num=\"2\" w:space=\"425\" w:sep=\"1\"/>", layout.columnsXml());
        assertEquals("<w:titlePg/>", layout.titlePageXml());
        assertEquals("<w:docGrid w:linePitch=\"312\"/>", layout.documentGridXml());
    }

    @Test
    void oneColumnOmitsColumnsFragmentLikeSource() {
        PaperLayoutConfig config = defaults();
        config.setColumnsCount(1);

        assertEquals("", resolver.resolve(config).columnsXml());
    }

    @Test
    void calculatesEveryBindingTokenFromSourceFormula() {
        PaperLayoutConfig config = defaults();
        config.setHeaderInfo("矿井____ 姓名____");

        var tokens = resolver.bindingTokens(config);

        var expected = java.util.Map.ofEntries(
                entry("%h1LineHeight%", "842pt"),
                entry("%h2LineHeight%", "842pt"),
                entry("%h1LineWidth%", "53pt"),
                entry("%h2LineWidth%", "53pt"),
                entry("%h1LineTop%", "558pt"),
                entry("%h2LineTop%", "558pt"),
                entry("%h1MarginLeftIn%", "-99pt"),
                entry("%h1MarginLeftInside%", "-46pt"),
                entry("%h1MarginLeftOutside%", "-125pt"),
                entry("%h1wordUpAndDown1%", "-584200"),
                entry("%h1wordAbout1%", "-546100"),
                entry("%h1wordUpAndDown2%", "-1257300"),
                entry("%h1wordAbout2%", "-546100"),
                entry("%h1wordUpAndDown3%", "-1257300"),
                entry("%h1wordAbout3%", "-546100"),
                entry("%h1wordUpAndDown4%", "-1257300"),
                entry("%h1wordAbout4%", "9422000"),
                entry("%h1wordUpAndDown5%", "-1587500"),
                entry("%h1wordAbout5%", "-546100"),
                entry("%h2wordUpAndDown1%", "13143100"),
                entry("%h2wordAbout1%", "-546100"),
                entry("%h2wordUpAndDown2%", "13473300"),
                entry("%h2wordAbout2%", "-546100"),
                entry("%h2wordUpAndDown3%", "13473300"),
                entry("%h2wordAbout3%", "-546100"),
                entry("%h2wordUpAndDown4%", "13473300"),
                entry("%h2wordAbout4%", "9422000"),
                entry("%h2wordUpAndDown5%", "14146400"),
                entry("%h2wordAbout5%", "-546100"),
                entry("%information%", "矿井____ 姓名____")
        );
        assertAll(expected.entrySet().stream()
                .map(item -> () -> assertEquals(item.getValue(), tokens.values().get(item.getKey()), item.getKey())));
        assertEquals(30, tokens.values().size());
    }

    @ParameterizedTest
    @MethodSource("bindingTokenPageHeights")
    void bindingTokenHeightUsesSourcePageHeightRulesForEveryPaperAndOrientation(
            PageSize pageSize,
            Orientation orientation,
            String lineHeight,
            String lineTop,
            String h1BottomCoordinate,
            String h2FirstCoordinate,
            String h2LastCoordinate
    ) {
        PaperLayoutConfig config = defaults();
        config.setPageSize(pageSize);
        config.setOrientation(orientation);

        var values = resolver.bindingTokens(config).values();

        assertAll(
                () -> assertEquals(lineHeight, values.get("%h1LineHeight%")),
                () -> assertEquals(lineHeight, values.get("%h2LineHeight%")),
                () -> assertEquals(lineTop, values.get("%h1LineTop%")),
                () -> assertEquals(lineTop, values.get("%h2LineTop%")),
                () -> assertEquals(h1BottomCoordinate, values.get("%h1wordAbout4%")),
                () -> assertEquals(h2FirstCoordinate, values.get("%h2wordUpAndDown1%")),
                () -> assertEquals(h2LastCoordinate, values.get("%h2wordUpAndDown5%"))
        );
    }

    static Stream<Arguments> bindingTokenPageHeights() {
        return Stream.of(
                Arguments.of(PageSize.A3, Orientation.LANDSCAPE,
                        "842pt", "558pt", "9422000", "13143100", "14146400"),
                Arguments.of(PageSize.A3, Orientation.PORTRAIT,
                        "1191pt", "907pt", "13850000", "17571100", "18574400"),
                Arguments.of(PageSize.A4, Orientation.LANDSCAPE,
                        "595pt", "312pt", "6290000", "10011100", "11014400"),
                Arguments.of(PageSize.A4, Orientation.PORTRAIT,
                        "842pt", "558pt", "9422000", "13143100", "14146400"),
                Arguments.of(PageSize.B4, Orientation.LANDSCAPE,
                        "595pt", "312pt", "6290000", "10011100", "11014400"),
                Arguments.of(PageSize.B4, Orientation.PORTRAIT,
                        "842pt", "558pt", "9422000", "13143100", "14146400")
        );
    }

    @Test
    void bindingToggleIsResolvedWithoutChangingSourceGeometry() {
        PaperLayoutConfig config = defaults();
        config.setHasBindingLine(false);

        assertFalse(resolver.resolve(config).hasBindingLine());
        assertEquals("842pt", resolver.bindingTokens(config).values().get("%h1LineHeight%"));
    }

    @ParameterizedTest
    @MethodSource("invalidConfigs")
    void rejectsValuesOutsideDocumentedLimits(PaperLayoutConfig config) {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(config));
    }

    static Stream<PaperLayoutConfig> invalidConfigs() {
        return Stream.of(
                changed(c -> c.setColumnsCount(0)), changed(c -> c.setColumnsCount(3)),
                changed(c -> c.setColumnSpace(-1)), changed(c -> c.setColumnSpace(2881)),
                changed(c -> { c.setMarginPreset(MarginPreset.CUSTOM); c.setCustomMarginTop(-1); }),
                changed(c -> { c.setMarginPreset(MarginPreset.CUSTOM); c.setCustomMarginRight(7201); }),
                changed(c -> c.setTitleFontSize(9)), changed(c -> c.setTitleFontSize(121)),
                changed(c -> c.setSubtitleFontSize(9)), changed(c -> c.setSubtitleFontSize(73)),
                changed(c -> c.setBodyFontSize(9)), changed(c -> c.setBodyFontSize(73))
        );
    }

    @Test
    void rejectsMissingCustomMarginAndRequiredLayoutFields() {
        PaperLayoutConfig custom = defaults();
        custom.setMarginPreset(MarginPreset.CUSTOM);
        custom.setCustomMarginLeft(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(custom));

        PaperLayoutConfig missing = defaults();
        missing.setPageSize(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(missing));
    }

    private static PaperLayoutConfig changed(java.util.function.Consumer<PaperLayoutConfig> change) {
        PaperLayoutConfig config = defaults();
        change.accept(config);
        return config;
    }

    private static PaperLayoutConfig defaults() {
        PaperLayoutConfig config = new PaperLayoutConfig();
        config.setPageSize(PageSize.A3);
        config.setOrientation(Orientation.LANDSCAPE);
        config.setMarginPreset(MarginPreset.BINDING);
        config.setColumnsCount(2);
        config.setColumnSpace(425);
        config.setHasBindingLine(true);
        config.setHeaderInfo("煤矿___________    部门___________   岗位___________    姓名___________");
        config.setTitleFontSize(50);
        config.setSubtitleFontSize(24);
        config.setBodyFontSize(21);
        return config;
    }
}
