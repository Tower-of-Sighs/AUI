package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chromium snapshots captured through wpt/tools/browser-adapter.mjs on 2026-10-14. */
class GridBrowserGeometryTest {
    private record GeometryCase(String name, String css, String body, double[][] expected) {
        String source() {
            return """
                    <!doctype html><html><head><style>
                    html,body { margin:0; padding:0; }
                    body { width:800px; height:600px; }
                    div { margin:0; padding:0; box-sizing:content-box; }
                    #g { display:grid; }
                    %s
                    </style></head><body>%s</body></html>
                    """.formatted(css, body);
        }
    }

    @TestFactory
    Stream<DynamicTest> matchesChromiumGeometry() {
        return cases().stream().map(testCase -> DynamicTest.dynamicTest(testCase.name(), () -> {
            Size.setViewportOverride(800, 600);
            try {
                String path = "test://grid/" + testCase.name();
                HTML.putTemple(path, testCase.source());
                Document document = new Document(path, false);
                try {
                    document.refresh();
                } catch (NoClassDefFoundError unavailableRuntime) {
                    if (document.documentElement == null
                            || !String.valueOf(unavailableRuntime.getMessage()).contains("net/minecraftforge/fml/ModList")) {
                        throw unavailableRuntime;
                    }
                }
                var nodes = document.querySelectorAll("div");
                assertEquals(testCase.expected().length, nodes.size());
                for (int i = 0; i < nodes.size(); i++) {
                    Element.DOMRect rect = nodes.get(i).getBoundingClientRect();
                    double[] actual = {rect.x, rect.y, rect.width, rect.height};
                    for (int axis = 0; axis < 4; axis++) {
                        assertEquals(testCase.expected()[i][axis], actual[axis], .25,
                                testCase.name() + " node=" + i + " axis=" + axis);
                    }
                }
            } finally {
                Size.clearViewportOverride();
            }
        }));
    }

