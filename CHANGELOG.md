# Changelog

## Unreleased

### Added

- Added a loader-independent Rhino host layer for built-in ECMAScript pages,
  including DOM host objects, Proxy/Reflect support, event callbacks,
  microtasks, animation frames, pointer capture, and script lifecycle cleanup.
- Added generic browser-compatible image, SVG, data-URI, audio, CSS transform,
  filter, form-control, text, and layout behavior required by Vue applications.
- Added the pinned mcui-oreui 1.2.2 Vue runtime as the only built-in Ore theme.
  The retained 32 components exclude SkinViewer and remain resource-level
  examples rather than Java-specific branches.
- Added one in-game mcui component overview alongside the existing pure-CSS Ore
  showcase, plus one self-contained customer demo (`mcui-oreui-customer-demo.html`).

### Changed

- Made Rhino a pure-Java runtime dependency for all loader targets while
  keeping KubeJS optional.
- Moved resource decoding and text raster work off the render hot path, with
  bounded main-thread publication and deterministic cache invalidation.
- Aligned generic DOM/CSS/layout semantics with Vue and mcui requirements,
  including intrinsic sizing, percentage/calc lengths, flex/grid behavior,
  stacking, clipping, transitions, generated content, and text line boxes.
- Updated Ore fonts, scoped component CSS, embedded runtime icons/audio, source
  records, license files, and integrity verification for reproducible review.

### Fixed

- Fixed text baseline instability, dynamic input raster updates, password and
  placeholder rendering, slider text/value updates, and fractional-pixel
  clipping.
- Fixed switch/icon edge clipping, dropdown stacking, tooltip/drawer/modal/pop
  hit testing, confirm-button activation, and pointer release/click routing.
- Fixed filter/color-matrix rendering, SVG sizing/rasterization, image cache
  lifecycle, transform hit testing, absolute-position invalidation, and
  nested overlay paint order.
- Removed frame-wide work that caused avoidable UI stalls during text input,
  slider dragging, pop display, and other reactive updates.

### Removed

- Removed legacy Ore editor assets and duplicate theme variants.
- Removed SkinViewer, generated per-component detail pages, documentation-shell
  runtime, optional browser-atlas experiments, physical-input capture tooling,
  verbose trace hooks, and generated validation residue.
- Removed packaged Chromium/WebView assumptions; AUI remains a Java/Rhino
  runtime and does not distribute a browser engine.
