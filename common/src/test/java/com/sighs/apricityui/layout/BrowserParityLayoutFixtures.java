package com.sighs.apricityui.layout;

final class BrowserParityLayoutFixtures {
    private BrowserParityLayoutFixtures() {
    }

    static String source(String name) {
        return switch (name) {
            case "flex-row-wrap-order" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .card { display: flex; flex-direction: row; flex-wrap: wrap; gap: 5px 8px;
                            width: 340px; padding: 10px 12px; border: 3px solid #444; }
                    .head { order: 1; flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 8px; }
                    .head .title { flex: 1 1 auto; min-width: 0; height: 18px; }
                    .badge { flex: 0 0 auto; width: 20px; height: 18px; }
                    .clock { order: 2; flex: 0 0 auto; width: 70px; height: 18px; }
                    .meta { order: 3; flex: 1 0 100%; margin: 0; height: 14px; }
                    </style></head><body>
                    <article class="card" id="card">
                      <div class="head" id="head"><span class="badge"></span><div class="title"></div></div>
                      <p class="meta" id="meta"></p>
                      <span class="clock" id="clock"></span>
                    </article>
                    </body></html>
                    """;
            case "flex-basis-percent" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .outer { width: 700px; }
                    .box { display: flex; flex-wrap: wrap; width: 300px; }
                    .a { width: 40px; height: 16px; }
                    .b { flex: 1 0 100%; height: 16px; }
                    .c { width: 40px; height: 16px; }
                    </style></head><body>
                    <div class="outer"><div class="box">
                      <div class="a" id="a"></div><div class="b" id="b"></div><div class="c" id="c"></div>
                    </div></div>
                    </body></html>
                    """;
            case "pseudo-tree-lines" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .tree-chart { display: flex; padding: 8px 0; }
                    .tree-branch { margin: 0; padding: 0; list-style: none; display: flex; }
                    .tree-node { position: relative; display: flex; flex-direction: column; align-items: center;
                                 padding: 24px 0 0; }
                    .tree-node::before { content: ""; position: absolute; top: 0; right: 50%; width: 50%; height: 4px; background: #4b4d51; }
                    .tree-node::after { content: ""; position: absolute; top: 0; left: 50%; width: 50%; height: 24px;
                                        border-top: 4px solid #4b4d51; border-left: 4px solid #4b4d51; }
                    .tree-node:first-child::before { display: none; }
                    .tree-node:last-child::after { border-top: 0; }
                    .tree-node > .tree-branch { margin-top: 24px; }
                    .tree-node > .tree-branch::before { content: ""; position: absolute; left: 50%; top: -24px; width: 4px; height: 24px; background: #4b4d51; }
                    .tree-chart > .tree-branch > .tree-node { padding-top: 0; }
                    .tree-chart > .tree-branch > .tree-node::before,
                    .tree-chart > .tree-branch > .tree-node::after { display: none; }
                    .leaf { width: 100px; height: 24px; background: #333; }
                    </style></head><body>
                    <div class="tree-chart"><ul class="tree-branch">
                      <li class="tree-node"><div class="leaf"></div>
                        <ul class="tree-branch">
                          <li class="tree-node"><div class="leaf"></div></li>
                          <li class="tree-node"><div class="leaf"></div></li>
                          <li class="tree-node"><div class="leaf"></div></li>
                        </ul>
                      </li>
                    </ul></div>
                    </body></html>
                    """;
            case "transform-scale-centering" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .canvas { position: relative; width: 400px; height: 200px; overflow: hidden; border: 3px solid #444; }
                    .world { position: absolute; left: 0; top: 0; transform-origin: 0 0; width: 800px; height: 400px; }
                    .fill { width: 100%; height: 100%; background: #333; }
                    </style></head><body>
                    <div class="canvas" id="canvas">
                      <div class="world" id="world" style="transform: translate(20px,20px) scale(0.5)"><div class="fill"></div></div>
                    </div>
                    </body></html>
                    """;
            case "box-metrics" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .pad { padding: 10px; border: 3px solid #444; width: 200px; height: 100px; }
                    .inner { width: 50px; height: 50px; }
                    </style></head><body>
                    <div class="pad" id="pad"><div class="inner" id="inner"></div></div>
                    </body></html>
                    """;
            case "nest-flex-auto-width-row" -> """
                    <html><head><style>
                    * { box-sizing: border-box; }
                    body { margin: 0; }
                    .outer { display: flex; gap: 8px; width: 300px; }
                    .row { display: flex; gap: 8px; flex: 1 1 auto; min-width: 0; }
                    .b { flex: 0 0 auto; width: 20px; height: 18px; }
                    .c { flex: 1 1 auto; min-width: 0; height: 18px; }
                    .d { flex: 0 0 auto; width: 60px; height: 18px; }
                    </style></head><body>
                    <div class="outer">
                      <div class="row" id="row"><span class="b" id="b"></span><div class="c" id="c"></div></div>
                      <div class="d" id="d"></div>
                    </div>
                    </body></html>
                    """;
            default -> throw new IllegalArgumentException("unknown browser parity fixture: " + name);
        };
    }
}
