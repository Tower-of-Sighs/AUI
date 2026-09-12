#!/usr/bin/env python3
"""Build the self-contained, customer-facing Ore application demo."""

from __future__ import annotations

import base64
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore"
SOURCE = ROOT / "scripts/ore/customer-showcase"
OUTPUT = ROOT / "mcui-oreui-customer-demo.html"

FONT_MIME = {".otf": "font/otf", ".ttf": "font/ttf"}
FONT_URL = re.compile(r"url\(\s*([\"']?)(fonts/[^\"')]+)\1\s*\)")
COMPONENT_CALL = re.compile(r'mc\(\s*"(mc-[a-z0-9-]+)"')
EXPECTED_COMPONENTS = {
    "mc-icon",
    "mc-button",
    "mc-card",
    "mc-panel",
    "mc-tooltip",
    "mc-progress",
    "mc-spinner",
    "mc-checkbox",
    "mc-radio",
    "mc-radio-group",
    "mc-form-field",
    "mc-switch",
    "mc-dropdown",
    "mc-text-field",
    "mc-slider",
    "mc-layout",
    "mc-header",
    "mc-appbar",
    "mc-appbar-button",
    "mc-appbar-icon",
    "mc-tabs",
    "mc-button-tabs",
    "mc-list",
    "mc-list-item",
    "mc-scroll-view",
    "mc-modal",
    "mc-confirm",
    "mc-drawer",
    "mc-loading-mask",
    "mc-pop-host",
    "mc-tcode",
    "mc-formatted-text",
}


def read_text(path: Path) -> str:
    with path.open("r", encoding="utf-8", newline=None) as handle:
        return handle.read()


def inline_fonts(css: str) -> str:
    references = {relative for _, relative in FONT_URL.findall(css)}
    expected = {
        "fonts/minecraft-ten.otf",
        "fonts/minecraft-seven.otf",
        "fonts/minecraft-five.otf",
        "fonts/minecraft-five-bold.otf",
        "fonts/minecraft-regular.otf",
        "fonts/minecraft-ten.ttf",
        "fonts/noto-sans-bold.ttf",
        "fonts/noto-sans-bold-italic.ttf",
        "fonts/noto-sans-italic.ttf",
    }
    assert references == expected, f"unexpected Ore font closure: {sorted(references)}"

    def replace(match: re.Match[str]) -> str:
        relative = match.group(2)
        font_path = THEME / relative
        assert font_path.is_file(), f"missing font asset: {font_path}"
        encoded = base64.b64encode(font_path.read_bytes()).decode("ascii")
        return f'url("data:{FONT_MIME[font_path.suffix.lower()]};base64,{encoded}")'

    return FONT_URL.sub(replace, css)


def escape_element_terminator(source: str, element: str) -> str:
    return re.sub(rf"</{element}", rf"<\/{element}", source, flags=re.IGNORECASE)


def build() -> str:
    components_css = read_text(THEME / "ore-components.css").strip()
    ore_css = read_text(THEME / "ore.css")
    mcui_css = read_text(THEME / "mcui.css")
    mcui_css = re.sub(r"(?m)^\s*@import\s+[^;]+;\s*", "", mcui_css, count=1).strip()
    demo_css = read_text(SOURCE / "demo.css").strip()
    css = inline_fonts("\n\n".join((components_css, ore_css, mcui_css, demo_css)))

    vue_js = read_text(THEME / "runtime/vue.aui.js").strip()
    mcui_js = read_text(THEME / "runtime/mcui-oreui.aui.js").strip()
    demo_js = read_text(SOURCE / "demo.aui.js").strip()
    used = set(COMPONENT_CALL.findall(demo_js))
    assert used == EXPECTED_COMPONENTS, (
        f"customer demo component mismatch; missing={sorted(EXPECTED_COMPONENTS - used)}, "
        f"extra={sorted(used - EXPECTED_COMPONENTS)}"
    )

    return "\n".join(
        (
            "<!doctype html>",
            '<html lang="zh-CN">',
            "<head>",
            '  <meta charset="utf-8">',
            '  <meta name="viewport" content="width=device-width, initial-scale=1">',
            '  <meta name="description" content="ApricityUI Ore 世界工作台客户单文件 Demo">',
            '  <link rel="icon" href="data:,">',
            "  <title>ApricityUI | Ore 世界工作台</title>",
            "  <style>",
            escape_element_terminator(css, "style"),
            "  </style>",
            "</head>",
            '<body class="ore-theme customer-demo" data-customer-showcase="true" data-customer-demo="true" data-mcui-runtime-components="32">',
            '  <div id="customer-demo-root"></div>',
            "  <script>",
            escape_element_terminator(vue_js, "script"),
            "  </script>",
            "  <script>",
            escape_element_terminator(mcui_js, "script"),
            "  </script>",
            "  <script>",
            "/* customer-demo-app:start */",
            escape_element_terminator(demo_js, "script"),
            "/* customer-demo-app:end */",
            "  </script>",
            "</body>",
            "</html>",
            "",
        )
    )


def assert_output(document: str) -> None:
    assert not re.search(r"<link\b[^>]*\bstylesheet\b", document, re.IGNORECASE)
    assert not re.search(r"<script\b[^>]*\bsrc\s*=", document, re.IGNORECASE)
    assert not re.search(r"href\s*=\s*[\"']details/", document, re.IGNORECASE)
    assert not re.search(
        r"<(?:script|img|audio|source|video)\b[^>]*\bsrc\s*=\s*[\"'](?:[^\"']*[\\/])",
        document,
        re.IGNORECASE,
    )
    style_contents = "\n".join(
        re.findall(r"<style>(.*?)</style>", document, re.DOTALL | re.IGNORECASE)
    )
    for _, value in re.findall(
        r"url\(\s*([\"']?)([^\"')]+)\1\s*\)", style_contents, re.IGNORECASE
    ):
        assert value.lower().startswith("data:"), f"non-inline CSS url: {value}"

    assert 'data-customer-showcase="true"' in document
    assert 'data-customer-demo="true"' in document
    assert 'data-mcui-runtime-components="32"' in document
    assert 'id="customer-demo-root"' in document
    assert "/* customer-demo-app:start */" in document
    assert "/* customer-demo-app:end */" in document
    assert "Vue.createApp" in document and "app.use(Mc.default)" in document
    assert "data-customer-demo-ready" in document
    assert "data-customer-demo-mounted-components" in document
    assert "renderedComponents[name] = true" in document
    assert "data-component-anchor" not in document
    assert "showcase.aui.js" not in document
    assert "docs-shell.aui.js" not in document
    assert "组件交互展示" not in document
    assert "Components / 25" not in document
    assert "details/" not in document
    assert "fetch(" not in document
    assert "XMLHttpRequest" not in document
    assert not re.search(r"\bimport\s*(?:\(|[\"'])", document)
    assert document.count("data:font/otf;base64,") == 5
    assert document.count("data:font/ttf;base64,") == 4
    assert "data:audio/ogg;base64," in document
    assert "data:image/png;base64," in document or "data:image/svg+xml" in document

    app_region = document.split("/* customer-demo-app:start */", 1)[1].split(
        "/* customer-demo-app:end */", 1
    )[0]
    assert set(COMPONENT_CALL.findall(app_region)) == EXPECTED_COMPONENTS


def main() -> None:
    document = build()
    assert_output(document)
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(document)
    assert_output(read_text(OUTPUT))
    print(f"wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
