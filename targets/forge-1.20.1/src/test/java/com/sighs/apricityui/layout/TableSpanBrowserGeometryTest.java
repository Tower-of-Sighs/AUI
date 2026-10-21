package com.sighs.apricityui.layout;

import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TableSpanBrowserGeometryTest {
    // Chromium/Edge 154.0.4258.37, 800 x 600 CSS pixels; snapshots captured 2026-10-22.
    private static final String CSS = "html,body{margin:0;padding:0}body{width:300px;height:400px;font-size:0;line-height:0}table{border-spacing:0}th,td{padding:0;vertical-align:top}div{margin:0;padding:0}";
    private static final Map<String,String> CASES = Map.ofEntries(
        Map.entry("nested_rows", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="4" style=""><div style="width:20px;height:160px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c" rowspan="2" style=""><div style="width:30px;height:100px"></div></td><td id="d"  style=""><div style="width:40px;height:20px"></div></td></tr><tr id="r3" style=""><td id="e"  style=""><div style="width:40px;height:30px"></div></td></tr><tr id="r4" style=""><td id="f"  style=""><div style="width:30px;height:10px"></div></td><td id="g"  style=""><div style="width:40px;height:10px"></div></td></tr></table>
            """),
        Map.entry("same_end_rows", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="3" style=""><div style="width:20px;height:150px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c" rowspan="2" style=""><div style="width:30px;height:100px"></div></td><td id="d"  style=""><div style="width:40px;height:10px"></div></td></tr><tr id="r3" style=""><td id="e"  style=""><div style="width:40px;height:20px"></div></td></tr></table>
            """),
        Map.entry("two_origins", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="4" style=""><div style="width:20px;height:140px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c" rowspan="3" style=""><div style="width:30px;height:80px"></div></td><td id="d"  style=""><div style="width:40px;height:10px"></div></td></tr><tr id="r3" style=""><td id="e" rowspan="2" style=""><div style="width:40px;height:50px"></div></td><td id="f"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r4" style=""><td id="g"  style=""><div style="width:30px;height:10px"></div></td></tr></table>
            """),
        Map.entry("empty_cell_row", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style=""><div style="width:20px;height:100px"></div></td><td id="b"  style=""><div style="width:30px;height:0px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:30px;height:0px"></div></td></tr></table>
            """),
        Map.entry("zero_percent", """
            <table id="t" style="width:200px"><tr id="r1" style=""><td id="a" colspan="2" style="width:0%"><div style="width:30px;height:10px"></div></td><td id="b"  style=""><div style="width:20px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:10px;height:10px"></div></td><td id="d"  style=""><div style="width:20px;height:10px"></div></td><td id="e"  style=""><div style="width:20px;height:10px"></div></td></tr></table>
            """),
        Map.entry("fixed_children_span", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td></tr><tr id="r2" style=""><td id="b"  style="width:20px"><div style="width:10px;height:10px"></div></td><td id="c"  style="width:30px"><div style="width:20px;height:10px"></div></td></tr></table>
            """),
        Map.entry("oversized_fixed", """
            <table id="t" style="width:150px"><tr id="r1" style=""><td id="a" colspan="2" style="width:300px"><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:20px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:20px;height:10px"></div></td></tr></table>
            """),
        Map.entry("span_min_width", """
            <table id="t" style="width:100px"><tr id="r1" style=""><td id="a" colspan="2" style="min-width:150px"><div style="width:30px;height:10px"></div></td><td id="b"  style=""><div style="width:20px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:20px;height:10px"></div></td></tr></table>
            """),
        Map.entry("all_percent", """
            <table id="t" style="width:300px"><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td></tr><tr id="r2" style=""><td id="b"  style="width:20%"><div style="width:20px;height:10px"></div></td><td id="c"  style="width:30%"><div style="width:30px;height:10px"></div></td></tr></table>
            """),
        Map.entry("custom_display", """
            <div id="t" style="display:table"><div id="foot" style="display:table-footer-group"><div id="rf" style="display:table-row"><div id="f" style="display:table-cell;width:80px;height:20px" colspan="3"></div></div></div><div id="head" style="display:table-header-group"><div id="rh" style="display:table-row"><div id="h" style="display:table-cell;width:40px;height:10px"></div></div></div></div>
            """),
        Map.entry("colspan", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:20px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:15px"></div></td><td id="d"  style=""><div style="width:40px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("span_only", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="3" style=""><div style="width:120px;height:10px"></div></td></tr><tr id="r2" style=""><td id="b" colspan="3" style=""><div style="width:60px;height:20px"></div></td></tr></table>
            """),
        Map.entry("crossed_columns", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:100px;height:10px"></div></td><td id="b" colspan="2" style=""><div style="width:200px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d" colspan="2" style=""><div style="width:180px;height:10px"></div></td><td id="e"  style=""><div style="width:50px;height:10px"></div></td></tr></table>
            """),
        Map.entry("rowspan", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style=""><div style="width:20px;height:90px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:50px;height:20px"></div></td></tr></table>
            """),
        Map.entry("rowspan_fixed_row", """
            <table id="t" style=""><tr id="r1" style="height:30px"><td id="a" rowspan="2" style=""><div style="width:20px;height:90px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:50px;height:20px"></div></td></tr></table>
            """),
        Map.entry("rowspan_fixed_cell", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style=""><div style="width:20px;height:90px"></div></td><td id="b"  style="height:30px"><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:50px;height:20px"></div></td></tr></table>
            """),
        Map.entry("rowspan_all_fixed", """
            <table id="t" style=""><tr id="r1" style="height:10px"><td id="a" rowspan="2" style=""><div style="width:20px;height:90px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style="height:20px"><td id="c"  style=""><div style="width:50px;height:20px"></div></td></tr></table>
            """),
        Map.entry("rowspan_empty_rows", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="3" style=""><div style="width:20px;height:90px"></div></td></tr><tr id="r2" style=""></tr><tr id="r3" style=""></tr></table>
            """),
        Map.entry("rowspan_mixed_empty", """
            <table id="t" style=""><tr id="r1" style="height:10px"><td id="a" rowspan="3" style=""><div style="width:20px;height:90px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""></tr><tr id="r3" style="height:20px"><td id="c"  style=""><div style="width:50px;height:20px"></div></td></tr></table>
            """),
        Map.entry("crossed_rows", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="3" style=""><div style="width:20px;height:100px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c" rowspan="3" style=""><div style="width:30px;height:120px"></div></td></tr><tr id="r3" style=""></tr><tr id="r4" style=""><td id="d"  style=""><div style="width:20px;height:10px"></div></td></tr></table>
            """),
        Map.entry("combined", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" colspan="2" style=""><div style="width:100px;height:80px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr><tr id="r3" style=""><td id="d"  style=""><div style="width:20px;height:10px"></div></td><td id="e"  style=""><div style="width:40px;height:10px"></div></td><td id="f"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("rowspan_zero", """
            <table id="t" style=""><tbody id="g1"><tr id="r1" style=""><td id="a" rowspan="0" style=""><div style="width:20px;height:80px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr><tr id="r3" style=""><td id="d"  style=""><div style="width:30px;height:10px"></div></td></tr></tbody><tbody id="g2"><tr id="r4" style=""><td id="e"  style=""><div style="width:10px;height:20px"></div></td><td id="f"  style=""><div style="width:20px;height:20px"></div></td></tr></tbody></table>
            """),
        Map.entry("rowspan_clipped", """
            <table id="t" style=""><tbody id="g1"><tr id="r1" style=""><td id="a" rowspan="999999999999999999999" style=""><div style="width:20px;height:80px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr></tbody><tbody id="g2"><tr id="r3" style=""><td id="d"  style=""><div style="width:20px;height:10px"></div></td><td id="e"  style=""><div style="width:30px;height:10px"></div></td></tr></tbody></table>
            """),
        Map.entry("hidden_row", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style=""><div style="width:20px;height:60px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="hidden" style="display:none"><td id="hiddenCell"  style=""><div style="width:500px;height:500px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr><tr id="r3" style=""><td id="d"  style=""><div style="width:20px;height:10px"></div></td><td id="e"  style=""><div style="width:30px;height:10px"></div></td></tr></table>
            """),
        Map.entry("hidden_cell", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style=""><div style="width:20px;height:60px"></div></td><td id="hidden" colspan="4" style="display:none"><div style="width:500px;height:500px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr></table>
            """),
        Map.entry("footer_first", """
            <table id="t" style=""><tfoot id="foot"><tr id="rf" style=""><td id="f" colspan="2" style=""><div style="width:100px;height:20px"></div></td></tr></tfoot><tbody id="body"><tr id="r1" style=""><td id="a"  style=""><div style="width:20px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr></tbody><thead id="head"><tr id="rh" style=""><td id="h" colspan="2" style=""><div style="width:60px;height:15px"></div></td></tr></thead></table>
            """),
        Map.entry("repeated_groups", """
            <table id="t" style=""><tfoot id="foot"><tr id="rf" style=""><td id="f"  style=""><div style="width:50px;height:10px"></div></td></tr></tfoot><thead id="head"><tr id="rh" style=""><td id="h"  style=""><div style="width:50px;height:15px"></div></td></tr></thead><tbody id="body"><tr id="rb" style=""><td id="b"  style=""><div style="width:50px;height:20px"></div></td></tr></tbody><tfoot id="foot2"><tr id="rf2" style=""><td id="f2"  style=""><div style="width:50px;height:25px"></div></td></tr></tfoot><thead id="head2"><tr id="rh2" style=""><td id="h2"  style=""><div style="width:50px;height:30px"></div></td></tr></thead></table>
            """),
        Map.entry("empty_groups", """
            <table id="t" style=""><thead id="head"></thead><tbody id="body"><tr id="r1" style=""><td id="a"  style=""><div style="width:20px;height:20px"></div></td></tr></tbody><tfoot id="foot"></tfoot></table>
            """),
        Map.entry("invalid_spans", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="0" rowspan="-2" style=""><div style="width:20px;height:10px"></div></td><td id="b" colspan="no" rowspan="1.9" style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c" colspan="+2tail" style=""><div style="width:80px;height:10px"></div></td></tr></table>
            """),
        Map.entry("fixed_columns", """
            <table id="t" style="width:240px"><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style="width:60px"><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("fixed_span", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2" style="width:150px"><div style="width:20px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("percent_span", """
            <table id="t" style="width:300px"><tr id="r1" style=""><td id="a" colspan="2" style="width:70%"><div style="width:20px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("percent_column", """
            <table id="t" style="width:300px"><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style="width:40%"><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("percent_auto", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style="width:40%"><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("percent_overflow", """
            <table id="t" style="width:300px"><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:120px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style="width:80%"><div style="width:20px;height:10px"></div></td><td id="d"  style="width:80%"><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("narrow", """
            <table id="t" style="width:100px"><tr id="r1" style=""><td id="a" colspan="2" style=""><div style="width:200px;height:10px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:20px;height:10px"></div></td><td id="d"  style=""><div style="width:30px;height:10px"></div></td><td id="e"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("boxes", """
            <table id="t" style="width:240px;box-sizing:border-box;padding:3px;border:2px solid black"><tr id="r1" style=""><td id="a" colspan="2" rowspan="2" style="padding:4px;border:2px solid black"><div style="width:100px;height:70px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr><tr id="r3" style=""><td id="d"  style=""><div style="width:20px;height:10px"></div></td><td id="e"  style=""><div style="width:30px;height:10px"></div></td><td id="f"  style=""><div style="width:10px;height:10px"></div></td></tr></table>
            """),
        Map.entry("nested", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" colspan="2"><table id="nested"><tr id="inner" style=""><td id="i" rowspan="2" style=""><div style="width:100px;height:50px"></div></td><td id="j"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="inner2" style=""><td id="k"  style=""><div style="width:50px;height:20px"></div></td></tr></table></td></tr><tr id="r2" style=""><td id="b"  style=""><div style="width:30px;height:20px"></div></td><td id="c"  style=""><div style="width:40px;height:10px"></div></td></tr></table>
            """),
        Map.entry("wrapping", """
            <table id="t" style="width:100px"><tr id="r1" style=""><td id="a" colspan="2"><span style="display:inline-block;width:50px;height:20px"></span><span style="display:inline-block;width:50px;height:20px"></span><span style="display:inline-block;width:50px;height:20px"></span></td></tr><tr id="r2" style=""><td id="b"  style=""><div style="width:20px;height:10px"></div></td><td id="c"  style=""><div style="width:30px;height:10px"></div></td></tr></table>
            """),
        Map.entry("small_cell_height", """
            <table id="t" style=""><tr id="r1" style=""><td id="a" rowspan="2" style="height:20px"><div style="width:20px;height:100px"></div></td><td id="b"  style=""><div style="width:30px;height:10px"></div></td></tr><tr id="r2" style=""><td id="c"  style=""><div style="width:40px;height:20px"></div></td></tr></table>
            """),
        Map.entry("cell_minimum", """
            <table id="t" style="width:60px"><tr id="r1" style=""><td id="a" colspan="2" style="width:40px"><div style="width:150px;height:10px"></div></td></tr><tr id="r2" style=""><td id="b"  style=""><div style="width:20px;height:10px"></div></td><td id="c"  style=""><div style="width:30px;height:10px"></div></td></tr></table>
            """)
    );
    private static final String CHROMIUM_JSON = """
        {
          "nested_rows":{"t":[0,0,90,160],"r1":[0,0,90,10],"a":[0,0,20,160],"b":[20,0,30,10],"r2":[0,10,90,80],"c":[20,10,30,140],"d":[50,10,40,80],"r3":[0,90,90,60],"e":[50,90,40,60],"r4":[0,150,90,10],"f":[20,150,30,10],"g":[50,150,40,10]},
          "same_end_rows":{"t":[0,0,90,150],"r1":[0,0,90,10],"a":[0,0,20,150],"b":[20,0,30,10],"r2":[0,10,90,73.3281],"c":[20,10,30,140],"d":[50,10,40,73.3281],"r3":[0,83.3281,90,66.6719],"e":[50,83.3281,40,66.6719]},
          "two_origins":{"t":[0,0,120,140],"r1":[0,0,120,10],"a":[0,0,20,140],"b":[20,0,30,10],"r2":[0,10,120,35],"c":[20,10,30,130],"d":[50,10,40,35],"r3":[0,45,120,70],"e":[50,45,40,95],"f":[90,45,30,70],"r4":[0,115,120,25],"g":[90,115,30,25]},
          "empty_cell_row":{"t":[0,0,50,100],"r1":[0,0,50,0],"a":[0,0,20,100],"b":[20,0,30,0],"r2":[0,0,50,100],"c":[20,0,30,100]},
          "zero_percent":{"t":[0,0,200,20],"r1":[0,0,200,10],"a":[0,0,120,10],"b":[120,0,80,10],"r2":[0,10,200,10],"c":[0,10,40,10],"d":[40,10,80,10],"e":[120,10,80,10]},
          "fixed_children_span":{"t":[0,0,120,20],"r1":[0,0,120,10],"a":[0,0,120,10],"r2":[0,10,120,10],"b":[0,10,48,10],"c":[48,10,72,10]},
          "oversized_fixed":{"t":[0,0,150,20],"r1":[0,0,150,10],"a":[0,0,130,10],"b":[130,0,20,10],"r2":[0,10,150,10],"c":[0,10,52,10],"d":[52,10,78,10],"e":[130,10,20,10]},
          "span_min_width":{"t":[0,0,170,20],"r1":[0,0,170,10],"a":[0,0,150,10],"b":[150,0,20,10],"r2":[0,10,170,10],"c":[0,10,60,10],"d":[60,10,90,10],"e":[150,10,20,10]},
          "all_percent":{"t":[0,0,300,20],"r1":[0,0,300,10],"a":[0,0,300,10],"r2":[0,10,300,10],"b":[0,10,120,10],"c":[120,10,180,10]},
          "custom_display":{"t":[0,0,80,30],"foot":[0,10,80,20],"rf":[0,10,80,20],"f":[0,10,80,20],"head":[0,0,80,10],"rh":[0,0,80,10],"h":[0,0,80,10]},
          "colspan":{"t":[0,0,150,35],"r1":[0,0,150,20],"a":[0,0,120,20],"b":[120,0,30,20],"r2":[0,20,150,15],"c":[0,20,40,15],"d":[40,20,80,15],"e":[120,20,30,15]},
          "span_only":{"t":[0,0,120,30],"r1":[0,0,120,10],"a":[0,0,120,10],"r2":[0,10,120,20],"b":[0,10,120,20]},
          "crossed_columns":{"t":[0,0,390,20],"r1":[0,0,390,10],"a":[0,0,190,10],"b":[190,0,200,10],"r2":[0,10,390,10],"c":[0,10,100,10],"d":[100,10,218.5625,10],"e":[318.5625,10,71.4375,10]},
          "rowspan":{"t":[0,0,70,90],"r1":[0,0,70,30],"a":[0,0,20,90],"b":[20,0,50,30],"r2":[0,30,70,60],"c":[20,30,50,60]},
          "rowspan_fixed_row":{"t":[0,0,70,90],"r1":[0,0,70,30],"a":[0,0,20,90],"b":[20,0,50,30],"r2":[0,30,70,60],"c":[20,30,50,60]},
          "rowspan_fixed_cell":{"t":[0,0,70,90],"r1":[0,0,70,30],"a":[0,0,20,90],"b":[20,0,50,30],"r2":[0,30,70,60],"c":[20,30,50,60]},
          "rowspan_all_fixed":{"t":[0,0,70,90],"r1":[0,0,70,30],"a":[0,0,20,90],"b":[20,0,50,30],"r2":[0,30,70,60],"c":[20,30,50,60]},
          "rowspan_empty_rows":{"t":[0,0,20,90],"r1":[0,0,20,0],"a":[0,0,20,90],"r2":[0,0,20,0],"r3":[0,0,20,90]},
          "rowspan_mixed_empty":{"t":[0,0,70,90],"r1":[0,0,70,30],"a":[0,0,20,90],"b":[20,0,50,30],"r2":[0,30,70,0],"r3":[0,30,70,60],"c":[20,30,50,60]},
          "crossed_rows":{"t":[0,0,50,130],"r1":[0,0,50,10],"a":[0,0,20,118],"b":[20,0,30,10],"r2":[0,10,50,108],"c":[20,10,30,120],"r3":[0,118,50,0],"r4":[0,118,50,12],"d":[0,118,20,12]},
          "combined":{"t":[0,0,140,90],"r1":[0,0,140,26.6563],"a":[0,0,100,80],"b":[100,0,40,26.6563],"r2":[0,26.6563,140,53.3438],"c":[100,26.6563,40,53.3438],"r3":[0,80,140,10],"d":[0,80,33.3281,10],"e":[33.3281,80,66.6719,10],"f":[100,80,40,10]},
          "rowspan_zero":{"t":[0,0,60,100],"g1":[0,0,60,80],"r1":[0,0,60,20],"a":[0,0,20,80],"b":[20,0,40,20],"r2":[0,20,60,40],"c":[20,20,40,40],"r3":[0,60,60,20],"d":[20,60,40,20],"g2":[0,80,60,20],"r4":[0,80,60,20],"e":[0,80,20,20],"f":[20,80,40,20]},
          "rowspan_clipped":{"t":[0,0,60,90],"g1":[0,0,60,80],"r1":[0,0,60,26.6563],"a":[0,0,20,80],"b":[20,0,40,26.6563],"r2":[0,26.6563,60,53.3438],"c":[20,26.6563,40,53.3438],"g2":[0,80,60,10],"r3":[0,80,60,10],"d":[0,80,20,10],"e":[20,80,40,10]},
          "hidden_row":{"t":[0,0,60,70],"r1":[0,0,60,20],"a":[0,0,20,60],"b":[20,0,40,20],"hidden":[0,0,0,0],"hiddenCell":[0,0,0,0],"r2":[0,20,60,40],"c":[20,20,40,40],"r3":[0,60,60,10],"d":[0,60,20,10],"e":[20,60,40,10]},
          "hidden_cell":{"t":[0,0,60,60],"r1":[0,0,60,20],"a":[0,0,20,60],"hidden":[0,0,0,0],"b":[20,0,40,20],"r2":[0,20,60,40],"c":[20,20,40,40]},
          "footer_first":{"t":[0,0,100,45],"foot":[0,25,100,20],"rf":[0,25,100,20],"f":[0,25,100,20],"body":[0,15,100,10],"r1":[0,15,100,10],"a":[0,15,40,10],"b":[40,15,60,10],"head":[0,0,100,15],"rh":[0,0,100,15],"h":[0,0,100,15]},
          "repeated_groups":{"t":[0,0,50,100],"foot":[0,90,50,10],"rf":[0,90,50,10],"f":[0,90,50,10],"head":[0,0,50,15],"rh":[0,0,50,15],"h":[0,0,50,15],"body":[0,15,50,20],"rb":[0,15,50,20],"b":[0,15,50,20],"foot2":[0,35,50,25],"rf2":[0,35,50,25],"f2":[0,35,50,25],"head2":[0,60,50,30],"rh2":[0,60,50,30],"h2":[0,60,50,30]},
          "empty_groups":{"t":[0,0,20,20],"head":[0,0,20,0],"body":[0,0,20,20],"r1":[0,0,20,20],"a":[0,0,20,20],"foot":[0,20,20,0]},
          "invalid_spans":{"t":[0,0,80,20],"r1":[0,0,80,10],"a":[0,0,32,10],"b":[32,0,48,10],"r2":[0,10,80,10],"c":[0,10,80,10]},
          "fixed_columns":{"t":[0,0,240,20],"r1":[0,0,240,10],"a":[0,0,180,10],"b":[180,0,60,10],"r2":[0,10,240,10],"c":[0,10,60,10],"d":[60,10,120,10],"e":[180,10,60,10]},
          "fixed_span":{"t":[0,0,180,20],"r1":[0,0,180,10],"a":[0,0,150,10],"b":[150,0,30,10],"r2":[0,10,180,10],"c":[0,10,60,10],"d":[60,10,90,10],"e":[150,10,30,10]},
          "percent_span":{"t":[0,0,300,20],"r1":[0,0,300,10],"a":[0,0,210,10],"b":[210,0,90,10],"r2":[0,10,300,10],"c":[0,10,84,10],"d":[84,10,126,10],"e":[210,10,90,10]},
          "percent_column":{"t":[0,0,300,20],"r1":[0,0,300,10],"a":[0,0,247.0469,10],"b":[247.0469,0,52.9531,10],"r2":[0,10,300,10],"c":[0,10,120,10],"d":[120,10,127.0469,10],"e":[247.0469,10,52.9531,10]},
          "percent_auto":{"t":[0,0,170,20],"r1":[0,0,170,10],"a":[0,0,140,10],"b":[140,0,30,10],"r2":[0,10,170,10],"c":[0,10,68,10],"d":[68,10,72,10],"e":[140,10,30,10]},
          "percent_overflow":{"t":[0,0,300,20],"r1":[0,0,300,10],"a":[0,0,270,10],"b":[270,0,30,10],"r2":[0,10,300,10],"c":[0,10,207.4531,10],"d":[207.4531,10,62.5469,10],"e":[270,10,30,10]},
          "narrow":{"t":[0,0,230,20],"r1":[0,0,230,10],"a":[0,0,200,10],"b":[200,0,30,10],"r2":[0,10,230,10],"c":[0,10,80,10],"d":[80,10,120,10],"e":[200,10,30,10]},
          "boxes":{"t":[0,0,240,102],"r1":[5,5,230,27.3281],"a":[5,5,169.4688,82],"b":[174.4688,5,60.5313,27.3281],"r2":[5,32.3281,230,54.6719],"c":[174.4688,32.3281,60.5313,54.6719],"r3":[5,87,230,10],"d":[5,87,67.7813,10],"e":[72.7813,87,101.6875,10],"f":[174.4688,87,60.5313,10]},
          "nested":{"t":[0,0,150,70],"r1":[0,0,150,50],"a":[0,0,150,50],"nested":[0,0,150,50],"inner":[0,0,150,16.6563],"i":[0,0,100,50],"j":[100,0,50,16.6563],"inner2":[0,16.6563,150,33.3438],"k":[100,16.6563,50,33.3438],"r2":[0,50,150,20],"b":[0,50,64.2813,20],"c":[64.2813,50,85.7188,20]},
          "wrapping":{"t":[0,0,100,50],"r1":[0,0,100,40],"a":[0,0,100,40],"r2":[0,40,100,10],"b":[0,40,40,10],"c":[40,40,60,10]},
          "small_cell_height":{"t":[0,0,60,100],"r1":[0,0,60,33.3281],"a":[0,0,20,100],"b":[20,0,40,33.3281],"r2":[0,33.3281,60,66.6719],"c":[20,33.3281,40,66.6719]},
          "cell_minimum":{"t":[0,0,150,20],"r1":[0,0,150,10],"a":[0,0,150,10],"r2":[0,10,150,10],"b":[0,10,60,10],"c":[60,10,90,10]}
        }
        """;
    @TestFactory Stream<DynamicTest> matchesChromiumSpans() {
        var expected = JsonParser.parseString(CHROMIUM_JSON).getAsJsonObject();
        return CASES.entrySet().stream().map(test -> DynamicTest.dynamicTest(test.getKey(), () -> {
            Size.setViewportOverride(800, 600);
            String path = "test://table-span/" + test.getKey();
            HTML.putTemple(path, "<!doctype html><html><head><style>" + CSS + "</style></head><body>" + test.getValue() + "</body></html>");
            Document doc = new Document(path, false);
            try {
                doc.refresh();
                doc.commitRenderState();
                for (var entry : expected.getAsJsonObject(test.getKey()).entrySet()) {
                    var node = doc.querySelector("#" + entry.getKey());
                    assertNotNull(node, entry.getKey());
                    var rect = node.getBoundingClientRect();
                    double[] actual = {rect.x, rect.y, rect.width, rect.height};
                    for (int axis = 0; axis < 4; axis++) assertEquals(entry.getValue().getAsJsonArray().get(axis).getAsDouble(), actual[axis], .25, test.getKey() + " #" + entry.getKey() + " axis=" + axis);
                }
            } finally { doc.remove(); Size.clearViewportOverride(); }
        }));
    }
}