    private static List<GeometryCase> cases() {
        return List.of(
                new GeometryCase("absolute", """
                        #g { position:relative; width:100px; height:50px;
                             grid-template:20px 30px/40px 60px; padding:5px; border:2px solid black; }
                        """, """
                        <div id="g"><div style="position:absolute;grid-area:2/2/3/3;inset:0"></div></div>
                        """, new double[][] {{0, 0, 114, 64}, {47, 27, 60, 30}}),
                new GeometryCase("alignment", """
                        #g { width:100px; height:50px; grid-template:20px/20px 20px;
                             place-content:center space-between; }
                        """, """
                        <div id="g"><div style="width:10px;height:10px;place-self:center end"></div>
                        <div style="width:10px;height:10px;margin:auto"></div></div>
                        """, new double[][] {{0, 0, 100, 50}, {10, 20, 10, 10}, {85, 20, 10, 10}}),
                new GeometryCase("areas", """
                        #g { width:100px; grid-template:[Top] "Head Head" 20px "Side Main" 30px/40px 60px; }
                        """, """
                        <div id="g"><div style="grid-area:Head"></div><div style="grid-area:Main"></div>
                        <div style="grid-area:Side"></div></div>
                        """, new double[][] {{0, 0, 100, 50}, {0, 0, 100, 20}, {40, 20, 60, 30}, {0, 20, 40, 30}}),
                new GeometryCase("aspect", """
                        #g { width:100px; grid-template-columns:40px 60px; }
                        #g > div { aspect-ratio:1; }
                        """, """
                        <div id="g"><div></div><div></div></div>
                        """, new double[][] {{0, 0, 100, 60}, {0, 0, 40, 40}, {40, 0, 60, 60}}),
                new GeometryCase("caps", """
                        #g { width:160px; grid-template-columns:minmax(auto,10px) minmax(auto,20px) minmax(auto,30px) 1fr;
                             grid-auto-rows:10px; }
                        """, """
                        <div id="g"><div style="width:100px;grid-column:1/4;min-width:0"></div>
                        <div style="grid-column:4"></div></div>
                        """, new double[][] {{0, 0, 160, 10}, {0, 0, 100, 10}, {100, 0, 60, 10}}),
                new GeometryCase("column", """
                        #g { width:30px; grid-template-rows:repeat(3,20px); grid-auto-columns:10px;
                             grid-auto-flow:column dense; justify-content:start; }
                        """, """
                        <div id="g"><div style="grid-row:span 2"></div><div style="grid-row:span 2"></div><div></div></div>
                        """, new double[][] {{0, 0, 30, 60}, {0, 0, 10, 40}, {10, 0, 10, 40}, {0, 40, 10, 20}}),
                new GeometryCase("dense", """
                        #g { width:60px; grid-template-columns:repeat(3,20px); grid-auto-rows:10px; grid-auto-flow:row dense; }
                        """, """
                        <div id="g"><div style="grid-column:span 2"></div><div style="grid-column:span 2"></div><div></div></div>
                        """, new double[][] {{0, 0, 60, 20}, {0, 0, 40, 10}, {0, 10, 40, 10}, {40, 0, 20, 10}}),
                new GeometryCase("fractions", """
                        #g { width:120px; grid-template-columns:20px 1fr 2fr; }
                        #g > div { width:5px; height:10px; }
                        """, """
                        <div id="g"><div></div><div></div><div></div></div>
                        """, new double[][] {{0, 0, 120, 10}, {0, 0, 5, 10}, {20, 0, 5, 10}, {53.3281, 0, 5, 10}}),
                new GeometryCase("intrinsic-fractions", """
                        body { display:flex; align-items:flex-start; }
                        #g { display:inline-grid; grid-template-columns:1fr 2fr; grid-auto-rows:10px; }
                        """, """
                        <div id="g"><div style="grid-column:span 2;width:90px"></div><div></div><div></div></div>
                        """, new double[][] {{0, 0, 90, 20}, {0, 0, 90, 10}, {0, 10, 30, 10}, {30, 10, 60, 10}}),
                new GeometryCase("locked-row", """
                        #g { width:100px; grid-template-columns:repeat(3,20px); grid-auto-columns:20px;
                             grid-auto-rows:10px; justify-content:start; }
                        """, """
                        <div id="g"><div style="grid-area:1/2/2/3"></div><div style="grid-row:1;grid-column:span 2"></div>
                        <div style="grid-row:1"></div></div>
                        """, new double[][] {{0, 0, 100, 10}, {20, 0, 20, 10}, {40, 0, 40, 10}, {80, 0, 20, 10}}),
                new GeometryCase("mixed-repeat", """
                        #g { width:140px; grid-template-columns:20px repeat(auto-fill,10px 20px); grid-auto-rows:10px; gap:10px; }
                        """, """
                        <div id="g"><div></div><div></div><div></div><div></div><div></div></div>
                        """, new double[][] {{0, 0, 140, 10}, {0, 0, 20, 10}, {30, 0, 10, 10},
                        {50, 0, 20, 10}, {80, 0, 10, 10}, {100, 0, 20, 10}}),
                new GeometryCase("nested", """
                        #g { width:100px; grid-template-columns:repeat(2,minmax(0,1fr)); }
                        #n { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
                        #n > div { height:10px; }
                        """, """
                        <div id="g"><div id="n"><div></div><div></div></div><div></div></div>
                        """, new double[][] {{0, 0, 100, 10}, {0, 0, 50, 10}, {0, 0, 25, 10}, {25, 0, 25, 10}, {50, 0, 50, 10}}),
                new GeometryCase("percentage-items", """
                        #g { width:100px; grid-template-columns:1fr 1fr; }
                        #g > div { width:100%; height:10px; }
                        """, """
                        <div id="g"><div></div><div></div></div>
                        """, new double[][] {{0, 0, 100, 10}, {0, 0, 50, 10}, {50, 0, 50, 10}}),
                new GeometryCase("placement", """
                        #g { width:100px; grid-template:20px 30px/40px 60px; grid-auto-rows:15px; }
                        """, """
                        <div id="g"><div id="a"></div><div style="grid-area:1/1/2/2"></div>
                        <div style="grid-column:-2/-1"></div></div>
                        """, new double[][] {{0, 0, 100, 50}, {40, 0, 60, 20}, {0, 0, 40, 20}, {40, 20, 60, 30}}),
                new GeometryCase("pre-grid", """
                        #g { width:100px; grid-template-columns:20px 30px; grid-auto-columns:10px 15px;
                             grid-auto-rows:10px; justify-content:start; }
                        """, """
                        <div id="g"><div style="grid-column:-5/-3"></div><div style="grid-column:-2/-1"></div></div>
                        """, new double[][] {{0, 0, 100, 10}, {0, 0, 25, 10}, {45, 0, 30, 10}}),
                new GeometryCase("repeat", """
                        #g { width:100px; grid-template-columns:repeat(auto-fit,minmax(20px,1fr)); grid-auto-rows:10px; gap:10px; }
                        """, """
                        <div id="g"><div></div></div>
                        """, new double[][] {{0, 0, 100, 10}, {0, 0, 100, 10}})
        );
    }
}
