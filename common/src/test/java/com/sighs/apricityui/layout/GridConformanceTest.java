package com.sighs.apricityui.layout;

import com.google.gson.Gson;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Transition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run with AUI_GRID_BROWSER=check or update to compare or refresh Chromium expectations.
 * The optional browserOracle factory requires Node.js and Chromium (CHROME_PATH if not detected).
 * Force a fresh test run when changing the environment variable with Gradle test --rerun.
 * Text baseline snapshots use equal font sizes; SubgridTest covers mixed sizes with runtime font metrics.
 */
class GridConformanceTest {
    private record Motion(String target, Map<String, String> to, double progress) {}
    private record Case(String name, String source, Map<String, double[]> expected, Motion motion) {}

    private static final String[] CASES = {
        """
        {
          "name": "subgrid-columns",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:30px/40px 110px;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              30
            ],
            "s": [
              0,
              0,
              160,
              30
            ],
            "a": [
              0,
              0,
              40,
              30
            ],
            "b": [
              50,
              0,
              110,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-rows",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:60px;grid-template:20px 50px/60px;gap:10px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              60,
              80
            ],
            "s": [
              0,
              0,
              60,
              80
            ],
            "a": [
              0,
              0,
              60,
              20
            ],
            "b": [
              0,
              30,
              60,
              50
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-both",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:20px 50px/40px 110px;gap:10px}#s{display:grid;grid-area:1/1/3/3;grid-template:subgrid/subgrid}#b{grid-area:2/2}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              80
            ],
            "s": [
              0,
              0,
              160,
              80
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              50,
              30,
              110,
              50
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-local-lines",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:190px;grid-template-columns:30px 60px 100px;grid-auto-rows:20px}#s{display:grid;grid-column:2/4;grid-template-columns:subgrid [Start] [Middle] [End]}#a{grid-column:Middle/End}#b{grid-column:-3/-2}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              190,
              20
            ],
            "s": [
              30,
              0,
              160,
              20
            ],
            "a": [
              90,
              0,
              100,
              10
            ],
            "b": [
              30,
              10,
              60,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-parent-names",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:190px;grid-template-columns:[Start] 30px [Mid] 60px [Finish] 100px;grid-auto-rows:20px}#s{display:grid;grid-column:1/3;grid-template-columns:subgrid}#a{grid-column:Mid/Finish}#b{grid-column:Start/Mid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              190,
              20
            ],
            "s": [
              0,
              0,
              90,
              20
            ],
            "a": [
              30,
              0,
              60,
              10
            ],
            "b": [
              0,
              10,
              30,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-repeat-names",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:20px/repeat(3,40px)}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid repeat(auto-fill,[Line])}#a{grid-column:Line 2/Line 3}#b{grid-column:Line 3/Line 4}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              20
            ],
            "s": [
              0,
              0,
              120,
              20
            ],
            "a": [
              40,
              0,
              40,
              20
            ],
            "b": [
              80,
              0,
              40,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-overflow-lines",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template:20px/40px 60px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{grid-column:-9/-7}#b{grid-column:8/10}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "s": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              40,
              0,
              60,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-gap-zero",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:30px/40px 60px;gap:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;gap:0}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              30
            ],
            "s": [
              0,
              0,
              120,
              30
            ],
            "a": [
              0,
              0,
              50,
              30
            ],
            "b": [
              50,
              0,
              70,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-gap-larger",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:30px/40px 60px;gap:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;column-gap:40px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              30
            ],
            "s": [
              0,
              0,
              120,
              30
            ],
            "a": [
              0,
              0,
              30,
              30
            ],
            "b": [
              70,
              0,
              50,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-box-edges",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:40px/40px 60px;gap:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;padding:4px;border:2px solid black;margin:3px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              40
            ],
            "s": [
              3,
              3,
              114,
              34
            ],
            "a": [
              9,
              9,
              31,
              22
            ],
            "b": [
              60,
              9,
              51,
              22
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-intrinsic-columns",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{width:40px;height:20px}#b{width:90px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              30
            ],
            "s": [
              0,
              0,
              140,
              30
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              50,
              0,
              90,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-intrinsic-rows",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template-rows:auto auto;gap:10px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid}#a{height:20px}#b{height:60px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              90
            ],
            "s": [
              0,
              0,
              100,
              90
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              0,
              30,
              100,
              60
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-intrinsic-edges",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;padding:4px;border:2px solid black;margin:3px}#a{width:40px;height:20px}#b{width:90px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              158,
              48
            ],
            "s": [
              3,
              3,
              152,
              42
            ],
            "a": [
              9,
              9,
              40,
              20
            ],
            "b": [
              59,
              9,
              90,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-gap-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;gap:30px}#a{width:40px;height:20px}#b{width:90px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              30
            ],
            "s": [
              0,
              0,
              160,
              30
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              70,
              0,
              90,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-grandchild-span",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto auto;gap:10px}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid}#a{grid-column:span 2;width:100px;height:20px}#b{width:60px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              170,
              30
            ],
            "s": [
              0,
              0,
              170,
              30
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              110,
              0,
              60,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-nested",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:180px;grid-template:30px/40px 50px 70px;gap:10px}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid}#n{display:grid;grid-column:2/4;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"n\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              30
            ],
            "s": [
              0,
              0,
              180,
              30
            ],
            "n": [
              50,
              0,
              130,
              30
            ],
            "a": [
              50,
              0,
              50,
              30
            ],
            "b": [
              110,
              0,
              70,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-nested-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto auto;gap:10px}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid;padding:2px}#n{display:grid;grid-column:2/4;grid-template-columns:subgrid;padding:3px}#a{width:40px;height:20px}#b{width:70px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"n\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              40
            ],
            "s": [
              0,
              0,
              140,
              40
            ],
            "n": [
              12,
              2,
              126,
              36
            ],
            "a": [
              15,
              5,
              40,
              20
            ],
            "b": [
              65,
              5,
              70,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-fallback",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template-columns:subgrid;grid-auto-columns:40px;grid-auto-rows:20px}#a{grid-column:2}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              40
            ],
            "a": [
              50,
              0,
              50,
              20
            ],
            "b": [
              0,
              20,
              50,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-absolute",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:30px/40px 60px;gap:20px}#s{display:grid;position:relative;grid-column:span 2;grid-template-columns:subgrid}#a{position:absolute;grid-column:2/3;inset:0}#b{height:20px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              30
            ],
            "s": [
              0,
              0,
              120,
              30
            ],
            "a": [
              60,
              0,
              60,
              30
            ],
            "b": [
              0,
              0,
              40,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-full-autoplacement",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template:20px/40px 60px}#s{display:grid;grid-area:1/1/2/3;grid-template:subgrid/subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div><div id=\\"d\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "s": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              40,
              0,
              60,
              20
            ],
            "c": [
              0,
              0,
              40,
              20
            ],
            "d": [
              40,
              0,
              60,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-independent-axis",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:70px/40px 60px;gap:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;grid-template-rows:20px 50px}#b{grid-area:2/2}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              70
            ],
            "s": [
              0,
              0,
              120,
              70
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              60,
              20,
              60,
              50
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-only-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-rows:auto auto}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid;grid-template-columns:auto auto}#a{width:50px;height:20px}#b{width:80px;height:30px;grid-area:2/2}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              130,
              50
            ],
            "s": [
              0,
              0,
              130,
              50
            ],
            "a": [
              0,
              0,
              50,
              20
            ],
            "b": [
              50,
              20,
              80,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "same-span-order",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto auto;gap:10px}#a{grid-column:1/3;width:100px;height:10px}#b{grid-column:2/4;width:100px;height:10px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              155,
              30
            ],
            "a": [
              0,
              0,
              100,
              10
            ],
            "b": [
              55,
              20,
              100,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "fraction-fixed-span",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:180px;grid-template-columns:auto 1fr 2fr;gap:10px}#a{grid-column:1/4;min-width:120px;height:20px}#b{grid-column:1;width:20px;height:10px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              40
            ],
            "a": [
              0,
              0,
              180,
              20
            ],
            "b": [
              0,
              30,
              20,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "percentage-gap-auto-height",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template-rows:20px 30px;row-gap:10%}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              50
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              0,
              25,
              100,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "auto-repeat-min-width",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;min-width:100px;grid-template-columns:repeat(auto-fill,30px);gap:5px;grid-auto-rows:20px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              30,
              20
            ],
            "b": [
              35,
              0,
              30,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-both-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template:auto auto/auto auto;gap:10px}#s{display:grid;grid-area:1/1/3/3;grid-template:subgrid/subgrid}#a{width:40px;height:20px}#b{grid-area:2/2;width:80px;height:60px}#c{grid-area:1/1;width:50px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              100
            ],
            "s": [
              0,
              0,
              140,
              100
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              60,
              40,
              80,
              60
            ],
            "c": [
              0,
              0,
              50,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-baseline",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:140px;grid-template-columns:100px 40px;align-items:baseline}#s{display:grid;grid-template-columns:subgrid;grid-template-rows:20px 30px}#a{height:20px}#b{height:30px}#c{height:40px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              50
            ],
            "s": [
              0,
              20,
              100,
              50
            ],
            "a": [
              0,
              20,
              100,
              20
            ],
            "b": [
              0,
              40,
              100,
              30
            ],
            "c": [
              100,
              0,
              40,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-last-baseline",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:140px;grid-template-columns:100px 40px;align-items:last baseline}#s{display:grid;grid-template-columns:subgrid;grid-template-rows:20px 30px}#a{height:20px}#b{height:30px}#c{height:40px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              50
            ],
            "s": [
              0,
              0,
              100,
              50
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              0,
              20,
              100,
              30
            ],
            "c": [
              100,
              10,
              40,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-gap-edges",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template-rows:auto auto;gap:10px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid;row-gap:30px;padding:3px;border:2px solid black}#a{height:20px}#b{height:60px}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              120
            ],
            "s": [
              0,
              0,
              100,
              120
            ],
            "a": [
              5,
              5,
              90,
              20
            ],
            "b": [
              5,
              55,
              90,
              60
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-ignores-axis-size",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:60px/40px 110px;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;width:50px;max-width:80px;justify-self:center}#a,#b{height:20px}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              60
            ],
            "s": [
              0,
              0,
              160,
              60
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              50,
              0,
              110,
              20
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-area-names",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:\\"Left Right\\" 40px/60px 100px}#s{display:grid;grid-area:1/1/2/3;grid-template-columns:subgrid}#a{grid-column:Right}#b{grid-column:Left}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              40
            ],
            "s": [
              0,
              0,
              160,
              40
            ],
            "a": [
              60,
              0,
              100,
              20
            ],
            "b": [
              0,
              20,
              60,
              20
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-local-areas",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template-columns:60px 100px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;grid-template-areas:\\"A B\\"}#a{grid-area:B;height:20px}#b{grid-area:A;height:30px}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              30
            ],
            "s": [
              0,
              0,
              160,
              30
            ],
            "a": [
              60,
              0,
              100,
              20
            ],
            "b": [
              0,
              0,
              60,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-column-flow",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:140px;grid-template:20px 30px/60px 80px}#s{display:grid;grid-area:1/1/3/3;grid-template:subgrid/subgrid;grid-auto-flow:column}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              140,
              50
            ],
            "s": [
              0,
              0,
              140,
              50
            ],
            "a": [
              0,
              0,
              60,
              20
            ],
            "b": [
              0,
              20,
              60,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-order",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:40px/60px 100px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{order:1}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              40
            ],
            "s": [
              0,
              0,
              160,
              40
            ],
            "a": [
              60,
              0,
              100,
              40
            ],
            "b": [
              0,
              0,
              60,
              40
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-ordinary-height",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template:60px/60px 100px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;height:30px;align-self:start}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              60
            ],
            "s": [
              0,
              0,
              160,
              30
            ],
            "a": [
              0,
              0,
              60,
              30
            ],
            "b": [
              60,
              0,
              100,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-fr-parent",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:160px;grid-template-columns:1fr 2fr;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{width:60px;height:20px}#b{height:30px}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              30
            ],
            "s": [
              0,
              0,
              160,
              30
            ],
            "a": [
              0,
              0,
              60,
              20
            ],
            "b": [
              70,
              0,
              90,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-auto-fit-parent",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:200px;grid-template-columns:repeat(auto-fit,minmax(30px,1fr));gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{height:20px}#b{height:30px}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              95,
              20
            ],
            "b": [
              105,
              0,
              95,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-full-dense",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template:20px/40px 60px}#s{display:grid;grid-area:1/1/2/3;grid-template:subgrid/subgrid;grid-auto-flow:dense}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div><div id=\\"d\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "s": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              40,
              0,
              60,
              20
            ],
            "c": [
              0,
              0,
              40,
              20
            ],
            "d": [
              40,
              0,
              60,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-gap-percent",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:30px/40px 60px;gap:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;column-gap:25%}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              30
            ],
            "s": [
              0,
              0,
              120,
              30
            ],
            "a": [
              0,
              0,
              35,
              30
            ],
            "b": [
              65,
              0,
              55,
              30
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-empty-decorations",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;padding:5px;border:2px solid black}</style></head><body><div id=\\"g\\"><div id=\\"s\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              24,
              14
            ],
            "s": [
              0,
              0,
              24,
              14
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-repeat-fixed",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:20px/repeat(3,40px)}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid repeat(2,[A] [B])}#a{grid-column:B 1/A 2}#b{grid-column:A 2/B 2}#c{display:none}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              20
            ],
            "s": [
              0,
              0,
              120,
              20
            ],
            "a": [
              40,
              0,
              40,
              20
            ],
            "b": [
              80,
              0,
              40,
              20
            ],
            "c": [
              0,
              0,
              0,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-normal-child-grid",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:120px;grid-template:40px/40px 70px;gap:10px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{display:grid;grid-template-columns:1fr 1fr}#c{height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"><div id=\\"b\\" style=\\"height:20px\\"></div><div id=\\"c\\"></div></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              40
            ],
            "s": [
              0,
              0,
              120,
              40
            ],
            "a": [
              0,
              0,
              40,
              40
            ],
            "b": [
              0,
              0,
              20,
              20
            ],
            "c": [
              20,
              0,
              20,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "auto-repeat-min-width-ceiling",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;min-width:110px;grid-template-columns:repeat(auto-fill,30px);gap:5px;grid-auto-rows:20px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              135,
              20
            ],
            "a": [
              0,
              0,
              30,
              20
            ],
            "b": [
              35,
              0,
              30,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "auto-repeat-max-width",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;max-width:100px;grid-template-columns:repeat(auto-fill,30px);gap:5px;grid-auto-rows:20px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              30,
              20
            ],
            "b": [
              35,
              0,
              30,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "auto-repeat-min-height",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;min-height:110px;grid-template-rows:repeat(auto-fill,30px);gap:5px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              135
            ],
            "a": [
              0,
              0,
              100,
              30
            ],
            "b": [
              0,
              35,
              100,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "percentage-columns-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:50% 50%}#a{width:40px;height:20px}#b{width:80px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              30
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              60,
              0,
              80,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "percentage-gap-auto-width",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:40px 60px;column-gap:10%}#a,#b{height:20px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              50,
              0,
              60,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "min-content-track-spanning",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:min-content max-content auto;gap:5px}#a{grid-column:span 2;width:100px;height:10px}#b{grid-column:2/4;width:130px;height:10px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              182.5,
              25
            ],
            "a": [
              0,
              0,
              100,
              10
            ],
            "b": [
              52.5,
              15,
              130,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-direct-text",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:180px;grid-template-columns:70px 100px;gap:10px;font:16px monospace;line-height:20px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}#a{height:20px}</style></head><body><div id=\\"g\\"><div id=\\"s\\">hello<div id=\\"a\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              20
            ],
            "s": [
              0,
              0,
              180,
              20
            ],
            "a": [
              80,
              0,
              100,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-local-columns",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:200px;grid-template-rows:auto auto;gap:5px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid;grid-template-columns:40px 160px}#a{grid-area:1/2;height:20px}#b{grid-area:2/1;height:50px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              75
            ],
            "s": [
              0,
              0,
              200,
              75
            ],
            "a": [
              40,
              0,
              160,
              20
            ],
            "b": [
              0,
              25,
              40,
              50
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-distributed-parent-gap",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:200px;grid-template:40px/40px 60px;gap:10px;justify-content:space-between}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              40
            ],
            "s": [
              0,
              0,
              200,
              40
            ],
            "a": [
              0,
              0,
              40,
              40
            ],
            "b": [
              140,
              0,
              60,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-distributed-parent-gap-zero",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:200px;grid-template:40px/40px 60px;gap:10px;justify-content:space-between}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;gap:0}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              40
            ],
            "s": [
              0,
              0,
              200,
              40
            ],
            "a": [
              0,
              0,
              90,
              40
            ],
            "b": [
              90,
              0,
              110,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-nested-gap",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:200px;grid-template:40px/40px 60px 80px;gap:10px}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid;gap:30px}#n{display:grid;grid-column:2/4;grid-template-columns:subgrid}#a,#b{height:20px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"n\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              40
            ],
            "s": [
              0,
              0,
              200,
              40
            ],
            "n": [
              60,
              0,
              140,
              40
            ],
            "a": [
              60,
              0,
              40,
              20
            ],
            "b": [
              130,
              0,
              70,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-span-gap-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto auto auto;gap:10px}#s{display:grid;grid-column:span 3;grid-template-columns:subgrid;gap:30px}#a{grid-column:span 2;width:100px;height:20px}#b{width:40px;height:30px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              170,
              30
            ],
            "s": [
              0,
              0,
              170,
              30
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              130,
              0,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-text-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-rows:auto auto;gap:10px;font-family:'Courier New';font-size:16px;line-height:20px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid}#b{width:160px;height:10px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\">one two three four</div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              172.8281,
              40
            ],
            "s": [
              0,
              0,
              172.8281,
              40
            ],
            "a": [
              0,
              0,
              172.8281,
              20
            ],
            "b": [
              0,
              30,
              160,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-text-wrap",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:100px;grid-template-rows:auto auto;gap:10px;font-family:'Courier New';font-size:16px;line-height:20px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid}#b{height:10px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\">one two three four</div><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              60
            ],
            "s": [
              0,
              0,
              100,
              60
            ],
            "a": [
              0,
              0,
              100,
              40
            ],
            "b": [
              0,
              50,
              100,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-empty-single-track",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{display:inline-grid;grid-template-columns:auto}#s{display:grid;grid-template-columns:subgrid;padding:5px;border:2px solid black}</style></head><body><div id=\\"g\\"><div id=\\"s\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              14,
              14
            ],
            "s": [
              0,
              0,
              14,
              14
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-row-autorepeat-columns",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:150px;grid-template-rows:20px 30px}#s{display:grid;grid-row:span 2;grid-template-rows:subgrid;grid-template-columns:repeat(auto-fill,40px);gap:5px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              150,
              50
            ],
            "s": [
              0,
              0,
              150,
              50
            ],
            "a": [
              0,
              0,
              40,
              17.5
            ],
            "b": [
              0,
              22.5,
              40,
              27.5
            ],
            "c": [
              0,
              22.5,
              40,
              27.5
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-column-autorepeat-rows",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid}#g{width:150px;height:80px;grid-template-columns:60px 90px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid;grid-template-rows:repeat(auto-fill,30px);gap:5px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              150,
              80
            ],
            "s": [
              0,
              0,
              150,
              80
            ],
            "a": [
              0,
              0,
              57.5,
              30
            ],
            "b": [
              62.5,
              0,
              87.5,
              30
            ],
            "c": [
              0,
              35,
              57.5,
              45
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-baseline-export",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:160px;grid-template-columns:80px 80px;grid-template-rows:auto auto;align-items:first baseline}#s{display:grid;grid-column:1;grid-row:span 2;grid-template-rows:subgrid}#a{grid-row:1;height:20px}#b{grid-row:2;height:12px}#c{grid-column:2;grid-row:1;height:12px;padding-top:10px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              34
            ],
            "s": [
              0,
              0,
              80,
              34
            ],
            "a": [
              0,
              0,
              80,
              20
            ],
            "b": [
              0,
              22,
              80,
              12
            ],
            "c": [
              80,
              0,
              80,
              22
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-aspect-ratio-grid-item",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-columns:100px;grid-template-rows:auto}#a{width:60px;aspect-ratio:2/1}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              30
            ],
            "a": [
              0,
              0,
              60,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-span-auto-minimum",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:12px;line-height:14px}div{box-sizing:content-box}#g{display:grid;width:120px;grid-template-columns:minmax(auto,1fr) minmax(auto,1fr);justify-content:start}#a{grid-column:span 2;white-space:nowrap}#b{grid-column:1;width:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\">long-unbreakable-grid-content</div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              120,
              14
            ],
            "a": [
              0,
              0,
              120,
              14
            ],
            "b": [
              0,
              14,
              80,
              0
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-shared-first",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;grid-template-columns:60px 60px 60px}#s,#t{display:grid;grid-row:span 2;grid-template-rows:subgrid;align-items:first baseline}#a{height:20px}#b{height:12px}#c{height:40px}#d{height:25px}#e{grid-column:3;grid-row:1;height:30px;align-self:first baseline}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"t\\"><div id=\\"c\\"></div><div id=\\"d\\"></div></div><div id=\\"e\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              65
            ],
            "s": [
              0,
              0,
              60,
              65
            ],
            "a": [
              0,
              20,
              60,
              20
            ],
            "b": [
              0,
              53,
              60,
              12
            ],
            "t": [
              60,
              0,
              60,
              65
            ],
            "c": [
              60,
              0,
              60,
              40
            ],
            "d": [
              60,
              40,
              60,
              25
            ],
            "e": [
              120,
              10,
              60,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-shared-last",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;grid-template-columns:60px 60px 60px}#s,#t{display:grid;grid-row:span 2;grid-template-rows:subgrid;align-items:last baseline}#a{height:20px}#b{height:12px}#c{height:40px}#d{height:25px}#e{grid-column:3;grid-row:1;height:30px;align-self:last baseline}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div><div id=\\"t\\"><div id=\\"c\\"></div><div id=\\"d\\"></div></div><div id=\\"e\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              65
            ],
            "s": [
              0,
              0,
              60,
              65
            ],
            "a": [
              0,
              20,
              60,
              20
            ],
            "b": [
              0,
              53,
              60,
              12
            ],
            "t": [
              60,
              0,
              60,
              65
            ],
            "c": [
              60,
              0,
              60,
              40
            ],
            "d": [
              60,
              40,
              60,
              25
            ],
            "e": [
              120,
              10,
              60,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-nested-shared-baseline",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;grid-template-columns:80px 100px}#s,#t{display:grid;grid-template-rows:subgrid;grid-row:span 2;align-items:baseline}#a{height:15px}#b{height:25px}#c{grid-column:2;grid-row:1;height:40px;align-self:baseline}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"t\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              180,
              65
            ],
            "s": [
              0,
              0,
              80,
              65
            ],
            "t": [
              0,
              0,
              80,
              65
            ],
            "a": [
              0,
              25,
              80,
              15
            ],
            "b": [
              0,
              40,
              80,
              25
            ],
            "c": [
              80,
              0,
              100,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "baseline-spanning-intrinsic",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-columns:50px 50px;align-items:baseline}#a{grid-row:span 2;height:20px}#b{height:40px}#c{height:10px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              50
            ],
            "a": [
              0,
              20,
              50,
              20
            ],
            "b": [
              50,
              0,
              50,
              40
            ],
            "c": [
              50,
              40,
              50,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-fraction-p25fr",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:.25fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              100,
              5
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-fraction-p5fr",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:.5fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              40
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-fraction-1fr",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:1fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              80
            ],
            "a": [
              0,
              0,
              100,
              80
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-fraction-2fr",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:2fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              100,
              80
            ],
            "a": [
              0,
              0,
              100,
              80
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-small-fractions-span",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:inline-grid;grid-template-columns:.25fr .25fr}#a{grid-column:span 2;overflow:hidden}#b{width:100px;height:10px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              50,
              10
            ],
            "a": [
              0,
              0,
              25,
              10
            ],
            "b": [
              0,
              0,
              100,
              10
            ]
          }
        }
        """,
        """
        {
          "name": "replaced-canvas-grid-normal",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:160px;grid-template-columns:160px}</style></head><body><div id=\\"g\\"><canvas id=\\"a\\" width=\\"80\\" height=\\"40\\"></canvas></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              40
            ],
            "a": [
              0,
              0,
              80,
              40
            ]
          }
        }
        """,
        """
        {
          "name": "replaced-canvas-grid-stretch",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:160px;grid-template:100px/160px}#a{place-self:stretch}</style></head><body><div id=\\"g\\"><canvas id=\\"a\\" width=\\"80\\" height=\\"40\\"></canvas></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              100
            ],
            "a": [
              0,
              0,
              160,
              100
            ]
          }
        }
        """,
        """
        {
          "name": "aspect-height-contribution",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:inline-grid;grid-template-columns:auto auto}#a{height:30px;aspect-ratio:2/1}#b{width:25px;height:15px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              85,
              30
            ],
            "a": [
              0,
              0,
              60,
              30
            ],
            "b": [
              60,
              0,
              25,
              15
            ]
          }
        }
        """,
        """
        {
          "name": "aspect-minmax-bounds",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template-columns:100px 100px}#a{aspect-ratio:2/1;max-width:60px}#b{aspect-ratio:1/1;min-height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              200,
              100
            ],
            "a": [
              0,
              0,
              60,
              30
            ],
            "b": [
              100,
              0,
              100,
              100
            ]
          }
        }
        """,
        """
        {
          "name": "intrinsic-text-min-max-distinct",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}body{font-family:\\"Courier New\\";font-size:10px;line-height:12px}#g{display:inline-grid;grid-template-columns:min-content max-content}#a{grid-column:span 2}#b{grid-column:1}#c{grid-column:2}</style></head><body><div id=\\"g\\"><div id=\\"a\\">aaaa aaaa aaaa</div><div id=\\"b\\">bb</div><div id=\\"c\\">ccc ccc</div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              84.0156,
              24
            ],
            "a": [
              0,
              0,
              84.0156,
              12
            ],
            "b": [
              0,
              12,
              12.0156,
              12
            ],
            "c": [
              12.0156,
              12,
              72,
              12
            ]
          }
        }
        """,
        """
        {
          "name": "subgrid-text-shared-baseline",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:160px;grid-template-columns:80px 80px}#s,#t{display:grid;grid-template-rows:subgrid;grid-row:span 2;align-items:baseline}#a,#b,#c,#d{font-family:monospace;font-size:12px}#a,#d{line-height:24px}#b{line-height:12px}#c{line-height:16px;padding-top:12px}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\">A</div><div id=\\"b\\">B</div></div><div id=\\"t\\"><div id=\\"c\\">C</div><div id=\\"d\\">D</div></div></div></body></html>",
          "expected": {
            "g": [
              0,
              0,
              160,
              56
            ],
            "s": [
              0,
              0,
              80,
              56
            ],
            "a": [
              0,
              8,
              80,
              24
            ],
            "b": [
              0,
              38,
              80,
              12
            ],
            "t": [
              80,
              0,
              80,
              56
            ],
            "c": [
              80,
              0,
              80,
              28
            ],
            "d": [
              80,
              32,
              80,
              24
            ]
          }
        }
        """,
        """
        {
          "name": "transition-rows-0",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:0fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-rows": "1fr"
            },
            "progress": 0
          },
          "expected": {
            "g": [
              0,
              0,
              100,
              0
            ],
            "a": [
              0,
              0,
              100,
              0
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "transition-rows-25",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:0fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-rows": "1fr"
            },
            "progress": 0.25
          },
          "expected": {
            "g": [
              0,
              0,
              100,
              20
            ],
            "a": [
              0,
              0,
              100,
              5
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "transition-rows-50",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:0fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-rows": "1fr"
            },
            "progress": 0.5
          },
          "expected": {
            "g": [
              0,
              0,
              100,
              40
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "transition-rows-75",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:0fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-rows": "1fr"
            },
            "progress": 0.75
          },
          "expected": {
            "g": [
              0,
              0,
              100,
              60
            ],
            "a": [
              0,
              0,
              100,
              45
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "transition-rows-100",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:100px;grid-template-rows:0fr}#a{overflow:hidden}#b{height:80px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-rows": "1fr"
            },
            "progress": 1
          },
          "expected": {
            "g": [
              0,
              0,
              100,
              80
            ],
            "a": [
              0,
              0,
              100,
              80
            ],
            "b": [
              0,
              0,
              100,
              80
            ]
          }
        }
        """,
        """
        {
          "name": "transition-columns-0",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:20px/40px 1fr}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr"
            },
            "progress": 0
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              20
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              40,
              0,
              160,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-columns-25",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:20px/40px 1fr}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr"
            },
            "progress": 0.25
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              20
            ],
            "a": [
              0,
              0,
              55,
              20
            ],
            "b": [
              55,
              0,
              145,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-columns-50",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:20px/40px 1fr}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr"
            },
            "progress": 0.5
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              20
            ],
            "a": [
              0,
              0,
              70,
              20
            ],
            "b": [
              70,
              0,
              130,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-columns-75",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:20px/40px 1fr}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr"
            },
            "progress": 0.75
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              20
            ],
            "a": [
              0,
              0,
              85,
              20
            ],
            "b": [
              85,
              0,
              115,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-columns-100",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:20px/40px 1fr}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr"
            },
            "progress": 1
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              20
            ],
            "a": [
              0,
              0,
              100,
              20
            ],
            "b": [
              100,
              0,
              100,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-calc-0",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:240px;grid-template:20px/20% calc(20px + 10%) 1fr;gap:4px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "calc(10px + 30%) 40% 2fr",
              "gap": "12px"
            },
            "progress": 0
          },
          "expected": {
            "g": [
              0,
              0,
              240,
              20
            ],
            "a": [
              0,
              0,
              48,
              20
            ],
            "b": [
              52,
              0,
              44,
              20
            ],
            "c": [
              100,
              0,
              140,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-calc-25",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:240px;grid-template:20px/20% calc(20px + 10%) 1fr;gap:4px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "calc(10px + 30%) 40% 2fr",
              "gap": "12px"
            },
            "progress": 0.25
          },
          "expected": {
            "g": [
              0,
              0,
              240,
              20
            ],
            "a": [
              0,
              0,
              56.5,
              20
            ],
            "b": [
              62.5,
              0,
              57,
              20
            ],
            "c": [
              125.5,
              0,
              114.5,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-calc-50",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:240px;grid-template:20px/20% calc(20px + 10%) 1fr;gap:4px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "calc(10px + 30%) 40% 2fr",
              "gap": "12px"
            },
            "progress": 0.5
          },
          "expected": {
            "g": [
              0,
              0,
              240,
              20
            ],
            "a": [
              0,
              0,
              65,
              20
            ],
            "b": [
              73,
              0,
              70,
              20
            ],
            "c": [
              151,
              0,
              89,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-calc-75",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:240px;grid-template:20px/20% calc(20px + 10%) 1fr;gap:4px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "calc(10px + 30%) 40% 2fr",
              "gap": "12px"
            },
            "progress": 0.75
          },
          "expected": {
            "g": [
              0,
              0,
              240,
              20
            ],
            "a": [
              0,
              0,
              73.5,
              20
            ],
            "b": [
              83.5,
              0,
              83,
              20
            ],
            "c": [
              176.5,
              0,
              63.5,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-calc-100",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:240px;grid-template:20px/20% calc(20px + 10%) 1fr;gap:4px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "calc(10px + 30%) 40% 2fr",
              "gap": "12px"
            },
            "progress": 1
          },
          "expected": {
            "g": [
              0,
              0,
              240,
              20
            ],
            "a": [
              0,
              0,
              82,
              20
            ],
            "b": [
              94,
              0,
              96,
              20
            ],
            "c": [
              202,
              0,
              38,
              20
            ]
          }
        }
        """,
        """
        {
          "name": "transition-gap-0",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;height:120px;grid-template:20px 30px/40px 50px;gap:4px 8px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "gap": "20px 16px"
            },
            "progress": 0
          },
          "expected": {
            "g": [
              0,
              0,
              180,
              120
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              48,
              0,
              50,
              20
            ],
            "c": [
              0,
              24,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-gap-25",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;height:120px;grid-template:20px 30px/40px 50px;gap:4px 8px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "gap": "20px 16px"
            },
            "progress": 0.25
          },
          "expected": {
            "g": [
              0,
              0,
              180,
              120
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              50,
              0,
              50,
              20
            ],
            "c": [
              0,
              28,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-gap-50",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;height:120px;grid-template:20px 30px/40px 50px;gap:4px 8px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "gap": "20px 16px"
            },
            "progress": 0.5
          },
          "expected": {
            "g": [
              0,
              0,
              180,
              120
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              52,
              0,
              50,
              20
            ],
            "c": [
              0,
              32,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-gap-75",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;height:120px;grid-template:20px 30px/40px 50px;gap:4px 8px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "gap": "20px 16px"
            },
            "progress": 0.75
          },
          "expected": {
            "g": [
              0,
              0,
              180,
              120
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              54,
              0,
              50,
              20
            ],
            "c": [
              0,
              36,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-gap-100",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:180px;height:120px;grid-template:20px 30px/40px 50px;gap:4px 8px}</style></head><body><div id=\\"g\\"><div id=\\"a\\"></div><div id=\\"b\\"></div><div id=\\"c\\"></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "gap": "20px 16px"
            },
            "progress": 1
          },
          "expected": {
            "g": [
              0,
              0,
              180,
              120
            ],
            "a": [
              0,
              0,
              40,
              20
            ],
            "b": [
              56,
              0,
              50,
              20
            ],
            "c": [
              0,
              40,
              40,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-subgrid-0",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:30px/40px 1fr;gap:4px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr",
              "column-gap": "20px"
            },
            "progress": 0
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              40,
              30
            ],
            "b": [
              44,
              0,
              156,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-subgrid-25",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:30px/40px 1fr;gap:4px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr",
              "column-gap": "20px"
            },
            "progress": 0.25
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              55,
              30
            ],
            "b": [
              63,
              0,
              137,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-subgrid-50",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:30px/40px 1fr;gap:4px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr",
              "column-gap": "20px"
            },
            "progress": 0.5
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              70,
              30
            ],
            "b": [
              82,
              0,
              118,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-subgrid-75",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:30px/40px 1fr;gap:4px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr",
              "column-gap": "20px"
            },
            "progress": 0.75
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              85,
              30
            ],
            "b": [
              101,
              0,
              99,
              30
            ]
          }
        }
        """,
        """
        {
          "name": "transition-subgrid-100",
          "source": "<!doctype html><html><head><style>html,body{margin:0;padding:0}body{width:800px;height:600px;font-size:0;line-height:0}div{box-sizing:content-box}#g{display:grid;width:200px;grid-template:30px/40px 1fr;gap:4px}#s{display:grid;grid-column:span 2;grid-template-columns:subgrid}</style></head><body><div id=\\"g\\"><div id=\\"s\\"><div id=\\"a\\"></div><div id=\\"b\\"></div></div></div></body></html>",
          "motion": {
            "target": "g",
            "to": {
              "grid-template-columns": "100px 2fr",
              "column-gap": "20px"
            },
            "progress": 1
          },
          "expected": {
            "g": [
              0,
              0,
              200,
              30
            ],
            "s": [
              0,
              0,
              200,
              30
            ],
            "a": [
              0,
              0,
              100,
              30
            ],
            "b": [
              120,
              0,
              80,
              30
            ]
          }
        }
        """
    };

