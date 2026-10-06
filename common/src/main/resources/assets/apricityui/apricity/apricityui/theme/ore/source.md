# Ore theme source

`ore.css` is ApricityUI's standalone, single-class Ore theme, adapted from
Minecraft-CSS and distributed under MPL-2.0 (`license.txt`). Its local fonts
and seven-page `example.html` remain part of the theme. Theme resources are
listed in `provenance.sha256` and checked by
`scripts/ore/refresh-integrity.ps1`.

The separate mcui-oreui 2.0 Vue component library is under
`/apricityui/runtime/mcui/`; see its `source.md`, MIT license and manifest.
`mcui-example.html` loads that library's 68-component upstream visual Gallery
without changing the Ore theme's `.ore-theme` contract.
