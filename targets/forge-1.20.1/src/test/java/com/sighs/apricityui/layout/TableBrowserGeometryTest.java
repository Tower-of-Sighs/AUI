package com.sighs.apricityui.layout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TableBrowserGeometryTest {
    private static final Map<String, String> HTML_CASES = Map.of(
            "groups", """
                <!doctype html><html><head><style>html,body{margin:0;padding:0}\
                body{width:300px;height:400px;font-size:0;line-height:0}\
                table{border-spacing:0}\
                th,td{padding:0;vertical-align:top}\
                div{margin:0;padding:0}\
                #t{padding:5px;border:2px solid black}\
                </style></head><body>\
                <table id="t">\
                <thead id="head">\
                <tr id="r1">\
                <th id="a"><div style="width:30px;height:10px"></div></th>\
                <th id="b"><div style="width:20px;height:20px"></div></th>\
                </tr>\
                </thead>\
                <tbody id="body">\
                <tr id="r2">\
                <td id="c"><div style="width:10px;height:30px"></div></td>\
                <td id="d"><div style="width:70px;height:10px"></div></td>\
                </tr>\
                </tbody>\
                </table>\
                </body></html>
                """,
            "expanded", """
                <!doctype html><html><head><style>html,body{margin:0;padding:0}\
                body{width:300px;height:400px;font-size:0;line-height:0}\
                table{border-spacing:0}\
                th,td{padding:0;vertical-align:top}\
                div{margin:0;padding:0}\
                #t{width:200px}\
                </style></head><body>\
                <table id="t">\
                <tbody id="body">\
                <tr id="r1">\
                <td id="a"><div style="width:20px;height:10px"></div></td>\
                <td id="b"><div style="width:80px;height:20px"></div></td>\
                </tr>\
                <tr id="r2">\
                <td id="c"></td>\
                <td id="d"><div style="width:10px;height:15px"></div></td>\
                </tr>\
                </tbody>\
                </table>\
                </body></html>
                """,
            "boxes", """
                <!doctype html><html><head><style>html,body{margin:0;padding:0}\
                body{width:300px;height:400px;font-size:0;line-height:0}\
                table{border-spacing:0}\
                th,td{padding:0;vertical-align:top}\
                div{margin:0;padding:0}\
                #t{width:160px;box-sizing:border-box;border:2px solid black;padding:3px}\
                td{padding:4px;border:1px solid black}\
                </style></head><body>\
                <table id="t">\
                <tbody id="body">\
                <tr id="r1">\
                <td id="a"><div style="width:20px;height:10px"></div></td>\
                <td id="b"><div style="width:30px;height:20px"></div></td>\
                <td id="c"><div style="width:10px;height:15px"></div></td>\
                </tr>\
                </tbody>\
                </table>\
                </body></html>
                """,
            "overflow", """
                <!doctype html><html><head><style>html,body{margin:0;padding:0}\
                body{width:300px;height:400px;font-size:0;line-height:0}\
                table{border-spacing:0}\
                th,td{padding:0;vertical-align:top}\
                div{margin:0;padding:0}\
                #wrap{width:80px;overflow:auto;scrollbar-width:none}\
                #t{width:100%}\
                </style></head><body><div id="wrap">\
                <table id="t">\
                <tbody id="body">\
                <tr id="r1">\
                <td id="a"><div style="width:60px;height:10px"></div></td>\
                <td id="b"><div style="width:100px;height:20px"></div></td>\
                <td id="c"><div style="width:30px;height:15px"></div></td>\
                </tr>\
                </tbody>\
                </table></div>\
                </body></html>
                """
    );

    private static final String CHROMIUM_JSON = """
            {
              "groups": {
                "t": [0, 0, 114, 64],
                "head": [7, 7, 100, 20],
                "r1": [7, 7, 100, 20],
                "a": [7, 7, 30, 20],
                "b": [37, 7, 70, 20],
                "body": [7, 27, 100, 30],
                "r2": [7, 27, 100, 30],
                "c": [7, 27, 30, 30],
                "d": [37, 27, 70, 30]
              },
              "expanded": {
                "t": [0, 0, 200, 35],
                "body": [0, 0, 200, 35],
                "r1": [0, 0, 200, 20],
                "a": [0, 0, 40, 20],
                "b": [40, 0, 160, 20],
                "r2": [0, 20, 200, 15],
                "c": [0, 20, 40, 15],
                "d": [40, 20, 160, 15]
              },
              "boxes": {
                "t": [0, 0, 160, 40],
                "body": [5, 5, 150, 30],
                "r1": [5, 5, 150, 30],
                "a": [5, 5, 50, 30],
                "b": [55, 5, 66.6563, 30],
                "c": [121.6563, 5, 33.3438, 30]
              },
              "overflow": {
                "wrap": [0, 0, 80, 20],
                "t": [0, 0, 190, 20],
                "body": [0, 0, 190, 20],
                "r1": [0, 0, 190, 20],
                "a": [0, 0, 60, 20],
                "b": [60, 0, 100, 20],
                "c": [160, 0, 30, 20]
              }
            }
            """;

    @Test void matchesChromiumBorderBoxes() {
        JsonObject cases = JsonParser.parseString(CHROMIUM_JSON).getAsJsonObject();
        Size.setViewportOverride(800, 600);
        try {
            for (var testCase : cases.entrySet()) {
                String path = "test://table/" + testCase.getKey();
                String html = HTML_CASES.get(testCase.getKey());
                assertNotNull(html, testCase.getKey());
                HTML.putTemple(path, html);
                Document doc = new Document(path, false);
                try {
                    doc.refresh();
                    for (var expected : testCase.getValue().getAsJsonObject().entrySet()) {
                        var element = doc.querySelector("#" + expected.getKey());
                        assertNotNull(element);
                        var rect = element.getBoundingClientRect();
                        double[] actual = {rect.x, rect.y, rect.width, rect.height};
                        var values = expected.getValue().getAsJsonArray();
                        for (int i = 0; i < 4; i++) assertEquals(values.get(i).getAsDouble(), actual[i], .25,
                                testCase.getKey() + " #" + expected.getKey() + " axis=" + i);
                    }
                } finally {
                    doc.remove();
                }
            }
        } finally {
            Size.clearViewportOverride();
        }
    }
}