    private static final String BROWSER_ORACLE = """
        import { captureBrowserSnapshots } from './wpt/tools/browser-adapter.mjs';
        import { mkdir, readFile, writeFile } from 'node:fs/promises';
        import path from 'node:path';

        const fixture = path.resolve('common/src/test/java/com/sighs/apricityui/layout/GridConformanceTest.java');
        const root = path.resolve('build/diagnostics/grid-conformance');
        const testSource = await readFile(fixture, 'utf8');
        const fixtureBlock = /(?<=    private static final String\\[\\] CASES = \\{\\r?\\n)[\\s\\S]*?(?=\\r?\\n    \\};)/;
        if (!fixtureBlock.test(testSource)) throw new Error('Inline Grid cases not found');
        const cases = /* inline-cases */;
        if (!cases.length) throw new Error('No inline Grid cases');
        await mkdir(root, { recursive: true });
        for (const c of cases) {
          let source = c.source;
          if (c.motion) {
            const script = `<script>addEventListener('load',()=>{
              const motion=${JSON.stringify(c.motion)}, element=document.getElementById(motion.target);
              getComputedStyle(element).gridTemplateColumns;
              element.style.transition='all 1000ms linear';
              for(const [property,value] of Object.entries(motion.to)) element.style.setProperty(property,value);
              getComputedStyle(element).gridTemplateColumns;
              for(const animation of element.getAnimations()) {animation.pause();animation.currentTime=motion.progress*1000;}
            },{once:true});</script>`;
            source = source.replace('</body>', `${script}</body>`);
          }
          await writeFile(path.join(root, `${c.name}.html`), source);
        }
        const results = await captureBrowserSnapshots({ corpus: root,
          cases: cases.map(c => ({ id: `${c.name}.html` })), output: path.join(root, 'snapshots'),
          viewport: { width: 800, height: 600 }, workers: 1 });
        let failures = 0;
        for (const c of cases) {
          const result = results.find(r => r.id === `${c.name}.html`);
          if (result?.status !== 'pass') throw new Error(JSON.stringify(result));
          const snapshot = JSON.parse(await readFile(path.join(root, 'snapshots', result.snapshot), 'utf8'));
          const actual = Object.fromEntries(snapshot.nodes.filter(n => n.id).map(n => [n.id, n.rect]));
          if (process.env.AUI_GRID_BROWSER === 'update') c.expected = actual;
          else for (const [id, rect] of Object.entries(c.expected)) {
            if (!actual[id] || rect.some((v, i) => Math.abs(v - actual[id][i]) > .25)) {
              console.error(c.name, id, { expected: rect, actual: actual[id] });
              failures++;
            }
          }
        }
        if (process.env.AUI_GRID_BROWSER === 'update') {
          const updated = cases.map(c => '        \"\"\"\\n'
            + JSON.stringify(c, null, 2).replaceAll('\\\\', '\\\\\\\\').split('\\n').map(line => '        ' + line).join('\\n')
            + '\\n        \"\"\"').join(',\\n');
          await writeFile(fixture, testSource.replace(fixtureBlock, () => updated));
        }
        console.log(`${cases.length} Chromium cases; ${failures} mismatches`);
        process.exitCode = failures ? 1 : 0;
        """;

