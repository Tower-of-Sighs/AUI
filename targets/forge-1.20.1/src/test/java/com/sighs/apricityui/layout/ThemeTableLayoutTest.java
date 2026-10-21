package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Interaction;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ThemeTableLayoutTest {
    @TestFactory Stream<DynamicTest> themesUseSharedColumns() {
        return Stream.of("ore", "t01-ore", "t03-claude-square", "t04-claude-round", "t05-curseforge",
                "t06-modrinth-light", "t07-modrinth-dark", "t08-arwes", "t10-neobrutalist",
                "t11-hand-drawn-doodle", "t12-retro-vintage", "t13-vaporwave", "t14-impressionist-oil", "t15-mecha")
                .map(name -> DynamicTest.dynamicTest(name, () -> {
                    Path path = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme", name, name + ".css");
                    String key = "apricityui/theme/" + name + "/table-test.html";
                    HTML.putTemple(key, "<html><head><style>" + Files.readString(path)
                            + "</style></head><body class='" + name + "-theme " + name + "-showcase' style='width:600px;height:400px'>"
                            + "<div class='table-wrap'><table class='table'><thead><tr>"
                            + "<th id='a'><div style='width:40px;height:10px'></div></th>"
                            + "<th><div style='width:20px;height:10px'></div></th>"
                            + "<th><div style='width:30px;height:10px'></div></th></tr></thead><tbody><tr>"
                            + "<td id='b'><div style='width:10px;height:10px'></div></td>"
                            + "<td><div style='width:120px;height:10px'></div></td>"
                            + "<td><div style='width:50px;height:10px'></div></td></tr></tbody></table></div></body></html>");
                    Document doc = new Document(key, false);
                    try {
                        doc.refresh();
                        assertEquals("table", doc.querySelector("table").getComputedStyle().display);
                        assertEquals("table-row", doc.querySelector("tr").getComputedStyle().display);
                        assertEquals("table-cell", doc.querySelector("th").getComputedStyle().display);
                        assertEquals(Interaction.Overflow.AUTO, doc.querySelector(".table-wrap").getComputedStyle().overflowX());
                        var first = doc.querySelector("#a").getBoundingClientRect();
                        var second = doc.querySelector("#b").getBoundingClientRect();
                        assertEquals(first.x, second.x, .01);
                        assertEquals(first.width, second.width, .01);
                        assertTrue(second.y >= first.bottom);
                    } finally {
                        doc.remove();
                    }
                }));
    }
}
