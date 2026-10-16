package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridExplicitTrackMinimumTest {
    @Test
    void narrowFractionTracksKeepTheItemsDeclaredWidth() {
        String path = "test://grid/explicit-column-minimum";
        HTML.putTemple(path, """
                <html><head><style>
                body{margin:0}
                .grid{display:grid;width:80px;grid-template-columns:repeat(3,1fr);gap:3px}
                .slot{width:44px;height:12px}
                </style></head><body><div class="grid">
                <div class="slot" id="a"></div><div class="slot" id="b"></div><div class="slot" id="c"></div>
                </div></body></html>
                """);
        Document document = new Document(path, false);
        try {
            document.refresh();
            Element a = document.getElementById("a");
            Element b = document.getElementById("b");
            Element c = document.getElementById("c");
            assertEquals(47, b.getBoundingClientRect().x - a.getBoundingClientRect().x, 0.01);
            assertEquals(47, c.getBoundingClientRect().x - b.getBoundingClientRect().x, 0.01);
        } finally {
            document.remove();
        }
    }

    @Test
    void shortFractionRowsKeepTheItemsDeclaredHeight() {
        String path = "test://grid/explicit-row-minimum";
        HTML.putTemple(path, """
                <html><head><style>
                body{margin:0}
                .grid{display:grid;width:70px;height:60px;grid-template-columns:70px;grid-template-rows:repeat(3,1fr);gap:3px}
                .slot{width:12px;height:36px}
                </style></head><body><div class="grid">
                <div class="slot" id="a"></div><div class="slot" id="b"></div><div class="slot" id="c"></div>
                </div></body></html>
                """);
        Document document = new Document(path, false);
        try {
            document.refresh();
            Element a = document.getElementById("a");
            Element b = document.getElementById("b");
            Element c = document.getElementById("c");
            assertEquals(39, b.getBoundingClientRect().y - a.getBoundingClientRect().y, 0.01);
            assertEquals(39, c.getBoundingClientRect().y - b.getBoundingClientRect().y, 0.01);
        } finally {
            document.remove();
        }
    }
}