    @TestFactory
    Stream<DynamicTest> browserOracle() {
        String mode = System.getenv("AUI_GRID_BROWSER");
        if (mode == null || mode.isBlank()) return Stream.empty();
        return Stream.of(DynamicTest.dynamicTest("chromium-" + mode, () -> {
            assertTrue(mode.equals("check") || mode.equals("update"), "AUI_GRID_BROWSER must be check or update");
            Path root = Path.of("").toAbsolutePath();
            while (!Files.isRegularFile(root.resolve("wpt/tools/browser-adapter.mjs"))) {
                root = root.getParent();
                assertNotNull(root, "Cannot locate repository root");
            }
            Path log = root.resolve("build/diagnostics/grid-conformance/browser-oracle.log");
            Files.createDirectories(log.getParent());
            Process process = new ProcessBuilder("node", "--input-type=module")
                    .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                try (var input = process.getOutputStream()) {
                    // Stdin avoids Windows command-line escaping and length limits.
                    input.write(BROWSER_ORACLE.replace("/* inline-cases */", "[" + String.join(",", CASES) + "]")
                            .getBytes(StandardCharsets.UTF_8));
                }
                assertTrue(process.waitFor(3, TimeUnit.MINUTES), "Browser oracle timed out; see " + log);
                assertEquals(0, process.exitValue(), Files.readString(log));
            } finally {
                process.destroyForcibly();
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> matchesBrowser() {
        Element.register("CANVAS", (document, tag) -> new com.sighs.apricityui.element.Canvas(document));
        Gson gson = new Gson();
        return Arrays.stream(CASES).map(raw -> gson.fromJson(raw, Case.class))
                .map(c -> DynamicTest.dynamicTest(c.name, () -> {
            Size.setViewportOverride(800, 600);
            try {
                String path = "test://grid-conformance/" + c.name;
                HTML.putTemple(path, c.source);
                Document document = new Document(path, false);
                try {
                    document.refresh();
                } catch (NoClassDefFoundError unavailableRuntime) {
                    if (document.documentElement == null || !String.valueOf(unavailableRuntime.getMessage())
                            .contains("net/minecraftforge/fml/ModList")) throw unavailableRuntime;
                }
                if (c.motion != null) {
                    Element target = document.querySelector("#" + c.motion.target);
                    Style before = target.getComputedStyle().clone();
                    c.motion.to.forEach((name, value) -> target.getStyle().setProperty(name, value));
                    document.flushPendingStyleUpdates();
                    Style after = target.getComputedStyle().clone();
                    after.transition = "all 1000ms linear " + (-1000 * c.motion.progress) + "ms";
                    Transition.create(target, before, after);
                    StyleFrameCache.begin();
                    document.stepMotionRender();
                }
                assertAll(c.expected.entrySet().stream().map(entry -> () -> {
                    Element.DOMRect rect = document.querySelector("#" + entry.getKey()).getBoundingClientRect();
                    double[] actual = {rect.x, rect.y, rect.width, rect.height};
                    for (int i = 0; i < 4; i++) assertEquals(entry.getValue()[i], actual[i], .25,
                            c.name + " #" + entry.getKey() + " axis=" + i);
                }));
            } finally {
                if (c.motion != null) StyleFrameCache.end();
                Size.clearViewportOverride();
            }
        }));
    }
}
