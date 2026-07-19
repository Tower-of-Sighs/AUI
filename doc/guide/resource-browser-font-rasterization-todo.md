# Resource Browser Font Rasterization TODO

Purpose: extracted font rasterization, text antialiasing, font metrics, and glyph-rendering work from `doc/guide/resource-browser-visual-todo-2026-07-15.md` so the main resource-browser visual TODO can continue on non-font browser parity work.

Status: extracted from the main goal file on 2026-07-15. Continue font work here only when the user explicitly reopens font rendering.

Related residuals:

- `doc/guide/resource-browser-font-short13-baseline-residual.md`

Main-file extraction update, 2026-07-15:

- The main resource-browser visual TODO now excludes font, glyph, text-metric, line-box, and rasterization work from automated goal runs.
- V5-04 in the main TODO is retained only as foreground-color and border-color evidence. Any text/raster statistics from that investigation belong here.
- Extracted full-resource text/raster statistic artifact:
  - Command previously recorded in the main TODO: `.\scripts\resource_browser_full_gray_text_stats.ps1 -AuiImage 'run/screenshots/aui/2026-07-15_13.21.08.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-last.log'`.
  - Result: `run/resource-browser-gray-text-border-resource-samples.log`.
- Extracted active-detail row/tag residual:
  - Row/tag height and later y differences were classified as text line-box/inline sizing deltas.
  - Recorded examples: row height `38.4` vs Chromium `38`, tag height `22.5` vs Chromium `23`, and `NBT width=42.625` vs Chromium `43.203125`.
  - These residuals must not block the main visual TODO unless the user explicitly reopens font/text-metric work.

### RBV-V5-05: Browser Font Rasterization and Antialiasing

Status: [~]

Dependencies: `RBV-V5-04`

Browser oracle:

- Type: pixel-sample + documented-rule.
- Standard source: Chromium raster output for matched text crops, with the same viewport, state, font family fallback, font size, letter spacing, background color, and device scale.
- Static policy: use static `devtools/resource.html` state with no interaction/prompt env. Disable or avoid animated regions.
- Tolerance:
  - do not compare a single glyph edge pixel;
  - use crop coverage, average ink RGB, darkness, and visible bounding extents;
  - tolerance must be declared before accepting a fix and should account only for device-pixel rounding or known renderer differences.

Problem:

- V5-04 proved CSS gray values and border colors match Chromium, but text rasterization does not. AUI text crops have lower coverage and darker ink than Chromium on both the minimal fixture and full `devtools/resource.html`.

Expected checks:

- Identify whether the difference comes from:
  - font family fallback / font file mismatch;
  - font size or pixel scaling;
  - glyph rasterizer antialiasing mode;
  - subpixel positioning;
  - letter-spacing application;
  - screenshot scale conversion.
- Compare at least:
  - `#contentCount` (`5 ITEMS`);
  - first normal `.file-meta` (`256 KB` or current browser value);
  - `.detail-empty` (`SELECT FILE TO VIEW DETAILS`).
- Preserve the already-completed V3 text geometry and V5-04 CSS color/border behavior.

Acceptance:

- Either AUI text crop coverage/ink statistics move within a documented browser-parity tolerance for the matched crops, or the exact unsupported raster primitive is documented and this task is marked `[!]`.
- The full `devtools/resource.html` static baseline remains in the same state for browser/AUI comparison.
- No `resource.html` CSS workaround is used to hide framework raster differences.

Current progress:

- Minimal fixture stats: `run/resource-browser-gray-text-border-pixel-samples.log`.
- Full resource stats: `run/resource-browser-gray-text-border-resource-samples.log`.
- DPR-matched full resource browser screenshot: `run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png`, dimensions `2560x1475`, CSS viewport `1463x843`, scale `1.749829,1.749703`.
- DPR-matched AUI screenshot: `run/screenshots/aui/2026-07-15_13.21.08.png`, dimensions `2560x1476`, CSS viewport `1463x843`, scale `1.749829,1.75089`.
- DPR-matched stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_13.21.08.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-dsf-matched.log'`.
- DPR-matched stats result: `run/resource-browser-gray-text-border-resource-samples-dsf-matched.log`.
  - `sidebarBorder`, `detailBorder`, and `cardBorder` still match exactly: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.164828`, avg ink `172.15,172.15,172.15`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.055979`, avg ink `176.05,176.05,176.05`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.114818`, avg ink `168.88,168.88,168.88`.
- Initial code inspection:
  - `src/main/resources/assets/apricityui/apricity/devtools/resource.html` declares `<meta name="aui-font-mode" content="web">`, so CSS `font-size` maps to the same nominal CSS px size in AUI.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` renders custom-font text into a `48px` base texture and then draws it scaled down with linear filtering. Chromium rasterizes text at the target CSS pixel size; this is a likely source of lower coverage/darker ink.
  - `src/main/java/com/sighs/apricityui/resource/Font.java` maps missing `Chakra Petch` to generic `sans-serif`; on this machine Chakra Petch is not installed. AUI resolves generic sans through installed AWT fonts such as `Sans Serif Collection`/Arial, while Chromium's exact fallback must still be captured with browser metrics or inferred through canvas width tests.
- Minimal fixture added: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster.html`.
  - Samples cover explicit `Arial`, generic `sans-serif`, and `"Chakra Petch", sans-serif`.
  - Samples cover `13px` with and without letter spacing, `10px` with `0.5px` letter spacing, and `12px` long detail text with `1px` letter spacing.
- Browser runner added: `scripts/resource_browser_font_raster_metrics.js`.
  - Browser command: `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Browser result note: direct headless Chromium reported `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`; the fixture uses absolute sample coordinates, and the stats script uses `devicePixelRatio` for browser CSS-to-physical conversion.
- AUI fixture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-last.log`.
  - AUI state: fixture loaded with viewport `1463x843`, `samples count=7`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_13.31.58.png`.
- Stats script added: `scripts/resource_browser_font_raster_stats.ps1`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -AuiImage 'run/screenshots/aui/2026-07-15_13.31.58.png'`.
  - Stats result: `run/resource-browser-font-raster-samples.log`.
- Minimal fixture result:
  - Explicit `Arial` still differs, so missing `Chakra Petch` is not the primary cause.
  - `arial13`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.010968`, avg ink `169.35,169.35,169.35`.
  - `arial13Plain`: browser coverage `0.013938`, avg ink `184.5,184.67,184.99`; AUI coverage `0.010212`, avg ink `165.16,165.16,165.16`.
  - `sans13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.010101`, avg ink `177.66,177.66,177.66`.
  - `chakra13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.009641`, avg ink `172.57,172.57,172.57`.
  - `arial10`: browser coverage `0.006640`, avg ink `189.79,190.19,189.96`; AUI coverage `0.005382`, avg ink `168.16,168.16,168.16`.
  - `arial12`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.030488`, avg ink `165.78,165.78,165.78`.
  - `chakra12`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.032129`, avg ink `177.41,177.41,177.41`.
- Updated interpretation:
  - The mismatch persists with explicit `Arial`, with normal and non-normal letter spacing, and across 10/12/13px samples.
  - The dominant pattern remains lower AUI coverage and darker AUI ink, pointing away from page CSS color and font-family fallback and toward the renderer path.
  - `ApricityViewport` browser mode maps CSS px through GLFW content scale, and `ApricityScreen` applies `viewport.renderScale()` to the whole document before `FontDrawer` draws text.
  - `FontDrawer` currently bakes custom-font text at `Font.getBaseFontSize()` (`48px`) and scales the texture down to CSS size with linear filtering. A Chromium-like path should be tested by baking at the target physical raster size derived from CSS font size and viewport/content scale, then drawing at the same CSS box.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the font-raster harness logging path; `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Guarded renderer experiment added:
  - `src/main/java/com/sighs/apricityui/render/Base.java` now pushes the active document pixel scale to `FontDrawer` during document rendering.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` supports an opt-in target-physical raster path.
  - Enable with environment variable `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1` or JVM property `apricityui.fontRaster.targetPhysical=true`.
  - Default behavior remains the old `48px` base texture path while the task is `[~]`.
- Default-path recheck:
  - AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-default-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_13.39.33.png`.
  - Stats: `run/resource-browser-font-raster-samples-default-after-change.log`.
  - Result: default path matches the previous baseline, preserving the old behavior when the experiment is disabled.
- First property-enabled run note:
  - `.\gradlew.bat "-Dapricityui.fontRaster.targetPhysical=true" runClient ...` completed, but the screenshot matched default behavior. In this Gradle run path the JVM property did not reach the client process reliably.
  - The environment variable path is the reliable automation switch for now.
- Target-physical fixture experiment:
  - AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-physical-env-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_13.42.57.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -AuiImage 'run/screenshots/aui/2026-07-15_13.42.57.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-physical-env-last.log' -OutLog 'run/resource-browser-font-raster-samples-physical-env-experiment.log'`.
  - Stats result: `run/resource-browser-font-raster-samples-physical-env-experiment.log`.
  - Improvements:
    - `sans13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.013382`, avg ink `190.61,190.61,190.61`.
    - `chakra13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.013235`, avg ink `191.93,191.93,191.93`.
    - `chakra12`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.040479`, avg ink `186.86,186.86,186.86`.
  - Remaining fixture mismatch:
    - `arial13` and `arial13Plain` improved but remain darker/lower coverage than Chromium.
    - `arial10` overshot coverage (`0.009401` vs browser `0.006640`).
    - `arial12` overshot coverage (`0.042157` vs browser `0.040064`) and remains darker.
- Target-physical full resource experiment:
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-env-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_13.44.40.png`.
  - Stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_13.44.40.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-env-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-env.log'`.
  - Stats result: `run/resource-browser-gray-text-border-resource-samples-physical-env.log`.
  - Full resource result:
    - Borders remain exact: browser/AUI `224,224,224,255`.
    - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.212010`, avg ink `187.89,187.89,187.89`.
    - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.074257`, avg ink `195.17,195.17,195.17`.
    - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.154809`, avg ink `185.73,185.73,185.73`.
- Updated interpretation after guarded experiment:
  - Target-physical rasterization is the correct direction for browser parity and dramatically improves the real `devtools/resource.html` text crops.
  - It is not sufficient as a final default yet because small explicit-Arial samples overshoot/undershoot inconsistently and `detailEmpty` remains too dark.
  - The next likely primitive is text antialiasing/subpixel AA and renderer hint parity, not CSS color, layout geometry, font-size mapping, or missing Chakra fallback.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the guarded experiment; `git diff --check` must be re-run after any further renderer edits.
- Previous next action: add a second guarded experiment for AWT text antialiasing mode, comparing `VALUE_TEXT_ANTIALIAS_LCD_HRGB` against current `VALUE_TEXT_ANTIALIAS_ON`, while keeping target-physical raster enabled. This has now been run; see the LCD AA experiment evidence below.

Automation-ready next run:

```powershell
# Browser oracle for the font raster fixture.
node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-last.log

# AUI fixture with target-physical raster plus the next AA-mode experiment.
$env:APRICITYUI_TEST_DOC_PATH='tests/resource-browser-font-raster.html'
$env:APRICITYUI_FONT_RASTER_TARGET_PHYSICAL='1'
$env:APRICITYUI_FONT_RASTER_AA_MODE='lcd-hrgb'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-physical-lcd-last.log

# Replace <latest-aui-screenshot> with the newest file under run/screenshots/aui.
.\scripts\resource_browser_font_raster_stats.ps1 -AuiImage 'run/screenshots/aui/<latest-aui-screenshot>.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-physical-lcd-last.log' -OutLog 'run/resource-browser-font-raster-samples-physical-lcd.log'

# Full devtools/resource.html confirmation with the same renderer flags.
$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
$env:APRICITYUI_FONT_RASTER_TARGET_PHYSICAL='1'
$env:APRICITYUI_FONT_RASTER_AA_MODE='lcd-hrgb'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-lcd-last.log

# Replace <latest-aui-screenshot> with the newest file under run/screenshots/aui.
.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/<latest-aui-screenshot>.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-lcd-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-lcd.log'

.\gradlew.bat compileJava --console plain --no-daemon --offline
git diff --check
```

Current `RBV-V5-05` acceptance discipline:

- Do not make target-physical raster or LCD AA default until both the minimal fixture and full `devtools/resource.html` improve against the Chromium metrics without regressing another sample.
- If LCD AA improves full-page screenshots but regresses the minimal fixture, keep `RBV-V5-05` `[~]` and revise the renderer experiment instead of accepting a page-specific win.
- If a platform limitation prevents Chromium-like font rasterization, document the exact unsupported primitive and mark this task `[!]`; do not silently widen tolerance.

LCD AA guarded experiment evidence:

- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now supports guarded `APRICITYUI_FONT_RASTER_AA_MODE` / `apricityui.fontRaster.aaMode` values. Default remains `on`, matching the previous `RenderingHints.VALUE_TEXT_ANTIALIAS_ON` path. `lcd-hrgb` maps to `RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB`; `off` and `gasp` are also accepted for future controlled experiments.
  - `FontDrawer` includes the resolved AA mode in the font texture cache key so different AA experiments cannot reuse stale textures.
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster.html` now calls `collect()` synchronously before the double-`requestAnimationFrame` refresh. This stabilizes Chromium `--dump-dom`; before this change the runner sometimes exited with `Browser font raster metrics payload not found`.
- Browser command: `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-last.log`.
- Browser result: `run/resource-browser-font-raster-browser-last.log`; screenshot `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`; viewport `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- AUI fixture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-physical-lcd-last.log`.
- AUI fixture screenshot: `run/screenshots/aui/2026-07-15_13.53.31.png`.
- AUI fixture stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -AuiImage 'run/screenshots/aui/2026-07-15_13.53.31.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-physical-lcd-last.log' -OutLog 'run/resource-browser-font-raster-samples-physical-lcd.log'`.
- AUI fixture stats result: `run/resource-browser-font-raster-samples-physical-lcd.log`.
  - `arial13`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011650`, avg ink `174.28,174.28,174.28`.
  - `arial13Plain`: browser coverage `0.013938`, avg ink `184.5,184.67,184.99`; AUI coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `sans13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.013382`, avg ink `190.61,190.61,190.61`.
  - `chakra13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.013235`, avg ink `191.93,191.93,191.93`.
  - `arial10`: browser coverage `0.006640`, avg ink `189.79,190.19,189.96`; AUI coverage `0.009401`, avg ink `182.56,182.56,182.56`.
  - `arial12`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `chakra12`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.040479`, avg ink `186.86,186.86,186.86`.
- Full resource AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-lcd-last.log`.
- Full resource AUI screenshot: `run/screenshots/aui/2026-07-15_13.54.38.png`.
- Full resource stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_13.54.38.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-lcd-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-lcd.log'`.
- Full resource stats result: `run/resource-browser-gray-text-border-resource-samples-physical-lcd.log`.
  - Borders remain exact: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.212010`, avg ink `187.89,187.89,187.89`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.082254`, avg ink `197.36,197.36,197.36`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.154809`, avg ink `185.73,185.73,185.73`.
- Interpretation:
  - `lcd-hrgb` does not solve `RBV-V5-05`. The minimal fixture still has explicit-Arial samples with lower coverage/darker ink and small-size samples that overshoot coverage.
  - Compared with the previous target-physical run, the LCD fixture result is effectively unchanged for the important mismatches. This is consistent with AWT LCD text AA being ineffective or suppressed when drawing into the current transparent `TYPE_INT_ARGB` texture path.
  - Full resource `contentCount` remains close to Chromium, but `detailEmpty` remains too dark and `fileMeta` overshoots coverage in this run. LCD AA should remain an opt-in diagnostic switch, not a default.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `FontDrawer.java` AA switch.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Investigate why AWT LCD AA is ineffective in the transparent texture path. The next browser-standard experiment should compare the current transparent `TYPE_INT_ARGB` bake against an opaque-background text bake or another Chromium-like glyph coverage path, while preserving exact browser text color and background compositing. Do not make this page-specific; use `resource-browser-font-raster.html` first, then full `devtools/resource.html`.

Opaque-white text composite experiment evidence:

- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now supports guarded `APRICITYUI_FONT_RASTER_COMPOSITE` / `apricityui.fontRaster.composite` values. Default remains `transparent`. `opaque-white` fills the font texture with opaque white before drawing glyphs, then caches that mode separately.
  - This is a diagnostic experiment only. It is not browser-correct for arbitrary pages because the real browser composites glyph coverage against the actual backdrop, not a constant white rectangle.
- AUI fixture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE=opaque-white`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-physical-lcd-opaque-white-last.log`.
- AUI fixture screenshot: `run/screenshots/aui/2026-07-15_13.58.49.png`.
- AUI fixture stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -AuiImage 'run/screenshots/aui/2026-07-15_13.58.49.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-physical-lcd-opaque-white-last.log' -OutLog 'run/resource-browser-font-raster-samples-physical-lcd-opaque-white.log'`.
- AUI fixture stats result: `run/resource-browser-font-raster-samples-physical-lcd-opaque-white.log`.
  - `arial13`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012571`, avg ink `184.29,184.29,184.29`.
  - `arial13Plain`: browser coverage `0.013938`, avg ink `184.5,184.67,184.99`; AUI coverage `0.012682`, avg ink `184.88,184.88,184.88`.
  - `sans13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.015650`, avg ink `205.75,205.75,205.75`.
  - `chakra13`: browser coverage `0.012489`, avg ink `189.34,189.21,190.18`; AUI coverage `0.015244`, avg ink `204.47,204.47,204.47`.
  - `arial10`: browser coverage `0.006640`, avg ink `189.79,190.19,189.96`; AUI coverage `0.010267`, avg ink `198.68,198.68,198.68`.
  - `arial12`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.046359`, avg ink `195.55,195.55,195.55`.
  - `chakra12`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.044498`, avg ink `202.66,202.66,202.66`.
- Full resource AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE=opaque-white`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-lcd-opaque-white-last.log`.
- Full resource AUI screenshot: `run/screenshots/aui/2026-07-15_14.00.02.png`.
- Full resource stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_14.00.02.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-lcd-opaque-white-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-lcd-opaque-white.log'`.
- Full resource stats result: `run/resource-browser-gray-text-border-resource-samples-physical-lcd-opaque-white.log`.
  - Borders remain exact: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.969669`, avg ink `241.82,241.82,241.82`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.099581`, avg ink `212.33,212.33,212.33`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.176904`, avg ink `202.22,202.22,202.22`.
- Interpretation:
  - Opaque-white precomposition proves that the transparent alpha texture path is a real part of the ink/coverage behavior: explicit `Arial` average ink becomes much closer to Chromium.
  - It is not a valid browser-parity fix. It over-brightens generic/fallback samples, increases coverage on multiple samples, and creates a visible white texture rectangle on non-white backdrops. The full `contentCount` crop on `#fafafa` is the clearest failure: coverage jumps from browser `0.208946` to AUI `0.969669`.
  - The browser-like primitive is not "always bake text on white"; it is glyph coverage composited against the actual backdrop. A correct implementation would need background-aware precomposition for solid backgrounds or a different shader/texture strategy that preserves browser-equivalent coverage without drawing rectangular background pixels.
  - Code inspection after the run: many `FontDrawer.drawFont` call sites only pass `Text` and `Position`, not the owning `Element`; `Element.drawStaticText`, direct flex text, input/select/textarea, and selection paths would need careful handling before a background-aware mode can be generalized.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding `APRICITYUI_FONT_RASTER_COMPOSITE`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not make `opaque-white` default.
  - Build a narrower browser-standard experiment for background-aware text precomposition on known solid backgrounds. Start with `resource-browser-font-raster.html` by adding paired samples on `#ffffff` and `#fafafa`, then add AUI metrics that log each sample's effective text backdrop. Only after the fixture proves the rule should the framework path be extended beyond directly owned Element text.

Background-pair fixture evidence:

- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster.html` now includes explicit paired text samples on `#ffffff` and `#fafafa`: `arial13White`/`arial13Fafafa`, `arial12White`/`arial12Fafafa`, and `chakra12White`/`chakra12Fafafa`.
  - The paired sample CSS uses individual selectors rather than grouped comma selectors. A first run showed AUI did not apply the grouped selector to the new `White` samples, making them `16px` with normal letter spacing; the fixture was corrected so this raster task does not mix in selector support.
  - `scripts/resource_browser_font_raster_stats.ps1` now parses per-sample `backgroundColor` from both Chromium computed style and AUI metrics logs. Text crop stats are measured against each sample's own background, not always against white.
- Browser command: `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-background-pairs-last.log`.
- Browser result: `run/resource-browser-font-raster-browser-background-pairs-last.log`; screenshot `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`; viewport `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- AUI transparent command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-background-pairs-transparent-last.log`.
- AUI transparent screenshot: `run/screenshots/aui/2026-07-15_14.10.22.png`.
- AUI transparent stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_14.10.22.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-transparent-last.log' -OutLog 'run/resource-browser-font-raster-samples-background-pairs-transparent.log'`.
- AUI transparent stats result: `run/resource-browser-font-raster-samples-background-pairs-transparent.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.031650`, avg ink `224.83,224.83,224.83`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.057014`, avg ink `201.59,201.59,201.59`.
  - `chakra12White`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.040737`, avg ink `186.61,186.61,186.61`.
  - `chakra12Fafafa`: browser coverage `0.061224`, avg ink `212.8,212.66,212.91`; AUI coverage `0.060479`, avg ink `207.88,207.88,207.88`.
- AUI opaque-white command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE=opaque-white`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-background-pairs-opaque-white-last.log`.
- AUI opaque-white screenshot: `run/screenshots/aui/2026-07-15_14.12.38.png`.
- AUI opaque-white stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_14.12.38.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-opaque-white-last.log' -OutLog 'run/resource-browser-font-raster-samples-background-pairs-opaque-white.log'`.
- AUI opaque-white stats result: `run/resource-browser-font-raster-samples-background-pairs-opaque-white.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012682`, avg ink `184.88,184.88,184.88`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.071484`, avg ink `242.55,242.55,242.55`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.046359`, avg ink `195.55,195.55,195.55`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.193917`, avg ink `240.82,240.82,240.82`.
  - `chakra12White`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.044737`, avg ink `202.97,202.97,202.97`.
  - `chakra12Fafafa`: browser coverage `0.061224`, avg ink `212.8,212.66,212.91`; AUI coverage `0.324829`, avg ink `247.87,247.87,247.87`.
- Interpretation:
  - Per-background measurement confirms that Chromium's `#fafafa` samples naturally have higher apparent coverage/lighter average ink than white samples because anti-aliased edge pixels are compared against a closer background.
  - The current transparent AUI path is much closer to Chromium on `#fafafa` samples than the fixed opaque-white diagnostic path. For example, `chakra12Fafafa` transparent coverage is `0.060479` vs browser `0.061224`, while opaque-white jumps to `0.324829`.
  - Fixed `opaque-white` only helps a narrow subset of white-background Arial ink averages and catastrophically fails on non-white solid backgrounds by drawing rectangular white texture pixels.
  - The remaining difference is not solved by a simple background fill. The likely next primitive is better glyph alpha/color coverage in the transparent texture path, or a true background-aware precomposition that fills only from the actual solid backdrop and does not leak rectangular texture pixels.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors after the fixture/script changes.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not use fixed `opaque-white` for parity.
  - Add a guarded `solid-bg` composite experiment that accepts the actual sample background color and converts precomposited pixels back to alpha against that same background, so no rectangular backdrop leaks into the final render. Validate it first on `resource-browser-font-raster.html` paired samples; only then consider threading element background context into framework text drawing.

Solid-bg alpha reconstruction experiment evidence:

- Source changes:
  - `src/main/java/com/sighs/apricityui/style/Text.java` now records `rasterBackgroundColor`, resolved from the nearest non-transparent ancestor `backgroundColor`, and includes it in the text cache key.
  - `src/main/java/com/sighs/apricityui/init/Element.java` copies `rasterBackgroundColor` through segmented text drawing.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now accepts guarded `APRICITYUI_FONT_RASTER_COMPOSITE=solid-bg` / `apricityui.fontRaster.composite=solid-bg`.
  - `solid-bg` renders text on the resolved solid background, then reconstructs straight alpha against that same background using `P = alpha * text + (1 - alpha) * background`; the final texture remains transparent, so no background rectangle is intentionally emitted.
- Verification before runs: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java changes.
- AUI fixture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE=solid-bg`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-background-pairs-solid-bg-last.log`.
- AUI fixture screenshot: `run/screenshots/aui/2026-07-15_14.18.49.png`.
- AUI fixture stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_14.18.49.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-solid-bg-last.log' -OutLog 'run/resource-browser-font-raster-samples-background-pairs-solid-bg.log'`.
- AUI fixture stats result: `run/resource-browser-font-raster-samples-background-pairs-solid-bg.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012000`, avg ink `172.37,172.37,172.37`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.031650`, avg ink `224.81,224.81,224.81`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042194`, avg ink `175.13,175.13,175.13`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.057014`, avg ink `201.55,201.55,201.55`.
  - `chakra12White`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.040756`, avg ink `186.3,186.3,186.3`.
  - `chakra12Fafafa`: browser coverage `0.061224`, avg ink `212.8,212.66,212.91`; AUI coverage `0.060479`, avg ink `207.78,207.78,207.78`.
- Full resource AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_COMPOSITE=solid-bg`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-lcd-solid-bg-last.log`.
- Full resource AUI screenshot: `run/screenshots/aui/2026-07-15_14.21.12.png`.
- Full resource stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_14.21.12.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-lcd-solid-bg-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-lcd-solid-bg.log'`.
- Full resource stats result: `run/resource-browser-gray-text-border-resource-samples-physical-lcd-solid-bg.log`.
  - Borders remain exact: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.212010`, avg ink `187.72,187.72,187.72`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.074448`, avg ink `195.01,195.01,195.01`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.154883`, avg ink `185.42,185.42,185.42`.
- Interpretation:
  - `solid-bg` removes the rectangular background leak that made `opaque-white` invalid, but it does not materially improve the remaining browser mismatch. Fixture and full-page numbers are effectively the same as the transparent target-physical path.
  - This suggests the main remaining issue is not simple solid-background compositing. The mismatch is more likely inside glyph coverage generation, grayscale/subpixel rasterizer behavior, font fallback/shape selection, or a texture filtering/blending detail after rasterization.
  - `solid-bg` should remain an opt-in diagnostic mode, not a default.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not promote `opaque-white` or `solid-bg`.
  - Next compare transparent target-physical output with and without Minecraft texture filtering changes for font textures. The remaining differences include coverage and darkness that can be affected by the final texture sampling stage.
  - Guarded filter switch exists in `src/main/java/com/sighs/apricityui/render/FontDrawer.java` as `APRICITYUI_FONT_RASTER_FILTER=nearest|linear` / `apricityui.fontRaster.filter=nearest|linear`; `compileJava` passed after adding it.
  - Start with `nearest`. Do not run full `devtools/resource.html` until the minimal fixture stats are compared against Chromium.

Next `RBV-V5-05` command block for goal mode:

```powershell
# Browser oracle first. Reuse existing log only if it is still valid for the same fixture and viewport.
node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-background-pairs-last.log

# AUI minimal fixture with target-physical raster, LCD AA, transparent composite, and nearest font texture filtering.
$env:APRICITYUI_TEST_DOC_PATH='tests/resource-browser-font-raster.html'
$env:APRICITYUI_FONT_RASTER_TARGET_PHYSICAL='1'
$env:APRICITYUI_FONT_RASTER_AA_MODE='lcd-hrgb'
$env:APRICITYUI_FONT_RASTER_FILTER='nearest'
Remove-Item Env:\APRICITYUI_FONT_RASTER_COMPOSITE -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-background-pairs-nearest-last.log

# Pick newest AUI screenshot, then run stats.
$latestAui = Get-ChildItem run/screenshots/aui/*.png | Sort-Object LastWriteTime -Descending | Select-Object -First 1
.\scripts\resource_browser_font_raster_stats.ps1 `
  -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' `
  -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' `
  -AuiImage $latestAui.FullName `
  -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-nearest-last.log' `
  -OutLog 'run/resource-browser-font-raster-samples-background-pairs-nearest.log'

# If and only if the minimal fixture improves toward Chromium without regression, run the full page.
$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
$env:APRICITYUI_FONT_RASTER_TARGET_PHYSICAL='1'
$env:APRICITYUI_FONT_RASTER_AA_MODE='lcd-hrgb'
$env:APRICITYUI_FONT_RASTER_FILTER='nearest'
Remove-Item Env:\APRICITYUI_FONT_RASTER_COMPOSITE -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-lcd-nearest-last.log

$latestAui = Get-ChildItem run/screenshots/aui/*.png | Sort-Object LastWriteTime -Descending | Select-Object -First 1
.\scripts\resource_browser_full_gray_text_stats.ps1 `
  -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' `
  -AuiImage $latestAui.FullName `
  -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-lcd-nearest-last.log' `
  -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-lcd-nearest.log'

.\gradlew.bat compileJava --console plain --no-daemon --offline
git diff --check
```

Acceptance note for the filter experiment:

- `nearest` is accepted only if the minimal fixture moves crop coverage, average ink RGB, darkness, and visible ink bounds closer to Chromium for the paired white/off-white samples without breaking the already-matched CSS geometry/color evidence.
- If `nearest` is neutral or worse, record it and test `linear` explicitly as the control/default. Do not leave the task ambiguous.
- If both filters fail, keep `RBV-V5-05` `[~]` and route the next exact action to glyph coverage generation, subpixel positioning, or font fallback measurement, not to page CSS changes.

Filter experiment evidence:

- Browser command: `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-browser-background-pairs-last.log`.
- Browser result: `run/resource-browser-font-raster-browser-background-pairs-last.log`; screenshot `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`; viewport `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- AUI nearest command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_FILTER=nearest`, `APRICITYUI_FONT_RASTER_COMPOSITE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-background-pairs-nearest-last.log`.
- AUI nearest result: build successful; log viewport `1463.0x843.0`; samples count `13`; screenshot `run/screenshots/aui/2026-07-15_14.30.13.png`.
- AUI nearest stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_14.30.13.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-nearest-last.log' -OutLog 'run/resource-browser-font-raster-samples-background-pairs-nearest.log'`.
- AUI nearest stats result: `run/resource-browser-font-raster-samples-background-pairs-nearest.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.031650`, avg ink `224.83,224.83,224.83`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.057014`, avg ink `201.59,201.59,201.59`.
  - `chakra12White`: browser coverage `0.039913`, avg ink `192.19,191.96,192.35`; AUI coverage `0.040737`, avg ink `186.61,186.61,186.61`.
  - `chakra12Fafafa`: browser coverage `0.061224`, avg ink `212.8,212.66,212.91`; AUI coverage `0.060479`, avg ink `207.88,207.88,207.88`.
- AUI linear control command: same as nearest except `APRICITYUI_FONT_RASTER_FILTER=linear`, output log `run\resource-browser-font-raster-aui-background-pairs-linear-last.log`.
- AUI linear control result: build successful; screenshot `run/screenshots/aui/2026-07-15_14.32.35.png`.
- AUI linear stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_14.32.35.png' -AuiMetricsLog 'run/resource-browser-font-raster-aui-background-pairs-linear-last.log' -OutLog 'run/resource-browser-font-raster-samples-background-pairs-linear.log'`.
- AUI linear stats result: `run/resource-browser-font-raster-samples-background-pairs-linear.log`; values are identical to nearest for the recorded samples.
- Interpretation:
  - `APRICITYUI_FONT_RASTER_FILTER=nearest` is neutral for this fixture. It does not move the paired white/off-white samples toward Chromium compared with explicit `linear`.
  - The full `devtools/resource.html` run was intentionally skipped because the minimal fixture did not improve, per the acceptance note above.
  - The remaining mismatch is before or inside glyph coverage/alpha generation, subpixel positioning, or font fallback/rasterizer behavior, not the final Minecraft texture min/mag filtering switch tested here.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `:compileJava UP-TO-DATE`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not promote `APRICITYUI_FONT_RASTER_FILTER=nearest`.
  - Next isolate glyph coverage before final texture draw: add a diagnostic that dumps or samples the generated font texture/alpha for explicit `Arial` samples, then compare those source texture values with Chromium crop behavior. If source glyph coverage already differs, focus on Java2D font render hints, fractional metrics, gamma/alpha reconstruction, and LCD/subpixel preservation. If source coverage is close but screenshot differs, inspect blend state and draw scaling again.

Source texture stats diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded source texture logging via `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1` / `apricityui.fontRaster.logTextureStats=true`.
  - The log line prefix is `[AUI FontRaster] textureStats` and records text, font family/size, raster mode, AA mode, composite mode, filter mode, image size, baseline, metrics, ink count, source texture coverage, average alpha, alpha range, average RGB, colored subpixel pixel count, and source ink bounds.
  - This is diagnostic-only and does not change rendering when the env/property is unset.
- Transparent source texture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=lcd-hrgb`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_COMPOSITE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-texture-stats-last.log`.
- Transparent source texture result: build successful; log viewport `1463.0x843.0`; samples count `13`; screenshot `run/screenshots/aui/2026-07-15_14.37.08.png`.
  - `5 ITEMS` / Arial / `13px` / `1px` letter spacing / transparent: image `103x31`, source ink `588`, source coverage `0.184153`, alphaAvg `205.921769`, alpha range `1..255`, avg RGB `153.564626,153.564626,153.564626`, `coloredSubpixelPixels=0`, inkBounds `3,6,97,17`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px` letter spacing / transparent: image `368x29`, source ink `1768`, source coverage `0.165667`, alphaAvg `211.473416`, alpha range `1..255`, avg RGB `153.128394,153.128394,153.128394`, `coloredSubpixelPixels=0`, inkBounds `3,6,362,15`.
  - `SELECT FILE TO VIEW DETAILS` / `"Chakra Petch", sans-serif` / `12px` / transparent: image `345x60`, source ink `1775`, source coverage `0.085749`, alphaAvg `178.909296`, avg RGB `153.83493,153.83493,153.83493`, `coloredSubpixelPixels=0`, inkBounds `3,20,339,17`.
- Opaque-white source texture command: same as transparent source texture command except `APRICITYUI_FONT_RASTER_COMPOSITE=opaque-white`, output log `run\resource-browser-font-raster-aui-texture-stats-opaque-white-last.log`.
- Opaque-white source texture result: build successful; log viewport `1463.0x843.0`; samples count `13`; screenshot `run/screenshots/aui/2026-07-15_14.40.53.png`.
  - `5 ITEMS` / Arial / `13px` / `1px` letter spacing / opaque-white: image `103x31`, source coverage `1.0`, alphaAvg `255.0`, avg RGB `239.829627,239.829627,239.829627`, `coloredSubpixelPixels=0`, inkBounds `0,0,103,31`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / opaque-white: image `368x29`, source coverage `1.0`, alphaAvg `255.0`, avg RGB `240.987069,240.987069,240.987069`, `coloredSubpixelPixels=0`, inkBounds `0,0,368,29`.
- Interpretation:
  - The transparent target-physical path produces grayscale source glyph textures: RGB channels are equal and `coloredSubpixelPixels=0` even when `aa=lcd-hrgb`.
  - Opaque-white also produces `coloredSubpixelPixels=0`; it only makes the whole texture opaque and averages white background with text. This confirms opaque-white's prior visual change is background leakage/precomposition, not true preserved LCD subpixel AA.
  - The browser mismatch is now more specifically routed to glyph rasterizer behavior and source alpha/gamma/coverage, not final texture filtering and not a simple opaque background precomposition.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding source texture logging.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Run a controlled AA-mode source-texture comparison (`APRICITYUI_FONT_RASTER_AA_MODE=on|lcd-hrgb|gasp|off` with `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`) and record whether source alpha coverage changes at all for explicit Arial samples.
  - If `lcd-hrgb` remains grayscale and similar to `on`, investigate Java2D rendering constraints on `BufferedImage.TYPE_INT_ARGB` and whether Chromium-like subpixel output is unavailable in this offscreen path; then test fractional metrics / gamma-correct alpha reconstruction as the next measurable primitive.

AA-mode source texture comparison evidence:

- Commands:
  - For each `APRICITYUI_FONT_RASTER_AA_MODE=on|lcd-hrgb|gasp|off`, run `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_COMPOSITE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-texture-stats-aa-on-last.log`, `run/resource-browser-font-raster-aui-texture-stats-aa-lcd-hrgb-last.log`, `run/resource-browser-font-raster-aui-texture-stats-aa-gasp-last.log`, `run/resource-browser-font-raster-aui-texture-stats-aa-off-last.log`.
  - Summary artifact: `run/resource-browser-font-raster-aa-mode-texture-stats-summary.csv`.
- Source texture result:
  - `on`, `lcd-hrgb`, and `gasp` are identical for explicit Arial samples.
  - `5 ITEMS` / Arial / `13px` / `1px`: `on|lcd-hrgb|gasp` image `103x31`, ink `588`, coverage `0.184153`, alphaAvg `205.921769`, alpha range `1..255`, avg RGB `153.564626,153.564626,153.564626`, `coloredSubpixelPixels=0`, inkBounds `3,6,97,17`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: `on|lcd-hrgb|gasp` image `368x29`, ink `1768`, coverage `0.165667`, alphaAvg `211.473416`, alpha range `1..255`, avg RGB `153.128394,153.128394,153.128394`, `coloredSubpixelPixels=0`, inkBounds `3,6,362,15`.
  - `off` changes only to hard-edged grayscale alpha: `5 ITEMS` ink `472`, coverage `0.147823`, alphaAvg `255.0`, `coloredSubpixelPixels=0`; `SELECT FILE...` ink `1478`, coverage `0.138493`, alphaAvg `255.0`, `coloredSubpixelPixels=0`.
- Interpretation:
  - `lcd-hrgb` is not providing Chromium-like LCD/subpixel information in this Java2D offscreen image path. It is functionally equivalent to grayscale AA for the tested source textures.
  - `off` proves the AA mode switch is wired, but it is not a browser-parity candidate.

Fractional metrics diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded fractional metrics control via `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on|off` / `apricityui.fontRaster.fractionalMetrics=on|off`.
  - Default behavior remains unchanged: when unset, no `KEY_FRACTIONALMETRICS` hint is applied.
  - The mode is included in the font texture cache key and in `[AUI FontRaster] textureStats` as `fractionalMetrics=...`.
- Source texture commands:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on|off`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_COMPOSITE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-texture-stats-fm-on-last.log`, `run/resource-browser-font-raster-aui-texture-stats-fm-off-last.log`.
  - Summary artifact: `run/resource-browser-font-raster-fractional-metrics-texture-stats-summary.csv`.
- Source texture result:
  - `fractionalMetrics=on` changes explicit Arial source coverage substantially; this is a real rasterizer variable unlike `lcd-hrgb`.
  - `5 ITEMS` / Arial / `13px` / `1px`: `off` ink `588`, coverage `0.184153`, alphaAvg `205.921769`, inkBounds `3,6,97,17`; `on` ink `728`, coverage `0.227999`, alphaAvg `163.181319`, inkBounds `2,6,98,18`; both have `coloredSubpixelPixels=0`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px`: `off` ink `1768`, coverage `0.165667`, alphaAvg `211.473416`, inkBounds `3,6,362,15`; `on` ink `2211`, coverage `0.207178`, alphaAvg `161.543645`, inkBounds `2,5,366,17`; both have `coloredSubpixelPixels=0`.
- Minimal fixture screenshot stats:
  - `fractionalMetrics=on` AUI screenshot: `run/screenshots/aui/2026-07-15_14.50.14.png`; stats `run/resource-browser-font-raster-samples-fm-on.log`.
  - `fractionalMetrics=off` AUI screenshot: `run/screenshots/aui/2026-07-15_14.50.49.png`; stats `run/resource-browser-font-raster-samples-fm-off.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; fm-on AUI coverage `0.013788`, avg ink `187.74,187.74,187.74`; fm-off AUI coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; fm-on AUI coverage `0.044276`, avg ink `188.9,188.9,188.9`; fm-off AUI coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; fm-on AUI coverage `0.059392`, avg ink `208.12,208.12,208.12`; fm-off AUI coverage `0.057014`, avg ink `201.59,201.59,201.59`.
- Full resource command:
  - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, composite/log texture stats/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-aa-on-fm-on-last.log`.
- Full resource result:
  - Build successful; state `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`; viewport `1463.0x843.0`; screenshot `run/screenshots/aui/2026-07-15_14.52.36.png`.
  - Stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_14.52.36.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-aa-on-fm-on-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-aa-on-fm-on.log'`.
  - Stats result: `run/resource-browser-gray-text-border-resource-samples-physical-aa-on-fm-on.log`.
  - Borders remain exact: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.212010`, avg ink `187.89,187.89,187.89`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.081302`, avg ink `197.22,197.22,197.22`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.154809`, avg ink `185.73,185.73,185.73`.
- Interpretation:
  - `fractionalMetrics=on` improves some minimal explicit-Arial fixture samples, especially `arial13White` and `arial12Fafafa`, but it also overshoots other coverage metrics.
  - On full `devtools/resource.html`, it is not a clean browser-parity improvement. `contentCount` is similar to the prior target-physical baseline, `detailEmpty` remains dark/under-covered, and `fileMeta` coverage overshoots.
  - Keep fractional metrics as an opt-in diagnostic switch. Do not promote it as default from this evidence.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the fractional metrics change; final verification was `:compileJava UP-TO-DATE`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not promote `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on`.
  - Next test gamma/alpha reconstruction or a Chromium-like glyph raster source. The current Java2D path remains grayscale, and browser-matching likely needs either a different source coverage model or a post-raster alpha/gamma transform validated first on `resource-browser-font-raster.html`.

Alpha gamma reconstruction experiment evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded alpha gamma control via `APRICITYUI_FONT_RASTER_ALPHA_GAMMA=<number>` / `apricityui.fontRaster.alphaGamma=<number>`.
  - The mode is included in the font texture cache key as `ag=...` and in `[AUI FontRaster] textureStats` as `alphaGamma=...`.
  - Default behavior remains unchanged: unset or `1.0` means no alpha transform.
  - The transform is applied after Java2D glyph drawing and before `NativeImage` upload, only for transparent texture paths. Opaque composite diagnostic paths are not changed by this experiment.
- Standalone alpha gamma commands:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_ALPHA_GAMMA=1.25|1.5`, `APRICITYUI_FONT_RASTER_FILTER=linear`, fractional metrics/composite/log texture stats/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-alpha-gamma-1p25-last.log`, `run/resource-browser-font-raster-aui-alpha-gamma-1p5-last.log`.
  - Stats: `run/resource-browser-font-raster-samples-alpha-gamma-1p25.log`, `run/resource-browser-font-raster-samples-alpha-gamma-1p5.log`.
- Standalone alpha gamma result:
  - Gamma greater than `1.0` reduces coverage and does not solve white-background Arial darkness.
  - `gamma=1.25`, `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011742`, avg ink `172.82,172.82,172.82`.
  - `gamma=1.5`, `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011484`, avg ink `172.7,172.7,172.7`.
  - `gamma=1.5`, `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.055613`, avg ink `202.34,202.34,202.34`.
- Combined fractional metrics + alpha gamma commands:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on`, `APRICITYUI_FONT_RASTER_ALPHA_GAMMA=0.85|0.9`, `APRICITYUI_FONT_RASTER_FILTER=linear`, composite/log texture stats/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-fm-on-alpha-gamma-0p85-last.log`, `run/resource-browser-font-raster-aui-fm-on-alpha-gamma-0p9-last.log`.
  - Stats: `run/resource-browser-font-raster-samples-fm-on-alpha-gamma-0p85.log`, `run/resource-browser-font-raster-samples-fm-on-alpha-gamma-0p9.log`.
- Combined fixture result:
  - `fractionalMetrics=on + gamma=0.9` improves some off-white Arial samples but worsens white-background coverage overshoot.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI `fm-on+gamma=0.9` coverage `0.014138`, avg ink `187.33,187.33,187.33`; baseline linear coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI `fm-on+gamma=0.9` coverage `0.032959`, avg ink `226.61,226.61,226.61`; baseline linear coverage `0.031650`, avg ink `224.83,224.83,224.83`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI `fm-on+gamma=0.9` coverage `0.045290`, avg ink `188.26,188.26,188.26`; baseline linear coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI `fm-on+gamma=0.9` coverage `0.060424`, avg ink `207.41,207.41,207.41`; baseline linear coverage `0.057014`, avg ink `201.59,201.59,201.59`.
- Full resource command:
  - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS=on`, `APRICITYUI_FONT_RASTER_ALPHA_GAMMA=0.9`, `APRICITYUI_FONT_RASTER_FILTER=linear`, composite/log texture stats/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-physical-aa-on-fm-on-gamma-0p9-last.log`.
- Full resource result:
  - Build successful; state `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`; viewport `1463.0x843.0`; screenshot `run/screenshots/aui/2026-07-15_15.01.39.png`.
  - Stats command: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_15.01.39.png' -AuiMetricsLog 'run/resource-browser-gray-text-border-resource-aui-physical-aa-on-fm-on-gamma-0p9-last.log' -OutLog 'run/resource-browser-gray-text-border-resource-samples-physical-aa-on-fm-on-gamma-0p9.log'`.
  - Stats result: `run/resource-browser-gray-text-border-resource-samples-physical-aa-on-fm-on-gamma-0p9.log`.
  - Borders remain exact: browser/AUI `224,224,224,255`.
  - `contentCount`: browser coverage `0.208946`, avg ink `189.84,189.07,188.52`; AUI coverage `0.215074`, avg ink `186.8,186.8,186.8`.
  - `fileMeta`: browser coverage `0.075724`, avg ink `195.63,195.59,195.73`; AUI coverage `0.083778`, avg ink `196.34,196.34,196.34`.
  - `detailEmpty`: browser coverage `0.168341`, avg ink `194.52,195.05,194.6`; AUI coverage `0.159449`, avg ink `185.93,185.93,185.93`.
- Interpretation:
  - Alpha gamma is a useful diagnostic but not a clean browser-parity fix. Standalone gamma greater than `1.0` mostly reduces coverage and leaves white-background Arial too dark. The `fm-on + gamma=0.9` combination improves some off-white samples and slightly improves full `detailEmpty` coverage, but full-page `contentCount` and `fileMeta` over-cover and `detailEmpty` remains too dark.
  - Do not promote `APRICITYUI_FONT_RASTER_ALPHA_GAMMA` or the `fm-on + gamma=0.9` combination as default.
  - The remaining mismatch likely requires a different glyph raster source or a richer source coverage model, not a single global post-raster alpha exponent.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding alpha gamma; final verification was `:compileJava UP-TO-DATE`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not promote `APRICITYUI_FONT_RASTER_ALPHA_GAMMA`.
  - Next investigate a Chromium-like glyph raster source or a richer coverage model. A useful next diagnostic is to generate source glyph masks using `GlyphVector`/outline rasterization or another Java font raster path, then compare source coverage and final fixture stats against Chromium before touching full `resource.html`.

GlyphVector source raster experiment evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has a guarded raster source switch: `APRICITYUI_FONT_RASTER_SOURCE=draw-string|glyph-vector` / `apricityui.fontRaster.source=draw-string|glyph-vector`.
  - Default behavior remains `draw-string`.
  - `glyph-vector` creates and fills `GlyphVector` outlines into the same font texture path. It is included in the font texture cache key and in `[AUI FontRaster] textureStats` as `source=...`.
- Browser oracle:
  - Type: pixel-sample.
  - Reused existing Chromium fixture because `tests/resource-browser-font-raster.html` did not change.
  - Browser log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- AUI minimal command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_SOURCE=glyph-vector`, `APRICITYUI_FONT_RASTER_FILTER=linear`, fractional metrics/alpha gamma/composite/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-glyph-vector-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_15.10.59.png`.
  - Stats: `run/resource-browser-font-raster-samples-glyph-vector.log`.
- AUI source-texture command:
  - Same as above with `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, output `run/resource-browser-font-raster-aui-texture-stats-glyph-vector-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_15.12.09.png`.
  - Stats: `run/resource-browser-font-raster-samples-glyph-vector-texture-stats-run.log`.
- Source texture result:
  - `glyph-vector` changes source coverage materially, so the diagnostic path is working.
  - `5 ITEMS` / Arial / `13px` / `1px`: prior `draw-string` source texture ink `588`, coverage `0.184153`, alphaAvg `205.921769`, inkBounds `3,6,97,17`; `glyph-vector` ink `715`, coverage `0.223927`, alphaAvg `165.28951`, inkBounds `2,6,98,18`; both have `coloredSubpixelPixels=0`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: prior `draw-string` source texture ink `1768`, coverage `0.165667`, alphaAvg `211.473416`, inkBounds `3,6,362,15`; `glyph-vector` ink `2083`, coverage `0.195184`, alphaAvg `171.05953`, inkBounds `2,5,363,17`; both have `coloredSubpixelPixels=0`.
- Minimal fixture screenshot result:
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI `glyph-vector` coverage `0.013788`, avg ink `188.07,188.07,188.07`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI `glyph-vector` coverage `0.032553`, avg ink `227.18,227.18,227.18`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI `glyph-vector` coverage `0.043300`, avg ink `185.28,185.28,185.28`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI `glyph-vector` coverage `0.058267`, avg ink `207.34,207.34,207.34`.
- Interpretation:
  - `glyph-vector` improves some white-background darkness and makes source coverage closer for a few samples, but it is not a clean Chromium-parity fix.
  - It over-covers `arial12White`, under-covers `arial13Fafafa` and `arial12Fafafa`, and still produces grayscale source textures with `coloredSubpixelPixels=0`.
  - Do not promote `APRICITYUI_FONT_RASTER_SOURCE=glyph-vector` as default from this evidence.
  - Full `devtools/resource.html` was intentionally not run because the minimal browser-standard fixture has regressions; per task discipline, full-page work only follows a clean minimal result.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded `glyph-vector` path.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=glyph-vector` as diagnostic only.
  - Next compare Java2D `GlyphVector` outline output under explicit `KEY_ANTIALIASING` / `KEY_STROKE_CONTROL` / `FontRenderContext` fractional-metrics combinations, or add a tiny source-texture export/crop harness that compares generated alpha masks directly against Chromium sample crops. Do not change `resource.html` and do not run full `devtools/resource.html` until the minimal fixture improves without regression.

Java2D stroke-control experiment evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has a guarded Java2D stroke-control switch: `APRICITYUI_FONT_RASTER_STROKE_CONTROL=default|normalize|pure` / `apricityui.fontRaster.strokeControl=default|normalize|pure`.
  - Default behavior remains unchanged: unset means no explicit `KEY_STROKE_CONTROL` hint.
  - The mode is included in the font texture cache key as `sc=...` and in `[AUI FontRaster] textureStats` as `strokeControl=...`.
- Browser oracle:
  - Type: pixel-sample.
  - Reused existing Chromium fixture because `tests/resource-browser-font-raster.html` did not change.
  - Browser log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- AUI commands:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_SOURCE=glyph-vector`, `APRICITYUI_FONT_RASTER_STROKE_CONTROL=normalize|pure`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, fractional metrics/alpha gamma/composite/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-glyph-vector-stroke-normalize-last.log`, `run/resource-browser-font-raster-aui-glyph-vector-stroke-pure-last.log`.
  - Screenshots: `run/screenshots/aui/2026-07-15_15.16.28.png` for `normalize`, `run/screenshots/aui/2026-07-15_15.17.19.png` for `pure`.
  - Stats: `run/resource-browser-font-raster-samples-glyph-vector-stroke-normalize.log`, `run/resource-browser-font-raster-samples-glyph-vector-stroke-pure.log`.
- Source texture result:
  - `normalize` and `pure` are identical to prior `glyph-vector` default for the key explicit Arial samples.
  - `5 ITEMS` / Arial / `13px` / `1px`: `normalize` and `pure` both ink `715`, coverage `0.223927`, alphaAvg `165.28951`, inkBounds `2,6,98,18`; prior `glyph-vector` default was the same.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: `normalize` and `pure` both ink `2083`, coverage `0.195184`, alphaAvg `171.05953`, inkBounds `2,5,363,17`; prior `glyph-vector` default was the same.
  - All tested variants still have `coloredSubpixelPixels=0`.
- Minimal fixture screenshot result:
  - `normalize` and `pure` crop stats are identical to the prior `glyph-vector` run for the key paired Arial samples.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.013788`, avg ink `188.07,188.07,188.07`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.032553`, avg ink `227.18,227.18,227.18`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.043300`, avg ink `185.28,185.28,185.28`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.058267`, avg ink `207.34,207.34,207.34`.
- Interpretation:
  - `KEY_STROKE_CONTROL` does not move the current `GlyphVector` outline path toward Chromium in this fixture.
  - Do not promote `APRICITYUI_FONT_RASTER_STROKE_CONTROL=normalize` or `pure`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture is unchanged and still has the same regressions as `glyph-vector` default.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded stroke-control path.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_STROKE_CONTROL` as diagnostic only.
  - Next add a direct source-alpha-mask comparison/export for the generated font texture, or add an explicit `FontRenderContext` construction mode so the experiment can vary antialias and fractional metrics at glyph-vector creation time rather than only through `Graphics2D` rendering hints. Continue using `resource-browser-font-raster.html` before any full `devtools/resource.html` run.

Explicit FontRenderContext experiment evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has a guarded `GlyphVector` construction switch: `APRICITYUI_FONT_RASTER_FRC=graphics|aa-on-fm-on|aa-on-fm-off|aa-off-fm-off` / `apricityui.fontRaster.frc=...`.
  - Default behavior remains `graphics`, which uses `Graphics2D.getFontRenderContext()` as before.
  - Explicit modes create `GlyphVector` with `new FontRenderContext(null, antialiasHint, fractionalMetricsHint)`.
  - The mode is included in the font texture cache key as `frc=...` and in `[AUI FontRaster] textureStats` as `frc=...`.
- Browser oracle:
  - Type: pixel-sample.
  - Reused existing Chromium fixture because `tests/resource-browser-font-raster.html` did not change.
  - Browser log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- AUI commands:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_SOURCE=glyph-vector`, `APRICITYUI_FONT_RASTER_FRC=aa-on-fm-on|aa-on-fm-off|aa-off-fm-off`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, stroke-control/fractional metrics/alpha gamma/composite/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Logs: `run/resource-browser-font-raster-aui-glyph-vector-frc-aa-on-fm-on-last.log`, `run/resource-browser-font-raster-aui-glyph-vector-frc-aa-on-fm-off-last.log`, `run/resource-browser-font-raster-aui-glyph-vector-frc-aa-off-fm-off-last.log`.
  - Screenshots: `run/screenshots/aui/2026-07-15_15.23.00.png` for `aa-on-fm-on`, `run/screenshots/aui/2026-07-15_15.23.59.png` for `aa-on-fm-off`, `run/screenshots/aui/2026-07-15_15.25.27.png` for `aa-off-fm-off`.
  - Stats: `run/resource-browser-font-raster-samples-glyph-vector-frc-aa-on-fm-on.log`, `run/resource-browser-font-raster-samples-glyph-vector-frc-aa-on-fm-off.log`, `run/resource-browser-font-raster-samples-glyph-vector-frc-aa-off-fm-off.log`.
- Source texture result:
  - The key paired Arial samples are unchanged from prior `glyph-vector` default across all tested FRC modes.
  - `5 ITEMS` / Arial / `13px` / `1px`: all explicit FRC modes ink `715`, coverage `0.223927`, alphaAvg `165.28951`, inkBounds `2,6,98,18`; prior `glyph-vector` default was the same.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: all explicit FRC modes ink `2083`, coverage `0.195184`, alphaAvg `171.05953`, inkBounds `2,5,363,17`; prior `glyph-vector` default was the same.
  - `arial13Plain` without letter-spacing changed slightly for `aa-on-fm-on`, but that does not address the paired white/off-white acceptance samples.
  - All tested variants still have `coloredSubpixelPixels=0`.
- Minimal fixture screenshot result:
  - For the paired Arial samples, the explicit FRC modes reproduce the same mismatch pattern as `glyph-vector` default.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.013788`, avg ink `188.07,188.07,188.07`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.032553`, avg ink `227.18,227.18,227.18`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.043300`, avg ink `185.28,185.28,185.28`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.058267`, avg ink `207.34,207.34,207.34`.
- Interpretation:
  - Explicit `FontRenderContext` construction does not move the important paired samples toward Chromium in this fixture.
  - Do not promote `APRICITYUI_FONT_RASTER_FRC=aa-on-fm-on`, `aa-on-fm-off`, or `aa-off-fm-off`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture is unchanged and still has the same regressions as `glyph-vector` default.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded FRC path.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_FRC` as diagnostic only.
  - Next add direct source-alpha-mask export/comparison for generated font textures. The current source texture stats are useful but too coarse; the next diagnostic should dump per-pixel alpha or a deterministic mask crop for the explicit Arial samples so the source glyph mask can be compared directly against Chromium crop coverage before another renderer hint is attempted.

Direct source-alpha-mask export evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded direct alpha-mask export: `APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK=1` / `apricityui.fontRaster.exportAlphaMask=true`.
  - Export is diagnostic only. Defaults remain unchanged.
  - Corrected export destination is `run/font-raster-masks` under the Minecraft game directory.
- Browser oracle:
  - Type: pixel-sample.
  - Reused existing Chromium fixture because `tests/resource-browser-font-raster.html` did not change.
  - Browser log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Browser viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- AUI command:
  - Previous corrected-path export run: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK=1`, source/fractional metrics/alpha gamma/composite/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-mask-export-draw-string-fixed-path-last.log`.
  - AUI screenshot for this evidence: `run/screenshots/aui/2026-07-15_15.32.10.png`.
  - Mask export directory: `run/font-raster-masks`.
- Stats command:
  - `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-alpha-mask-export-draw-string-fixed-path-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_15.32.10.png' -OutLog 'run/resource-browser-font-raster-samples-alpha-mask-export-draw-string-fixed-path.log'`.
- Stats result:
  - `run/resource-browser-font-raster-samples-alpha-mask-export-draw-string-fixed-path.log`.
  - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012000`, avg ink `172.54,172.54,172.54`.
  - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.031650`, avg ink `224.83,224.83,224.83`.
  - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042157`, avg ink `175.18,175.18,175.18`.
  - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.057014`, avg ink `201.59,201.59,201.59`.
- Exported mask metadata:
  - `run/font-raster-masks/406e9744-9e35-39b1-9f37-0b56e8fdd5fe.txt` / `.png`: `5 ITEMS`, Arial `13px`, `letterSpacing=1`, `background=#ffffff`, image `103x31`, baseline `23`.
  - `run/font-raster-masks/99125bb9-76a8-3a7c-bb85-a379d44f19ff.txt` / `.png`: `5 ITEMS`, Arial `13px`, `letterSpacing=0`, `background=#ffffff`, image `92x31`, baseline `23`.
  - `run/font-raster-masks/963549c2-aa7c-302a-848a-f564b50044bb.txt` / `.png`: `SELECT FILE TO VIEW DETAILS`, Arial `12px`, `letterSpacing=1`, `background=#ffffff`, image `368x29`, baseline `21`.
  - `run/font-raster-masks/a564c31c-8851-3f08-8ad3-7cf43cc8f3f9.txt` / `.png`: `SELECT FILE TO VIEW DETAILS`, Arial `12px`, `letterSpacing=1`, `background=#fafafa`, image `368x29`, baseline `21`.
- Source texture result:
  - `5 ITEMS` / Arial / `13px` / `1px`: source texture ink `588`, coverage `0.184153`, alphaAvg `205.921769`, inkBounds `3,6,97,17`, `coloredSubpixelPixels=0`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: source texture ink `1768`, coverage `0.165667`, alphaAvg `211.473416`, inkBounds `3,6,362,15`, `coloredSubpixelPixels=0`.
- Interpretation:
  - Direct export confirms the current Java2D `draw-string` source mask is grayscale and has no colored subpixel coverage.
  - The exported masks explain why AUI remains single-channel in the final screenshot, but they do not provide a Chromium source-mask oracle by themselves.
  - The fixture result remains mixed: AUI under-covers `arial13White`, `arial13Fafafa`, and `arial12Fafafa`, while `arial12White` over-covers and remains darker.
  - Do not promote any guarded font-raster option from this evidence.
  - Full `devtools/resource.html` was intentionally not run because this evidence does not improve the minimal fixture.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
  - `compileJava` was not rerun in this evidence-only continuation because no Java file changed during this goal turn.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Add a browser-side source-raster oracle for the same explicit Arial samples, preferably a minimal Chromium canvas/ImageData fixture that renders text at the same font, size, letter spacing/background policy and records per-channel alpha/RGB coverage. Compare that browser source evidence against `run/font-raster-masks` before adding another Java renderer hint.

Chromium source-raster oracle evidence:

- Source change:
  - Added `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html`.
  - Added `scripts/resource_browser_font_source_raster_metrics.js`.
  - The fixture uses Chromium Canvas 2D and records both `css1x` and `physicalDpr` ImageData stats for explicit Arial `13px`/`12px`, `letterSpacing=1px`, white and `#fafafa` background pairs.
  - Canvas `letterSpacing` support is recorded by the fixture and was `true` with resolved value `1px`.
- Browser command:
  - `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-last.log`.
- Browser result:
  - Log: `run/resource-browser-font-source-raster-browser-last.log`.
  - Viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
  - User agent: Headless Chromium `150.0.0.0`.
- Physical-DPR source-alpha result:
  - `arial13White`: physical canvas `114x39`, source ink `755`, source coverage `0.169816`, source avg alpha `161.933775`, colored pixels `0`, source bounds `7,7,99,17`; composited coverage `0.166442`, composited avg RGB `183.98,183.98,183.98`.
  - `arial13Fafafa`: physical canvas `114x39`, source ink `755`, source coverage `0.169816`, source avg alpha `161.933775`, colored pixels `0`, source bounds `7,7,99,17`; composited coverage `0.166442`, composited avg RGB `182.44,182.44,182.44`.
  - `arial12White`: physical canvas `378x35`, source ink `2167`, source coverage `0.163794`, source avg alpha `163.932626`, colored pixels `0`, source bounds `7,8,363,15`; composited coverage `0.160317`, composited avg RGB `182.84,182.84,182.84`.
  - `arial12Fafafa`: physical canvas `378x35`, source ink `2167`, source coverage `0.163794`, source avg alpha `163.932626`, colored pixels `0`, source bounds `7,8,363,15`; composited coverage `0.160317`, composited avg RGB `181.42,181.42,181.42`.
- Comparison against exported AUI source masks:
  - AUI `5 ITEMS` / Arial / `13px` / `1px`: image `103x31`, ink `588`, coverage `0.184153`, alphaAvg `205.921769`, inkBounds `3,6,97,17`, coloredSubpixelPixels `0`.
  - Chromium `arial13` physical source bounds have nearly the same visible glyph extent (`99x17`) but much lower average alpha (`161.93` vs AUI `205.92`).
  - AUI `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `1px`: image `368x29`, ink `1768`, coverage `0.165667`, alphaAvg `211.473416`, inkBounds `3,6,362,15`, coloredSubpixelPixels `0`.
  - Chromium `arial12` physical source bounds are nearly identical in visible extent (`363x15`) and coverage (`0.163794` vs AUI `0.165667`), but much lower average alpha (`163.93` vs AUI `211.47`).
- Interpretation:
  - Chromium Canvas 2D source raster for these samples is also grayscale in this environment (`coloredPixels=0`), so the current mismatch is not explained by missing colored subpixel source masks.
  - The clearer source-mask difference is alpha distribution: AUI Java2D masks are substantially harder/more opaque than Chromium at the same DPR-scaled sample, especially for Arial `12px` where glyph bounds and coverage already align closely.
  - The next diagnostic should target source alpha shaping or coverage softening, not another global CSS color, font-family, layout, stroke-control, FRC, or LCD-color experiment.
  - Full `devtools/resource.html` was intentionally not run because this goal run produced the missing source oracle and did not change Java rendering behavior.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
  - `compileJava` was not run because this goal turn added only HTML/JS diagnostic fixtures and did not edit Java.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Add a guarded Java source-alpha shaping diagnostic, driven by the Chromium source-raster oracle. Start with the minimal Arial `12px`/`13px` fixture only; do not run full `devtools/resource.html` unless the minimal fixture moves toward Chromium without regression.

Guarded linear alpha-scale diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded linear source-alpha scaling: `APRICITYUI_FONT_RASTER_ALPHA_SCALE=<number>` / `apricityui.fontRaster.alphaScale=<number>`.
  - Default behavior remains unchanged: unset means `scale=1.0`.
  - The mode is included in the font texture cache key as `alphaScale=...`, in `RasterMode.cacheKey()` as `as=...`, in `[AUI FontRaster] textureStats` as `alphaScale=...`, and in exported alpha-mask metadata.
  - Scaling is applied only on the transparent source texture path, after alpha gamma, before stats/export/upload.
- Browser oracle:
  - Type: pixel-sample + source-raster ImageData.
  - Browser crop log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser source-raster log: `run/resource-browser-font-source-raster-browser-last.log`.
  - Chromium source target summary: Arial `13px` physical source avg alpha `161.933775`, bounds `99x17`; Arial `12px` physical source avg alpha `163.932626`, bounds `363x15`.
- Verification before AUI runs:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded alpha-scale path.
- AUI `alphaScale=0.78` command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_SCALE=0.78`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK=1`, source/fractional metrics/alpha gamma/composite/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-scale-0p78-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_15.46.31.png`.
  - Stats: `run/resource-browser-font-raster-samples-alpha-scale-0p78.log`.
- AUI `alphaScale=0.78` result:
  - Source texture moved close to the Chromium source avg alpha: `5 ITEMS` Arial `13px` alphaAvg `160.670068`; `SELECT FILE TO VIEW DETAILS` Arial `12px` alphaAvg `165.0181`.
  - Final crop over-lightened despite source-alpha alignment:
    - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011816`, avg ink `189.87,189.87,189.87`.
    - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.041364`, avg ink `191.82,191.82,191.82`.
    - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.056055`, avg ink `212.26,212.26,212.26`.
  - Interpretation: matching source avg alpha alone is too aggressive for final screenshot parity.
- AUI `alphaScale=0.88` command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_SCALE=0.88`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, export/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-scale-0p88-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_15.48.59.png`.
  - Stats: `run/resource-browser-font-raster-samples-alpha-scale-0p88.log`.
- AUI `alphaScale=0.88` result:
  - Source texture remains harder than Chromium source but softer than baseline: `5 ITEMS` Arial `13px` alphaAvg `180.97449`; `SELECT FILE TO VIEW DETAILS` Arial `12px` alphaAvg `185.832014`.
  - Final crop average ink improves substantially for several explicit Arial samples:
    - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011908`, avg ink `181.98,181.98,181.98`.
    - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.041770`, avg ink `184.33,184.33,184.33`.
    - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.056516`, avg ink `207.52,207.52,207.52`.
  - Remaining regressions/mismatches:
    - `arial13White` still under-covers (`0.011908` vs browser `0.013618`).
    - `arial13Fafafa` still under-covers and is too light (`0.031539`, avg `228.24` vs browser `0.034929`, avg `226.54,226.28,226.37`).
    - `arial12White` still over-covers (`0.041770` vs browser `0.040064`).
    - Some non-Arial/generic fallback samples become too light, so this is not a clean global default.
- Interpretation:
  - Linear alpha scale is a useful diagnostic and confirms source-alpha hardness is part of the mismatch.
  - It is not sufficient as a default because one scalar cannot simultaneously match coverage and average ink across white/off-white backgrounds and Arial/non-Arial samples.
  - Do not promote `APRICITYUI_FONT_RASTER_ALPHA_SCALE=0.78` or `0.88`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still has mixed regressions.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java edit.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_ALPHA_SCALE` diagnostic only.
  - Next test a guarded source-alpha curve or coverage-preserving post-process that can soften high-alpha interiors without dropping edge coverage. Use the same minimal fixture and Chromium source-raster oracle before any full `devtools/resource.html` run.

Guarded alpha-cap diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded source-alpha capping: `APRICITYUI_FONT_RASTER_ALPHA_CAP=<1..254>` / `apricityui.fontRaster.alphaCap=<1..254>`.
  - Default behavior remains unchanged: unset or `>=255` means no cap.
  - The mode is included in the font texture cache key as `alphaCap=...`, in `RasterMode.cacheKey()` as `ac=...`, in `[AUI FontRaster] textureStats` as `alphaCap=...`, and in exported alpha-mask metadata.
  - Capping only affects non-transparent pixels whose alpha is above the cap, so low-alpha edge pixels remain unchanged.
- Verification before AUI runs:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded alpha-cap path.
- AUI `alphaCap=190` command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_CAP=190`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, alpha scale/export/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-cap-190-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_15.53.43.png`.
  - Stats: `run/resource-browser-font-raster-samples-alpha-cap-190.log`.
- AUI `alphaCap=190` result:
  - Source texture closely matches the Chromium source average alpha while preserving source ink/bounds:
    - `5 ITEMS` Arial `13px`: source ink `588`, coverage `0.184153`, alphaAvg `161.5`, alphaMax `190`, bounds `97x17`; Chromium source avg alpha `161.933775`, bounds `99x17`.
    - `SELECT FILE TO VIEW DETAILS` Arial `12px`: source ink `1768`, coverage `0.165667`, alphaAvg `164.572398`, alphaMax `190`, bounds `362x15`; Chromium source avg alpha `163.932626`, bounds `363x15`.
  - Final crop is still too light and coverage remains mixed:
    - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.011982`, avg ink `190.28,190.28,190.28`.
    - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042046`, avg ink `193.03,193.03,193.03`.
    - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.056959`, avg ink `212.75,212.75,212.75`.
  - Interpretation: matching the Chromium source average alpha is not sufficient for final screenshot parity.
- AUI `alphaCap=220` command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_CAP=220`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, alpha scale/export/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-cap-220-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_15.54.58.png`.
  - Stats: `run/resource-browser-font-raster-samples-alpha-cap-220.log`.
- AUI `alphaCap=220` result:
  - Source texture remains softer than baseline but harder than Chromium source:
    - `5 ITEMS` Arial `13px`: alphaAvg `183.022109`, alphaMax `220`.
    - `SELECT FILE TO VIEW DETAILS` Arial `12px`: alphaAvg `187.117647`, alphaMax `220`.
  - Final crop average ink is close for several samples, but coverage still prevents acceptance:
    - `arial13White`: browser coverage `0.013618`, avg ink `183.47,182.75,183.02`; AUI coverage `0.012000`, avg ink `181.74,181.74,181.74`.
    - `arial13Fafafa`: browser coverage `0.034929`, avg ink `226.54,226.28,226.37`; AUI coverage `0.031650`, avg ink `227.92,227.92,227.92`.
    - `arial12White`: browser coverage `0.040064`, avg ink `183.65,183.74,183.5`; AUI coverage `0.042120`, avg ink `184.36,184.36,184.36`.
    - `arial12Fafafa`: browser coverage `0.061375`, avg ink `207.49,207.53,207.4`; AUI coverage `0.056959`, avg ink `207.23,207.23,207.23`.
  - Some generic/fallback samples improve average ink but still have coverage deltas; this is not a clean global default.
- Interpretation:
  - Alpha cap preserves source ink/bounds better than linear scale and confirms that high-alpha interiors are part of the perceived darkness.
  - It still cannot solve final crop coverage: `13px` Arial remains under-covered, `12px` white remains over-covered, and off-white samples still under-cover.
  - Do not promote `APRICITYUI_FONT_RASTER_ALPHA_CAP=190` or `220`.
  - The remaining mismatch is no longer just source alpha hardness. The next diagnostic should explain how the source texture maps to screen pixels: draw quad offset/scale, texture filtering, viewport scale, crop mapping, or the pixel threshold used by the comparison.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still has mixed regressions.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java edit.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_ALPHA_CAP` diagnostic only.
  - Add a source-to-screen projection diagnostic for the minimal font-raster fixture. Record the generated texture size, draw scale, draw quad, expected physical bounds, and screenshot crop bounds for the explicit Arial `13px`/`12px` samples before trying another alpha transform.

Source-to-screen projection diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded projection logging: `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1` / `apricityui.fontRaster.logProjection=true`.
  - `FontEntry` now carries computed texture stats so projection logs can report source ink bounds without rescanning during every draw.
  - Default rendering remains unchanged; the projection log is diagnostic only.
- Browser oracle:
  - Type: pixel-sample + source-raster ImageData.
  - Browser crop log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Browser source-raster log: `run/resource-browser-font-source-raster-browser-last.log`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, alpha scale/alpha cap/export/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-projection-baseline-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_16.01.07.png`.
  - Stats: `run/resource-browser-font-raster-samples-projection-baseline.log`.
- Projection result:
  - `5 ITEMS` / Arial / `13px` / `letterSpacing=1` at `arial13White`:
    - texture `103x31`, drawScale `0.571429`, pixelScale `1.75`, CSS quad `112.582855,434.488403,58.857147,17.714287`.
    - physical quad `197.019997,760.354706,103.000008,31.000002`.
    - source ink bounds `3,6,97,17`; physical ink bounds `200.019997,766.354706,97.000004,17.0`.
    - Screenshot stats for the same sample: browser inkBounds `(199,766,99,17)`; AUI inkBounds `(200,766,97,18)`.
  - `SELECT FILE TO VIEW DETAILS` / Arial / `12px` / `letterSpacing=1` at `arial12White`:
    - texture `368x29`, drawScale `0.571429`, pixelScale `1.75`, CSS quad `112.674286,526.56073,210.285721,16.57143`.
    - physical quad `197.18,921.481277,368.000011,29.000003`.
    - source ink bounds `3,6,362,15`; physical ink bounds `200.180001,927.481278,362.000008,15.000001`.
    - Screenshot stats for the same sample: browser inkBounds `(199,927,362,15)`; AUI inkBounds `(200,927,363,16)`.
  - `arial13Fafafa` and `arial12Fafafa` projection logs use the same source bounds as their white-background counterparts, but screenshot stats report full-width AUI/browser ink bounds because the off-white background delta is included in the crop-level ink test.
- Interpretation:
  - The source-to-screen projection itself is not obviously losing glyph coverage. Physical source bounds map almost exactly to AUI screenshot ink bounds for white-background samples.
  - For `arial13White`, the AUI glyph is physically narrower than Chromium (`97px` vs browser `99px`), consistent with a source glyph coverage/advance issue rather than projection scale loss.
  - For `arial12White`, the AUI projected source bounds and screenshot bounds are effectively aligned with Chromium; remaining differences are mostly alpha/threshold behavior.
  - The off-white paired samples cannot be interpreted as glyph coverage from the current stats alone because the comparison script treats full-crop background deltas as ink. This explains why off-white samples show full-width bounds even though projection logs prove the glyph texture is narrow.
  - Do not add another renderer alpha transform until the stats script separates glyph-only ink from background-delta ink.
  - Full `devtools/resource.html` was intentionally not run because this goal turn produced projection evidence and did not produce a clean minimal-fixture fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java edit.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_LOG_PROJECTION` diagnostic only.
  - Update `scripts/resource_browser_font_raster_stats.ps1` or add a companion script to emit separate metrics for glyph-only ink and background-delta ink. Use white-background samples for glyph coverage acceptance and off-white samples for compositing/background handling, not as a single mixed coverage metric.

Split glyph/background ink stats evidence:

- Source change:
  - Updated `scripts/resource_browser_font_raster_stats.ps1`.
  - Existing `browser crop=...` and `aui crop=...` lines are preserved unchanged for historical comparisons.
  - Added `browser glyphOnly=...` and `aui glyphOnly=...` lines.
  - New glyph-only filter uses `thresholdDarkness=20`; pixels above the old ink threshold but not above this glyph darkness threshold are reported as `backgroundDeltaInk`.
- Commands:
  - Baseline projection stats: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-projection-baseline-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.01.07.png' -OutLog 'run/resource-browser-font-raster-samples-projection-split-ink.log'`.
  - Alpha-cap `220` stats: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-alpha-cap-220-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_15.54.58.png' -OutLog 'run/resource-browser-font-raster-samples-alpha-cap-220-split-ink.log'`.
  - Attempted alpha-scale `0.88` split stats, but `run/screenshots/aui/2026-07-15_15.48.59.png` had already been removed by screenshot retention; do not cite split stats for that run without rerunning it.
- Baseline split result:
  - `arial13White`: browser glyphOnly ink `624`, coverage `0.011737`, avg RGB `174.73,171.52,173.3`, bounds `(200,766,97,17)`, backgroundDeltaInk `100`; AUI glyphOnly ink `631`, coverage `0.011631`, avg RGB `170.41,170.41,170.41`, bounds `(200,766,97,18)`, backgroundDeltaInk `20`.
  - `arial13Fafafa`: browser glyphOnly ink `623`, coverage `0.011718`, avg RGB `173.6,170.58,172.29`, bounds `(200,847,97,17)`, backgroundDeltaInk `1234`; AUI glyphOnly ink `610`, coverage `0.011244`, avg RGB `170.85,170.85,170.85`, bounds `(200,846,97,18)`, backgroundDeltaInk `1107`.
  - `arial12White`: browser glyphOnly ink `1786`, coverage `0.033594`, avg RGB `173.36,170.24,173.1`, bounds `(200,927,361,15)`, backgroundDeltaInk `344`; AUI glyphOnly ink `2259`, coverage `0.041641`, avg RGB `174.4,174.4,174.4`, bounds `(200,927,363,16)`, backgroundDeltaInk `28`.
  - `arial12Fafafa`: browser glyphOnly ink `1781`, coverage `0.033499`, avg RGB `172.27,169.28,172.08`, bounds `(200,1008,361,15)`, backgroundDeltaInk `1482`; AUI glyphOnly ink `1949`, coverage `0.035926`, avg RGB `170.87,170.87,170.87`, bounds `(200,1008,363,15)`, backgroundDeltaInk `1144`.
- Alpha-cap `220` split result:
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI glyphOnly coverage `0.011631`, avg RGB `179.9,179.9,179.9`.
  - `arial13Fafafa`: browser glyphOnly coverage `0.011718`, avg RGB `173.6,170.58,172.29`; AUI glyphOnly coverage `0.011226`, avg RGB `179.45,179.45,179.45`.
  - `arial12White`: browser glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`; AUI glyphOnly coverage `0.041567`, avg RGB `183.64,183.64,183.64`.
  - `arial12Fafafa`: browser glyphOnly coverage `0.033499`, avg RGB `172.27,169.28,172.08`; AUI glyphOnly coverage `0.035816`, avg RGB `179.71,179.71,179.71`.
- Interpretation:
  - The old off-white full-crop coverage was mixing two different signals. Split stats show large `backgroundDeltaInk` on off-white samples for both browser and AUI, while `glyphOnly` bounds remain narrow and text-shaped.
  - Baseline AUI is already close to browser glyph-only coverage for `arial13White`, but it is darker.
  - Baseline AUI over-covers `arial12White` strongly in glyph-only terms (`0.041641` vs browser `0.033594`), so the 12px issue is not just full-crop background accounting.
  - `alphaCap=220` improves perceived/old full-crop average ink, but under split metrics it makes glyph-only RGB too light for 13px and still leaves 12px over-covered. Do not promote it.
  - Future acceptance must report both old crop stats and split glyph/background stats. Old off-white full-crop coverage alone is no longer acceptable evidence for glyph raster parity.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
  - `compileJava` was not rerun in this evidence-only continuation because only the PowerShell stats script and TODO text changed during this goal turn.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Re-run or generate split stats for the most promising candidate runs only when their screenshots are available. Use split glyph-only metrics to decide whether a renderer change actually improves Chromium parity.
  - The next likely diagnostic is not another alpha scalar; it is a font-size/sample-specific source coverage investigation, especially why AUI `12px` explicit Arial glyph-only coverage is much higher than Chromium while `13px` explicit Arial is close.

Glyph darkness threshold sweep evidence:

- Source change:
  - Updated `scripts/resource_browser_font_raster_stats.ps1`.
  - Added optional `-GlyphDarknessThresholds` comma-list support.
  - Existing `crop` and default `glyphOnly thresholdDarkness=20` lines remain unchanged when the new parameter is omitted.
  - When the parameter is present, the script emits paired `browser glyphSweep=...` and `aui glyphSweep=...` lines for each requested threshold.
- Browser oracle type:
  - Pixel-sample, reusing the existing Chromium fixture oracle in `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - The browser oracle is still valid because the fixture, viewport policy, browser screenshot, and browser metrics log were not changed in this subtask.
- Hypothesis tested:
  - The apparent `arial12White` AUI over-coverage might be an artifact of using `thresholdDarkness=20` for glyph-only segmentation.
  - Rejection metric: if AUI remains materially over-covered across thresholds `12,16,20,24,28,32,40`, then the mismatch is not just the chosen glyph-only threshold.
- Commands:
  - Baseline projection sweep: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-projection-baseline-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.01.07.png' -OutLog 'run/resource-browser-font-raster-samples-projection-threshold-sweep.log' -GlyphDarknessThresholds '12,16,20,24,28,32,40'`.
  - Alpha-cap `220` sweep: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-alpha-cap-220-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_15.54.58.png' -OutLog 'run/resource-browser-font-raster-samples-alpha-cap-220-threshold-sweep.log' -GlyphDarknessThresholds '12,16,20,24,28,32,40'`.
- Logs:
  - Baseline projection sweep: `run/resource-browser-font-raster-samples-projection-threshold-sweep.log`.
  - Alpha-cap `220` sweep: `run/resource-browser-font-raster-samples-alpha-cap-220-threshold-sweep.log`.
- Baseline projection result:
  - `arial13White` stays close in coverage but AUI remains darker at most thresholds:
    - threshold `12`: browser `0.012320`, AUI `0.011926`.
    - threshold `20`: browser `0.011737`, AUI `0.011631`.
    - threshold `40`: browser `0.010157`, AUI `0.010747`.
  - `arial13Fafafa` AUI is consistently under-covered through the sweep:
    - threshold `12`: browser `0.012320`, AUI `0.011613`.
    - threshold `20`: browser `0.011718`, AUI `0.011244`.
    - threshold `40`: browser `0.010101`, AUI `0.009143`.
  - `arial12White` AUI remains strongly over-covered at every threshold:
    - threshold `12`: browser `0.037788`, AUI `0.042138`.
    - threshold `20`: browser `0.033594`, AUI `0.041641`.
    - threshold `40`: browser `0.029587`, AUI `0.038249`.
  - `arial12Fafafa` is mixed and threshold-sensitive:
    - threshold `12`: browser `0.037788`, AUI `0.036811`.
    - threshold `20`: browser `0.033499`, AUI `0.035926`.
    - threshold `40`: browser `0.029550`, AUI `0.028424`.
- Alpha-cap `220` result:
  - `arial13White` coverage stays close but AUI glyph RGB is too light compared with Chromium:
    - threshold `20`: browser coverage `0.011737`, AUI `0.011631`; browser avg RGB `174.73,171.52,173.3`, AUI `179.9,179.9,179.9`.
  - `arial12White` still over-covers across the sweep:
    - threshold `12`: browser `0.037788`, AUI `0.042101`.
    - threshold `20`: browser `0.033594`, AUI `0.041567`.
    - threshold `40`: browser `0.029587`, AUI `0.034581`.
  - `arial12Fafafa` moves closer only at high thresholds, while glyph RGB remains too light:
    - threshold `20`: browser coverage `0.033499`, AUI `0.035816`; browser avg RGB `172.27,169.28,172.08`, AUI `179.71,179.71,179.71`.
    - threshold `40`: browser coverage `0.029550`, AUI `0.028018`.
- Interpretation:
  - The `arial12White` over-coverage survives the whole threshold sweep in both baseline and alpha-cap runs. This rejects the idea that the mismatch is only the fixed `thresholdDarkness=20` segmentation rule.
  - `alphaCap=220` continues to be diagnostic only. It makes several glyph averages too light and does not solve `arial12White` source coverage.
  - The next useful direction is font-size/source coverage generation or browser font metric parity for explicit Arial `12px`, not another global alpha scalar and not a `resource.html` CSS workaround.
  - Full `devtools/resource.html` was intentionally not run because this subtask was evidence/script work and did not produce a renderer fix that improves the minimal fixture.
- Verification:
  - Both sweep commands completed and wrote logs.
  - `compileJava` was not rerun because only the PowerShell stats script and TODO text changed.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Investigate explicit Arial `12px` source coverage generation: compare browser Canvas/TextMetrics actual bounding boxes and AUI Java2D glyph/source bounds for `SELECT FILE TO VIEW DETAILS` at the same CSS font, letter spacing, DPR, and target physical raster size.

Source coverage and TextMetrics diagnostic evidence:

- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded source-bounds logging: `APRICITYUI_FONT_RASTER_LOG_SOURCE_BOUNDS=1` / `apricityui.fontRaster.logSourceBounds=true`.
  - The diagnostic logs Java2D/AWT run count, `FontMetrics` width, spacing-adjusted width, `GlyphVector` visual/logical bounds, image size, pad, baseline, and active `FontRenderContext` antialias/fractional-metrics flags.
  - Default rendering remains unchanged; this is a diagnostic log only.
- Browser oracle:
  - Type: source-raster + TextMetrics.
  - Command: `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-last.log`.
  - Result log: `run/resource-browser-font-source-raster-browser-last.log`.
  - Chromium viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, `APRICITYUI_FONT_RASTER_LOG_SOURCE_BOUNDS=1`, alpha scale/cap unset, interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-bounds-last.log`.
  - AUI log: `run/resource-browser-font-raster-aui-source-bounds-last.log`.
  - AUI screenshots: newest captured set includes `run/screenshots/aui/2026-07-15_16.22.51.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-bounds-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.22.51.png' -OutLog 'run/resource-browser-font-raster-samples-source-bounds.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-bounds.log`.
- Browser source/TextMetrics result:
  - `arial13White`: `measureText.width=57.5654296875`, `actualBoundingBoxRight=56.89453125`, `actualBoundingBoxAscent=10`, physical canvas `114x39`, source ink `755`, source coverage `0.16981556455240665`, source avg alpha `161.9337748344371`, source bounds `7,7,99,17`, composited coverage `0.1664417453891138`, composited avg RGB `183.97702702702702`.
  - `arial12White`: `measureText.width=208.599609375`, `actualBoundingBoxRight=207.595703125`, `actualBoundingBoxAscent=9`, physical canvas `378x35`, source ink `2167`, source coverage `0.1637944066515495`, source avg alpha `163.93262574988464`, source bounds `7,8,363,15`, composited coverage `0.16031746031746033`, composited avg RGB `182.84111268269683`.
  - `arial12Fafafa`: same physical source alpha as `arial12White`; composited avg RGB `181.4163130598774`.
- AUI source/TextMetrics result:
  - `arial13White`: source-bounds log for `5 ITEMS` reports image `103x31`, pad `2`, baseline `23`, metrics `ascent=21`, `descent=5`, `leading=1`, `height=27`, `awtWidth=88.0`, `awtWidthWithSpacing=100.25`, visual bounds `2.9375,6.4375,96.546875,16.84375`, logical bounds `2.0,2.405029,98.5,26.160278`, `fontRenderContextAa=true`, `fontRenderContextFm=false`.
  - `arial12White`: source-bounds log for `SELECT FILE TO VIEW DETAILS` reports image `368x29`, pad `2`, baseline `21`, metrics `ascent=19`, `descent=5`, `leading=1`, `height=25`, `awtWidth=318.0`, `awtWidthWithSpacing=365.25`, visual bounds `2.9375,5.703125,361.46875,15.546875`, logical bounds `2.0,1.989258,363.5,24.147949`, `fontRenderContextAa=true`, `fontRenderContextFm=false`.
  - `arial12White` texture stats in the same run: image `368x29`, source ink `1768`, source coverage `0.165667`, source avg alpha `211.473416`, source bounds `3,6,362,15`, colored subpixel pixels `0`.
  - Projection for `arial12White`: source ink bounds map to physical bounds `200.180001,927.481278,362.000008,15.000001`, matching the screenshot crop location.
- Matched screenshot stats:
  - `arial12White`: browser glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`; AUI glyphOnly coverage `0.041641`, avg RGB `174.4,174.4,174.4`.
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI glyphOnly coverage `0.011631`, avg RGB `170.41,170.41,170.41`.
- Interpretation:
  - The source geometry/width chain is mostly aligned for explicit Arial `12px`: Chromium physical source bounds are `363x15`, while AUI source ink bounds are `362x15`; Chromium `measureText.width * DPR` is about `365.0`, and AUI `awtWidthWithSpacing` is `365.25`.
  - The remaining `arial12White` mismatch is therefore not primarily glyph width, TextMetrics, projection, or CSS font-size mapping.
  - AUI source alpha is much harder than Chromium for the same physical-size sample: Chromium source avg alpha `163.93` over `2167` alpha pixels, AUI source avg alpha `211.47` over `1768` alpha pixels. This can explain why thresholded screenshot glyph coverage over-covers even though source bounds align.
  - This refines the next diagnostic away from font geometry and toward source alpha distribution/CDF or antialiasing coverage generation. Do not promote a global alpha scale/cap; previous alpha scalar/cap tests already failed mixed samples.
  - Full `devtools/resource.html` was intentionally not run because this subtask added diagnostics and evidence, not a renderer fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java diagnostic edit.
  - AUI fixture run completed and wrote `run/resource-browser-font-raster-aui-source-bounds-last.log`.
  - Matched stats run completed and wrote `run/resource-browser-font-raster-samples-source-bounds.log`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Add a browser-vs-AUI source alpha histogram/CDF diagnostic for explicit Arial `12px` and `13px`, using the existing Chromium source-raster ImageData and AUI source texture/mask output. The goal is to identify whether Java2D antialiasing produces too many high-alpha interior pixels or too few low-alpha edge pixels compared with Chromium.

Direct source alpha histogram/CDF evidence:

- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html` now emits `alphaBins` and `alphaCdf` for source-alpha ImageData.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded direct source alpha histogram logging: `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1` / `apricityui.fontRaster.logAlphaHistogram=true`.
  - Added `scripts/resource_browser_font_source_alpha_histogram.ps1` as an auxiliary mask-based comparison script. It is useful for exported-mask inspection, but the direct `FontDrawer` alpha histogram log is the authoritative AUI source-alpha evidence because PNG grayscale export/readback can alter low-alpha counts.
- Browser oracle:
  - Type: source-raster alpha histogram/CDF.
  - Command: `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-last.log`.
  - Result log: `run/resource-browser-font-source-raster-browser-last.log`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, alpha scale/cap/export/projection/source-bounds/interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-histogram-last.log`.
  - AUI log: `run/resource-browser-font-raster-aui-alpha-histogram-last.log`.
  - AUI screenshots: newest captured set includes `run/screenshots/aui/2026-07-15_16.33.39.png`.
- Browser result:
  - `arial13White`: source image `114x39`, ink `755`, coverage `0.169816`, avg alpha `161.933775`, bounds `7,7,99,17`, bins `1-31=122,32-63=35,64-95=74,96-127=54,128-159=21,160-191=55,192-223=83,224-255=311`, CDF `le32=0.161589,le64=0.207947,le96=0.307285,le128=0.380132,le160=0.405298,le192=0.544371,le224=0.594702,le240=0.630464`.
  - `arial12White`: source image `378x35`, ink `2167`, coverage `0.163794`, avg alpha `163.932626`, bounds `7,8,363,15`, bins `1-31=330,32-63=105,64-95=177,96-127=178,128-159=81,160-191=178,192-223=88,224-255=1030`, CDF `le32=0.152746,le64=0.200738,le96=0.282418,le128=0.367328,le160=0.401938,le192=0.493309,le224=0.530688,le240=0.704199`.
- AUI direct result:
  - `arial13White`: source image `103x31`, ink `588`, coverage `0.184153`, avg alpha `205.921769`, bounds `3,6,97,17`, bins `1-31=41,32-63=29,64-95=18,96-127=25,128-159=24,160-191=21,192-223=22,224-255=408`, CDF `le32=0.069728,le64=0.119048,le96=0.14966,le128=0.192177,le160=0.232993,le192=0.268707,le224=0.312925,le240=0.35034`.
  - `arial12White`: source image `368x29`, ink `1768`, coverage `0.165667`, avg alpha `211.473416`, bounds `3,6,362,15`, bins `1-31=117,32-63=70,64-95=57,96-127=53,128-159=61,160-191=55,192-223=72,224-255=1283`, CDF `le32=0.067308,le64=0.1069,le96=0.138575,le128=0.167986,le160=0.204751,le192=0.233597,le224=0.274887,le240=0.312217`.
- Interpretation:
  - The alpha distribution mismatch is now explicit. AUI source alpha is much harder than Chromium, with far more high-alpha pixels and far fewer low/mid alpha pixels.
  - For `arial12White`, Chromium has `1030/2167` pixels in `224-255` and `le240=0.704199`; AUI has `1283/1768` pixels in `224-255` and only `le240=0.312217`. AUI is dominated by near-opaque pixels.
  - For `arial13White`, Chromium has `311/755` pixels in `224-255`; AUI has `408/588`. The same hardness pattern appears at `13px`, even though final glyph-only coverage is closer there.
  - This explains why previous global alpha scale/cap experiments were mixed: the mismatch is distribution-shaped, not a simple average-alpha or max-alpha problem.
  - The next diagnostic should be a guarded non-linear alpha remap or CDF-matching transform tested only on the minimal fixture. It must be rejected if it improves `arial12White` by regressing `arial13White`, generic/fallback samples, or off-white compositing.
  - Full `devtools/resource.html` was intentionally not run because this subtask added diagnostics and evidence, not a renderer fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java diagnostic edit.
  - Browser source-raster command completed and wrote `run/resource-browser-font-source-raster-browser-last.log`.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-alpha-histogram-last.log`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Add a guarded non-linear source-alpha remap diagnostic, ideally parameterized by a small piecewise curve or CDF-inspired curve, then validate it only on `resource-browser-font-raster.html` with split glyph/background stats before any full-page `devtools/resource.html` run.

Current continuation block for goal automation:

```powershell
# Next diagnostic: source coverage generation, not global alpha remap.
# Use the direct alpha histogram evidence above as the reason:
# AUI has too many 224-255 pixels and too few low/mid alpha pixels.
# Test only on tests/resource-browser-font-raster.html first.
# Reject any path that fixes source CDF but expands ink bounds, over-covers
# arial12White, or regresses arial13/generic/fallback/off-white samples.
```

Current continuation acceptance rule:

- Treat the browser crop stats in `run/resource-browser-font-raster-browser-background-pairs-last.log` as the expected result.
- Treat exported AUI masks under `run/font-raster-masks` as AUI source evidence only. They do not define expected coverage or antialiasing.
- Record only samples that map to the browser fixture acceptance crops, especially explicit Arial `13px`/`12px` white and off-white background pairs.
- The Chromium source-raster oracle, linear alpha-scale evidence, alpha-cap evidence, projection evidence, split glyph/background stats, threshold sweep, source-bounds/TextMetrics diagnostic, and direct source alpha histogram/CDF diagnostic now exist.
- The threshold sweep proved `arial12White` remains over-covered across thresholds. The source-bounds diagnostic then showed width/source geometry is mostly aligned. The direct alpha histogram shows AUI source alpha is too hard. Linear alpha scale, alpha cap, non-linear alpha remap, and source `oversample-2x` have now failed mixed samples, so route the next action to a different coverage generation path, not to page CSS or another global post-raster alpha transform.
- Do not run full `devtools/resource.html` until the minimal font-raster fixture moves toward Chromium without regression.
- Do not promote any guarded font-raster option unless the minimal fixture moves toward Chromium without regressing already-recorded browser-standard samples.

Guarded non-linear alpha-remap diagnostic evidence:

- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has a guarded source-alpha remap diagnostic: `APRICITYUI_FONT_RASTER_ALPHA_REMAP=<mode-or-points>` / `apricityui.fontRaster.alphaRemap=<mode-or-points>`.
  - Supported diagnostic forms include `soft-v1` and custom point lists such as `0:0,32:32,64:56,96:76,128:96,160:116,192:136,224:158,240:190,255:245`.
  - The alpha remap mode is included in raster/cache keys. Default rendering remains unchanged when the flag is unset or set to `off`/`default`/`none`.
- Browser oracle:
  - Type: `source-raster` + `pixel-sample`.
  - Reused browser source-raster oracle: `run/resource-browser-font-source-raster-browser-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because the fixture, browser viewport policy, font source, device scale, and crop policy were not changed in this subtask.
- Predeclared rejection metric:
  - Reject a remap if it improves explicit `arial12White` only by regressing `arial13White`, generic/fallback samples, off-white samples, or split glyph/background accounting.
  - Use split `glyphOnly thresholdDarkness=20` stats as the primary acceptance metric.
- Hypothesis tested:
  - A guarded non-linear remap can soften AUI's overly hard source alpha distribution enough to move explicit Arial `12px`/`13px` toward Chromium without breaking other recorded browser-standard samples.
- Soft-v1 AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_REMAP=soft-v1`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-remap-soft-v1-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_16.40.22.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-alpha-remap-soft-v1-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.40.22.png' -OutLog 'run/resource-browser-font-raster-samples-alpha-remap-soft-v1.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-alpha-remap-soft-v1.log`.
- Soft-v1 source-alpha result:
  - `arial13White`: AUI source avg alpha `195.098639`, bins `1-31=41,32-63=33,64-95=32,96-127=31,128-159=25,160-191=22,192-223=20,224-255=384`, `le240=0.389456`; Chromium source target remains avg alpha `161.933775`, `224-255=311/755`, `le240=0.630464`.
  - `arial12White`: AUI source avg alpha `201.190611`, bins `1-31=117,32-63=83,64-95=80,96-127=78,128-159=64,160-191=65,192-223=61,224-255=1220`, `le240=0.334276`; Chromium source target remains avg alpha `163.932626`, `224-255=1030/2167`, `le240=0.704199`.
- Soft-v1 split pixel result:
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI `0.011594`, avg RGB `174.8,174.8,174.8`.
  - `arial12White`: browser `0.033594`, avg RGB `173.36,170.24,173.1`; AUI `0.041124`, avg RGB `177.59,177.59,177.59`.
  - `arial12Fafafa`: browser `0.033499`; AUI `0.035429`.
  - `sans13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012553`, avg RGB `194.22,194.22,194.22`.
  - `chakra13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012055`, avg RGB `194.13,194.13,194.13`.
- Custom-strong AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_ALPHA_REMAP=0:0,32:32,64:56,96:76,128:96,160:116,192:136,224:158,240:190,255:245`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-alpha-remap-custom-strong-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_16.41.48.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-alpha-remap-custom-strong-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.41.48.png' -OutLog 'run/resource-browser-font-raster-samples-alpha-remap-custom-strong.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-alpha-remap-custom-strong.log`.
- Custom-strong source-alpha result:
  - `arial13White`: AUI source avg alpha `188.552721`, bins `1-31=41,32-63=36,64-95=36,96-127=39,128-159=32,160-191=22,192-223=22,224-255=360`, `le240=0.39966`.
  - `arial12White`: AUI source avg alpha `195.184955`, bins `1-31=117,32-63=89,64-95=91,96-127=95,128-159=94,160-191=66,192-223=31,224-255=1185`, `le240=0.346154`.
  - This is softer than the AUI baseline but still far from Chromium's source CDF, especially `arial12White` Chromium `le240=0.704199`.
- Custom-strong split pixel result:
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI `0.011465`, avg RGB `176.73,176.73,176.73`.
  - `arial12White`: browser `0.033594`, avg RGB `173.36,170.24,173.1`; AUI `0.040774`, avg RGB `179.35,179.35,179.35`.
  - `arial12Fafafa`: browser `0.033499`, avg RGB `172.27,169.28,172.08`; AUI `0.035078`, avg RGB `175.74,175.74,175.74`.
  - `sans13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012166`, avg RGB `196.82,196.82,196.82`.
  - `chakra13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.011650`, avg RGB `196.62,196.62,196.62`.
  - `chakra12White`: browser `0.032409`, avg RGB `181.26,178.41,180.74`; AUI `0.038710`, avg RGB `194.57,194.57,194.57`.
- Interpretation:
  - Non-linear alpha remap is useful as a diagnostic because it confirms the source-alpha hardness axis affects final glyph stats.
  - Both tested remaps fail the rejection metric. They do not fix `arial12White` over-coverage, and they regress generic/fallback `13px` and `12px` samples by making glyph coverage too high and/or glyph RGB too light.
  - The source histogram also remains too far from Chromium after remap. For `arial12White`, custom-strong only moves AUI `le240` from `0.312217` to `0.346154`, while Chromium is `0.704199`.
  - Do not promote `APRICITYUI_FONT_RASTER_ALPHA_REMAP=soft-v1` or the custom point curve.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still fails and has regressions.
- Verification:
  - Soft-v1 and custom-strong AUI fixture runs completed and wrote their logs.
  - Split stats completed for both runs.
  - `compileJava` was run after the guarded `FontDrawer` diagnostic existed and passed.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Stop pursuing global post-raster alpha remap curves as a likely default. The next task should inspect or prototype a different source coverage generation path: either a browser-like grayscale coverage model, an alternate glyph raster backend, or a per-font-size coverage reconstruction validated against Chromium source-raster CDF before final screenshot stats.

Guarded source oversampling diagnostic evidence:

- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has a guarded source coverage generation experiment: `APRICITYUI_FONT_RASTER_SOURCE=oversample-2x` / `apricityui.fontRaster.source=oversample-2x`.
  - The diagnostic keeps the final target texture size unchanged, draws the glyph source into a 2x temporary image with 2x font size/spacing/position, then bilinear-downsamples into the normal target texture.
  - Default rendering remains unchanged because unset source mode still resolves to `draw-string`.
- Browser oracle:
  - Type: `source-raster` + `pixel-sample`.
  - Reused source oracle: `run/resource-browser-font-source-raster-browser-last.log`.
  - Reused crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because the fixture, Chromium screenshot, viewport policy, font source, device scale, animation state, and crop policy were not changed in this subtask.
- Predeclared rejection metric:
  - Reject the source coverage path if it moves the source CDF toward Chromium but expands ink bounds, worsens `arial12White` over-coverage, or regresses explicit Arial `13px`, generic/fallback samples, or off-white compositing under split glyph/background stats.
- Hypothesis tested:
  - Rendering the glyph source at 2x and downsampling to the same target texture might generate browser-like low/mid alpha coverage without a global post-raster alpha curve.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=oversample-2x`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, alpha scale/cap/remap and interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-oversample-2x-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_16.50.37.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-oversample-2x-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.50.37.png' -OutLog 'run/resource-browser-font-raster-samples-source-oversample-2x.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-oversample-2x.log`.
- Source-alpha result:
  - `arial13White`: AUI source image `103x31`, ink `679`, avg alpha `180.795287`, bounds `3,6,98,18`, bins `1-31=44,32-63=25,64-95=36,96-127=28,128-159=163,160-191=25,192-223=22,224-255=336`, `le240=0.533137`; Chromium source target remains image `114x39`, ink `755`, avg alpha `161.933775`, bounds `7,7,99,17`, `le240=0.630464`.
  - `arial12White`: AUI source image `368x29`, ink `1947`, avg alpha `195.304571`, bounds `3,6,365,15`, bins `1-31=131,32-63=66,64-95=62,96-127=68,128-159=315,160-191=77,192-223=57,224-255=1171`, `le240=0.426297`; Chromium source target remains image `378x35`, ink `2167`, avg alpha `163.932626`, bounds `7,8,363,15`, `le240=0.704199`.
- Split pixel result:
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI `0.013346`, avg RGB `182.54,182.54,182.54`.
  - `arial12White`: browser `0.033594`, avg RGB `173.36,170.24,173.1`; AUI `0.042949`, avg RGB `177.95,177.95,177.95`.
  - `arial12Fafafa`: browser `0.033499`, avg RGB `172.27,169.28,172.08`; AUI `0.036424`, avg RGB `173.74,173.74,173.74`.
  - `sans13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.013180`, avg RGB `190.05,190.05,190.05`.
  - `chakra13`: browser `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012756`, avg RGB `190.51,190.51,190.51`.
  - `chakra12White`: browser `0.032409`, avg RGB `181.26,178.41,180.74`; AUI `0.041770`, avg RGB `189.46,189.46,189.46`.
- Interpretation:
  - `oversample-2x` proves that changing source coverage generation can move the alpha CDF much more than post-raster remap: `arial13White le240` moves from baseline AUI `0.35034` to `0.533137`, closer to Chromium `0.630464`; `arial12White le240` moves from baseline AUI `0.312217` to `0.426297`, closer to Chromium `0.704199`.
  - It still fails the screenshot acceptance metric. The generated source ink expands and final glyph-only coverage over-shoots more broadly: `arial12White` worsens from prior AUI `0.041641`/`0.040774` range to `0.042949`, and `arial13White`, `sans13`, `chakra13`, and `chakra12White` all over-cover versus Chromium.
  - Do not promote `APRICITYUI_FONT_RASTER_SOURCE=oversample-2x`.
  - This narrows the next investigation: source coverage must be changed without expanding visual bounds or adding extra glyph ink. A more promising route is to compare exact browser glyph mask positioning/quantization, then prototype a coverage generation path that preserves Chromium source bounds before matching the CDF.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture regressed.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded source mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-oversample-2x-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-oversample-2x.log`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Add a source-mask geometry/position diagnostic that compares Chromium and AUI source ink bounds at the per-sample and per-glyph level, especially `arial12White`. The next useful rejection metric is whether a new coverage path preserves Chromium-like source bounds before matching CDF; do not try another global post-raster alpha transform.

Per-glyph source geometry diagnostic evidence:

- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html` now emits `glyphGeometry` for `css1x` and `physicalDpr`: codepoint, cursor, physical cursor, measured advance, per-glyph source ink bounds, ink count, and average alpha.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` source-bounds logging now appends a compact `glyphs=[...]` list with codepoint, cursor, advance, and visual bounds for each glyph.
  - These are diagnostics only. Default rendering remains unchanged.
- Browser oracle:
  - Type: `source-raster` + per-glyph geometry.
  - Command: `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-glyph-geometry-last.log`.
  - Result log: `run/resource-browser-font-source-raster-browser-glyph-geometry-last.log`.
  - Chromium viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
  - Note: the first implementation double-counted `letterSpacing` in browser per-glyph advances because Chromium `canvas.measureText(glyph)` already reflects `context.letterSpacing`; this was corrected before recording the evidence below.
- Predeclared rejection metric:
  - If per-glyph source advances and bounds are already within normal rounding/pad differences, reject geometry/letter-spacing/subpixel positioning as the main cause and route the next task to glyph coverage generation.
  - If per-glyph geometry differs materially, fix geometry before further CDF shaping.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_SOURCE_BOUNDS=1`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, source/alpha scale/cap/remap and interaction/prompt unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-glyph-geometry-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_16.56.30.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-glyph-geometry-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_16.56.30.png' -OutLog 'run/resource-browser-font-raster-samples-source-glyph-geometry.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-glyph-geometry.log`.
- `arial12White` browser geometry result:
  - Browser box: CSS `width=216`, `height=20`, `pad=4`, `baseline=13`, `measureText.width=208.599609375`, `actualBoundingBoxRight=207.595703125`.
  - Browser physical source: image `378x35`, source bounds `7,8,363,15`, ink `2167`, avg alpha `163.932626`, `le240=0.704199`.
  - Browser per-glyph physical final cursor: `373.950798`.
  - Browser representative advances/bounds:
    - `S` index `0`: cursor `6.999`, advance `15.755`, bounds `7,8,14,15`.
    - `E` index `1`: cursor `22.755`, advance `15.755`, bounds `24,8,12,15`.
    - `L` index `2`: cursor `38.510`, advance `13.428`, bounds `39,8,11,15`.
    - `W` index `18`: cursor `245.031`, advance `21.569`, bounds `245,8,20,15`.
    - final `S` index `26`: cursor `358.195`, advance `15.755`, bounds `359,8,13,15`.
- `arial12White` AUI geometry result:
  - AUI source: image `368x29`, pad `2`, baseline `21`, source bounds `3,6,362,15`, source visual bounds `2.9375,5.703125,361.46875,15.546875`, logical bounds `2.0,1.989258,363.5,24.147949`.
  - AUI widths: `awtWidth=318.0`, `awtWidthWithSpacing=365.25`; after accounting for coordinate/pad policy, this is close to browser physical source width `363` and browser physical final cursor `373.95` with browser pad `7`.
  - AUI representative advances/bounds:
    - `S` index `0`: cursor `2.0`, advance `15.75`, visual `2.9375,5.71875,11.96875,15.53125`.
    - `E` index `1`: cursor `17.75`, advance `15.75`, visual `19.40625,5.96875,11.21875,15.03125`.
    - `L` index `2`: cursor `33.5`, advance `13.75`, visual `35.03125,5.96875,9.40625,15.03125`.
    - `W` index `18`: cursor `238.5`, advance `22.75`, visual `238.75,5.96875,19.328125,15.03125`.
    - final `S` index `26`: cursor `351.5`, advance `15.75`, visual `352.4375,5.71875,11.96875,15.53125`.
- Matched screenshot result:
  - `arial12White`: browser glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`; AUI `0.041641`, avg RGB `174.4,174.4,174.4`.
  - `arial13White`: browser `0.011737`, avg RGB `174.73,171.52,173.3`; AUI `0.011631`, avg RGB `170.41,170.41,170.41`.
  - `arial12Fafafa`: browser `0.033499`, avg RGB `172.27,169.28,172.08`; AUI `0.035926`, avg RGB `170.87,170.87,170.87`.
- Interpretation:
  - Per-glyph advance and source positioning are close enough that they no longer look like the primary cause of `arial12White` over-coverage. Representative advances differ by fractions of a physical pixel for common glyphs (`S`, `E`) and by about `1.18` physical px for `W`, but the total source width and source bounds remain aligned.
  - AUI source bounds are not too wide: browser source bounds are `363x15`; AUI source bounds are `362x15` with visual bounds width `361.47`. The final screenshot over-coverage is therefore not explained by a wider string or gross per-glyph placement drift.
  - The remaining mismatch is inside glyph coverage generation: AUI still has far fewer low/mid-alpha pixels and far more near-opaque pixels than Chromium for nearly the same geometry.
  - Do not spend another goal turn on letter-spacing, source width, pad, or source position unless a new browser fixture contradicts this evidence.
  - Full `devtools/resource.html` was intentionally not run because this subtask added diagnostics/evidence and did not produce a renderer fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java diagnostic edit.
  - Browser source-raster command completed and wrote `run/resource-browser-font-source-raster-browser-glyph-geometry-last.log`.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-glyph-geometry-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-glyph-geometry.log`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Prototype or inspect a different glyph coverage generator that preserves the now-verified source geometry. A concrete next diagnostic is to add a coverage-only source mode that keeps current AWT glyph positions and bounds but derives alpha coverage from a browser-like mask model or another raster backend, then validate source CDF before screenshot stats.

Guarded outline coverage diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused Chromium source-raster oracle and crop oracle because the fixture, browser screenshot, viewport policy, font source, device scale, animation state, and crop policy were unchanged.
  - Source target remains Chromium `arial12White`: image `378x35`, ink `2167`, avg alpha `163.932626`, bounds `7,8,363,15`, bins `1-31=330,32-63=105,64-95=177,96-127=178,128-159=81,160-191=178,192-223=88,224-255=1030`, `le240=0.704199`.
  - Split crop target remains Chromium `arial12White` glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`, ink bounds `361x15`.
- Browser-standard rule matched:
  - CSS text rasterization must match Chromium-observed source alpha distribution and final glyph-only pixel coverage. AUI output cannot define the target.
- Predeclared rejection metric:
  - Reject the diagnostic as a default if it moves the CDF toward Chromium by expanding ink bounds, increasing final glyph-only over-coverage, or regressing other fixture samples.
- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded source coverage generation mode `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x` / `apricityui.fontRaster.source=outline-coverage-4x`.
  - The mode keeps existing AWT glyph cursor/spacing geometry, builds glyph outlines with the active `FontRenderContext`, and derives alpha from 4x4 subpixel shape coverage. Default rendering remains unchanged.
- Hypothesis tested:
  - A manual outline coverage generator might preserve AWT geometry while creating Chromium-like low/mid alpha coverage, avoiding global post-raster alpha transforms.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.07.58.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.07.58.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x.log`.
- AUI source-alpha result:
  - `arial13White`: source image `103x31`, ink `671`, avg alpha `177.515648`, bounds `3,6,97,18`, bins `1-31=22,32-63=33,64-95=89,96-127=25,128-159=102,160-191=80,192-223=29,224-255=291`, `le240=0.602086`. Chromium target remains image `114x39`, ink `755`, avg alpha `161.933775`, bounds `7,7,99,17`, `le240=0.630464`.
  - `arial12White`: source image `368x29`, ink `1871`, avg alpha `190.719401`, bounds `3,5,362,17`, bins `1-31=50,32-63=87,64-95=130,96-127=69,128-159=252,160-191=300,192-223=98,224-255=885`, `le240=0.556387`. Chromium target remains image `378x35`, ink `2167`, avg alpha `163.932626`, bounds `7,8,363,15`, `le240=0.704199`.
- Split pixel result:
  - `arial13White`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`; AUI `0.013401`, avg RGB `184.72,184.72,184.72`, ink bounds `97x18`.
  - `arial12White`: browser glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`; AUI `0.042820`, avg RGB `181.93,181.93,181.93`, ink bounds `362x18`.
  - `arial12Fafafa`: browser glyphOnly coverage `0.033499`, avg RGB `172.27,169.28,172.08`; AUI `0.036664`, avg RGB `177.76,177.76,177.76`, ink bounds `362x17`.
  - `sans13`: browser glyphOnly coverage `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012829`, avg RGB `187.94,187.94,187.94`, ink bounds `94x19`.
  - `chakra13`: browser glyphOnly coverage `0.010402`, avg RGB `178.07,176.94,180.12`; AUI `0.012406`, avg RGB `188.49,188.49,188.49`.
  - `chakra12White`: browser glyphOnly coverage `0.032409`, avg RGB `181.26,178.41,180.74`; AUI `0.041714`, avg RGB `187.65,187.65,187.65`.
- Interpretation:
  - `outline-coverage-4x` confirms that coverage generation is the right layer to investigate: `arial12White le240` improves from baseline AUI `0.312217` and `oversample-2x` `0.426297` to `0.556387`, closer to Chromium `0.704199`.
  - It still fails the predeclared rejection metric. The source and final glyph bounds expand vertically: `arial12White` source bounds become `362x17` versus Chromium `363x15`, and final glyph-only ink bounds become `362x18` versus Chromium `361x15`.
  - Final split stats regress or remain over-covered across the fixture. `arial12White` AUI glyphOnly coverage `0.042820` is still far above Chromium `0.033594`, and `arial13White`, `sans13`, `chakra13`, and `chakra12White` also over-cover.
  - Do not promote `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture fails the rejection metric.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded source mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x` diagnostic only.
  - The next source-coverage diagnostic must preserve Chromium-like vertical bounds before CDF matching. A concrete next step is to inspect Chromium canvas source rasterization y-position/baseline quantization and compare it against AUI outline/source pixel rows; then prototype a coverage generator or row-constrained coverage pass that matches `arial12White` `15px` source/final ink height before optimizing CDF.

Source row-profile diagnostic evidence:

- Browser oracle type:
  - `source-raster`.
  - The browser fixture was intentionally rerun because this subtask adds row-profile fields to the source-raster oracle.
- Browser-standard rule matched:
  - Chromium source raster row occupancy and per-row alpha distribution define the expected source mask. AUI row positions are implementation evidence only.
- Predeclared rejection metric:
  - Reject any new coverage generator if it cannot preserve Chromium-like source/final vertical ink height before CDF matching. For `arial12White`, the target source height is `15px`.
- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html` now emits `rowProfile` for each `sourceAlpha` and composited source image: row y, ink count, and average alpha.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` alpha histogram logging now appends `rows=[...]` with row y, ink count, and average alpha.
  - These are diagnostics only. Default rendering remains unchanged.
- Browser command:
  - `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Result log: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
- Browser result:
  - Chromium `arial12White` physical source remains image `378x35`, bounds `7,8,363,15`, ink `2167`, avg alpha `163.932626`, `le240=0.704199`.
  - Chromium `arial12White` source rows occupy exactly y=`8..22`:
    - row ink/avgA: `8=203/193.463054`, `9=215/188.013953`, `10=125/138.112`, `11=115/143.191304`, `12=108/140.5`, `13=109/138.091743`, `14=165/174.666667`, `15=165/168.672727`, `16=113/137.548673`, `17=110/147.118182`, `18=118/142.728814`, `19=114/143.526316`, `20=120/144.741667`, `21=200/186.035`, `22=187/189.475936`.
- Hypothesis tested:
  - The `outline-coverage-4x` failure might be caused by source y-position/baseline quantization rather than coverage generation itself. Row-profile evidence should separate baseline row placement from extra edge rows.
- AUI baseline command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_SOURCE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-row-profile-draw-string-last.log`.
  - Result log: `run/resource-browser-font-raster-aui-row-profile-draw-string-last.log`.
- AUI outline command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-row-profile-outline-coverage-4x-last.log`.
  - Result log: `run/resource-browser-font-raster-aui-row-profile-outline-coverage-4x-last.log`.
- AUI row result:
  - AUI baseline `draw-string` `arial12White` remains image `368x29`, bounds `3,6,362,15`, ink `1768`, avg alpha `211.473416`, `le240=0.312217`.
  - AUI baseline rows occupy y=`6..20`, exactly `15px` tall like Chromium but shifted upward by `2px` in source-local coordinates: `6=179/221.318436`, `7=188/235.021277`, `8=106/182.490566`, `9=92/190.01087`, `10=77/205.961039`, `11=78/201.948718`, `12=135/214.77037`, `13=135/218.103704`, `14=85/203.211765`, `15=82/206.536585`, `16=83/204.915663`, `17=89/196.539326`, `18=107/181.542056`, `19=171/233.157895`, `20=161/218.677019`.
  - AUI `outline-coverage-4x` `arial12White` becomes image `368x29`, bounds `3,5,362,17`, ink `1871`, avg alpha `190.719401`, `le240=0.556387`.
  - AUI `outline-coverage-4x` rows occupy y=`5..21`; the extra height comes from two low-alpha edge rows: `5=16/52.0` and `21=16/49.0`. Interior rows y=`6..20` align with baseline occupancy but have softened alpha.
- Interpretation:
  - The baseline AUI source is not vertically taller than Chromium for `arial12White`; both have `15px` source row height. It is source-local shifted by `-2px` because AUI and browser use different pad/baseline boxes, but the height mismatch is not the baseline cause.
  - `outline-coverage-4x` failed vertical bounds because outline subpixel coverage admitted two extra low-alpha rows outside the baseline/Chromium 15-row occupancy.
  - This narrows the next diagnostic: keep the coverage-generation direction, but constrain coverage to Chromium-like/baseline row occupancy before comparing CDF and final split pixel stats.
  - Full `devtools/resource.html` was intentionally not run because this subtask added row-profile diagnostics and did not produce a candidate renderer fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the Java diagnostic edit.
  - Browser source-raster command completed and wrote `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - AUI baseline fixture command completed and wrote `run/resource-browser-font-raster-aui-row-profile-draw-string-last.log`.
  - AUI outline fixture command completed and wrote `run/resource-browser-font-raster-aui-row-profile-outline-coverage-4x-last.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep row-profile logging diagnostic only.
  - Prototype a guarded row-constrained coverage mode that starts from `outline-coverage-4x` but clips or gates coverage to the baseline/Chromium-like non-empty source rows for each text run, then validate whether `arial12White` keeps `15px` source/final ink height while moving `le240` toward Chromium without increasing split glyph-only coverage.

Guarded row-constrained outline coverage evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because the fixture, browser screenshot, viewport policy, font source, device scale, animation state, and crop policy were not changed in this subtask.
- Browser-standard rule matched:
  - `arial12White` target remains Chromium source bounds `7,8,363,15`, source rows y=`8..22`, source `le240=0.704199`, and split glyphOnly coverage `0.033594` with final ink bounds `361x15`.
- Predeclared rejection metric:
  - Reject the diagnostic as default if it cannot keep `arial12White` source and final glyph-only ink height at Chromium-like `15px`, or if it increases split glyph-only over-coverage versus Chromium.
- Source change:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded source coverage mode `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` / `apricityui.fontRaster.source=outline-coverage-4x-row-clamp`.
  - The mode starts from `outline-coverage-4x`, but first renders a baseline `draw-string` mask and only allows outline coverage into source rows that the baseline mask already occupies. Default rendering remains unchanged.
- Hypothesis tested:
  - If `outline-coverage-4x` only failed because of extra low-alpha top/bottom rows, row-clamping should preserve `15px` source height while keeping the improved source-alpha CDF.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.19.22.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.19.22.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp.log' -GlyphDarknessThresholds '20'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp.log`.
- AUI source-alpha result:
  - `arial12White`: source image `368x29`, ink `1839`, avg alpha `193.159326`, bounds `3,6,362,15`, rows y=`6..20`, bins `1-31=47,32-63=76,64-95=112,96-127=69,128-159=252,160-191=300,192-223=98,224-255=885`, `le240=0.548668`.
  - This preserves baseline AUI source height `15px`, fixes the `outline-coverage-4x` source height regression (`17px`), and remains closer to Chromium source CDF (`le240=0.704199`) than baseline AUI (`0.312217`) or `oversample-2x` (`0.426297`).
- Split pixel result:
  - `arial13`: browser glyphOnly coverage `0.011737`, avg RGB `174.73,171.52,173.3`, bounds `97x17`; AUI `0.012092`, avg RGB `181.03,181.03,181.03`, bounds `97x17`.
  - `arial12White`: browser glyphOnly coverage `0.033594`, avg RGB `173.36,170.24,173.1`, bounds `361x15`; AUI `0.042581`, avg RGB `181.26,181.26,181.26`, bounds `362x16`.
  - `arial12Fafafa`: browser glyphOnly coverage `0.033499`, avg RGB `172.27,169.28,172.08`, bounds `361x15`; AUI `0.036276`, avg RGB `177.22,177.22,177.22`, bounds `362x15`.
  - `sans13`: browser glyphOnly coverage `0.010402`, avg RGB `178.07,176.94,180.12`, bounds `92x17`; AUI `0.012829`, avg RGB `187.94,187.94,187.94`, bounds `94x19`.
  - `chakra12White`: browser glyphOnly coverage `0.032409`, avg RGB `181.26,178.41,180.74`, bounds `336x16`; AUI `0.041714`, avg RGB `187.65,187.65,187.65`, bounds `337x17`.
- Interpretation:
  - Row-clamping solves the source-height failure introduced by unconstrained outline coverage: `arial12White` source bounds return from `362x17` to `362x15`.
  - It does not solve final screenshot parity. `arial12White` final glyph-only bounds are still `362x16` rather than Chromium `361x15`, and final glyphOnly coverage remains far too high (`0.042581` vs `0.033594`).
  - Because source height is now correct but final height/coverage still fail, the next diagnostic should inspect source-to-final projection and final threshold/soft-alpha behavior for row-clamped text. The failure is no longer just source vertical bounds.
  - Do not promote `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture fails the rejection metric.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded source mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` diagnostic only.
  - Add or run a source-to-final row projection diagnostic for the row-clamped run: record source row bounds, draw scale, physical projected row bounds, and screenshot glyphOnly row bounds for `arial12White`. The next useful rejection metric is whether the extra final row comes from texture sampling/projection rounding, glyph-darkness threshold classification, or source alpha remaining too strong inside the correct source rows.

Source-to-final row projection diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask only interprets the existing row-clamped AUI projection run; fixture file, Chromium viewport, DPR, font source, animation state, zoom/meta mode, and crop policy were unchanged.
- Browser-standard rule matched:
  - Chromium `arial12White` source remains image `378x35`, bounds `7,8,363,15`, ink `2167`, avg alpha `163.932626`, `le240=0.704199`, rows y=`8..22`.
  - Chromium final split glyph-only target remains thresholdDarkness `20`, ink `1786`, coverage `0.033594`, bounds `(200,927,361,15)`, rows y=`927..941`.
- Predeclared rejection metric:
  - Reject row-clamped projection as a default fix if the AUI source/projection claims `15px` physical height but the final split glyph-only screenshot still occupies `16px`, especially when the extra row persists at thresholdDarkness `40` and `60`.
- Source changes:
  - None in this subtask. Existing guarded projection logging and stats row output were reused.
- Hypothesis tested:
  - If the row-clamped source has Chromium-like `15px` height, then the extra final row might come from source-to-screen projection, texture filtering, or threshold sensitivity rather than source row occupancy.
- AUI command:
  - Reused row-clamped projection run: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.23.34.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.23.34.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-projection-rows.log`.
- AUI source/projection result:
  - Row-clamped AUI `arial12White` source remains image `368x29`, bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`, rows y=`6..20`.
  - Projection log maps that exact source ink box to `sourceInkBounds=3,6,362,15` and `physicalInkBounds=200.180001,927.481278,362.000008,15.000001` with `drawScale=0.571429`, `pixelScale=1.75`, `filter=linear`.
  - The projection evidence says the intended final physical ink span is about `15px` high starting at physical y=`927.481278`.
- AUI final screenshot result:
  - At thresholdDarkness `20`, browser glyphOnly bounds are `(200,927,361,15)`, rows y=`927..941`; AUI glyphOnly bounds are `(200,927,362,16)`, rows y=`927..942`.
  - The AUI extra bottom row y=`942` has ink `162` and avgDarkness `76.981481`, so it is not a near-empty threshold artifact.
  - At thresholdDarkness `40`, browser remains `(200,927,360,15)` while AUI remains `(200,927,362,16)`; extra row y=`942` still has ink `145`, avgDarkness `81.972414`.
  - At thresholdDarkness `60`, browser remains `(200,927,360,15)` while AUI remains `(200,927,361,16)`; extra row y=`942` still has ink `131`, avgDarkness `85.175573`.
- Interpretation:
  - The failure is no longer source vertical bounds: row-clamped source occupancy and projection both report `15px` height.
  - The final split glyph-only screenshot still includes a dark sixteenth row. Because that row survives stricter darkness thresholds, the mismatch is not just the stats threshold being too permissive.
  - The next useful diagnostic is final texture sampling/projection row inclusion, not another global alpha scalar/cap/remap and not a `resource.html` CSS workaround.
  - Do not promote `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still fails the declared browser-standard rejection metric.
- Verification:
  - Existing AUI projection run completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-projection-rows.log`.
  - This subtask edited only this TODO document; no Java source changed during this subtask, so `compileJava` was not required for this evidence-only closeout.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` diagnostic only.
  - Run a guarded final-sampling diagnostic against the same minimal fixture: first compare `APRICITYUI_FONT_RASTER_FILTER=nearest` with row-clamped source and projection logging, then, if nearest removes or changes the y=`942` extra row without source CDF regression, inspect whether the renderer needs browser-like texture coordinate/quad edge snapping rather than a different source coverage generator.

Nearest final-sampling diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask intentionally changed only AUI texture filter mode while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains source bounds `7,8,363,15`, source rows y=`8..22`, final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
- Predeclared rejection metric:
  - Reject `nearest` as a default fix if it leaves `arial12White` final glyphOnly bounds at `16px` height or leaves the y=`942` extra row present at thresholdDarkness `20`, `40`, and `60`.
- Source changes:
  - None. `APRICITYUI_FONT_RASTER_FILTER=nearest` was an existing guarded diagnostic mode.
- Hypothesis tested:
  - If the extra final row is produced by linear texture filtering across the source quad edge, switching to nearest filtering should remove or materially change the y=`942` row while leaving the row-clamped source CDF unchanged.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=nearest`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-nearest-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.31.04.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-nearest-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.31.04.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-nearest-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-nearest-projection-rows.log`.
- AUI source/projection result:
  - `arial12White` source is unchanged from the row-clamped linear run: image `368x29`, bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`, `le240=0.548668`, rows y=`6..20`.
  - Projection remains `sourceInkBounds=3,6,362,15` and `physicalInkBounds=200.180001,927.481278,362.000008,15.000001` with `drawScale=0.571429`, `pixelScale=1.75`; only `filter=nearest` changed.
- AUI final screenshot result:
  - At thresholdDarkness `20`, AUI remains exactly `ink=2310`, coverage `0.042581`, bounds `(200,927,362,16)`, rows y=`927..942`; extra row y=`942` remains ink `162`, avgDarkness `76.981481`.
  - At thresholdDarkness `40`, AUI remains exactly `ink=2042`, coverage `0.037641`, bounds `(200,927,362,16)`; extra row y=`942` remains ink `145`, avgDarkness `81.972414`.
  - At thresholdDarkness `60`, AUI remains exactly `ink=1626`, coverage `0.029972`, bounds `(200,927,361,16)`; extra row y=`942` remains ink `131`, avgDarkness `85.175573`.
  - These values match the previous row-clamped `linear` stats, so `nearest` did not change the measured final glyph-only output for this sample.
- Interpretation:
  - The extra bottom row is not caused by the high-level texture filter mode toggled through `NativeImageBackedTexture#setFilter`.
  - Because `nearest` and `linear` produce identical `arial12White` final glyph rows while the source/projection still reports `15px`, the next diagnostic should inspect the actual draw quad, UV edge inclusion, vertex coordinate rounding, framebuffer scaling, or stats crop/scale mapping.
  - Do not promote `APRICITYUI_FONT_RASTER_FILTER=nearest`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture did not improve.
- Verification:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-nearest-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-nearest-projection-rows.log`.
  - This subtask edited only this TODO document; no Java source changed during this subtask, so `compileJava` was not required for this evidence-only closeout.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_FILTER=nearest` diagnostic only.
  - Add a guarded draw-quad/UV edge diagnostic for the same minimal fixture: log or test integer-snapped physical quad edges and half-open bottom/right sampling semantics for text quads, then compare whether `arial12White` final glyphOnly can become y=`927..941` without changing source alpha generation.

Physical quad snapping diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only AUI text quad placement while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Source-raster target remains Chromium source bounds `7,8,363,15`, source rows y=`8..22`.
- Predeclared rejection metric:
  - Reject `snap-physical` as a default fix if it cannot remove the AUI y=`942` extra row for `arial12White`, or if it fixes height only by changing source alpha generation instead of final quad placement.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical` / `apricityui.fontRaster.quadMode=snap-physical`.
  - The mode snaps final text texture quad x/y/w/h to physical pixel boundaries after the existing text texture and draw offsets are computed. It is diagnostic only and does not change default rendering.
  - Projection logging now includes `quadMode=...`.
- Hypothesis tested:
  - The y=`942` extra row may come from fractional final physical ink bounds (`927.481278..942.481279`) causing bottom-edge inclusion. Snapping the text quad to physical pixels should keep the source mask unchanged while making final ink bounds approximately `927..942`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.36.20.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.36.20.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-projection-rows.log`.
- AUI source/projection result:
  - Source is unchanged: `arial12White` remains image `368x29`, bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`, rows y=`6..20`.
  - Projection changed from `quadMode=default`, `physicalInkBounds=200.180001,927.481278,362.000008,15.000001` to `quadMode=snap-physical`, `physicalInkBounds=199.999996,926.999985,362.000008,15.000001`.
  - The diagnostic therefore moved the intended final ink interval from fractional y=`927.481..942.481` to effectively y=`927..942` without changing source alpha generation.
- AUI final screenshot result:
  - At thresholdDarkness `20`, AUI changed from bounds `(200,927,362,16)`, rows y=`927..942`, ink `2310`, coverage `0.042581` to bounds `(200,927,362,15)`, rows y=`927..941`, ink `1716`, coverage `0.031631`.
  - At thresholdDarkness `40`, AUI changed from bounds `(200,927,362,16)`, y=`942` present, ink `2042`, coverage `0.037641` to bounds `(200,927,362,15)`, rows y=`927..941`, ink `1562`, coverage `0.028793`.
  - At thresholdDarkness `60`, AUI changed from bounds `(200,927,361,16)`, y=`942` present, ink `1626`, coverage `0.029972` to bounds `(200,927,361,15)`, rows y=`927..941`, ink `1322`, coverage `0.024369`.
  - The y=`942` extra row is removed at all tested thresholds. Height now matches Chromium, but width is still `362` vs Chromium `361`, and thresholdDarkness `20` coverage is now slightly low (`0.031631` vs browser `0.033594`) instead of too high.
  - `arial13` remains `17px` tall like Chromium, but coverage shifts from slightly high (`0.012092`) to slightly low (`0.011263`) versus browser `0.011737`.
- Interpretation:
  - This strongly implicates fractional final quad placement/bottom-edge inclusion as the cause of the `arial12White` 16th row. The source mask was not the height cause.
  - `snap-physical` is directionally useful but not yet acceptable as a default: it fixes the vertical row count while undershooting glyph coverage and leaves a horizontal width mismatch.
  - The next diagnostic should refine edge semantics rather than return to source alpha transforms: compare snapping only x/y, snapping only y, or explicit half-open bottom/right edge adjustment so height is fixed without reducing overall glyph coverage too far.
  - Full `devtools/resource.html` was intentionally not run because this is still a guarded diagnostic and the minimal fixture has remaining coverage/width mismatches.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded quad mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical` diagnostic only.
  - Split the quad diagnostic into narrower guarded modes against the same minimal fixture: test `snap-physical-y` and/or bottom-edge half-open adjustment so `arial12White` keeps rows y=`927..941` while preserving x/coverage closer to Chromium. Promote none of these until the minimal fixture improves without regressing already-completed browser-standard checks.

Y-only physical quad snapping diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only AUI text quad y/height placement while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Source-raster target remains Chromium source bounds `7,8,363,15`, source rows y=`8..22`.
- Predeclared rejection metric:
  - Reject `snap-physical-y` as a default fix if it cannot remove the y=`942` extra row while preserving horizontal placement/coverage closer to Chromium than full `snap-physical`, or if it changes source alpha generation instead of final quad placement.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y` / `apricityui.fontRaster.quadMode=snap-physical-y`.
  - Accepted aliases are `physical-snap-y`, `snap_physical_y`, `pixel-snap-y`, and `pixel_snap_y`.
  - The mode snaps only final text texture quad y and height to physical pixel boundaries. x and width remain on the default fractional path. It is diagnostic only and does not change default rendering.
- Hypothesis tested:
  - If the extra final row is specifically caused by fractional vertical quad placement/bottom-edge inclusion, snapping only y/height should remove y=`942` while preserving more x coverage than full x/y/w/h snapping.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.40.39.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.40.39.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-rows.log`.
- AUI source/projection result:
  - Source is unchanged: `arial12White` remains image `368x29`, bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`, rows y=`6..20`.
  - Projection changed to `quadMode=snap-physical-y`, `physicalQuad=197.18,920.999985,368.000011,29.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.180001,926.999985,362.000008,15.000001`.
  - Compared with full `snap-physical`, this preserves the default fractional x position while snapping the vertical interval.
- AUI final screenshot result:
  - At thresholdDarkness `20`, browser remains bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`; AUI `snap-physical-y` is bounds `(200,927,362,15)`, rows y=`927..941`, ink `1962`, coverage `0.036166`.
  - At thresholdDarkness `40`, AUI is bounds `(200,927,362,15)`, rows y=`927..941`, ink `1705`, coverage `0.031429`.
  - At thresholdDarkness `60`, AUI is bounds `(200,927,361,15)`, rows y=`927..941`, ink `1338`, coverage `0.024664`.
  - Relative to default row-clamp linear thresholdDarkness `20` (`362x16`, rows y=`927..942`, ink `2310`, coverage `0.042581`), y-only snapping removes the extra row and lowers over-coverage.
  - Relative to full `snap-physical` thresholdDarkness `20` (`362x15`, ink `1716`, coverage `0.031631`), y-only snapping preserves more coverage and is closer to Chromium coverage, but still over target and keeps width `362` vs Chromium `361`.
- Interpretation:
  - The height problem is now isolated to final vertical quad edge semantics. `snap-physical-y` removes the y=`942` row without source-mask changes, so source row height is not the cause.
  - `snap-physical-y` is better evidence than full `snap-physical` for the vertical edge hypothesis because it fixes height while preserving horizontal placement. It is still not acceptable as default because thresholdDarkness `20` coverage remains too high (`0.036166` vs `0.033594`) and width remains one pixel too wide.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still fails the declared browser-standard coverage/width mismatch.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded y-only quad mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y` diagnostic only.
  - Next run should test exactly one final edge diagnostic against the same minimal fixture: bottom-edge half-open semantics or right-edge-only/right-width snapping. The goal is to keep `arial12White` rows y=`927..941` while reducing width from `362` toward Chromium `361` and moving thresholdDarkness `20` coverage toward `0.033594`. Do not promote a mode until the minimal fixture improves without regressing already-completed browser-standard checks.

Y-only plus right-edge 1px inset diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only AUI final text quad width while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Source-raster target remains Chromium source bounds `7,8,363,15`, source rows y=`8..22`.
- Predeclared rejection metric:
  - Reject the right-edge diagnostic as a default fix if it only reduces width by shrinking geometry while increasing `arial12White` over-coverage or regressing adjacent already-near samples such as `arial13`.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-right-inset-1` / `apricityui.fontRaster.quadMode=snap-physical-y-right-inset-1`.
  - Accepted aliases are `physical-snap-y-right-inset-1`, `snap_physical_y_right_inset_1`, `pixel-snap-y-right-inset-1`, and `pixel_snap_y_right_inset_1`.
  - The mode keeps y/height physical snapping from `snap-physical-y` and subtracts `1` physical pixel from the final quad width. It is diagnostic only and does not change default rendering.
- Hypothesis tested:
  - If the remaining `arial12White` `362` vs Chromium `361` width mismatch is caused by right-edge inclusion, then reducing the final physical right edge by `1px` should keep rows y=`927..941`, reduce width to `361`, and move coverage toward Chromium without harming adjacent samples.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-right-inset-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.46.39.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.46.39.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-rows.log`.
- AUI source/projection result:
  - Source is unchanged: `arial12White` remains image `368x29`, bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`, rows y=`6..20`.
  - Projection logging shows `quadMode=snap-physical-y-right-inset-1`, `physicalQuad=197.18,920.999985,367.000015,29.0`, `sourceInkBounds=3,6,362,15`.
  - Note: the current projection `physicalInkBounds` field is inaccurate for anisotropic width diagnostics because it still multiplies ink width by uniform `drawScale`; the screenshot stats are the authoritative result for this subtask.
- AUI final screenshot result:
  - At thresholdDarkness `20`, `arial12White` changed from y-only snapping bounds `(200,927,362,15)`, ink `1962`, coverage `0.036166` to bounds `(200,927,361,15)`, ink `2013`, coverage `0.037106`.
  - Chromium target is bounds `(200,927,361,15)`, ink `1786`, coverage `0.033594`.
  - At thresholdDarkness `40`, AUI is bounds `(200,927,361,15)`, ink `1764`, coverage `0.032516`; browser is bounds `(200,927,360,15)`, ink `1573`, coverage `0.029587`.
  - At thresholdDarkness `60`, AUI is bounds `(200,927,360,15)`, ink `1444`, coverage `0.026618`; browser is bounds `(200,927,360,15)`, ink `1383`, coverage `0.026013`.
  - Adjacent `arial13` regressed at thresholdDarkness `20`: browser remains bounds `(200,203,97,17)`, ink `624`, coverage `0.011737`, while AUI became bounds `(200,203,96,17)`, ink `712`, coverage `0.013124`. The previous y-only diagnostic was closer for this sample (`613`, `0.011300`).
- Interpretation:
  - The right-edge inset proves that width can be forced to Chromium's `361px` for `arial12White`, but it does so by geometry shrink and makes `arial12White` coverage worse than y-only snapping.
  - The adjacent `arial13` regression rejects this mode as a browser-parity default. The remaining mismatch is not solved by a uniform 1 physical pixel right inset.
  - This result also exposed a diagnostic gap: projection logging needs separate x/y scale reporting before future anisotropic edge diagnostics can rely on logged `physicalInkBounds`.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture regressed against the declared browser-standard rejection metric.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded right-inset mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-right-inset-1` diagnostic only.
  - Do not promote or repeat uniform right-edge 1px inset. Historical note: the diagnostic-infrastructure fix requested here was completed by the following `Separate x/y projection logging evidence` block. Use the latest next exact action below, not this historical one.

Separate x/y projection logging evidence:

- Browser oracle type:
  - Diagnostic infrastructure for the existing `source-raster` plus split `pixel-sample` oracle.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only AUI diagnostic logging and kept the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - This subtask does not claim a rendering improvement; it fixes measurement so future anisotropic edge diagnostics can be judged against the browser oracle.
- Predeclared rejection metric:
  - Reject the logging fix if `physicalInkBounds` still reports a uniform-scale width for anisotropic quads or omits enough scale data to distinguish geometry shrink from edge sampling semantics.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` projection logging now computes `physicalScaleX = physicalDrawW / entry.width()` and `physicalScaleY = physicalDrawH / entry.height()`.
  - `physicalInkBounds` now uses `physicalScaleX` for x/width and `physicalScaleY` for y/height.
  - Projection logs now include `quadScale=x,y`.
  - This is diagnostic-only and does not change default rendering or guarded quad rendering behavior.
- Hypothesis tested:
  - The previous `right-inset-1` projection line was misleading because it used uniform `drawScale` for both axes even after width-only geometry shrink. Separate x/y scale logging should expose the actual anisotropic quad scale and corrected physical ink width.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-right-inset-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-fixed-scale-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.50.58.png`.
  - Run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-fixed-scale-last.log`.
- AUI projection result:
  - For `arial12White`, corrected projection logging now reports `quadMode=snap-physical-y-right-inset-1`, `physicalQuad=197.18,920.999985,367.000015,29.0`, `quadScale=0.997283,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.171848,926.999985,361.016319,15.0`.
  - The previous log for the same diagnostic reported the physical quad width shrink but still implied a `362px` ink width. The corrected log now matches the screenshot-level observation that the right-inset diagnostic forced approximately `361px` physical ink width.
- Interpretation:
  - The infrastructure gap is closed: future anisotropic final-edge diagnostics can use projection logs to see whether they changed x/y scale, physical quad bounds, and physical ink bounds.
  - This does not improve browser parity by itself and does not rehabilitate `snap-physical-y-right-inset-1`; that mode remains rejected because it worsened coverage and regressed adjacent samples.
  - Full `devtools/resource.html` was intentionally not run because this was a diagnostic logging subtask, not a promoted rendering fix.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the logging change.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-right-inset-1-projection-fixed-scale-last.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Do not promote or repeat uniform right-edge 1px inset.
  - Next run should test exactly one non-geometry final edge semantic against the same minimal fixture, such as half-open UV/right-bottom sampling in `ImageDrawer` or a text-only guarded draw path. The rejection metric remains: keep `arial12White` rows y=`927..941`, avoid geometry shrink, move thresholdDarkness `20` coverage toward Chromium `0.033594`, and do not regress adjacent samples such as `arial13`.

Y-only plus UV half-open inset diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only guarded AUI text sampling while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Adjacent `arial13` target remains bounds `(200,203,97,17)`, rows y=`203..219`, ink `624`, coverage `0.011737`.
- Predeclared rejection metric:
  - Reject the UV half-open diagnostic as default if it introduces geometry shrink, fails to keep `arial12White` rows y=`927..941`, moves thresholdDarkness `20` coverage away from Chromium `0.033594`, or regresses adjacent samples such as `arial13`.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/ImageDrawer.java` now has guarded `drawWithUvInset(...)` for callers that explicitly provide texture dimensions and right/bottom texel insets. Default image drawing remains unchanged.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-uv-half-open` / `apricityui.fontRaster.quadMode=snap-physical-y-uv-half-open`.
  - Accepted aliases are `physical-snap-y-uv-half-open`, `snap_physical_y_uv_half_open`, `pixel-snap-y-uv-half-open`, and `pixel_snap_y_uv_half_open`.
  - The mode keeps y/height physical snapping from `snap-physical-y`, leaves final quad x/width unchanged, and samples the font texture with `uvInset=0.5,0.5`. It is diagnostic only and does not change default rendering.
- Hypothesis tested:
  - If the remaining mismatch is caused by inclusive right/bottom UV edge sampling rather than geometry, then a text-only half-texel right/bottom UV inset should keep `quadScale=1.0,1.0`, keep rows y=`927..941`, and move `arial12White` coverage toward Chromium without regressing `arial13`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-uv-half-open`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_17.57.15.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_17.57.15.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-rows.log`.
- AUI projection result:
  - For `arial12White`, projection logging reports `quadMode=snap-physical-y-uv-half-open`, `uvInset=0.5,0.5`, `physicalQuad=197.18,920.999985,368.000011,29.0`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.18,926.999985,362.000011,15.0`.
  - This confirms the diagnostic avoided width geometry shrink; unlike `right-inset-1`, it left the physical quad and x/y scale unchanged.
- AUI final screenshot result:
  - At thresholdDarkness `20`, `arial12White` regressed from y-only snapping bounds `(200,927,362,15)`, ink `1962`, coverage `0.036166` to bounds `(200,927,363,16)`, rows y=`927..942`, ink `2408`, coverage `0.044387`.
  - Chromium target remains bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - At thresholdDarkness `40`, AUI is bounds `(200,927,362,16)`, ink `2124`, coverage `0.039152`; browser is bounds `(200,927,360,15)`, ink `1573`, coverage `0.029587`.
  - At thresholdDarkness `60`, AUI is bounds `(200,927,362,16)`, ink `1698`, coverage `0.031300`; browser is bounds `(200,927,360,15)`, ink `1383`, coverage `0.026013`.
  - Adjacent `arial13` also regressed at thresholdDarkness `20`: browser remains bounds `(200,203,97,17)`, ink `624`, coverage `0.011737`, while AUI became bounds `(200,203,98,18)`, ink `818`, coverage `0.015078`.
- Interpretation:
  - `snap-physical-y-uv-half-open` is rejected. It did not model Chromium edge semantics; shrinking the sampled UV range while drawing it over the same quad effectively stretches the source and creates more right/bottom ink.
  - The result preserves the important distinction: geometry was not shrunk (`quadScale=1.0,1.0`), but the sampling transform itself was wrong for browser parity.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture regressed against the declared browser-standard rejection metric.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded UV inset path.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-uv-half-open-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-uv-half-open` diagnostic only.
  - Do not promote or repeat UV max inset over unchanged geometry. Next run should test a different final sampling/edge hypothesis against the same minimal fixture, preferably one that does not rescale source UVs, such as adding a transparent texture gutter/padding diagnostic or a sampler clamp/border diagnostic for text textures. The rejection metric remains: keep `arial12White` rows y=`927..941`, move thresholdDarkness `20` coverage toward Chromium `0.033594`, and do not regress adjacent samples such as `arial13`.

Y-only plus transparent texture gutter diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only guarded AUI text texture padding while keeping the same fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Adjacent `arial13` target remains bounds `(200,203,97,17)`, rows y=`203..219`, ink `624`, coverage `0.011737`.
- Predeclared rejection metric:
  - Reject transparent texture gutter as a default fix if it fails to keep `arial12White` rows y=`927..941`, fails to move thresholdDarkness `20` coverage toward Chromium `0.033594`, or regresses adjacent samples such as `arial13`.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1`.
  - Accepted aliases are `physical-snap-y-texture-gutter-1`, `snap_physical_y_texture_gutter_1`, `pixel-snap-y-texture-gutter-1`, and `pixel_snap_y_texture_gutter_1`.
  - The mode keeps y/height physical snapping from `snap-physical-y`, appends one transparent texel at the right and bottom of the generated text texture, and includes the texture gutter mode in the font texture cache key.
  - Default rendering and existing diagnostic modes remain unchanged.
- Hypothesis tested:
  - If the extra final row/over-coverage is caused by sampling or interpolation near the text texture's bottom/right edge, then adding a transparent gutter should absorb edge sampling without shrinking the source UV range over unchanged geometry.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_18.03.59.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.03.59.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rows.log`.
- AUI source/projection result:
  - For `arial12White`, texture stats changed from image `368x29` to `369x30`, while source ink stayed bounds `3,6,362,15`, ink `1839`, avg alpha `193.159326`.
  - Projection logging reports `quadMode=snap-physical-y-texture-gutter-1`, `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.099999,926.999985,362.000007,15.0`.
  - This confirms the diagnostic added transparent right/bottom texture space without changing the source ink size.
- AUI final screenshot result:
  - At thresholdDarkness `20`, `arial12White` improved from y-only snapping bounds `(200,927,362,15)`, ink `1962`, coverage `0.036166` to bounds `(200,927,362,15)`, rows y=`927..941`, ink `1856`, coverage `0.034212`.
  - Chromium target is bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - At thresholdDarkness `40`, AUI is bounds `(200,927,362,15)`, ink `1587`, coverage `0.029253`; browser is bounds `(200,927,360,15)`, ink `1573`, coverage `0.029587`.
  - At thresholdDarkness `60`, AUI is bounds `(200,927,361,15)`, ink `1332`, coverage `0.024553`; browser is bounds `(200,927,360,15)`, ink `1383`, coverage `0.026013`.
  - Adjacent `arial13` did not regress at thresholdDarkness `20`: browser is bounds `(200,203,97,17)`, ink `624`, coverage `0.011737`; AUI is bounds `(200,203,97,17)`, ink `621`, coverage `0.011447`.
- Interpretation:
  - This is the strongest final-edge diagnostic so far. It keeps the `arial12White` vertical rows at Chromium's y=`927..941` and moves thresholdDarkness `20` coverage close to Chromium without the adjacent `arial13` regression seen in right-inset or UV-inset diagnostics.
  - It is not yet acceptable as default because the target sample remains one pixel too wide at thresholdDarkness `20` (`362` vs Chromium `361`), and other font-family samples still have documented raster differences.
  - The result suggests final edge/padding behavior is a real part of the mismatch, but not sufficient alone to complete browser parity.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture still has a width mismatch and this remains guarded diagnostic behavior.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded texture gutter mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Do not promote it yet. Next run should test exactly one width-focused refinement on top of this direction against the same minimal fixture, while preserving the improved height/coverage: for example a text-only transparent gutter plus right-edge coverage/threshold diagnostic, or a browser source/screenshot row/column profile comparison to identify whether the remaining 1px width comes from final sampling or source column coverage. Acceptance still requires `arial12White` rows y=`927..941`, thresholdDarkness `20` coverage near `0.033594`, width moving from `362` toward Chromium `361`, and no `arial13` regression.

Transparent texture gutter plus right-edge inset diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus split `pixel-sample`.
  - Reused browser source row-profile oracle: `run/resource-browser-font-source-raster-browser-row-profile-last.log`.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reuse is valid because this subtask changed only a guarded AUI final text quad mode while keeping the same minimal fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` target remains final glyphOnly thresholdDarkness `20` bounds `(200,927,361,15)`, rows y=`927..941`, ink `1786`, coverage `0.033594`.
  - Adjacent `arial13` target remains bounds `(200,203,97,17)`, rows y=`203..219`, ink `624`, coverage `0.011737`.
- Predeclared rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-right-inset-1` if it fails to keep `arial12White` rows y=`927..941`, fails to move thresholdDarkness `20` coverage toward Chromium `0.033594`, or regresses adjacent samples such as `arial13`.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-inset-1` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-right-inset-1`.
  - Accepted aliases are `physical-snap-y-texture-gutter-1-right-inset-1`, `snap_physical_y_texture_gutter_1_right_inset_1`, `pixel-snap-y-texture-gutter-1-right-inset-1`, and `pixel_snap_y_texture_gutter_1_right_inset_1`.
  - The mode combines y/height physical snapping, one transparent right/bottom texture gutter, and a one physical-pixel right-edge inset.
  - Default rendering and existing diagnostic modes remain unchanged.
- Hypothesis tested:
  - If the remaining one-pixel `arial12White` width mismatch after transparent texture gutter is caused only by final right-edge quad coverage, then applying the existing one physical-pixel right-edge inset on top of the gutter should reduce width to Chromium `361` while preserving the gutter's improved height/coverage and without regressing `arial13`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-inset-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_18.08.32.png`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.08.32.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-rows.log`.
- AUI source/projection result:
  - For `arial12White`, projection logging reports `quadMode=snap-physical-y-texture-gutter-1-right-inset-1`, `texture=369x30`, `physicalQuad=197.099998,920.999985,368.000011,29.999999`, `quadScale=0.99729,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.091868,926.999985,361.018981,15.0`.
  - This confirms the diagnostic changed final x projection without changing source ink bounds.
- AUI final screenshot result:
  - At thresholdDarkness `20`, `arial12White` reached the browser width/height bounds `(200,927,361,15)` and rows y=`927..941`, but ink worsened to `2013` and coverage worsened to `0.037106`; Chromium is ink `1786`, coverage `0.033594`.
  - Compared to transparent gutter alone, target width improved from `362` to `361`, but ink worsened from `1856` to `2013` and coverage worsened from `0.034212` to `0.037106`.
  - At thresholdDarkness `40`, AUI is bounds `(200,927,361,15)`, ink `1772`, coverage `0.032664`; browser is bounds `(200,927,360,15)`, ink `1573`, coverage `0.029587`.
  - At thresholdDarkness `60`, AUI is bounds `(200,927,360,15)`, ink `1432`, coverage `0.026396`; browser is bounds `(200,927,360,15)`, ink `1383`, coverage `0.026013`.
  - Adjacent `arial13` regressed at thresholdDarkness `20`: AUI became bounds `(200,203,96,17)`, ink `704`, coverage `0.012977`; browser is bounds `(200,203,97,17)`, ink `624`, coverage `0.011737`, and transparent gutter alone had AUI bounds `(200,203,97,17)`, ink `621`, coverage `0.011447`.
- Interpretation:
  - This hypothesis is rejected. The mode proves the last-pixel width can be forced through final x projection, but the forced shrink increases target over-coverage and regresses adjacent `arial13`.
  - The failure makes the remaining width mismatch unlikely to be solved by a raw geometry/right-edge shrink. The next useful diagnostic should inspect column-level browser/AUI ink distribution or adjust source/final edge classification without changing final quad width.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture regressed under the predeclared acceptance gate.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after adding the guarded combo mode.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-last.log`.
  - Stats command completed and wrote `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-inset-1-projection-rows.log`.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-inset-1` diagnostic only.
  - Do not promote or repeat transparent gutter plus raw right-edge inset. Next run should compare browser/AUI column profiles for `arial12White` in the same minimal fixture, or test exactly one width-focused diagnostic that does not shrink the final quad. The rejection metric remains: keep rows y=`927..941`, move thresholdDarkness `20` coverage toward Chromium `0.033594`, reduce or explain width `362` vs `361`, and do not regress `arial13`.

Final screenshot column-profile diagnostic evidence:

- Browser oracle type:
  - Split `pixel-sample` with per-column glyph ink profile.
  - Reused browser crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reuse is valid because this subtask changed only diagnostic scripting and read the same browser fixture, browser viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `arial12White` thresholdDarkness `20` final columns remain bounds `(200,927,361,15)`, width `361`, ink `1786`.
  - Chromium `arial13` thresholdDarkness `20` final columns remain bounds `(200,203,97,17)`, width `97`, ink `624`.
- Predeclared rejection metric:
  - Reject a raw final-quad crop explanation if the extra AUI width is accompanied by real glyph ink and broader internal column-distribution differences, because browser parity would then require source/final edge classification evidence rather than merely shrinking the final quad.
- Source changes:
  - Added `scripts/resource_browser_font_raster_columns.ps1`.
  - The script reads the existing Chromium/AUI metrics logs and screenshots, applies the same glyph darkness threshold as `resource_browser_font_raster_stats.ps1`, and writes per-column glyph profiles, edge summaries, AUI-only columns, and browser-only columns for selected sample IDs.
  - No Java or `resource.html` change was made in this subtask.
- Hypothesis tested:
  - If the remaining `arial12White` width mismatch in the non-regressed transparent-gutter run is a pure final right-edge sampling issue, then the column profile should show a localized extra AUI edge column without wider internal distribution changes.
- Diagnostic command:
  - `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.03.59.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
  - Output log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log`.
- Result:
  - Browser image scale: dimensions `2560x1475`, CSS viewport `1440x746`, DPR/scale `1.749829`.
  - AUI image scale: `run/screenshots/aui/2026-07-15_18.03.59.png`, dimensions `2560x1476`, CSS viewport `1463x843`, scale `1.749829,1.75089`.
  - `arial12White` browser columns: bounds `(200,927,361,15)`, width `361`, ink `1786`; right edge includes `x=557` ink `10`, `x=558` ink `10`, `x=559` ink `7`, `x=560` ink `2`, avgDarkness `27.5`.
  - `arial12White` AUI columns in the non-regressed transparent-gutter run: bounds `(200,927,362,15)`, width `362`, ink `1856`; right edge includes `x=558` ink `8`, `x=559` ink `12`, `x=560` ink `9`, and AUI-only `x=561` ink `3`, avgDarkness `39.333333`.
  - `arial12White` also has internal AUI-only columns including `x=317` ink `15`, `x=479` ink `9`, `x=531` ink `15`, and browser-only columns including `x=245` ink `15`, `x=335` ink `15`, `x=466` ink `15`, `x=528` ink `15`, `x=535` ink `15`.
  - `arial13` remains width-stable in this run: browser bounds `(200,203,97,17)`, width `97`, ink `624`; AUI bounds `(200,203,97,17)`, width `97`, ink `621`.
  - `arial13` still has internal column distribution differences: AUI-only `x=229` ink `2`, `x=246` ink `16`; browser-only `x=243` ink `2`, `x=259` ink `2`, `x=279` ink `17`.
- Interpretation:
  - The remaining `arial12White` extra rightmost AUI column is real glyph ink, not background noise or crop bookkeeping.
  - The mismatch is not proven to be a pure final edge crop problem because both `arial12White` and `arial13` show internal column shifts/distribution differences even when `arial13` total width and ink are close to Chromium.
  - This supports keeping transparent texture gutter diagnostic-only and rejects returning to raw final-quad shrink. The next useful browser-standard evidence should compare source/exported alpha columns against Chromium's source raster, or test a diagnostic that changes edge classification without shrinking final geometry.
  - Full `devtools/resource.html` was intentionally not run because this was a diagnostic-only evidence run and did not improve the minimal fixture.
- Verification:
  - Column script completed and wrote `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should compare source/exported alpha-mask column profiles for Chromium source raster vs AUI `outline-coverage-4x-row-clamp`, especially the right edge and the internal columns called out above. If source columns already differ, fix source coverage/classification; if source columns match but final columns differ, test exactly one final sampler/edge diagnostic that preserves final quad width and `arial13`.

Source/export alpha column-profile diagnostic evidence:

- Browser oracle type:
  - `source-raster` with bounds-relative source alpha column profiles.
  - Browser fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html`.
  - Browser runner: `scripts/resource_browser_font_source_raster_metrics.js`.
- Browser-standard rule matched:
  - Chromium `physicalDpr.sourceAlpha` is the source-mask oracle before final screenshot sampling/compositing.
  - Source columns must be compared relative to each source ink bound, not by absolute texture x, because Chromium's canvas fixture and AUI's texture exporter use different padding.
- Predeclared rejection metric:
  - Reject "AUI source mask is too wide" if AUI's source alpha bounds are not wider than Chromium after bounds-relative comparison.
  - Reject a raw final-quad crop fix if source alpha widths and final screenshot widths disagree in opposite directions.
- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-source-raster.html` now emits `columnProfile` beside `rowProfile` in `statsFromImageData(...)`.
  - Added `scripts/resource_browser_font_source_alpha_columns.ps1` to compare Chromium `physicalDpr.sourceAlpha.columnProfile` with exported AUI alpha masks using bounds-relative columns.
  - No `resource.html` change was made.
- Browser command:
  - `node scripts\resource_browser_font_source_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Browser oracle log: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK=1`, `APRICITYUI_FONT_RASTER_QUAD_MODE` unset, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-alpha-mask-export-last.log`.
  - AUI run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-alpha-mask-export-last.log`.
- Diagnostic command:
  - `.\scripts\resource_browser_font_source_alpha_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-source-raster-browser-column-profile-last.log' -AuiMaskDir 'run/font-raster-masks' -OutLog 'run/resource-browser-font-source-alpha-columns-outline-coverage-4x-row-clamp.log' -SampleIds 'arial12White,arial13White' -AuiSource 'outline-coverage-4x-row-clamp'`.
  - Output log: `run/resource-browser-font-source-alpha-columns-outline-coverage-4x-row-clamp.log`.
- Result:
  - `arial12White` Chromium source alpha: image `378x35`, bounds `(7,8,363,15)`, relative right `362`, ink `2167`, avgA `163.932626`; right edge relative columns include `x=359` ink `13`, `x=360` ink `12`, `x=361` ink `10`, `x=362` ink `6`, avgA `24.5`.
  - `arial12White` AUI `outline-coverage-4x-row-clamp` exported mask: image `368x29`, bounds `(3,6,362,15)`, relative right `361`, ink `1839`, avgA `163.307776`; right edge relative columns include `x=358` ink `11`, `x=359` ink `13`, `x=360` ink `10`, `x=361` ink `4`, avgA `17.5`.
  - `arial12White` source mask is therefore one column narrower in AUI than Chromium (`362` vs `363`), while the final screenshot transparent-gutter run was one column wider in AUI than Chromium (`362` vs `361`).
  - `arial13White` Chromium source alpha: image `114x39`, bounds `(7,7,99,17)`, relative right `98`, ink `755`, avgA `161.933775`.
  - `arial13White` AUI `outline-coverage-4x-row-clamp` exported mask: image `103x31`, bounds `(3,6,97,17)`, relative right `96`, ink `663`, avgA `146.962293`.
  - `arial13White` source mask is two columns narrower in AUI than Chromium (`97` vs `99`), while the final screenshot transparent-gutter run had matched browser/AUI final width `97`.
- Interpretation:
  - The final `arial12White` width mismatch is not explained by an AUI source mask that is too wide. At source-alpha level, AUI is narrower than Chromium.
  - Chromium loses or classifies away more source-edge columns before the final screenshot glyph threshold than AUI does. This points to source-to-final edge classification/compositing/sampling behavior rather than source width or raw final geometry.
  - `arial13White` reinforces the same conclusion: source alpha is narrower in AUI, yet final width can still match Chromium. Width parity alone is not sufficient; edge alpha distribution and final threshold/composite behavior must be tested.
  - Full `devtools/resource.html` was intentionally not run because this was a diagnostic-only source evidence run and did not improve the minimal fixture.
- Verification:
  - Browser source command completed and wrote `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - AUI export command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-alpha-mask-export-last.log`.
  - Source alpha column script completed and wrote `run/resource-browser-font-source-alpha-columns-outline-coverage-4x-row-clamp.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only HTML fixture/script/documentation and used `runClient` for runtime validation.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one source-to-final edge classification diagnostic that preserves final quad width: compare browser `sourceAlpha` vs browser `composited` column loss and AUI exported alpha vs final screenshot column loss for `arial12White`/`arial13`, or add an AUI diagnostic that logs projected source alpha columns contributing to final edge pixels. Do not retry source-width expansion or raw final right-edge shrink until this source-to-final loss is quantified.

Source-to-final edge loss diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus final split `pixel-sample`.
  - Reused browser source oracle with column profiles: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reuse is valid because this subtask changed only diagnostic scripting and read the same browser/AUI fixture outputs, viewport, DPR, font source, animation state, zoom/meta mode, and crop policy.
- Browser-standard rule matched:
  - Chromium `sourceAlpha` and Chromium final screenshot glyph columns define the expected source-to-final edge classification behavior.
  - For this diagnostic, width loss is measured in bounds-relative physical columns: source alpha width minus final thresholdDarkness `20` glyph width.
- Predeclared rejection metric:
  - Reject source-width expansion and raw final-quad shrink as next actions if Chromium and AUI differ mainly in how many source-edge columns survive final glyph classification.
- Source changes:
  - Added `scripts/resource_browser_font_source_to_final_loss.ps1`.
  - The script compares Chromium `sourceAlpha`, Chromium source-fixture `composited`, Chromium final screenshot glyph columns, AUI exported alpha masks, and AUI final screenshot glyph columns using bounds-relative columns.
  - No Java or `resource.html` change was made.
- Diagnostic command:
  - `.\scripts\resource_browser_font_source_to_final_loss.ps1 -BrowserSourceLog 'run/resource-browser-font-source-raster-browser-column-profile-last.log' -BrowserFinalMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -AuiMaskDir 'run/font-raster-masks' -AuiFinalMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.03.59.png' -OutLog 'run/resource-browser-font-source-to-final-loss-outline-coverage-4x-row-clamp-texture-gutter-1.log' -SampleIds 'arial12White,arial13White' -AuiSource 'outline-coverage-4x-row-clamp' -GlyphDarknessThreshold 20`.
  - Output log: `run/resource-browser-font-source-to-final-loss-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
- Result:
  - `arial12White` Chromium sourceAlpha is width `363`, ink `2167`, relativeRight `362`; Chromium source-fixture composited is width `363`, ink `2121`, sourceToCompositedWidthLoss `0`.
  - `arial12White` Chromium finalGlyph is width `361`, ink `1786`, sourceToFinalWidthLoss `2`; missing final edge source columns include relative `x=361` ink `10`, avg `133`, and `x=362` ink `6`, avg `24.5`.
  - `arial12White` AUI sourceAlpha is width `362`, ink `1839`, relativeRight `361`; AUI finalGlyph in the transparent-gutter run is width `362`, ink `1856`, sourceToFinalWidthLoss `0`.
  - `arial13White` Chromium sourceAlpha is width `99`, ink `755`; Chromium source-fixture composited is width `99`, sourceToCompositedWidthLoss `0`; Chromium finalGlyph is width `97`, ink `624`, sourceToFinalWidthLoss `2`.
  - `arial13White` AUI sourceAlpha is width `97`, ink `663`; AUI finalGlyph in the transparent-gutter run is width `97`, ink `621`, sourceToFinalWidthLoss `0`.
- Interpretation:
  - Chromium's source fixture compositing does not remove width by itself; the two-column loss appears when moving to the final screenshot glyph classification under the actual page/crop/threshold path.
  - AUI keeps all source-width columns through the transparent-gutter final screenshot path for both target samples. That explains why `arial12White` can be source-narrower than Chromium but still final-wider than Chromium.
  - The next useful experiment should not expand the AUI source mask and should not shrink the final quad. It should test one browser-like final edge attenuation/classification behavior that causes weak outer source columns to fall below final glyph threshold while preserving geometry and avoiding `arial13` regression.
  - Full `devtools/resource.html` was intentionally not run because this was a diagnostic-only evidence run and did not improve the minimal fixture.
- Verification:
  - Source-to-final loss script completed and wrote `run/resource-browser-font-source-to-final-loss-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
  - `git diff --check reported only existing LF/CRLF warnings and no whitespace errors`.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one final edge attenuation/classification diagnostic that preserves source generation and final quad width. Candidate: add a guarded text-only final-edge alpha attenuation for the transparent-gutter outer source columns, then reject it unless `arial12White` keeps rows y=`927..941`, reduces final width/coverage toward Chromium, and does not regress `arial13`.

Final right-edge alpha attenuation diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus final split `pixel-sample`.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused browser source-to-final loss evidence: `run/resource-browser-font-source-to-final-loss-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - Reuse is valid because this subtask changed only a guarded AUI diagnostic mode and validated the same minimal fixture, viewport/crop policy, font source, target physical raster mode, zoom/meta mode, animation state, and thresholdDarkness `20` rule.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture is the expected result. For the target `arial12White` sample, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
  - The prior source-to-final loss diagnostic showed Chromium final classification drops two source-width columns while the AUI transparent-gutter run drops zero; this run tested whether a guarded edge-alpha attenuation can emulate that final classification without changing final quad geometry.
- Predeclared tolerance/rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-edge-attenuate-2` if `arial12White` does not keep rows `y=927..941`, does not move width/coverage toward Chromium `361x15` / `0.033594`, or regresses adjacent `arial13` from the transparent-gutter baseline of `97x17`, ink `621`, coverage `0.011447`.
- Hypothesis tested:
  - Preserving the transparent texture gutter and final quad width while attenuating only the rightmost two source ink columns before gutter insertion might make weak final edge columns fall below the browser glyph threshold, matching Chromium's final edge classification better than raw quad shrink.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-edge-attenuate-2` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-edge-attenuate-2`.
  - The mode keeps y physical snapping and transparent `1px` right/bottom texture gutter, preserves final quad width, and attenuates the rightmost two source ink columns before adding the gutter. Rightmost column alpha is multiplied by `0.0`; the second-rightmost column is multiplied by `0.25`.
  - Accepted aliases are `physical-snap-y-texture-gutter-1-edge-attenuate-2`, `snap_physical_y_texture_gutter_1_edge_attenuate_2`, `pixel-snap-y-texture-gutter-1-edge-attenuate-2`, and `pixel_snap_y_texture_gutter_1_edge_attenuate_2`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-edge-attenuate-2`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-last.log`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.30.44.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Column command: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.30.44.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_18.30.44.png`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-rows.log`.
  - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2.log`.
  - For `arial12White`, projection logging reports `quadMode=snap-physical-y-texture-gutter-1-edge-attenuate-2`, `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,361,15`, `physicalInkBounds=200.099999,926.999985,361.000007,15.0`.
  - For `arial12White`, thresholdDarkness `20` final AUI is `361x15`, rows `y=927..941`, ink `1851`, coverage `0.034120`; Chromium is `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`.
  - `arial12White` column profile shows right-edge bounds now match Chromium at `x=560`, but AUI still has more edge ink there: Chromium `x=560` ink `2`, avgDarkness `27.5`; AUI `x=560` ink `7`, avgDarkness `29.142857`.
  - For adjacent `arial13`, thresholdDarkness `20` final AUI regressed to `96x17`, ink `615`, coverage `0.011336`; Chromium is `97x17`, ink `624`, coverage `0.011737`, and the prior transparent-gutter AUI baseline was `97x17`, ink `621`, coverage `0.011447`.
- Interpretation:
  - The diagnostic fixes the target `arial12White` final width without shrinking final quad geometry, but it does so by making source ink bounds narrower and still leaves target ink/coverage above Chromium.
  - The same uniform right-edge attenuation regresses the adjacent `arial13` sample, so it is not a browser-parity fix and must stay diagnostic only.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture hit the predeclared `arial13` regression condition.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` completed successfully.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-edge-attenuate-2-projection-last.log`.
  - Stats and column scripts completed and wrote their logs.
- Accepted or remaining mismatch:
  - Reject `snap-physical-y-texture-gutter-1-edge-attenuate-2` as a default or promotion candidate because it regresses `arial13` width and does not fully match `arial12White` ink/coverage.
- Next exact action:
  - Keep `RBV-V5-05` `[~]`.
  - Keep `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-edge-attenuate-2` diagnostic only.
  - Next run should avoid uniform edge-alpha attenuation. Test a source-to-final sampling/classification diagnostic that logs the actual projected source columns contributing to the final rightmost glyph pixels, then decide whether the mismatch is from bilinear sample footprint, pixel-center rounding, or glyph threshold/crop classification rather than mutating source alpha.

Source-to-final right-edge sampling footprint diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus final split `pixel-sample`.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused browser source-to-final loss evidence: `run/resource-browser-font-source-to-final-loss-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - Reuse is valid because this subtask changed only a diagnostic script and reran the same minimal AUI fixture with the same viewport/crop policy, font source, target physical raster mode, transparent-gutter quad mode, zoom/meta mode, animation state, and thresholdDarkness `20` rule.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. For `arial12White`, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
  - The browser/AUI discrepancy to explain is still: transparent-gutter AUI keeps all source-width columns through final classification for `arial12White`, yielding final width `362`, while Chromium final classification drops two source-width columns and yields final width `361`.
- Predeclared tolerance/rejection metric:
  - This was a diagnostic-only run, not a promotion candidate. Reject source-alpha mutation as the next explanation if the extra final AUI right-edge column maps to nonzero source-alpha texels through the current projection/sampling footprint; in that case the next experiment should target pixel-center/linear-sampling footprint or projection rounding, not global or uniform source alpha changes.
- Source changes:
  - Added `scripts/resource_browser_font_source_projection_edges.ps1`.
  - The script reads AUI fixture metrics, AUI projection logs, exported alpha masks, and the final AUI screenshot. For each requested sample it reports final glyph bounds and maps the rightmost final glyph columns back to normalized source coordinates, texel-center coordinates, nearest source column, bilinear footprint columns, and source-alpha stats for those footprint columns.
  - No `resource.html` or Java changes were made in this subtask.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, `APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK=1`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Diagnostic command: `.\scripts\resource_browser_font_source_projection_edges.ps1 -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.36.36.png' -AuiMaskDir 'run/font-raster-masks' -OutLog 'run/resource-browser-font-source-projection-edges-outline-coverage-4x-row-clamp-texture-gutter-1.log' -SampleIds 'arial12White,arial13' -AuiSource 'outline-coverage-4x-row-clamp' -QuadMode 'snap-physical-y-texture-gutter-1' -GlyphDarknessThreshold 20 -RightColumns 4`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_18.36.36.png`.
  - Diagnostic log: `run/resource-browser-font-source-projection-edges-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - For `arial12White`, projection remains `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1,1`, `sourceInkBounds=3,6,362,15`. Final AUI glyph remains `362x15`, ink `1856`.
  - `arial12White` rightmost final column `finalX=561`, relative `361`, has ink `3`, avgDarkness `39.333333`. Its source mapping is `normalizedSource=364.399994`, `texelCenter=363.899994`, `nearestSource=364`, `bilinearFootprint=363,364`. The footprint is not empty: source column `363` has ink `10`, avgAlpha `141.4`, maxAlpha `255`; source column `364` has ink `4`, avgAlpha `17.5`, maxAlpha `41`.
  - For comparison, `arial12White` `finalX=560` maps to footprint `362,363`, both nonempty: source column `362` has ink `13`, avgAlpha `147.692308`, maxAlpha `255`; source column `363` has ink `10`, avgAlpha `141.4`, maxAlpha `255`.
  - For adjacent `arial13`, final AUI glyph remains `97x17`, ink `621`. Its rightmost final column `finalX=296`, relative `96`, has ink `3`, avgDarkness `43.333333`, and maps to footprint `99,100`; source column `99` is weak but nonempty with ink `5`, avgAlpha `23.4`, maxAlpha `55`, while source column `100` is empty.
- Interpretation:
  - The extra `arial12White` final column in transparent-gutter mode is not a crop-only or threshold-only artifact. The final column still samples a strong penultimate source column through the current projected bilinear footprint.
  - Because `quadScale=1,1`, the remaining mismatch is more likely in source-to-screen pixel-center alignment, linear sample footprint, or physical projection rounding than in source source-width generation. Uniform source-alpha mutation already regressed `arial13` and should not be repeated.
  - Full `devtools/resource.html` was intentionally not run because this was diagnostic-only evidence and did not improve the minimal fixture.
- Verification:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Diagnostic script completed and wrote `run/resource-browser-font-source-projection-edges-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter is still diagnostic only: it preserves `arial13` better than edge attenuation and fixes `arial12White` height/coverage direction, but leaves `arial12White` one final pixel too wide under the browser threshold.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test one pixel-center/projection diagnostic, not source alpha mutation: add a guarded quad sampling alignment mode that preserves source generation and transparent gutter but shifts the sampled texture footprint right by a subpixel/texel-center amount, then reject it unless `arial12White` drops final `x=561` without losing `arial13` width or regressing `arial12White` rows `y=927..941`.

UV sampling window right-shift diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus final split `pixel-sample`.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reuse is valid because this subtask changed only a guarded AUI diagnostic mode and validated the same minimal fixture, viewport/crop policy, font source, target physical raster mode, zoom/meta mode, animation state, and thresholdDarkness `20` rule.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. For `arial12White`, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
- Predeclared tolerance/rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-uv-shift-right-half` unless `arial12White` drops final `x=561` and moves final bounds/coverage toward Chromium without losing or adding `arial13` width, and without regressing `arial12White` rows `y=927..941`.
- Hypothesis tested:
  - If the transparent-gutter mismatch is caused by a half-texel source-to-screen center offset, preserving source generation, transparent gutter, and final quad geometry while shifting the sampled UV window right by `0.5` texel should make the weak `arial12White` final right edge fall into transparent gutter without changing source bounds.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/ImageDrawer.java` now has guarded helper `drawWithUvWindow(...)` for diagnostics that need a non-zero UV start while preserving destination geometry.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` now has guarded text quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-uv-shift-right-half` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-uv-shift-right-half`.
  - The mode keeps y physical snapping, source generation, transparent `1px` right/bottom texture gutter, and final quad width unchanged. It sets the sampled UV window start to `u=0.5` texel for this diagnostic.
  - Accepted aliases are `physical-snap-y-texture-gutter-1-uv-shift-right-half`, `snap_physical_y_texture_gutter_1_uv_shift_right_half`, `pixel-snap-y-texture-gutter-1-uv-shift-right-half`, and `pixel_snap_y_texture_gutter_1_uv_shift_right_half`.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-uv-shift-right-half`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, alpha-mask export/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-last.log`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.42.05.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Column command: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.42.05.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_18.42.05.png`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-rows.log`.
  - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half.log`.
  - Projection logging confirms source and final quad geometry were preserved for `arial12White`: `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.099999,926.999985,362.000007,15.0`.
  - `arial12White` regressed: Chromium final is `361x15`, ink `1786`, coverage `0.033594`; AUI became `363x15`, ink `2139`, coverage `0.039429`. Rows stayed `y=927..941`, but width and coverage moved away from Chromium. Column profile shows new left expansion at `x=199` and the rightmost AUI column still exists at `x=561`.
  - Adjacent `arial13` also regressed: Chromium final is `97x17`, ink `624`, coverage `0.011737`; AUI became `98x17`, ink `752`, coverage `0.013862`.
- Interpretation:
  - A global `+0.5 texel` UV-window shift is not the browser-parity fix. It increased coverage and created left-edge expansion while preserving the same source bounds and destination quad, so the remaining issue cannot be solved by simply shifting the whole sampled texture footprint right.
  - The result supports a narrower next diagnostic: inspect or alter asymmetric edge classification/crop at the final glyph bounds, not a global UV offset and not source alpha mutation.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture hit both predeclared rejection conditions: `arial12White` moved away from Chromium and `arial13` regressed.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` completed successfully.
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-uv-shift-right-half-projection-last.log`.
  - Stats and column scripts completed and wrote their logs.
- Accepted or remaining mismatch:
  - Reject `snap-physical-y-texture-gutter-1-uv-shift-right-half` as a default or promotion candidate.
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter remains the best diagnostic baseline, but it still leaves `arial12White` one final pixel too wide under the browser threshold.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should not use global UV shifts or source alpha mutation. Add a diagnostic comparing left and right final edge classification against source footprints for both browser and AUI, or test a right-edge-only final classification/crop policy that preserves left edge, source generation, transparent gutter, and `arial13` width.

Left/right edge classification diagnostic evidence:

- Browser oracle type:
  - `source-raster` plus final split `pixel-sample`.
  - Reused browser source oracle with column profiles: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused AUI transparent-gutter alpha-export fixture: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Reuse is valid because this subtask changed only a diagnostic script and read the same browser/AUI minimal fixture outputs, viewport/crop policy, font source, target physical raster mode, transparent-gutter quad mode, zoom/meta mode, animation state, and thresholdDarkness `20` rule.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. This diagnostic compares source edge columns against final glyph columns by side: left edge and right edge are reported independently instead of merged into one missing-column list.
- Predeclared tolerance/rejection metric:
  - Reject further global UV offset or source-alpha mutation if Chromium's source-to-final loss is asymmetric by side while AUI preserves both sides. In that case, the next implementation experiment should be right-edge-only final classification/crop, not a symmetric/global transform.
- Source changes:
  - Added `scripts/resource_browser_font_edge_classification.ps1`.
  - The script reads Chromium source column profiles, Chromium final screenshot glyph columns, AUI exported alpha masks, and AUI final screenshot glyph columns. It reports source/final widths plus left/right edge source columns, final columns, and missing edge columns for each side.
  - No Java, HTML, or `resource.html` change was made in this subtask.
- Diagnostic command:
  - `.\scripts\resource_browser_font_edge_classification.ps1 -BrowserSourceLog 'run/resource-browser-font-source-raster-browser-column-profile-last.log' -BrowserFinalMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -AuiFinalMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.36.36.png' -AuiMaskDir 'run/font-raster-masks' -OutLog 'run/resource-browser-font-edge-classification-outline-coverage-4x-row-clamp-texture-gutter-1.log' -SampleIds 'arial12White,arial13White' -AuiSource 'outline-coverage-4x-row-clamp' -AuiQuadMode 'snap-physical-y-texture-gutter-1' -GlyphDarknessThreshold 20 -EdgeColumns 4`.
  - Output log: `run/resource-browser-font-edge-classification-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
- Result:
  - For `arial12White`, Chromium source width is `363`, final width is `361`: left edge loses `0` columns, right edge loses `2` columns. Chromium right source columns `x=361` and `x=362` are missing from final; right source `x=359` and `x=360` survive.
  - For `arial12White`, AUI transparent-gutter source width is `362`, final width is `362`: left edge loses `0` columns, right edge loses `0` columns. AUI right source columns `x=358..361` all survive in final.
  - For `arial13White`/final `arial13`, Chromium source width is `99`, final width is `97`: left edge loses `0` columns, right edge loses `2` columns. Chromium right source columns `x=97` and `x=98` are missing from final.
  - For `arial13White`/final `arial13`, AUI transparent-gutter source width is `97`, final width is `97`: left edge loses `0` columns, right edge loses `0` columns. AUI right source columns `x=93..96` all survive in final.
- Interpretation:
  - The browser behavior is side-specific in this fixture: Chromium keeps the left source edge and classifies away the outer two right source columns for both target samples.
  - AUI transparent-gutter keeps both left and right edges. This explains why global UV shifts and global source alpha changes regress: the mismatch is not symmetric.
  - The next useful experiment should be a guarded right-edge-only final classification/crop policy that preserves source generation, transparent gutter, destination quad, left edge, and `arial13` width unless the minimal fixture proves otherwise.
  - Full `devtools/resource.html` was intentionally not run because this was diagnostic-only evidence and did not improve the minimal fixture.
- Verification:
  - Diagnostic script completed and wrote `run/resource-browser-font-edge-classification-outline-coverage-4x-row-clamp-texture-gutter-1.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter remains the best diagnostic baseline, but it still leaves `arial12White` one final pixel too wide under the browser threshold because AUI keeps right-edge columns that Chromium classifies away.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one guarded right-edge-only final classification/crop diagnostic. It must preserve left edge/source generation/transparent gutter/destination quad and be rejected unless `arial12White` drops the extra final right column toward Chromium while `arial13` remains `97x17` and `arial12White` rows remain `y=927..941`.

Right-edge `right-crop-1` diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` plus `source-raster` edge context.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused browser source column oracle: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Reuse is valid because the fixture, viewport, DPR, zoom/meta mode, animation state, font source, crop policy, and thresholdDarkness `20` classifier did not change; this subtask changed only the AUI final right-edge sampling diagnostic.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. For `arial12White`, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
- Predeclared tolerance/rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-right-crop-1` unless `arial12White` drops the extra final right column and moves bounds/coverage toward Chromium while `arial13` remains `97x17` and `arial12White` rows remain `y=927..941`.
- Hypothesis tested:
  - A guarded right-edge-only final crop of one physical pixel might remove the AUI-only final `arial12White` right edge while preserving source generation, transparent gutter, destination quad, left edge, and adjacent `arial13` width.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/ImageDrawer.java` has `drawWithUvWindow(...)` for diagnostic UV-window drawing.
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` has guarded quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-crop-1` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-right-crop-1`.
  - No `resource.html` change was made.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-crop-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, alpha-mask export/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-last.log`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.51.32.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Column command: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.51.32.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_18.51.32.png`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1-projection-rows.log`.
  - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-1.log`.
  - Projection logging confirms `right-crop-1` preserved source and final quad geometry for `arial12White`: `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.099999,926.999985,362.000007,15.0`.
  - `arial12White` did not improve: Chromium final is `361x15`, ink `1786`, coverage `0.033594`; AUI remained `362x15`, ink `1856`, coverage `0.034212`; rows stayed `y=927..941`. Column profile still has AUI rightmost `x=561` with `ink=3`, `avgDarkness=39.333333`.
  - Adjacent `arial13` did not regress: Chromium final is `97x17`, ink `624`, coverage `0.011737`; AUI remained `97x17`, ink `621`, coverage `0.011447`.
- Interpretation:
  - `right-crop-1` is not enough to match Chromium final edge classification. It appears to crop only the empty/gutter edge and does not remove the strong source contribution that still maps into AUI final `x=561`.
  - The experiment is rejected as a default or promotion candidate because it leaves the primary `arial12White` width, ink, coverage, and rightmost column unchanged from the transparent-gutter baseline.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture did not improve.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` completed successfully; `:compileJava` was up-to-date.
  - AUI fixture command completed successfully; the captured run log ends with `BUILD SUCCESSFUL in 44s`.
  - Stats and column scripts completed and wrote their logs.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter remains the best diagnostic baseline, but it still leaves `arial12White` one final pixel too wide under the browser threshold. `right-crop-1` preserves `arial13`, but does not remove the target mismatch.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one stronger right-edge-only diagnostic, such as `right-crop-2` or a source-column-aware final right-edge cutoff. It must preserve left edge, source generation, transparent gutter, and destination quad, and be rejected unless `arial12White` drops from `362` toward Chromium `361`, rows remain `y=927..941`, and `arial13` remains `97x17`.

Right-edge `right-crop-2` diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` plus `source-raster` edge context.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused browser source column oracle: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Reuse is valid because the fixture, viewport, DPR, zoom/meta mode, animation state, font source, crop policy, and thresholdDarkness `20` classifier did not change; this subtask changed only the AUI final right-edge sampling diagnostic.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. For `arial12White`, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
- Predeclared tolerance/rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-right-crop-2` unless `arial12White` drops from AUI baseline `362x15` toward Chromium `361x15`, rows stay `y=927..941`, and adjacent `arial13` remains `97x17`.
- Hypothesis tested:
  - Since `right-crop-1` appeared to crop only the empty/gutter edge, a two-physical-texel right-edge crop might reach the sampled source contribution that maps to AUI final `arial12White` `x=561`, while preserving left edge, source generation, transparent gutter, destination quad, and adjacent `arial13` width.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` added guarded quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-crop-2` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-right-crop-2`.
  - This mode reuses the existing `ImageDrawer.drawWithUvWindow(...)` diagnostic path added for `right-crop-1`.
  - No `resource.html` change was made.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-right-crop-2`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, alpha-mask export/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-last.log`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.59.47.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Column command: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_18.59.47.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_18.59.47.png`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2-projection-rows.log`.
  - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-right-crop-2.log`.
  - Projection logging confirms `right-crop-2` preserved source and final quad geometry for `arial12White`: `texture=369x30`, `physicalQuad=197.099998,920.999985,369.000008,29.999999`, `quadScale=1.0,1.0`, `sourceInkBounds=3,6,362,15`, `physicalInkBounds=200.099999,926.999985,362.000007,15.0`.
  - `arial12White` did not improve: Chromium final is `361x15`, ink `1786`, coverage `0.033594`; AUI remained `362x15`, ink `1856`, coverage `0.034212`; rows stayed `y=927..941`. Column profile still has AUI rightmost `x=561` with `ink=3`, `avgDarkness=39.333333`.
  - Adjacent `arial13` did not regress: Chromium final is `97x17`, ink `624`, coverage `0.011737`; AUI remained `97x17`, ink `621`, coverage `0.011447`.
- Interpretation:
  - `right-crop-2` has the same final classification result as `right-crop-1` and the transparent-gutter baseline for the target samples. Fixed texel crop through the current UV-window path is not reaching the final sampled footprint that creates `arial12White` `x=561`.
  - The experiment is rejected as a default or promotion candidate because it leaves the primary `arial12White` width, ink, coverage, and rightmost column unchanged.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture did not improve.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` completed successfully.
  - AUI fixture command completed successfully; the captured run log ends with `BUILD SUCCESSFUL in 44s`.
  - Stats and column scripts completed and wrote their logs.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter remains the best diagnostic baseline, but it still leaves `arial12White` one final pixel too wide under the browser threshold. `right-crop-2` preserves `arial13`, but does not remove the target mismatch.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should not continue fixed `right-crop-N` texture-window cropping. Test exactly one source-column-aware right-edge final classification/cutoff diagnostic that targets the actual source columns feeding final `x=561`, preserves left edge/source generation/transparent gutter/destination quad, and is rejected unless `arial12White` drops from `362` toward Chromium `361`, rows remain `y=927..941`, and `arial13` remains `97x17`.

Source-column-aware `source-cutoff-1` diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` plus `source-raster` edge context.
  - Reused browser final crop oracle: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused browser screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reused browser source column oracle: `run/resource-browser-font-source-raster-browser-column-profile-last.log`.
  - Reuse is valid because the fixture, viewport, DPR, zoom/meta mode, animation state, font source, crop policy, and thresholdDarkness `20` classifier did not change; this subtask changed only the AUI source-edge cutoff diagnostic.
- Browser-standard rule matched:
  - Chromium final screenshot classification for the minimal fixture remains the expected result. For `arial12White`, Chromium final glyph bounds are `361x15`, rows `y=927..941`, ink `1786`, coverage `0.033594`. For adjacent `arial13`, Chromium final glyph bounds are `97x17`, ink `624`, coverage `0.011737`.
- Predeclared tolerance/rejection metric:
  - Reject `snap-physical-y-texture-gutter-1-source-cutoff-1` unless `arial12White` drops from AUI baseline `362x15` toward Chromium `361x15`, rows stay `y=927..941`, and adjacent `arial13` remains `97x17`.
- Hypothesis tested:
  - Fixed texture-window crop missed the sampled ink footprint. Clearing exactly the actual source ink's rightmost column before adding the transparent gutter should target the source column that feeds final `arial12White` `x=561`, while preserving left edge, destination quad, and y snapping.
- Source changes:
  - `src/main/java/com/sighs/apricityui/render/FontDrawer.java` added guarded quad mode `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-source-cutoff-1` / `apricityui.fontRaster.quadMode=snap-physical-y-texture-gutter-1-source-cutoff-1`.
  - The mode applies `applySourceRightCutoff(...)` before transparent gutter expansion. It finds the actual source alpha `maxX` and clears only the rightmost source-ink column.
  - No `resource.html` change was made.
- AUI command:
  - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-source-cutoff-1`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, alpha-mask export/interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-last.log`.
  - Stats command: `.\scripts\resource_browser_font_raster_stats.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_19.08.50.png' -OutLog 'run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-rows.log' -GlyphDarknessThresholds '20,40,60'`.
  - Column command: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_19.08.50.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1.log' -SampleIds 'arial12White,arial13' -GlyphDarknessThreshold 20`.
- AUI result:
  - AUI fixture command completed and wrote `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-last.log`.
  - Screenshot: `run/screenshots/aui/2026-07-15_19.08.50.png`.
  - Stats log: `run/resource-browser-font-raster-samples-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-rows.log`.
  - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1.log`.
  - Source texture stats confirm the cutoff hit the target source edge: `arial12White` AUI source ink bounds changed to `3,6,361,15` from the transparent-gutter baseline `3,6,362,15`.
  - `arial12White` final width improved to Chromium width: Chromium final is `361x15`, ink `1786`, coverage `0.033594`; AUI became `361x15`, ink `1853`, coverage `0.034157`; rows stayed `y=927..941`. Column profile no longer has AUI `x=561`; AUI right edge is `x=557..560`.
  - Adjacent `arial13` regressed: Chromium final is `97x17`, ink `624`, coverage `0.011737`; AUI became `96x17`, ink `618`, coverage `0.011392`.
- Interpretation:
  - The diagnostic proves the target `arial12White` width mismatch is caused by the actual right source ink edge, not the transparent texture gutter or UV window.
  - The same unconditional source-edge cutoff breaks an already-correct adjacent sample. This matches the earlier warning from source/export profiles: AUI source masks are narrower than Chromium for `arial13`, so deleting a right source column there removes real browser-visible width.
  - The experiment is rejected as a default or promotion candidate because it violates the predeclared `arial13` guard, even though it fixes the target `arial12White` width.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture regressed `arial13`.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` completed successfully.
  - AUI fixture command completed successfully; the captured run log ends with `BUILD SUCCESSFUL in 44s`.
  - Stats and column scripts completed and wrote their logs.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Transparent texture gutter remains the best baseline; `source-cutoff-1` is diagnostic only because it fixes `arial12White` width by creating an `arial13` width regression.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one diagnostic that distinguishes samples with final-width overflow from samples already matching Chromium before applying source/final right-edge cutoff. A plausible next diagnostic is to log or compute projected final-width surplus from source ink width, destination scale, and final threshold columns for `arial12White` vs `arial13`, then only attempt a guarded cutoff if that diagnostic predicts a surplus. Reject any implementation that fixes `arial12White` by reducing `arial13` below `97x17`.

Browser-oracle width-surplus guard diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` plus AUI source projection diagnostics.
  - Reused browser/AUI final column log for the transparent-gutter baseline: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log`.
  - Reused AUI source-cutoff final column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1.log`.
  - Reused AUI transparent-gutter projection log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Reuse is valid because this subtask added only a read-only diagnostic script and did not change fixture, viewport, DPR, zoom/meta mode, animation state, font source, crop policy, thresholdDarkness `20`, or AUI rendering output.
- Browser-standard rule matched:
  - Chromium final width remains the expected result. `arial12White` should be `361` px wide at thresholdDarkness `20`; adjacent `arial13` should remain `97` px wide.
- Predeclared tolerance/rejection metric:
  - A useful guard diagnostic must classify `arial12White` as needing cutoff while classifying `arial13` as already correct. Reject it if the predicted guarded width differs from Chromium for either sample.
- Hypothesis tested:
  - A browser-oracle baseline final-width surplus can distinguish the target overflow from already-correct adjacent samples: apply source cutoff only when `baselineAuiFinalWidth - browserFinalWidth > 0`; otherwise skip.
- Source changes:
  - Added `scripts/resource_browser_font_width_surplus_guard.ps1`.
  - The script reads existing column/projection logs, computes `baselineSurplus`, compares unconditional cutoff output, and reports the guarded decision and guarded final-width error.
  - No Java, HTML, or `resource.html` change was made in this subtask.
- Diagnostic command:
  - `.\scripts\resource_browser_font_width_surplus_guard.ps1 -OutLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1.log' -SampleIds 'arial12White,arial13'`.
- Result:
  - Output log: `run/resource-browser-font-width-surplus-guard-source-cutoff-1.log`.
  - `arial12White`: browser width `361`, baseline AUI width `362`, `baselineSurplus=1`, source-cutoff width `361`, `guardDecision=apply`, `guardedWidth=361`, `guardedError=0`, baseline source ink width `362`, physical ink width `362.000007`, `sourceInkBounds=3,6,362,15`.
  - `arial13`: browser width `97`, baseline AUI width `97`, `baselineSurplus=0`, source-cutoff width `96`, `guardDecision=skip`, `guardedWidth=97`, `guardedError=0`, baseline source ink width `97`, physical ink width `97.000004`, `sourceInkBounds=3,6,97,17`.
- Interpretation:
  - The browser-oracle surplus rule correctly separates the two target samples in the current fixture: it would apply cutoff only where the transparent-gutter baseline is too wide and skip the already-correct adjacent sample.
  - This is not yet promotable framework behavior because it depends on knowing the Chromium final width at runtime. It is useful evidence for the next step: find a runtime-available proxy that predicts the same `apply`/`skip` decisions without using browser oracle data.
  - Full `devtools/resource.html` was intentionally not run because this subtask was read-only diagnostic evidence and did not change AUI rendering.
- Verification:
  - Diagnostic script completed and wrote `run/resource-browser-font-width-surplus-guard-source-cutoff-1.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. A browser-oracle guard would avoid the `arial13` regression, but browser final width cannot be the runtime framework rule.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should test exactly one runtime-available proxy for the browser-oracle surplus guard. Candidate inputs are AUI source column alpha/ink distribution, source ink width, physical ink width, projected rightmost final column footprint, and right-edge darkness/alpha strength. Reject any proxy that would apply cutoff to `arial13` or skip `arial12White`.

Runtime physical right-edge fraction proxy diagnostic evidence:

- Browser oracle type:
  - Runtime proxy compared against the previous final split `pixel-sample` browser-oracle guard.
  - Reused oracle guard log: `run/resource-browser-font-width-surplus-guard-source-cutoff-1.log`.
  - Reused AUI transparent-gutter projection log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log`.
  - Reuse is valid because this subtask added only a read-only diagnostic script and did not change fixture, viewport, DPR, zoom/meta mode, animation state, font source, crop policy, thresholdDarkness `20`, or AUI rendering output.
- Browser-standard rule matched:
  - The target classification remains Chromium final width at thresholdDarkness `20`: `arial12White` requires a guarded cutoff decision, while `arial13` must be skipped to preserve its Chromium-matching width.
- Predeclared tolerance/rejection metric:
  - Reject the proxy if it does not match the browser-oracle guard decisions for both samples: `arial12White=apply`, `arial13=skip`.
- Hypothesis tested:
  - A runtime-available proxy based on the fractional physical position of the source ink's right edge can predict whether the rightmost final pixel column is only a thin fringe. If `physicalInkRightFrac <= 0.25`, apply source cutoff; otherwise skip.
- Source changes:
  - Added `scripts/resource_browser_font_runtime_edge_proxy.ps1`.
  - The script reads the transparent-gutter projection log and computes `physicalInkRight = physicalInkX + physicalInkW`, `rightFrac = fract(physicalInkRight)`, and `proxyDecision`.
  - No Java, HTML, or `resource.html` change was made in this subtask.
- Diagnostic command:
  - `.\scripts\resource_browser_font_runtime_edge_proxy.ps1 -OutLog 'run/resource-browser-font-runtime-edge-proxy-right-frac.log' -SampleIds 'arial12White,arial13' -RightFracThreshold 0.25`.
- Result:
  - Output log: `run/resource-browser-font-runtime-edge-proxy-right-frac.log`.
  - `arial12White`: `physicalInkX=200.099999`, `physicalInkW=362.000007`, `physicalInkRight=562.100006`, `rightFrac=0.100006`, `proxyDecision=apply`, `oracleDecision=apply`, `matchesOracle=True`, guarded width `361`, guarded error `0`.
  - `arial13`: `physicalInkX=199.939995`, `physicalInkW=97.000004`, `physicalInkRight=296.939999`, `rightFrac=0.939999`, `proxyDecision=skip`, `oracleDecision=skip`, `matchesOracle=True`, guarded width `97`, guarded error `0`.
- Interpretation:
  - The proxy matches the browser-oracle guard for the two target samples and is runtime-available from AUI projection/source bounds data.
  - The result is promising because it connects the decision to physical pixel coverage instead of text length, hard-coded sample IDs, or browser-only final width.
  - It is still not promotable: two samples are insufficient, and the threshold `0.25` has not been validated across the rest of the minimal fixture's text samples or across full `resource.html`.
  - Full `devtools/resource.html` was intentionally not run because this subtask was read-only diagnostic evidence and did not change AUI rendering.
- Verification:
  - Diagnostic script completed and wrote `run/resource-browser-font-runtime-edge-proxy-right-frac.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask changed only a PowerShell diagnostic script and this document, not Java.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. Runtime `physicalInkRightFrac` is now the leading guard hypothesis, but it needs broader minimal-fixture validation before implementation.
- Next exact action:
  - Keep `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp` and `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1` diagnostic only.
  - Next run should expand `physicalInkRightFrac <= 0.25` evaluation to all relevant text samples in `tests/resource-browser-font-raster.html`, comparing predicted apply/skip decisions against Chromium final-width surplus. Reject or revise the proxy if it applies cutoff to any already-correct sample or skips any width-surplus sample.

Goal automation preparation for all-sample proxy evaluation:

- Browser oracle type:
  - No new oracle result was created in this preparation step.
  - The next run remains bound to the existing Chromium final split `pixel-sample` width oracle from `run/resource-browser-font-raster-browser-background-pairs-last.log` and browser screenshot `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- Browser-standard rule preserved:
  - Browser/Chromium final screenshot columns define expected glyph width. AUI output, current Minecraft rendering, and visual preference cannot redefine expected width.
- Predeclared tolerance/rejection metric:
  - The next all-sample run must reject `physicalInkRightFrac <= 0.25` if any sample has `proxyDecision != oracleDecision`.
  - A sample's `oracleDecision` is `apply` only when the transparent-gutter AUI final width has positive surplus over Chromium final width; otherwise it is `skip`.
- Source changes:
  - Updated `scripts/resource_browser_font_width_surplus_guard.ps1` to read AUI sample metadata and match projection rows by sample text, font family, font size, letter spacing, and rect position instead of hard-coded sample IDs.
  - Updated `scripts/resource_browser_font_runtime_edge_proxy.ps1` with the same metadata-driven projection matching.
  - Updated this document's active quickstart with the exact all-sample command bundle and strict reject conditions.
  - No Java, HTML, `resource.html`, or rendering default was changed.
- Regression-check commands:
  - `.\scripts\resource_browser_font_width_surplus_guard.ps1 -OutLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-regression-check.log' -SampleIds 'arial12White,arial13'`.
  - `.\scripts\resource_browser_font_runtime_edge_proxy.ps1 -OutLog 'run/resource-browser-font-runtime-edge-proxy-right-frac-regression-check.log' -SampleIds 'arial12White,arial13' -RightFracThreshold 0.25`.
- Result:
  - Width-surplus guard regression log: `run/resource-browser-font-width-surplus-guard-source-cutoff-1-regression-check.log`.
  - Runtime proxy regression log: `run/resource-browser-font-runtime-edge-proxy-right-frac-regression-check.log`.
  - `arial12White` remained `guardDecision=apply`, `proxyDecision=apply`, `matchesOracle=True`.
  - `arial13` remained `guardDecision=skip`, `proxyDecision=skip`, `matchesOracle=True`.
- Interpretation:
  - The scripts are now suitable for the next automated all-sample diagnostic without changing the current browser-parity conclusion.
  - The two-sample proxy is still not promotable; this update only removes automation friction for the required broader validation.
- Verification:
  - Both regression-check scripts completed successfully.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this preparation step changed only PowerShell diagnostics and this document, not Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`. The active mismatch and next decision are unchanged: `arial12White` remains one final pixel too wide in the transparent-gutter baseline, while unconditional source cutoff still regresses `arial13`.
- Next exact action:
  - Run the all-sample command bundle in `Next Goal Run Quickstart`, then record whether every `physicalInkRightFrac <= 0.25` decision matches the Chromium width-surplus oracle.
  - If any mismatch appears, keep the proxy diagnostic-only and revise the runtime predictor before any Java rendering change.

All-sample `physicalInkRightFrac <= 0.25` proxy rejection evidence:

- Browser oracle type:
  - Final split `pixel-sample` width classification at `thresholdDarkness=20`.
  - Reused Chromium metrics log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reuse is valid for the browser oracle because `tests/resource-browser-font-raster.html`, browser viewport `1440x746`, DPR `1.7498291730880737`, thresholdDarkness `20`, and static fixture state were unchanged.
- Browser-standard rule matched:
  - Chromium final screenshot glyph columns define expected width for each sample. Current AUI output cannot define expected width.
- Predeclared tolerance/rejection metric:
  - Reject `physicalInkRightFrac <= 0.25` if any sample has `proxyDecision != oracleDecision`.
  - `oracleDecision=apply` only when transparent-gutter baseline final width has positive surplus over Chromium; otherwise `oracleDecision=skip`.
- Hypothesis tested:
  - The fractional physical position of the source ink right edge can identify when a source right-edge cutoff should be applied: `rightFrac <= 0.25` means apply, otherwise skip.
- Source changes:
  - No Java, HTML, `resource.html`, or rendering default was changed in this subtask.
  - The previously added metadata-driven scripts were used as-is for the all-sample diagnostic.
- AUI baseline rerun:
  - Command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`, then `.\gradlew.bat runClient --console plain --no-daemon --offline`.
  - Run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-rerun-last.log`.
  - Screenshot used for baseline columns: `run/screenshots/aui/2026-07-15_19.34.19.png`.
  - The run completed with `BUILD SUCCESSFUL in 57s`.
  - This rerun did not emit `[AUI FontRaster] projection` rows, so the proxy step reused the earlier transparent-gutter projection log for source/physical ink geometry.
- Diagnostic commands:
  - Baseline all-sample columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-rerun-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_19.34.19.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log' -SampleIds <13 samples> -GlyphDarknessThreshold 20`.
  - Source-cutoff all-sample columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-projection-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_19.08.50.png' -OutLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log' -SampleIds <13 samples> -GlyphDarknessThreshold 20`.
  - Browser-oracle guard: `.\scripts\resource_browser_font_width_surplus_guard.ps1 -BaselineColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log' -CutoffColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-rerun-last.log' -BaselineProjectionLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log' -OutLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-all.log' -SampleIds <13 samples>`.
  - Runtime proxy: `.\scripts\resource_browser_font_runtime_edge_proxy.ps1 -OracleGuardLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-all.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-rerun-last.log' -BaselineProjectionLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-alpha-export-last.log' -OutLog 'run/resource-browser-font-runtime-edge-proxy-right-frac-all.log' -SampleIds <13 samples> -RightFracThreshold 0.25`.
- Result:
  - Baseline columns log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log`.
  - Source-cutoff columns log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log`.
  - Browser-oracle guard log: `run/resource-browser-font-width-surplus-guard-source-cutoff-1-all.log`.
  - Runtime proxy log: `run/resource-browser-font-runtime-edge-proxy-right-frac-all.log`.
  - The proxy matched the oracle for 8/13 samples and failed for 5/13 samples.
  - False apply mismatches: `sans13`, `chakra13`, `arial12`, `arial12White`, `arial12Fafafa`.
  - `sans13`: `rightFrac=0.220003`, `proxyDecision=apply`, `oracleDecision=skip`, baseline AUI width `91` vs Chromium `92`.
  - `chakra13`: `rightFrac=0.220003`, `proxyDecision=apply`, `oracleDecision=skip`, baseline AUI width `91` vs Chromium `92`.
  - `arial12`: `rightFrac=0.100006`, `proxyDecision=apply`, `oracleDecision=skip`, baseline AUI width `361` vs Chromium `361`.
  - `arial12White`: `rightFrac=0.100006`, `proxyDecision=apply`, `oracleDecision=skip`, baseline AUI width `361` vs Chromium `361`.
  - `arial12Fafafa`: `rightFrac=0.100006`, `proxyDecision=apply`, `oracleDecision=skip`, baseline AUI width `361` vs Chromium `361`.
  - Current rerun baseline also differs from older two-sample evidence: `arial13` now measures `96` vs Chromium `97`, and `arial12White` now measures `361` vs Chromium `361` rather than the earlier `362` vs `361`.
- Interpretation:
  - `physicalInkRightFrac <= 0.25` is rejected as a runtime promotion candidate. It applies cutoff to samples that are already correct or already too narrow under the current Chromium width oracle.
  - The all-sample result also shows baseline instability or a current-worktree behavior change relative to earlier evidence. The next useful step is not another cutoff heuristic; it is to reproduce and explain why the transparent-gutter baseline changed from the earlier `arial12White` surplus/`arial13` preserved state to the current `arial12White` matched/`arial13` undershoot state.
  - Full `devtools/resource.html` was intentionally not run because the minimal fixture rejected the proxy and revealed baseline inconsistency.
- Verification:
  - AUI baseline run completed successfully.
  - All four diagnostic scripts completed and wrote their logs.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask did not edit Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The runtime right-frac proxy is diagnostic-only and should not be promoted.
  - Current minimal fixture mismatch is now broader than the earlier right-edge overflow: multiple samples are too narrow against Chromium in the rerun baseline, while some source-cutoff results overshoot.
- Next exact action:
  - Stop pursuing `physicalInkRightFrac <= 0.25`.
  - Next run should perform one reproducibility diagnostic for the transparent-gutter baseline: rerun the same fixture with projection logging enabled, compare the new screenshot columns against `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log` and the older two-sample column log `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log`, and identify whether the width change comes from code changes, diagnostic env flags, screenshot timing, projection logging side effects, or column-threshold measurement state.

Transparent-gutter baseline reproducibility evidence:

- Browser oracle type:
  - Final split `pixel-sample` width classification at `thresholdDarkness=20`.
  - Reused Chromium metrics log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Reuse is valid because this subtask only varied AUI diagnostic environment flags and did not change the browser fixture, Chromium screenshot, viewport, DPR, threshold, or browser state.
- Browser-standard rule matched:
  - Chromium final screenshot glyph columns define expected width. AUI reruns are implementation evidence only.
- Predeclared tolerance/rejection metric:
  - The reproducibility diagnostic must explain why the minimal-env rerun measured `arial12White=361`/`arial13=96` while older transparent-gutter evidence measured `arial12White=362`/`arial13=97`.
  - Reject projection logging as the cause if a target-physical run without projection logging reproduces the older two-sample widths.
- Hypothesis tested:
  - The earlier inconsistency was caused by rerunning without the historical `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1` diagnostic environment, not by Java code changes, screenshot timing, projection logging side effects, or column-threshold measurement state.
- Source changes:
  - No Java, HTML, `resource.html`, script, or rendering default was changed in this subtask.
  - This document was updated with the latest cursor override and evidence.
- Diagnostic commands and artifacts:
  - Minimal-env rerun from previous subtask: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`, then `.\gradlew.bat runClient --console plain --no-daemon --offline`.
    - Run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-rerun-last.log`.
    - Column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log`.
    - Screenshot: `run/screenshots/aui/2026-07-15_19.34.19.png`.
  - Full historical-env projection rerun: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`, `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`, `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`, `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`, then `.\gradlew.bat runClient --console plain --no-daemon --offline`.
    - Run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log`.
    - All-sample column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-all.log`.
    - Two-sample column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-two-sample.log`.
    - Screenshot: `run/screenshots/aui/2026-07-15_19.38.32.png`.
    - The run emitted 13 `[AUI FontRaster] projection` rows and completed with `BUILD SUCCESSFUL in 38s`.
  - Target-physical-only rerun: same as the full historical-env run, but with `LOG_TEXTURE_STATS`, `LOG_ALPHA_HISTOGRAM`, and `LOG_PROJECTION` unset.
    - Run log: `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-target-physical-rerun-last.log`.
    - Two-sample column log: `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-target-physical-rerun-two-sample.log`.
    - Screenshot: `run/screenshots/aui/2026-07-15_19.40.23.png`.
    - The run emitted 0 projection rows and completed with `BUILD SUCCESSFUL in 39s`.
- Result:
  - Older two-sample transparent-gutter log `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1.log`: `arial12White` AUI `width=362`, `ink=1856`; `arial13` AUI `width=97`, `ink=621`.
  - Minimal-env rerun `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-all.log`: `arial12White` AUI `width=361`, `ink=1777`; `arial13` AUI `width=96`, `ink=632`.
  - Full historical-env projection rerun `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-all.log`: `arial12White` AUI `width=362`, `ink=1856`; `arial13` AUI `width=97`, `ink=621`.
  - Target-physical-only rerun `run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-target-physical-rerun-two-sample.log`: `arial12White` AUI `width=362`, `ink=1856`; `arial13` AUI `width=97`, `ink=621`.
- Interpretation:
  - The apparent baseline instability is explained by diagnostic environment mismatch. The older transparent-gutter baseline requires `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`; omitting it switches the AUI rendering/scaling path enough to alter final screenshot classification.
  - Projection logging is not the cause: the target-physical-only rerun, with 0 projection rows, reproduced the old two-sample widths exactly.
  - No evidence points to Java code changes, screenshot timing, or column-threshold measurement state as the cause of the earlier discrepancy.
  - For `RBV-V5-05`, `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1` must be part of the canonical minimal fixture command unless a subtask explicitly compares target-physical against default viewport scaling.
- Verification:
  - Both AUI reruns completed successfully.
  - Column scripts completed and wrote the named logs.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask did not edit Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - Canonical target-physical transparent-gutter mismatch is restored: `arial12White` remains one final pixel too wide (`362` vs Chromium `361`) while `arial13` width is preserved (`97` vs Chromium `97`).
- Next exact action:
  - Continue only from the target-physical transparent-gutter baseline.
  - Do not use the minimal-env rerun logs as baseline evidence for promotion/rejection.
  - Next run should test exactly one browser-standard runtime predictor or source-to-final classification diagnostic that distinguishes `arial12White` surplus from `arial13` preserved width without using browser final width at runtime. Reject it if it applies cutoff to any target-physical sample that already matches or undershoots Chromium, or if it skips a positive-surplus sample.

Target-physical `physicalInkRightFrac <= 0.75` proxy diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` width classification at `thresholdDarkness=20`, using the canonical target-physical transparent-gutter baseline.
  - Reused Chromium metrics log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- Browser-standard rule matched:
  - Chromium final screenshot glyph width defines expected width.
  - The runtime proxy is only judged by whether it matches the browser-oracle surplus classification; it does not define expected behavior.
- Predeclared tolerance/rejection metric:
  - Reject a proxy if any target-physical sample has `proxyDecision != oracleDecision`.
  - `oracleDecision=apply` only when target-physical transparent-gutter AUI final width has positive surplus over Chromium; otherwise `oracleDecision=skip`.
- Hypothesis tested:
  - Under target-physical rendering, the fractional physical position of the source ink right edge can classify final-width surplus if the threshold is widened from `0.25` to `0.75`: apply source-edge cutoff when `physicalInkRightFrac <= 0.75`, otherwise skip.
- Source changes:
  - No Java, HTML, `resource.html`, script, or rendering default was changed in this subtask.
  - This document was updated with the latest cursor override and evidence.
- Diagnostic commands:
  - Target-physical browser-oracle guard: `.\scripts\resource_browser_font_width_surplus_guard.ps1 -BaselineColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-all.log' -CutoffColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -BaselineProjectionLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -OutLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log' -SampleIds <13 samples>`.
  - Control proxy: `.\scripts\resource_browser_font_runtime_edge_proxy.ps1 -OracleGuardLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -BaselineProjectionLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -OutLog 'run/resource-browser-font-runtime-edge-proxy-right-frac-target-physical-all.log' -SampleIds <13 samples> -RightFracThreshold 0.25`.
  - Candidate proxy: `.\scripts\resource_browser_font_runtime_edge_proxy.ps1 -OracleGuardLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -BaselineProjectionLog 'run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log' -OutLog 'run/resource-browser-font-runtime-edge-proxy-right-frac-0p75-target-physical-all.log' -SampleIds <13 samples> -RightFracThreshold 0.75`.
- Result:
  - Browser-oracle guard log: `run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log`.
  - Control proxy log: `run/resource-browser-font-runtime-edge-proxy-right-frac-target-physical-all.log`.
  - Candidate proxy log: `run/resource-browser-font-runtime-edge-proxy-right-frac-0p75-target-physical-all.log`.
  - The old `0.25` threshold matched 9/13 samples and missed 4 positive-surplus samples: `arial10`, `chakra12`, `chakra12White`, `chakra12Fafafa`.
  - The `0.75` threshold matched 13/13 target-physical samples with zero `matchesOracle=False` rows.
  - Skip samples all had `rightFrac ~= 0.94`: `arial13`, `arial13Plain`, `arial13White`, `arial13Fafafa`.
  - Apply samples had `rightFrac` values `0.100006`, `0.220003`, `0.420009`, or `0.62001`: `sans13`, `chakra13`, `arial10`, `arial12`, `chakra12`, `arial12White`, `arial12Fafafa`, `chakra12White`, `chakra12Fafafa`.
- Interpretation:
  - `physicalInkRightFrac <= 0.75` is a stronger runtime-available classifier than the rejected `0.25` threshold for the canonical target-physical minimal fixture.
  - This is not a default rendering promotion. It only proves apply/skip classification against the current minimal fixture and browser width oracle.
  - Source-cutoff output itself is still imperfect for some apply samples: `sans13`, `chakra13`, and `arial10` remain `guardedError=1` after cutoff. A promoted implementation must validate actual final columns, not just the classifier.
  - Full `devtools/resource.html` was intentionally not run because this subtask was read-only classification evidence.
- Verification:
  - All diagnostic scripts completed and wrote the named logs.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask did not edit Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - `physicalInkRightFrac <= 0.75` is the leading runtime guard candidate, but only as a diagnostic candidate.
- Next exact action:
  - Implement or simulate exactly one guarded target-physical minimal-fixture diagnostic using `physicalInkRightFrac <= 0.75`.
  - Reject it unless final columns improve positive-surplus samples without shrinking skip samples such as `arial13`, `arial13White`, and `arial13Fafafa`, and without moving `arial12White` rows away from `y=927..941`.

Simulated target-physical guarded-width diagnostic evidence:

- Browser oracle type:
  - Final split `pixel-sample` width classification at `thresholdDarkness=20`, using the canonical target-physical transparent-gutter baseline.
  - Reused Chromium metrics log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Reused Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
- Browser-standard rule matched:
  - Chromium final screenshot glyph width defines expected width.
  - The simulation is judged by choosing between two real AUI final-column results: transparent-gutter baseline and source-cutoff output.
- Predeclared tolerance/rejection metric:
  - Reject if the guarded selection shrinks any skip sample or increases absolute width error for any target-physical sample.
  - Track exact width matches separately because some apply samples may improve but remain one pixel too wide.
- Hypothesis tested:
  - A position-derived runtime guard using `physicalInkRightFrac <= 0.75` can safely decide when to use the source-cutoff result: apply source cutoff for positive-surplus samples and keep the transparent-gutter baseline for samples that already match or undershoot Chromium.
- Source changes:
  - Added `scripts/resource_browser_font_runtime_guarded_width.ps1`.
  - No Java, HTML, `resource.html`, or rendering default was changed.
- Diagnostic command:
  - `.\scripts\resource_browser_font_runtime_guarded_width.ps1 -BaselineColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-all.log' -CutoffColumnsLog 'run/resource-browser-font-raster-columns-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-source-cutoff-1-all.log' -OracleGuardLog 'run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log' -ProxyLog 'run/resource-browser-font-runtime-edge-proxy-right-frac-0p75-target-physical-all.log' -OutLog 'run/resource-browser-font-runtime-guarded-width-right-frac-0p75-target-physical-all.log' -SampleIds <13 samples>`.
- Result:
  - Output log: `run/resource-browser-font-runtime-guarded-width-right-frac-0p75-target-physical-all.log`.
  - Summary: `total=13`, `decisionMatches=13`, `improvedOrEqual=13`, `exact=9`.
  - Skip samples preserved baseline width:
    - `arial13`: Chromium `97`, selected baseline `97`, exact.
    - `arial13White`: Chromium `97`, selected baseline `97`, exact.
    - `arial13Fafafa`: Chromium `97`, selected baseline `97`, exact.
    - `arial13Plain`: Chromium `87`, selected baseline `86`, unchanged undershoot rather than further cutoff to `85`.
  - Apply samples improved or matched:
    - Exact after cutoff: `arial12`, `chakra12`, `arial12White`, `arial12Fafafa`, `chakra12White`, `chakra12Fafafa`.
    - Improved but still `+1` px wide: `sans13`, `chakra13`, `arial10`.
- Interpretation:
  - The simulated guard passes the minimal target-physical width-safety check: it avoids the known `arial13` regression while preserving or improving width error for every sample.
  - It is still not a safe Java implementation plan by itself. The guard input `physicalInkRightFrac` depends on the final draw position, but the current source mutation path (`applySourceRightCutoff`) runs while building a reusable texture cache entry. Applying the guard there could make a position-specific source texture leak to another draw of the same text/style at a different position.
  - The next implementation must be draw-position-safe: either a final draw-time guarded crop/classification path that leaves cached source textures unchanged, or a diagnostic cache key/refactor that includes the relevant projected position so source mutation cannot be reused incorrectly.
  - Full `devtools/resource.html` was intentionally not run because this subtask was a minimal-fixture simulation and did not change rendering behavior.
- Verification:
  - The diagnostic script completed and wrote `run/resource-browser-font-runtime-guarded-width-right-frac-0p75-target-physical-all.log`.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not run because this subtask did not edit Java.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The target-physical width guard is now promising, but requires a draw-position-safe implementation before AUI validation.
- Next exact action:
  - Implement exactly one draw-position-safe guarded diagnostic for `physicalInkRightFrac <= 0.75`.
  - Prefer a final draw-time right-edge classification/crop path over source texture mutation, because it can use draw position without contaminating the texture cache.
  - Reject the implementation unless the target-physical minimal fixture reproduces the simulated safety result: no skip sample shrinks, positive-surplus samples improve or stay no worse, and `arial12White` remains on rows `y=927..941`.

Draw-position-safe runtime right-frac cutoff diagnostic evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Final split `pixel-sample` width classification at `thresholdDarkness=20`.
  - Reused Chromium fixture output because `tests/resource-browser-font-raster.html`, browser screenshot, viewport/DPR, font source, crop policy, and sample set were unchanged from the target-physical all-sample oracle.
- Browser-standard source:
  - Chromium metrics log: `run/resource-browser-font-raster-browser-background-pairs-last.log`.
  - Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Browser viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- Fixture/state:
  - Minimal AUI fixture: `tests/resource-browser-font-raster.html`.
  - AUI viewport screenshot dimensions: `2560x1476`, CSS viewport `1463x843`, column-script scale `1.749829,1.75089`.
  - Raster state: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `AA_MODE=on`, `FILTER=linear`, `SOURCE=outline-coverage-4x-row-clamp`, `QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`.
  - Interaction and prompt env vars unset.
- Predeclared rejection metric:
  - Reject if final columns fail to reproduce the simulated guarded-width safety result: no skip sample may shrink, every positive-surplus sample must improve or stay no worse, and `arial12White` must remain on the previously accepted target rows.
- Hypothesis:
  - A draw-time guarded crop using `physicalInkRightFrac <= 0.75` can safely apply the source-edge cutoff decision without mutating reusable cached textures.
  - The diagnostic should use runtime draw position and immutable `TextureStats` source ink bounds, then draw only through the source right edge before the last cutoff column.
- Source changes:
  - Added diagnostic mode in `src/main/java/com/sighs/apricityui/render/FontDrawer.java`: `snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`.
  - Added draw-time `drawRuntimeRightFracCutoff(...)` path. It leaves cached source textures unchanged and uses `TextureStats` plus final draw position to classify/apply the crop.
  - Added `[AUI FontRaster] runtimeRightFracCutoff` projection log rows with threshold, `rightFrac`, `apply`, `sourceRightExclusive`, and `croppedDrawW`.
  - No HTML or `resource.html` edits.
- Commands:
  - Java compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - Minimal AUI run:
    - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`
    - `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`
    - `APRICITYUI_FONT_RASTER_AA_MODE=on`
    - `APRICITYUI_FONT_RASTER_FILTER=linear`
    - `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`
    - `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`
    - `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`
    - `APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS=1`
    - `APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM=1`
    - `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-aui-runtime-right-frac-cutoff-0p75-last.log`
  - Column stats: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-runtime-right-frac-cutoff-0p75-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_19.57.50.png' -OutLog 'run/resource-browser-font-raster-columns-runtime-right-frac-cutoff-0p75-all.log' -SampleIds <13 samples> -GlyphDarknessThreshold 20`.
  - Full-page AUI promotion run:
    - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`
    - same raster state as minimal fixture, with projection logging enabled and texture/histogram logging unset
    - `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-full-aui-runtime-right-frac-cutoff-0p75-last.log`
  - Verification: `git diff --check`.
- Results:
  - `compileJava` succeeded.
  - Minimal AUI run succeeded and auto-closed.
  - Runtime guard classification matched the prior 13-sample proxy: skip for `arial13`, `arial13Plain`, `arial13White`, `arial13Fafafa`; apply for the other 9 samples.
  - Final width table from `run/resource-browser-font-raster-columns-runtime-right-frac-cutoff-0p75-all.log`:
    - `arial13`: Chromium `97`, AUI `97`, delta `0`.
    - `arial13Plain`: Chromium `87`, AUI `86`, delta `-1`; unchanged existing undershoot, not a guard shrink.
    - `sans13`: Chromium `92`, AUI `92`, delta `0`.
    - `chakra13`: Chromium `92`, AUI `92`, delta `0`.
    - `arial10`: Chromium `61`, AUI `61`, delta `0`.
    - `arial12`: Chromium `361`, AUI `361`, delta `0`.
    - `chakra12`: Chromium `336`, AUI `336`, delta `0`.
    - `arial13White`: Chromium `97`, AUI `97`, delta `0`.
    - `arial13Fafafa`: Chromium `97`, AUI `97`, delta `0`.
    - `arial12White`: Chromium `361`, AUI `361`, delta `0`.
    - `arial12Fafafa`: Chromium `361`, AUI `361`, delta `0`.
    - `chakra12White`: Chromium `336`, AUI `336`, delta `0`.
    - `chakra12Fafafa`: Chromium `336`, AUI `336`, delta `0`.
  - Summary: `exact=12/13`, `positiveDeltas=none`, `negativeDeltas=arial13Plain:-1`.
  - Full `devtools/resource.html` run succeeded and auto-closed; latest full-page screenshot: `run/screenshots/aui/2026-07-15_19.59.39.png`.
- Artifacts:
  - Minimal AUI log: `run/resource-browser-font-raster-aui-runtime-right-frac-cutoff-0p75-last.log`.
  - Minimal AUI screenshot: `run/screenshots/aui/2026-07-15_19.57.50.png`.
  - Minimal columns: `run/resource-browser-font-raster-columns-runtime-right-frac-cutoff-0p75-all.log`.
  - Full-page AUI log: `run/resource-browser-full-aui-runtime-right-frac-cutoff-0p75-last.log`.
  - Full-page AUI screenshot: `run/screenshots/aui/2026-07-15_19.59.39.png`.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The draw-position-safe diagnostic passes the target-physical minimal fixture width-safety gate and improves on the simulated result: it reaches 12/13 exact width matches instead of 9/13.
  - Remaining minimal mismatch: `arial13Plain` stays one pixel narrower than Chromium (`86` vs `87`), but this was a skip sample and was not worsened by the runtime guard.
  - Full-page screenshot evidence exists, but full-page text crop/pixel metrics have not yet been compared against the browser reference for this diagnostic.
- Next exact action:
  - Run or create a full-page browser/AUI text crop metric for `devtools/resource.html` using the same strict Chromium oracle, then compare the new runtime guard full-page screenshot against the current browser reference.
  - Reject promotion if the full-page text crop metrics show a browser-standard regression even though the minimal fixture improved.
  - If full-page text metrics are neutral or improved, decide whether this diagnostic should remain behind `APRICITYUI_FONT_RASTER_QUAD_MODE` or become the target-physical default path.

Full-page runtime right-frac cutoff crop-metric evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Full-page `pixel-sample` and text crop statistics for `devtools/resource.html`.
  - Reused Chromium static browser metrics and screenshot because the target page, static state, viewport, DPR-aligned crop policy, and sampled crop targets were unchanged.
- Browser-standard source:
  - Browser metrics log: `run/resource-browser-browser-static-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png`.
  - Browser CSS viewport: `1463x843`; screenshot dimensions: `2560x1475`; scale `1.749829,1.749703`.
- Fixture/state:
  - Full AUI page: `devtools/resource.html`.
  - AUI browser-mode viewport: `1463x843`.
  - Same raster state for both AUI runs except quad mode:
    - `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`
    - `APRICITYUI_FONT_RASTER_AA_MODE=on`
    - `APRICITYUI_FONT_RASTER_FILTER=linear`
    - `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`
  - Baseline quad mode: `snap-physical-y-texture-gutter-1`.
  - Candidate quad mode: `snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`.
  - Interaction and prompt env vars unset.
- Predeclared rejection metric:
  - Reject the runtime guard as a promotion candidate if the full-page text crop metrics show a browser-standard regression relative to the same-env `snap-physical-y-texture-gutter-1` baseline, despite the minimal fixture improvement.
  - Use the existing full-page crop metric script as a coarse promotion check only; it samples `contentCount`, first normal file `fileMeta`, and `detailEmpty`.
- Hypothesis:
  - The draw-position-safe runtime right-frac crop should not regress full-page text crop statistics relative to the same target-physical transparent-gutter baseline.
- Source changes:
  - No Java, HTML, script, or `resource.html` changes in this subtask.
  - This document was updated with full-page evidence.
- Commands:
  - Runtime guard full-page crop metric:
    - `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_19.59.39.png' -BrowserMetricsLog 'run/resource-browser-browser-static-last.log' -AuiMetricsLog 'run/resource-browser-full-aui-runtime-right-frac-cutoff-0p75-last.log' -OutLog 'run/resource-browser-full-gray-text-stats-runtime-right-frac-cutoff-0p75.log'`.
  - Same-env full-page baseline AUI run:
    - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`
    - `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`
    - `APRICITYUI_FONT_RASTER_AA_MODE=on`
    - `APRICITYUI_FONT_RASTER_FILTER=linear`
    - `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`
    - `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1`
    - `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`
    - `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-full-aui-target-physical-texture-gutter-1-baseline-last.log`
  - Same-env baseline full-page crop metric:
    - `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_20.05.00.png' -BrowserMetricsLog 'run/resource-browser-browser-static-last.log' -AuiMetricsLog 'run/resource-browser-full-aui-target-physical-texture-gutter-1-baseline-last.log' -OutLog 'run/resource-browser-full-gray-text-stats-target-physical-texture-gutter-1-baseline.log'`.
  - Verification: `git diff --check`.
- Results:
  - Baseline full-page AUI run succeeded and auto-closed.
  - Baseline full-page screenshot: `run/screenshots/aui/2026-07-15_20.05.00.png`, dimensions `2560x1476`, scale `1.749829,1.75089`.
  - Runtime guard full-page crop log: `run/resource-browser-full-gray-text-stats-runtime-right-frac-cutoff-0p75.log`.
  - Same-env baseline full-page crop log: `run/resource-browser-full-gray-text-stats-target-physical-texture-gutter-1-baseline.log`.
  - Browser crop targets:
    - `contentCount`: ink `682`, coverage `0.208946`, avgInkDarkness `60.85`.
    - `fileMeta`: ink `413`, coverage `0.075724`, avgInkDarkness `59.35`.
    - `detailEmpty`: ink `2212`, coverage `0.168341`, avgInkDarkness `60.28`.
  - Same-env baseline AUI:
    - `contentCount`: ink `697`, coverage `0.213542`, avgInkDarkness `62.59`.
    - `fileMeta`: ink `882`, coverage `0.167936`, avgInkDarkness `144.55`.
    - `detailEmpty`: ink `1776`, coverage `0.130800`, avgInkDarkness `73.41`.
  - Runtime guard AUI:
    - `contentCount`: ink `691`, coverage `0.211703`, avgInkDarkness `62.61`.
    - `fileMeta`: ink `879`, coverage `0.167365`, avgInkDarkness `144.75`.
    - `detailEmpty`: ink `1768`, coverage `0.130211`, avgInkDarkness `73.46`.
  - Runtime-vs-baseline interpretation:
    - `contentCount` coverage moved closer to browser (`0.213542 -> 0.211703`, browser `0.208946`); darkness was effectively neutral (`62.59 -> 62.61`, browser `60.85`).
    - `fileMeta` coverage moved very slightly closer to browser (`0.167936 -> 0.167365`, browser `0.075724`); darkness was effectively neutral but still far from browser (`144.55 -> 144.75`, browser `59.35`).
    - `detailEmpty` coverage moved slightly farther from browser (`0.130800 -> 0.130211`, browser `0.168341`); darkness was effectively neutral but still too dark (`73.41 -> 73.46`, browser `60.28`).
    - The runtime guard did not introduce a meaningful full-page crop regression relative to same-env transparent-gutter baseline, but full-page text rasterization still has major non-edge mismatches outside the minimal right-edge width issue.
- Artifacts:
  - Runtime full-page AUI log: `run/resource-browser-full-aui-runtime-right-frac-cutoff-0p75-last.log`.
  - Runtime full-page AUI screenshot: `run/screenshots/aui/2026-07-15_19.59.39.png`.
  - Runtime full-page crop stats: `run/resource-browser-full-gray-text-stats-runtime-right-frac-cutoff-0p75.log`.
  - Same-env baseline full-page AUI log: `run/resource-browser-full-aui-target-physical-texture-gutter-1-baseline-last.log`.
  - Same-env baseline full-page AUI screenshot: `run/screenshots/aui/2026-07-15_20.05.00.png`.
  - Same-env baseline full-page crop stats: `run/resource-browser-full-gray-text-stats-target-physical-texture-gutter-1-baseline.log`.
- Verification:
  - Baseline full-page `runClient` completed successfully.
  - Both full-page crop metric scripts completed and wrote the named logs.
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` was not rerun because this subtask did not edit Java.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The runtime right-frac diagnostic has now passed the minimal width gate and a coarse full-page no-regression check.
  - It should still not be promoted blindly as the final text-rasterization answer: full-page `fileMeta` and `detailEmpty` retain large browser-standard crop mismatches, and the full-page script is a coarse crop statistic rather than a complete visual diff.
- Next exact action:
  - Promote the draw-position-safe runtime right-frac crop from a diagnostic environment mode toward a default target-physical path only behind a narrow condition: target-physical text with texture gutter and runtime source-ink bounds available.
  - Before changing defaults, add or identify one regression fixture that draws the same text/style at two different x positions so the position-dependent guard cannot leak through cache reuse; reject promotion if either draw incorrectly reuses the other draw's cutoff decision.
  - Keep the broader non-edge full-page text darkness/coverage mismatches in `RBV-V5-05` for a later subtask after the cache-safety/default-promotion decision.

Runtime right-frac cache-safety regression fixture evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` column width at `thresholdDarkness=20`.
  - Chromium also serves as the browser-standard no-cache-leak oracle: two identical text/style samples at different x positions must be evaluated independently.
- Browser-standard source:
  - Added fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster-cache-safety.html`.
  - Chromium metrics log: `run/resource-browser-font-raster-cache-safety-browser-last.log`.
  - Chromium screenshot: `run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png`.
  - Browser viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
- Fixture/state:
  - Two `.sample` elements use identical text and style: `SELECT FILE TO VIEW DETAILS`, Arial `12px`, `letter-spacing: 1px`, `#999999` on white.
  - `cacheApply` is positioned at `x=114`.
  - `cacheSkip` is positioned at `x=114.5`.
  - AUI raster state: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `AA_MODE=on`, `FILTER=linear`, `SOURCE=outline-coverage-4x-row-clamp`, `QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`, projection logging enabled.
- Predeclared rejection metric:
  - Reject default promotion if either same-text draw incorrectly reuses the other draw's runtime cutoff decision through the texture cache.
  - Also reject default promotion if the new browser oracle proves a skipped position still needs the cutoff to match Chromium.
- Hypothesis:
  - Because the cutoff is implemented at draw time and cached textures are immutable, two identical cached text/style entries at different x positions should log independent runtime decisions.
  - If `physicalInkRightFrac <= 0.75` is robust enough for default promotion, both positions should also match browser final width.
- Source changes:
  - Added `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster-cache-safety.html`.
  - Updated `scripts/resource_browser_font_raster_metrics.js` to allow `RESOURCE_BROWSER_FONT_RASTER_DOC_PATH` and `RESOURCE_BROWSER_FONT_RASTER_SCREENSHOT`, preserving the old defaults.
  - Updated `src/main/java/com/sighs/apricityui/event/Test.java` so the cache-safety fixture reuses the existing `resource-browser-font-raster` AUI metrics logger.
  - No `resource.html` edits.
- Commands:
  - Compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - Browser oracle:
    - `RESOURCE_BROWSER_FONT_RASTER_DOC_PATH=resource-browser-font-raster-cache-safety.html`
    - `RESOURCE_BROWSER_FONT_RASTER_SCREENSHOT=resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png`
    - `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-cache-safety-browser-last.log`
  - AUI runtime guard run:
    - `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster-cache-safety.html`
    - `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`
    - `APRICITYUI_FONT_RASTER_AA_MODE=on`
    - `APRICITYUI_FONT_RASTER_FILTER=linear`
    - `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`
    - `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75`
    - `APRICITYUI_FONT_RASTER_LOG_PROJECTION=1`
    - `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log`
  - Column stats: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-cache-safety-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_20.11.12.png' -OutLog 'run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-cutoff-0p75.log' -SampleIds 'cacheApply,cacheSkip' -GlyphDarknessThreshold 20`.
  - Verification: `git diff --check`.
- Results:
  - `compileJava` succeeded.
  - Browser oracle completed and wrote the named metrics log and screenshot.
  - AUI run succeeded and auto-closed.
  - Runtime decision log:
    - `cacheApply`: `cssPosition=114.0,115.875`, `physicalInkRight=562.100006`, `rightFrac=0.100006`, `apply=true`.
    - `cacheSkip`: `cssPosition=114.5,161.875`, `physicalInkRight=562.975006`, `rightFrac=0.975006`, `apply=false`.
  - This proves the draw-time implementation does not leak the first sample's cutoff decision through the shared cached texture.
  - Final column widths:
    - `cacheApply`: Chromium `361`, AUI `361`, delta `0`.
    - `cacheSkip`: Chromium `361`, AUI `362`, delta `+1`.
- Artifacts:
  - Fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster-cache-safety.html`.
  - Browser log: `run/resource-browser-font-raster-cache-safety-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png`.
  - AUI log: `run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_20.11.12.png`.
  - Column stats: `run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-cutoff-0p75.log`.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - Cache safety passes: the draw-time runtime guard makes independent apply/skip decisions for the same cached source texture at different draw positions.
  - Default promotion is rejected for now: the browser oracle shows `cacheSkip` should still be `361px` wide, but `physicalInkRightFrac <= 0.75` skips the cutoff at `rightFrac=0.975006`, leaving AUI `362px`.
  - The `0.75` threshold was sufficient for the earlier 13-sample fixture positions, but this new position sweep proves it is not a browser-standard runtime rule by itself.
- Next exact action:
  - Keep `snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75` as a diagnostic mode, not a default.
  - Search for a runtime-available classifier that handles both the original 13-sample set and this same-text shifted-position fixture. The next candidate should distinguish shifted `Arial 12px` (`rightFrac=0.975006`, still needs cutoff) from preserved `Arial 13px` skip samples (`rightFrac≈0.94`, no cutoff) without using browser final width.
  - Reject the next classifier unless it matches browser width decisions for the 13-sample fixture and the two cache-safety samples.

Runtime classifier probe evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` final column width decision at `thresholdDarkness=20`.
  - The browser oracle is reused, not rerun, because the fixture files, viewport/DPR state, target-physical raster configuration, sample IDs, and crop policy are unchanged.
- Browser-standard source:
  - Original 13-sample Chromium fixture: `run/resource-browser-font-raster-browser-background-pairs-last.log` and `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`.
  - Original 13-sample target-physical width-surplus oracle: `run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log`.
  - Cache-safety Chromium fixture: `run/resource-browser-font-raster-cache-safety-browser-last.log` and `run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png`.
  - Cache-safety column oracle: `run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-cutoff-0p75.log`.
- Fixture/state:
  - Original sample set: `arial13,arial13Plain,sans13,chakra13,arial10,arial12,chakra12,arial13White,arial13Fafafa,arial12White,arial12Fafafa,chakra12White,chakra12Fafafa`.
  - Cache-safety sample set: `cacheApply,cacheSkip`.
  - Browser viewport for both fixtures: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
  - AUI feature source logs: original target-physical projection log `run/resource-browser-font-raster-aui-source-outline-coverage-4x-row-clamp-quad-snap-y-texture-gutter-1-projection-rerun-last.log`; cache-safety projection log `run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-cutoff-0p75-last.log`.
- Predeclared rejection metric:
  - Reject the next classifier unless it matches browser width decisions for all 15 samples without using browser final width as a runtime input.
- Hypothesis:
  - A draw-time runtime classifier can distinguish the shifted long Arial `12px` text that still needs source-edge cutoff from Arial `13px` skip samples by combining final physical right-edge fraction with runtime-available source geometry.
  - Candidate rule tested: apply cutoff when `physicalInkRightFrac <= 0.75 OR (fontSize <= 12 && sourceInkWidth >= 300)`.
- Source changes:
  - Added `scripts/resource_browser_font_runtime_classifier_probe.ps1`.
  - Updated this TODO's automation cursor to make the classifier probe the latest evidence.
  - No Java edits.
  - No `resource.html` edits.
- Commands:
  - Probe: `.\scripts\resource_browser_font_runtime_classifier_probe.ps1`.
  - Verification: `git diff --check`.
  - Compile: skipped because this subtask only added a PowerShell diagnostic script and documentation.
  - AUI minimal: skipped because this subtask was the required offline classifier gate before Java diagnostic implementation.
  - AUI full page: skipped because no Java rendering behavior changed and minimal Java validation has not run.
- Results:
  - Probe output: `run/resource-browser-font-runtime-classifier-probe.log`.
  - Rejected baseline rule: `rightFracLe0p75` matched `14/15`, mismatches `1`, accepted `False`.
  - The mismatch was `cacheSkip`: oracle decision `apply`, `rightFracLe0p75` decision `skip`, Chromium width `361`, observed AUI width `362`, `rightFrac=0.975006`.
  - Accepted diagnostic candidate: `rightFracLe0p75OrLong12pxSource` matched `15/15`, mismatches `0`, accepted `True`.
  - The candidate preserved Arial `13px` skip decisions at `rightFrac≈0.94` and applied cutoff to shifted Arial `12px` long text at `rightFrac=0.975006` because `fontSize=12` and `sourceInkWidth=362`.
- Artifacts:
  - Probe script: `scripts/resource_browser_font_runtime_classifier_probe.ps1`.
  - Probe log: `run/resource-browser-font-runtime-classifier-probe.log`.
- Verification:
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The classifier is accepted only as an offline diagnostic candidate. It is not default framework behavior and has not yet been validated by a Java rendering run.
  - The rule is still heuristic and runtime-feature-based, not a normative browser text-rasterization explanation; promotion must wait for draw-time Java diagnostic validation on both minimal fixtures.
- Cursor update:
  - Changed to the Java diagnostic implementation of `rightFrac <= 0.75 OR (fontSize <= 12 && sourceInkWidth >= 300)` because the offline classifier gate matched all 15 browser-oracle decisions.
- Next exact action:
  - Implement exactly one draw-position-safe diagnostic quad mode for the accepted classifier, keeping cached source textures immutable and applying the one-column cutoff only at draw time.
  - Validate first on the original 13-sample fixture and the cache-safety fixture through `Test.java`.
  - Reject the Java diagnostic if either fixture regresses against the same Chromium width oracle. Run full `devtools/resource.html` only after both minimal fixtures pass without regression.

Runtime classifier Java diagnostic evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` final column width at `thresholdDarkness=20`.
  - Full-page coarse `pixel-sample` gray text crop check for promotion no-regression evidence.
- Browser-standard source:
  - Reused original 13-sample Chromium fixture: `run/resource-browser-font-raster-browser-background-pairs-last.log`, `run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png`, and target-physical width-surplus oracle `run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log`.
  - Reused cache-safety Chromium fixture: `run/resource-browser-font-raster-cache-safety-browser-last.log`, `run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png`, and `run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-cutoff-0p75.log`.
  - Reused full-page Chromium static reference: `run/resource-browser-browser-static-last.log` and `run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png`.
- Fixture/state:
  - AUI raster state: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `AA_MODE=on`, `FILTER=linear`, `SOURCE=outline-coverage-4x-row-clamp`, `QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff`, projection logging enabled.
  - Original sample set: `arial13,arial13Plain,sans13,chakra13,arial10,arial12,chakra12,arial13White,arial13Fafafa,arial12White,arial12Fafafa,chakra12White,chakra12Fafafa`.
  - Cache-safety sample set: `cacheApply,cacheSkip`.
  - Full-page sample crops: `contentCount`, `fileMeta`, `detailEmpty`.
- Predeclared rejection metric:
  - Reject the Java diagnostic if either minimal fixture regresses against the same Chromium width oracle.
  - Run full `devtools/resource.html` only after both minimal fixtures pass without regression.
- Hypothesis:
  - The offline-accepted runtime classifier can be implemented at draw time without mutating cached source textures: apply one source-right-column cutoff when `physicalInkRightFrac <= 0.75 OR (fontSize <= 12 && sourceInkWidth >= 300)`.
- Source changes:
  - Updated `src/main/java/com/sighs/apricityui/render/FontDrawer.java`.
  - Added diagnostic quad mode aliases for `snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff`.
  - Extended runtime guard logging with `long12pxSource=...`.
  - No `resource.html` edits.
- Commands:
  - Compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - Original minimal AUI: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster.html`, target-physical raster env above, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Original columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-browser-background-pairs-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_20.24.40.png' -OutLog 'run/resource-browser-font-raster-columns-runtime-right-frac-or-long-12px-source-cutoff-all.log' -SampleIds 'arial13,arial13Plain,sans13,chakra13,arial10,arial12,chakra12,arial13White,arial13Fafafa,arial12White,arial12Fafafa,chakra12White,chakra12Fafafa' -GlyphDarknessThreshold 20`.
  - Cache-safety AUI: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster-cache-safety.html`, same raster env, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-cache-safety-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Cache-safety columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-cache-safety-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-cache-safety-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_20.26.14.png' -OutLog 'run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-or-long-12px-source-cutoff.log' -SampleIds 'cacheApply,cacheSkip' -GlyphDarknessThreshold 20`.
  - Full page AUI: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, same raster env, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-full-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Full page crop stats: `.\scripts\resource_browser_full_gray_text_stats.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-direct-static-1463x843-dsf-aui.png' -AuiImage 'run/screenshots/aui/2026-07-15_20.28.12.png' -BrowserMetricsLog 'run/resource-browser-browser-static-last.log' -AuiMetricsLog 'run/resource-browser-full-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log' -OutLog 'run/resource-browser-full-gray-text-stats-runtime-right-frac-or-long-12px-source-cutoff.log'`.
  - Verification: `git diff --check`.
- Results:
  - `compileJava` succeeded.
  - Original 13-sample run succeeded and auto-closed. AUI screenshot: `run/screenshots/aui/2026-07-15_20.24.40.png`.
  - Original width results: `12/13` exact Chromium width; only `arial13Plain` remained the pre-existing `-1` undershoot (`browser=87`, `AUI=86`). No new positive width surplus remained.
  - Cache-safety run succeeded and auto-closed. AUI screenshot: `run/screenshots/aui/2026-07-15_20.26.14.png`.
  - Cache-safety runtime decisions: `cacheApply` `rightFrac=0.100006 long12pxSource=true apply=true`; `cacheSkip` `rightFrac=0.975006 long12pxSource=true apply=true`.
  - Cache-safety width results: `cacheApply` Chromium `361`, AUI `361`, delta `0`; `cacheSkip` Chromium `361`, AUI `361`, delta `0`.
  - Full page run succeeded and auto-closed. AUI screenshot: `run/screenshots/aui/2026-07-15_20.28.12.png`.
  - Full page crop stats:
    - `contentCount`: browser coverage `0.208946`, AUI coverage `0.211703`; browser avgInkDarkness `60.85`, AUI `62.61`.
    - `fileMeta`: browser coverage `0.075724`, AUI coverage `0.167365`; browser avgInkDarkness `59.35`, AUI `144.75`.
    - `detailEmpty`: browser coverage `0.168341`, AUI coverage `0.130211`; browser avgInkDarkness `60.28`, AUI `73.46`.
  - Full page crop interpretation: no meaningful regression compared with the prior runtime-right-frac full-page crop, but large non-edge text darkness/coverage mismatches remain in `fileMeta` and `detailEmpty`.
- Artifacts:
  - Java diagnostic source: `src/main/java/com/sighs/apricityui/render/FontDrawer.java`.
  - Original minimal AUI log: `run/resource-browser-font-raster-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Original minimal columns: `run/resource-browser-font-raster-columns-runtime-right-frac-or-long-12px-source-cutoff-all.log`.
  - Cache-safety AUI log: `run/resource-browser-font-raster-cache-safety-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Cache-safety columns: `run/resource-browser-font-raster-cache-safety-columns-runtime-right-frac-or-long-12px-source-cutoff.log`.
  - Full-page AUI log: `run/resource-browser-full-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Full-page crop stats: `run/resource-browser-full-gray-text-stats-runtime-right-frac-or-long-12px-source-cutoff.log`.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - All three `runClient` runs completed successfully and auto-closed.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - The Java diagnostic passed the current 15-sample minimal browser-width gate and coarse full-page no-regression check.
  - It should not be promoted as default yet because the accepted rule is a runtime-feature heuristic. It needs an expanded browser-standard sweep to prove it is not overfitting this page's current long Arial `12px` cases.
- Cursor update:
  - Changed to expanded browser oracle work before default promotion.
- Next exact action:
  - Add or run a minimal position/font-size/source-width sweep fixture for the runtime classifier.
  - The sweep must include long Arial `12px` positions around different `rightFrac` values, preserved Arial `13px` samples around `rightFrac≈0.94`, and at least one nearby negative case where `fontSize <= 12 && sourceInkWidth >= 300` should not force cutoff if Chromium does not.
  - Reject default promotion if any expanded-sweep sample disagrees with Chromium. Keep `snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff` diagnostic-only until that expanded oracle passes.

Classifier sweep rejection evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` final text-column width at `thresholdDarkness=20`.
- Browser-standard source:
  - New Chromium sweep fixture output from `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
- Fixture/state:
  - Browser viewport: `innerWidth=1440`, `innerHeight=746`, `devicePixelRatio=1.7498291730880737`.
  - AUI viewport: `1463x843`; screenshot scale from columns script: `1.749829,1.75089`.
  - Sample set: `long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625`.
  - AUI raster state: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `AA_MODE=on`, `FILTER=linear`, `SOURCE=outline-coverage-4x-row-clamp`, `QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff`, projection logging enabled.
- Predeclared rejection metric:
  - Reject default promotion if any sweep sample disagrees with Chromium final width at `thresholdDarkness=20`.
- Hypothesis:
  - The 15-sample diagnostic classifier `rightFrac <= 0.75 OR (fontSize <= 12 && sourceInkWidth >= 300)` might generalize across nearby `12px` long-text, near-long `12px`, and preserved `13px` subpixel positions.
- Commands:
  - Browser: `RESOURCE_BROWSER_FONT_RASTER_DOC_PATH=resource-browser-font-raster-classifier-sweep.html`, `RESOURCE_BROWSER_FONT_RASTER_SCREENSHOT=resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`, then `node scripts\resource_browser_font_raster_metrics.js | Tee-Object -FilePath run\resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - AUI minimal: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster-classifier-sweep.html`, raster env above, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-classifier-sweep-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-classifier-sweep-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-classifier-sweep-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_20.37.50.png' -OutLog 'run/resource-browser-font-raster-classifier-sweep-columns-runtime-right-frac-or-long-12px-source-cutoff.log' -SampleIds 'long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625' -GlyphDarknessThreshold 20`.
  - AUI full page: skipped because the minimal sweep rejected the classifier.
  - Verification: `git diff --check`.
- Results:
  - Browser fixture ran and wrote the expected `BROWSER_FONT_RASTER_METRICS` payload plus screenshot.
  - `compileJava` succeeded after adding the fixture path to `Test.java`.
  - AUI sweep run succeeded and auto-closed. AUI screenshot: `run/screenshots/aui/2026-07-15_20.37.50.png`.
  - Columns result:
    - `long12X114000`: browser `361`, AUI `361`, delta `0`.
    - `long12X114125`: browser `361`, AUI `361`, delta `0`.
    - `long12X114250`: browser `361`, AUI `362`, delta `+1`.
    - `long12X114375`: browser `361`, AUI `362`, delta `+1`.
    - `long12X114500`: browser `361`, AUI `361`, delta `0`.
    - `long12X114625`: browser `361`, AUI `361`, delta `0`.
    - `long12X114750`: browser `362`, AUI `361`, delta `-1`.
    - `mid12X114250`: browser `345`, AUI `346`, delta `+1`.
    - `mid12X114500`: browser `346`, AUI `345`, delta `-1`.
    - `short13X114375`: browser `98`, AUI `97`, delta `-1`.
    - `short13X114500`: browser `98`, AUI `98`, delta `0`.
    - `short13X114625`: browser `98`, AUI `96`, delta `-2`.
  - Rejection result: `7/12` sweep samples mismatched Chromium, so the diagnostic classifier is rejected for default promotion.
  - Runtime guard log confirmed that the current diagnostic applied cutoff to all `long12` and `mid12` samples through `long12pxSource=true`, and also cropped `short13X114375` and `short13X114625` through `rightFrac <= 0.75`; those 13px cases prove the existing `rightFrac` clause is too broad for the expanded sweep.
- Artifacts:
  - Fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster-classifier-sweep.html`.
  - Harness edit: `src/main/java/com/sighs/apricityui/event/Test.java`.
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
  - AUI log: `run/resource-browser-font-raster-classifier-sweep-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_20.37.50.png`.
  - Columns log: `run/resource-browser-font-raster-classifier-sweep-columns-runtime-right-frac-or-long-12px-source-cutoff.log`.
- Source changes:
  - Added `src/main/resources/assets/apricityui/apricity/tests/resource-browser-font-raster-classifier-sweep.html`.
  - Updated `src/main/java/com/sighs/apricityui/event/Test.java` so the automated AUI harness logs `.sample` metrics for the new sweep fixture.
  - Updated this TODO cursor/evidence.
  - No `resource.html` edits.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - `.\gradlew.bat runClient --console plain --no-daemon --offline` succeeded and auto-closed through `Test.java`.
  - `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - `snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff` remains diagnostic-only and is rejected for default promotion.
  - Full `devtools/resource.html` was intentionally skipped because the minimal browser-standard sweep failed.
- Cursor update:
  - Changed from Java diagnostic promotion testing to offline classifier revision against original + cache-safety + sweep samples.
- Next exact action:
  - Extend `scripts/resource_browser_font_runtime_classifier_probe.ps1` with the 12 classifier-sweep samples, using browser widths from `run/resource-browser-font-raster-classifier-sweep-browser-last.log`/columns log and runtime features from `run/resource-browser-font-raster-classifier-sweep-aui-runtime-right-frac-or-long-12px-source-cutoff-last.log`.
  - Search for one stricter runtime-available classifier that matches the original 13-sample fixture, the two cache-safety samples, and the 12-sample sweep without using browser final width as a runtime input.
  - Do not edit Java or run full `devtools/resource.html` until the offline probe accepts a revised candidate.

Offline classifier probe with sweep evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` final text-column width at `thresholdDarkness=20`.
- Browser-standard source:
  - Reused Chromium sweep output from `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
  - Reuse reason: fixture, browser screenshot, glyph darkness threshold, viewport, DPR, and sample IDs are unchanged from `Classifier sweep rejection evidence`.
- Fixture/state:
  - Original fixture group: 13 target-physical font-raster samples from `run/resource-browser-font-width-surplus-guard-source-cutoff-1-target-physical-all.log`.
  - Cache-safety group: `cacheApply`, `cacheSkip`.
  - Classifier sweep group: `long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625`.
  - Sweep baseline side input: `run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log`.
  - Sweep apply side input: `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1-rerun.log`.
  - Ignored invalid side input: `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1.log`, because it used a `1x1` screenshot.
- Predeclared rejection metric:
  - Reject a classifier if it mismatches any decisive browser-oracle apply/skip row.
  - Reject promotion if any sweep row is `ambiguousBad`, meaning neither baseline nor the current `source-cutoff-1` apply action can reach the Chromium width.
- Hypothesis:
  - A stricter runtime-available predicate can fix the expanded sweep decision errors, but the current `source-cutoff-1` apply action may still be insufficient for some browser widths.
- Commands:
  - Probe: `.\scripts\resource_browser_font_runtime_classifier_probe.ps1 -OutLog run/resource-browser-font-runtime-classifier-probe-with-sweep.log`.
  - AUI minimal: skipped because this run only extended the offline probe and reused existing AUI baseline/cutoff logs.
  - AUI full page: skipped because no classifier/action pair passed the minimal promotion gate.
  - Verification: `git diff --check -- scripts/resource_browser_font_runtime_classifier_probe.ps1 doc/guide/resource-browser-visual-todo-2026-07-15.md`.
- Results:
  - The probe script now loads original 13 + cache-safety 2 + classifier sweep 12.
  - Old rule `rightFracLe0p75`: `21/26` decisive rows matched, `5` mismatches, `1` `ambiguousBad`, accepted `False`.
  - Old rule `rightFracLe0p75OrLong12pxSource`: `23/26` decisive rows matched, `3` mismatches, `1` `ambiguousBad`, accepted `False`.
  - New diagnostic `strictRuntimeV1`: `26/26` decisive rows matched, `0` decision mismatches, but `1` `ambiguousBad`, accepted `False`.
  - `strictRuntimeV1` fixes the decision-level failures for the known skip rows:
    - `mid12X114500`: skip, chosen width `346`, browser width `346`.
    - `short13X114375`: skip, chosen width `98`, browser width `98`.
    - `short13X114625`: skip, chosen width `97`, browser width `98`; decision is correct, but baseline remains `-1`.
  - The unresolved blocker is `long12X114625`: browser width `361`, baseline width `362`, current `source-cutoff-1` width `362`; both sides are `1px` too wide, so no classifier using only the current skip/apply actions can pass the sweep.
  - Decisive apply rows still prove current `source-cutoff-1` is not enough to reach exact Chromium width for several cases: `long12X114125`, `long12X114250`, `long12X114375`, and `mid12X114250` choose cutoff but remain `+1`.
- Artifacts:
  - Updated script: `scripts/resource_browser_font_runtime_classifier_probe.ps1`.
  - Probe log: `run/resource-browser-font-runtime-classifier-probe-with-sweep.log`.
- Source changes:
  - Extended `scripts/resource_browser_font_runtime_classifier_probe.ps1` with sweep parameters, sweep row construction, decisive/ambiguous oracle labels, chosen-width error reporting, and `strictRuntimeV1`.
  - Updated this TODO cursor/evidence.
  - No Java edits.
  - No `resource.html` edits.
- Verification:
  - `git diff --check -- scripts/resource_browser_font_runtime_classifier_probe.ps1 doc/guide/resource-browser-visual-todo-2026-07-15.md` succeeded with no output.
  - `compileJava` was not run because no Java file changed.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - Classifier selection has progressed: `strictRuntimeV1` is a diagnostic candidate with no decisive apply/skip mismatches.
  - Promotion is still rejected because the current `source-cutoff-1` apply action is not strong or precise enough for the sweep width oracle.
- Cursor update:
  - Changed from classifier predicate search to apply-action/magnitude validation.
- Next exact action:
  - Add or run one minimal offline/AUI diagnostic for a revised browser-standard apply action on the classifier sweep.
  - The first target must explain `long12X114625` where Chromium is `361` but both baseline and `source-cutoff-1` are `362`.
  - Reject the revised action if it regresses decisive skip rows (`mid12X114500`, `short13X114375`, `short13X114500`, `short13X114625`) or previously exact apply rows (`long12X114000`, `long12X114500`, `long12X114750`).
  - Do not run full `devtools/resource.html` or promote `strictRuntimeV1` until the minimal sweep has an accepted classifier/action pair.

Source-cutoff-2 apply-action evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Minimal fixture `pixel-sample` final text-column width at `thresholdDarkness=20`.
- Browser-standard source:
  - Reused Chromium sweep output from `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
  - Reuse reason: fixture, browser screenshot, glyph darkness threshold, viewport, DPR, and sample IDs are unchanged from the previous sweep evidence.
- Fixture/state:
  - AUI fixture: `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Raster env: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `AA_MODE=on`, `FILTER=linear`, `SOURCE=outline-coverage-4x-row-clamp`, `QUAD_MODE=snap-physical-y-texture-gutter-1-source-cutoff-2`, projection logging enabled.
  - AUI screenshot used for columns: `run/screenshots/aui/2026-07-15_21.00.00.png`.
  - Existing comparison logs:
    - Baseline: `run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log`.
    - `source-cutoff-1`: `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1-rerun.log`.
    - `source-cutoff-2`: `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-2.log`.
- Predeclared rejection metric:
  - Reject global `source-cutoff-2` if it regresses any previously exact apply row or decisive skip row.
  - Reject promotion if the best available action set still cannot reach all Chromium widths.
- Hypothesis:
  - Cutting two source columns might fix `long12X114625`, where `source-cutoff-1` stayed `+1`, and might establish whether the remaining problem is action magnitude or action selection.
- Commands:
  - Java edit: added diagnostic quad mode `snap-physical-y-texture-gutter-1-source-cutoff-2`.
  - Compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - AUI minimal: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster-classifier-sweep.html`, raster env above, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-classifier-sweep-aui-source-cutoff-2-last.log`.
  - Columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-classifier-sweep-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-classifier-sweep-aui-source-cutoff-2-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_21.00.00.png' -OutLog 'run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-2.log' -SampleIds 'long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625' -GlyphDarknessThreshold 20`.
  - Apply-action probe: `.\scripts\resource_browser_font_apply_action_probe.ps1 -OutLog run/resource-browser-font-apply-action-probe-source-cutoff-2.log`.
  - AUI full page: skipped because no minimal classifier/action pair passed.
  - Verification: `git diff --check -- src/main/java/com/sighs/apricityui/render/FontDrawer.java scripts/resource_browser_font_apply_action_probe.ps1 doc/guide/resource-browser-visual-todo-2026-07-15.md`.
- Results:
  - `compileJava` succeeded.
  - AUI minimal run succeeded and auto-closed through `Test.java`.
  - `source-cutoff-2` width results:
    - `long12X114000`: browser `361`, cutoff2 `360`, delta `-1`; regressed from cutoff1 exact.
    - `long12X114125`: browser `361`, cutoff2 `361`, delta `0`; improved from cutoff1 `+1`.
    - `long12X114250`: browser `361`, cutoff2 `361`, delta `0`; improved from cutoff1 `+1`.
    - `long12X114375`: browser `361`, cutoff2 `361`, delta `0`; improved from cutoff1 `+1`.
    - `long12X114500`: browser `361`, cutoff2 `360`, delta `-1`; regressed from cutoff1 exact.
    - `long12X114625`: browser `361`, cutoff2 `361`, delta `0`; fixed the previous ambiguous-bad row.
    - `long12X114750`: browser `362`, cutoff2 `361`, delta `-1`; regressed from cutoff1 exact.
    - `mid12X114250`: browser `345`, cutoff2 `345`, delta `0`; improved from cutoff1 `+1`.
    - `mid12X114500`: browser `346`, cutoff2 `344`, delta `-2`; skip/baseline remains exact.
    - `short13X114375`: browser `98`, cutoff2 `96`, delta `-2`; skip/baseline remains exact.
    - `short13X114500`: browser `98`, cutoff2 `96`, delta `-2`; skip/baseline remains exact.
    - `short13X114625`: browser `98`, cutoff2 `95`, delta `-3`; skip/baseline remains `-1`.
  - Apply-action probe results:
    - `strictRuntimeV1Cutoff1`: `6/12` exact, errorSum `6`, maxError `1`, accepted `False`.
    - `strictRuntimeV1Cutoff2`: `8/12` exact, errorSum `4`, maxError `1`, accepted `False`.
    - `bestAvailableAction`: `11/12` exact, errorSum `1`, maxError `1`, accepted `False`.
  - Interpretation:
    - `source-cutoff-2` is rejected as a global action.
    - The available action set can exactly match all 12px apply/skip sweep rows if the framework can choose between cutoff1 and cutoff2 with a runtime rule.
    - The remaining non-action mismatch is `short13X114625`, where the correct classifier decision is skip, but baseline is already `97` vs Chromium `98`.
- Artifacts:
  - Java diagnostic source: `src/main/java/com/sighs/apricityui/render/FontDrawer.java`.
  - New probe script: `scripts/resource_browser_font_apply_action_probe.ps1`.
  - AUI log: `run/resource-browser-font-raster-classifier-sweep-aui-source-cutoff-2-last.log`.
  - AUI screenshot: `run/screenshots/aui/2026-07-15_21.00.00.png`.
  - Columns log: `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-2.log`.
  - Apply-action probe log: `run/resource-browser-font-apply-action-probe-source-cutoff-2.log`.
- Source changes:
  - Added diagnostic-only quad mode `snap-physical-y-texture-gutter-1-source-cutoff-2` in `FontDrawer.java`.
  - Added `scripts/resource_browser_font_apply_action_probe.ps1`.
  - Updated this TODO cursor/evidence.
  - No `resource.html` edits.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - `.\gradlew.bat runClient --console plain --no-daemon --offline` succeeded and auto-closed through `Test.java`.
  - `git diff --check -- src/main/java/com/sighs/apricityui/render/FontDrawer.java scripts/resource_browser_font_apply_action_probe.ps1 doc/guide/resource-browser-visual-todo-2026-07-15.md` reported only the existing LF/CRLF warning for `FontDrawer.java` and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - `source-cutoff-2` must remain diagnostic-only and is rejected as a default or global apply action.
  - The next target is a runtime-available action selector or promotion gate for cutoff1 vs cutoff2 on 12px rows.
  - The `short13X114625` baseline undershoot is now a deferred residual tracked in `doc/guide/resource-browser-font-short13-baseline-residual.md`.
- Cursor update:
  - Changed from "test stronger apply action" to "derive or reject runtime action selection among proven available actions".
- Next exact action:
  - Extend `scripts/resource_browser_font_apply_action_probe.ps1` with candidate runtime selectors for choosing `skip`, `source-cutoff-1`, or `source-cutoff-2` using only runtime features already logged in the sweep: font size, source ink width/height/y, texture size, `rightFrac`, css/physical position, and text identity class.
  - Reject any selector that fails to match the `bestAvailableAction` table for the non-deferred 12px rows, or that claims to solve the deferred `short13X114625` baseline residual.
  - Do not edit Java defaults or run full `devtools/resource.html` until the selector passes the non-deferred minimal sweep gate.

Short13 baseline residual extraction:

- Active task:
  - `RBV-V5-05`.
- Extraction:
  - Moved the isolated `short13X114625` 13px baseline/skip undershoot into `doc/guide/resource-browser-font-short13-baseline-residual.md`.
  - The residual remains browser-standard evidence, but it is intentionally deferred and must not block the next mainline `RBV-V5-05` run.
- Reason:
  - Runtime selector evidence reached the best available action table (`11/12` exact, total error `1`).
  - The remaining row is not an action-selection failure: `skip` is the correct action, while baseline/skip is already `97` vs Chromium `98`.
  - `source-cutoff-1` and `source-cutoff-2` make that row worse, so it should not drive 12px cutoff selector work.
- Source changes:
  - Added `doc/guide/resource-browser-font-short13-baseline-residual.md`.
  - Updated the top override, quickstart, and this evidence cursor so the next goal run continues mainline font-raster parity instead of reopening the deferred residual.
- Next exact action:
  - Continue the main `RBV-V5-05` path by deciding the next non-deferred `runtime12pxPhysicalPhaseV1` promotion gate or broader font-raster parity subtask.
  - Do not work on `short13X114625` unless the user explicitly reopens `doc/guide/resource-browser-font-short13-baseline-residual.md`.

Non-deferred runtime selector gate evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Reused Chromium final text-column width at `thresholdDarkness=20` from the classifier sweep fixture.
- Browser-standard source:
  - Fixture: `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
  - Reuse reason: fixture, browser screenshot, sample IDs, and glyph darkness threshold are unchanged from the previous action-selector evidence.
- Fixture/state:
  - Non-deferred sample set: `long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500`.
  - Deferred sample excluded from this gate: `short13X114625`, tracked in `doc/guide/resource-browser-font-short13-baseline-residual.md`.
  - Candidate actions: `skip`, `source-cutoff-1`, `source-cutoff-2`.
  - Candidate selector under test: `runtime12pxPhysicalPhaseV1`.
- Predeclared rejection metric:
  - Reject the selector if it fails to match the best available action table for any non-deferred sample.
  - Reject the selector if it claims to solve or hide the deferred `short13X114625` baseline residual.
- Hypothesis:
  - After extracting the known 13px baseline residual, `runtime12pxPhysicalPhaseV1` should be able to choose between `skip`, `source-cutoff-1`, and `source-cutoff-2` for the remaining sweep rows without using browser final width as a runtime input.
- Commands:
  - Probe: `.\scripts\resource_browser_font_apply_action_probe.ps1 -OutLog run\resource-browser-font-apply-action-probe-runtime-selector-nondeferred.log -SampleIds 'long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500'`.
  - AUI minimal: skipped because this run reused existing AUI baseline/cutoff column logs and only evaluated the offline selector gate.
  - AUI full page: skipped because no Java diagnostic has implemented the selector yet.
- Results:
  - `strictRuntimeV1Cutoff1`: `6/11` exact, errorSum `5`, maxError `1`, accepted `False`.
  - `strictRuntimeV1Cutoff2`: `8/11` exact, errorSum `3`, maxError `1`, accepted `False`.
  - `runtime12pxFracBandV1`: `10/11` exact, errorSum `1`, maxError `1`, accepted `False`.
  - `runtime12pxPhysicalPhaseV1`: `11/11` exact, errorSum `0`, maxError `0`, accepted `True`.
  - `bestAvailableAction`: `11/11` exact, errorSum `0`, maxError `0`, accepted `True`.
- Sample-level selector result:
  - `runtime12pxPhysicalPhaseV1` chose `cutoff1` for exact rows `long12X114000`, `long12X114500`, and `long12X114750`.
  - It chose `cutoff2` for exact rows `long12X114125`, `long12X114250`, `long12X114375`, `long12X114625`, and `mid12X114250`.
  - It chose `skip` for exact rows `mid12X114500`, `short13X114375`, and `short13X114500`.
- Interpretation:
  - The non-deferred minimal promotion gate is now passed offline.
  - The selector is still diagnostic-only because the Java renderer does not yet have a draw-position-safe mode that can choose `source-cutoff-1` vs `source-cutoff-2` per draw without leaking position-dependent source mutation through the reusable texture cache.
- Artifacts:
  - Probe log: `run/resource-browser-font-apply-action-probe-runtime-selector-nondeferred.log`.
  - Existing side inputs: `run/resource-browser-font-runtime-classifier-probe-with-sweep.log`, `run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log`, `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-1-rerun.log`, and `run/resource-browser-font-raster-classifier-sweep-columns-source-cutoff-2.log`.
- Source changes:
  - Updated this TODO cursor/evidence.
  - No Java edits.
  - No `resource.html` edits.
- Verification:
  - `compileJava` was not run because no Java file changed.
  - `git diff --check -- doc/guide/resource-browser-visual-todo-2026-07-15.md doc/guide/resource-browser-font-short13-baseline-residual.md scripts/resource_browser_font_apply_action_probe.ps1` succeeded with no output.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - Non-deferred offline gate passed for `runtime12pxPhysicalPhaseV1`.
  - Deferred residual remains `short13X114625` in `doc/guide/resource-browser-font-short13-baseline-residual.md`.
- Cursor update:
  - Changed from "decide the next non-deferred selector/promotion gate" to "implement the draw-position-safe Java diagnostic for the accepted non-deferred selector".
- Next exact action:
  - Add one diagnostic quad mode in `FontDrawer.java` for `runtime12pxPhysicalPhaseV1` that chooses `skip`, `source-cutoff-1`, or `source-cutoff-2` at draw time.
  - Do not implement it as position-dependent mutation of the cached source texture. The runtime action must be expressed through draw-time texture windowing/cropping or through a cache key that includes all position-dependent selector inputs.
  - Validate only the minimal classifier sweep first; do not run full `devtools/resource.html` until the Java diagnostic reaches the same non-deferred `11/11` gate without regressing the deferred residual documentation.

Runtime 12px physical phase Java diagnostic evidence:

- Active task:
  - `RBV-V5-05`.
- Browser oracle type:
  - Reused Chromium final text-column width at `thresholdDarkness=20` from `tests/resource-browser-font-raster-classifier-sweep.html`.
- Browser-standard source:
  - Browser log: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
  - Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
  - Reuse reason: fixture, sample IDs, viewport/DPR, screenshot, and glyph darkness threshold are unchanged.
- Fixture/state:
  - AUI fixture: `tests/resource-browser-font-raster-classifier-sweep.html`.
  - Raster env: `APRICITYUI_FONT_RASTER_TARGET_PHYSICAL=1`, `APRICITYUI_FONT_RASTER_AA_MODE=on`, `APRICITYUI_FONT_RASTER_FILTER=linear`, `APRICITYUI_FONT_RASTER_SOURCE=outline-coverage-4x-row-clamp`, `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-12px-physical-phase`, projection logging enabled.
  - AUI screenshot used for final columns: `run/screenshots/aui/2026-07-15_21.26.35.png`.
- Predeclared rejection metric:
  - Reject the Java diagnostic if any non-deferred sweep sample fails to match Chromium final text-column width.
  - Keep `short13X114625` out of this gate; it remains the deferred 13px baseline residual.
  - Reject the implementation if it mutates a reusable source texture with position-dependent selector state.
- Hypothesis:
  - `runtime12pxPhysicalPhaseV1` can be implemented safely by choosing among three position-independent cached texture variants at draw time: baseline texture gutter, `source-cutoff-1`, and `source-cutoff-2`.
- Implementation:
  - Added diagnostic quad mode `snap-physical-y-texture-gutter-1-runtime-12px-physical-phase`.
  - Refactored font texture cache lookup so `toCacheKey(...)` and `rebuildTextureEntry(...)` receive an explicit `TextQuadMode`.
  - The runtime selector computes the same `strictRuntimeV1` apply/skip decision, then selects cutoff1 vs cutoff2 using the physical phase rule.
  - For cutoff1/cutoff2 it draws the corresponding position-independent cached texture entry instead of applying position-dependent mutation to the source texture cache.
  - A first UV-window implementation was tested and rejected because it over-cropped actual rendered width for `long12X114125`, `long12X114625`, and `long12X114750`.
- Commands:
  - Compile: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - AUI minimal rejected UV-window run: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-font-raster-classifier-sweep.html`, raster env above, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-classifier-sweep-aui-runtime-12px-physical-phase-last.log`.
  - Rejected UV-window columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-classifier-sweep-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-classifier-sweep-aui-runtime-12px-physical-phase-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_21.22.40.png' -OutLog 'run/resource-browser-font-raster-classifier-sweep-columns-runtime-12px-physical-phase-2140.log' -SampleIds 'long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625' -GlyphDarknessThreshold 20`.
  - AUI minimal accepted cache-action run: same raster env, then `.\gradlew.bat runClient --console plain --no-daemon --offline *>&1 | Tee-Object -FilePath run\resource-browser-font-raster-classifier-sweep-aui-runtime-12px-physical-phase-cache-actions-last.log`.
  - Accepted columns: `.\scripts\resource_browser_font_raster_columns.ps1 -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png' -BrowserMetricsLog 'run/resource-browser-font-raster-classifier-sweep-browser-last.log' -AuiMetricsLog 'run/resource-browser-font-raster-classifier-sweep-aui-runtime-12px-physical-phase-cache-actions-last.log' -AuiImage 'run/screenshots/aui/2026-07-15_21.26.35.png' -OutLog 'run/resource-browser-font-raster-classifier-sweep-columns-runtime-12px-physical-phase-cache-actions.log' -SampleIds 'long12X114000,long12X114125,long12X114250,long12X114375,long12X114500,long12X114625,long12X114750,mid12X114250,mid12X114500,short13X114375,short13X114500,short13X114625' -GlyphDarknessThreshold 20`.
- Results:
  - Rejected UV-window draw-time implementation:
    - `long12X114125`: browser `361`, AUI `360`, delta `-1`.
    - `long12X114625`: browser `361`, AUI `360`, delta `-1`.
    - `long12X114750`: browser `362`, AUI `361`, delta `-1`.
  - Accepted cache-action draw-time implementation:
    - `long12X114000`: browser `361`, AUI `361`, delta `0`.
    - `long12X114125`: browser `361`, AUI `361`, delta `0`.
    - `long12X114250`: browser `361`, AUI `361`, delta `0`.
    - `long12X114375`: browser `361`, AUI `361`, delta `0`.
    - `long12X114500`: browser `361`, AUI `361`, delta `0`.
    - `long12X114625`: browser `361`, AUI `361`, delta `0`.
    - `long12X114750`: browser `362`, AUI `362`, delta `0`.
    - `mid12X114250`: browser `345`, AUI `345`, delta `0`.
    - `mid12X114500`: browser `346`, AUI `346`, delta `0`.
    - `short13X114375`: browser `98`, AUI `98`, delta `0`.
    - `short13X114500`: browser `98`, AUI `98`, delta `0`.
    - Deferred `short13X114625`: browser `98`, AUI `97`, delta `-1`.
- Interpretation:
  - The Java diagnostic now passes the non-deferred minimal classifier sweep: `11/11` exact.
  - The implementation is draw-position-safe because draw position affects only which cached texture entry is selected, while each cached texture entry is position-independent.
  - The deferred 13px baseline residual remains unchanged and must not block the next mainline full-page evidence run.
- Artifacts:
  - Java source: `src/main/java/com/sighs/apricityui/render/FontDrawer.java`.
  - Accepted AUI log: `run/resource-browser-font-raster-classifier-sweep-aui-runtime-12px-physical-phase-cache-actions-last.log`.
  - Accepted AUI screenshot: `run/screenshots/aui/2026-07-15_21.26.35.png`.
  - Accepted columns: `run/resource-browser-font-raster-classifier-sweep-columns-runtime-12px-physical-phase-cache-actions.log`.
  - Rejected UV-window columns: `run/resource-browser-font-raster-classifier-sweep-columns-runtime-12px-physical-phase-2140.log`.
- Source changes:
  - Updated `src/main/java/com/sighs/apricityui/render/FontDrawer.java`.
  - Updated this TODO cursor/evidence.
  - No `resource.html` edits.
- Verification:
  - `.\gradlew.bat compileJava --console plain --no-daemon --offline` succeeded.
  - `.\gradlew.bat runClient --console plain --no-daemon --offline` succeeded and auto-closed through `Test.java`.
  - `git diff --check -- src/main/java/com/sighs/apricityui/render/FontDrawer.java doc/guide/resource-browser-visual-todo-2026-07-15.md doc/guide/resource-browser-font-short13-baseline-residual.md scripts/resource_browser_font_apply_action_probe.ps1` reported only the existing LF/CRLF warning for `FontDrawer.java` and no whitespace errors.
- Accepted or remaining mismatch:
  - `RBV-V5-05` remains `[~]`.
  - Non-deferred Java diagnostic gate passed.
  - Remaining mismatch is the deferred `short13X114625` 13px baseline residual in `doc/guide/resource-browser-font-short13-baseline-residual.md`.
- Cursor update:
  - Changed from "implement the draw-position-safe Java diagnostic" to "run full `devtools/resource.html` promotion/no-regression evidence".
- Next exact action:
  - Run full `devtools/resource.html` with `APRICITYUI_FONT_RASTER_QUAD_MODE=snap-physical-y-texture-gutter-1-runtime-12px-physical-phase` and the same target-physical raster env.
  - Compare against the existing browser-standard resource page artifacts; do not tune from full-page output.
  - If full-page evidence improves or is neutral without regressing completed checks, decide whether to keep this diagnostic as the current promotion candidate or broaden the minimal fixture before default promotion.

## Extracted Milestone V3: Browser Text Metrics

The following completed text metrics/font-source work was extracted from the main visual TODO so future main-goal runs do not reopen font-related tasks.

## Milestone V3: Browser Text Metrics

Goal: make text boxes match browser before judging final antialiasing.

### RBV-V3-01: Deterministic Font Source

Status: [x]

Dependencies: none

Problem:

- Page requests `font-family: 'Chakra Petch', sans-serif` without embedding a font.
- Browser and AUI may use different installed fallback.

Expected decision:

- Use a real embedded font for the test, or
- define the exact fallback policy, or
- document the residual environment-dependent difference.

Acceptance:

- Future browser/AUI metric comparisons know which font file/fallback is being used.
- No task assumes browser parity while the font source is unknown.

Current evidence:

- `resource.html` requests `font-family: 'Chakra Petch', sans-serif` but does not embed the font.
- Browser computed style keeps the family chain as `"Chakra Petch", sans-serif`; rect metrics show a `24px` normal line box around `35px`.
- Browser probing on this machine showed missing `Chakra Petch` falls through to generic `sans-serif`, not to `Bahnschrift` or `Agency FB`.
- AUI no longer maps missing `Chakra Petch`/`Rajdhani` to approximate installed fonts. It only uses the real installed/registered family; otherwise it continues the CSS font-family chain, matching browser fallback semantics.
- AUI now computes `line-height: normal` from resolved font metrics plus browser-like leading. Current static resource metrics match browser vertical text boxes closely enough for layout geometry work.

Evidence:

- Browser command: `node scripts\resource_browser_text_metrics.js`.
- Browser result: `sample-logo width=198.28 height=30`, `sample-sidebar width=105.45 height=17`, `sample-file-1 width=63.03 height=18`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result after fallback/leading fix: `sample-logo width=198.08 height=30.0`, `sample-sidebar width=102.88 height=16.95`, `sample-icon width=64.50 height=18.4`.
- RBV-V3-02 later tightened generic `sans-serif` width matching by preferring `Sans Serif Collection` when available.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `git diff --check` reported only existing LF/CRLF warnings.

### RBV-V3-02: Letter Spacing and Width Metrics

Status: [x]

Dependencies: RBV-V3-01

Expected samples:

- `MINE//EXPLORER`
- `DIRECTORIES`
- `SURVIVAL`
- `SELECT FILE TO VIEW DETAILS`
- `LEVEL.DAT`
- `SESSION.LOCK`
- `AUTO_PROMPT_FILE` when in interaction state

Acceptance:

- AUI text widths match browser metrics within documented tolerance.
- Any remaining mismatch is traced to font fallback or rasterization, not letter-spacing arithmetic.

Current evidence:

- `tests/resource-browser-text-metrics.html` covers the V3-02 sample list:
  `MINE//EXPLORER`, `DIRECTORIES`, `SURVIVAL`, `SELECT FILE TO VIEW DETAILS`, `LEVEL.DAT`, `SESSION.LOCK`, `AUTO_PROMPT_FILE`, and `ICON.PNG`.
- Browser `Chakra Petch, sans-serif` fallback was measured with `node scripts\resource_browser_text_metrics.js`.
- AUI was measured with `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`.
- AUI generic `sans-serif` now prefers installed `Sans Serif Collection` when present, because this matches Chromium's generic `sans-serif` advance widths on this machine much more closely than Java logical `SansSerif`.
- `line-height: normal` is capped to browser-like bounds so `Sans Serif Collection`'s unusually tall AWT metrics do not inflate layout boxes.

Width comparison after the generic fallback fix:

- `MINE//EXPLORER`: browser `198.28`, AUI `198.08`, delta `-0.20`.
- `DIRECTORIES`: browser `105.45`, AUI `102.88`, delta `-2.57`.
- `SURVIVAL`: browser `123.25`, AUI `123.50`, delta `+0.25`.
- `SELECT FILE TO VIEW DETAILS`: browser `193.72`, AUI `193.75`, delta `+0.03`.
- `LEVEL.DAT`: browser `67.05`, AUI `67.25`, delta `+0.20`.
- `SESSION.LOCK`: browser `93.39`, AUI `92.25`, delta `-1.14`.
- `AUTO_PROMPT_FILE`: browser `130.50`, AUI `128.75`, delta `-1.75`.
- `ICON.PNG`: browser `63.03`, AUI `64.50`, delta `+1.47`.

Evidence:

- Browser command: `node scripts\resource_browser_text_metrics.js`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Full resource browser static command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Full resource browser result after the text fix: `.contentTitle width=123.5 height=34.8`, browser static `.contentTitle width=123.25 height=35`; `#contentCount width=54.4792`, browser static `54.6875`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- Remaining mismatch at the time: `U+25C0 BACK` was a special-symbol glyph/fallback issue and belonged to `RBV-V3-04`, not this width-metric task. It is now covered by the completed V3-04 evidence.

### RBV-V3-03: Line Height, Ascent, and Descent

Status: [x]

Dependencies: RBV-V3-01

Expected checks:

- `.content-title`.
- `.file-name { line-height: 1.4 }`.
- `.file-meta`.
- `.detail-empty`.
- Button labels and tree labels.

Acceptance:

- Text vertical placement and line boxes match browser closely enough that card/header spacing can be judged fairly.

Current evidence:

- `line-height: normal` now uses resolved font metrics with browser-like leading.
- `line-height: normal` also has a browser-like max ratio to prevent unusually tall AWT fallback collection metrics from inflating layout.
- Browser static `resource.html`: `.contentTitle height=35`, `#contentCount height=19`, first normal `.file-card height=141.796875`.
- AUI static `resource.html`: `.contentTitle height=34.8`, `#contentCount height=18.85`, first normal `.file-card height=142`.
- Normal-line-height vertical placement is now close enough to unblock card/header geometry tasks.
- Explicit line-height samples now compare closely enough to browser for layout work:
  - `sample-file-line`: browser `width=93.39 height=17.8 computedLineHeight=16.8px`; AUI `width=92.25 height=18.4 cssLineHeight=1.4`.
  - `sample-file-meta`: browser `width=34.92 height=16`; AUI `width=34.58 height=15.5`.
  - `sample-action-new`: browser `width=78.16 height=37`; AUI `width=78.25 height=37.4`.
  - `sample-tree-label`: browser `width=48.83 height=20`; AUI `width=47.40 height=19.85`.
  - `sample-tree-selected`: browser `width=52.53 height=20`; AUI `width=50.65 height=19.85`.
  - `sample-detail-name`: browser `width=102.81 height=27`; AUI `width=102.88 height=27.1`.
  - `sample-detail-label`: browser `width=27.34 height=16`; AUI `width=25.92 height=15.5`.
  - `sample-detail-value`: browser `width=31.66 height=18`; AUI `width=34.0 height=18.4`.
- Remaining mismatch at the time: `sample-action-back` browser `width=88.5 height=37`, AUI `width=84.25 height=37.4`; this was dominated by the `U+25C0` glyph fallback/advance and is now covered by the completed V3-04 evidence.

Evidence:

- Browser command: `node scripts\resource_browser_text_metrics.js`.
- Browser result: see explicit sample metrics above.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result: see explicit sample metrics above.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `git diff --check` reported only existing LF/CRLF warnings.
- Follow-up completed: `RBV-V3-04` special symbols.

### RBV-V3-04: Special Symbol Glyphs

Status: [x]

Dependencies: RBV-V3-01

Expected checks:

- Header button arrows.
- Breadcrumb separators.
- Tree expand/collapse triangles.
- Rotated collapsed tree toggles.

Acceptance:

- Glyph size, alignment, center, and rotation match browser.
- Console mojibake from PowerShell is not treated as source corruption unless byte-level inspection proves it.

Goal-mode starting instructions:

- Browser first: extend or reuse `scripts\resource_browser_text_metrics.js` and `tests/resource-browser-text-metrics.html` to isolate `U+25C0 BACK`, breadcrumb separators, and tree expand/collapse glyphs.
- Capture browser rects and computed styles before reading AUI internals.
- AUI second: run `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html` through `runClient` and compare the same ids.
- Likely root to inspect after evidence: font-run fallback for symbols in `Font` and text measurement/drawing, not page CSS.
- Do not change source glyphs in `resource.html`.
- Correct symbol set for this task:
  - `U+25C0 BLACK LEFT-POINTING TRIANGLE` in `BACK`;
  - `U+25B2 BLACK UP-POINTING TRIANGLE` in `UP`;
  - `U+25B8 BLACK RIGHT-POINTING SMALL TRIANGLE` as breadcrumb separator and sidebar collapsed marker;
  - `U+25BE BLACK DOWN-POINTING SMALL TRIANGLE` as expanded tree toggle.

Current evidence:

- `tests/resource-browser-text-metrics.html` now includes code point logging and symbol samples for `U+25C0 BACK`, `U+25B2 UP`, `U+25B8 DIRECTORIES`, nav separator `U+25B8`, tree toggle `U+25BE`, and isolated `U+25C0`, `U+25B2`, `U+25B8`, `U+25BE`.
- `Test.java` logs text sample code points so PowerShell mojibake is not treated as source corruption.
- Browser command: `node scripts\resource_browser_text_metrics.js`.
- Browser symbol results:
  - `sample-action-back` `U+25C0 BACK`, code points `25c0 20 42 41 43 4b`: `width=88.5 height=37`.
  - `sample-action-up` `U+25B2 UP`, code points `25b2 20 55 50`: `width=71.72 height=37`.
  - `sample-sidebar-title` `U+25B8 DIRECTORIES`: `width=123.36 height=17`.
  - `sample-nav-sep` `U+25B8`: `width=13 height=26`.
  - `sample-tree-toggle` `U+25BE`: `width=16 height=16`.
  - isolated `U+25C0`: `width=13 height=18`; isolated `U+25B2`: `width=13 height=18`; isolated `U+25B8`: `width=12 height=19`; isolated `U+25BE`: `width=12 height=19`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result after adding browser-style generic sans symbol fallback chain:
  - `sample-action-back` `U+25C0 BACK`: `width=88.5 height=37.4`.
  - `sample-action-up` `U+25B2 UP`: `width=71.25 height=37.4`.
  - `sample-sidebar-title` `U+25B8 DIRECTORIES`: `width=121.48 height=16.95`.
  - `sample-nav-sep` `U+25B8`: `width=13.67 height=24.2`.
  - `sample-tree-toggle` `U+25BE`: `width=16 height=16`.
  - isolated `U+25C0`: `width=12.5 height=18.4`; isolated `U+25B2`: `width=12.5 height=18.4`; isolated `U+25B8`: `width=10.75 height=18.4`; isolated `U+25BE`: `width=10.75 height=18.4`.
- Full resource static browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Full resource static browser result: `.headerActions x=1184.625 y=10 width=254.375 height=37 right=1439`.
- Full resource static AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Full resource static AUI result: `.headerActions x=1185 y=9.8 width=254 height=37.4 right=1439`.
- Screenshot(s): `run/screenshots/aui/2026-07-15_11.49.52.png` through `run/screenshots/aui/2026-07-15_11.49.56.png`.
- Framework change: `Font.resolveBaseFontChain(...)` now expands generic sans families into a browser-like fallback chain while keeping the primary generic sans font unchanged; fallback order adds `Noto Sans SC`, `Segoe UI Symbol`, then `Segoe UI Emoji`.
- Remaining mismatch: isolated `U+25B8` and `U+25BE` glyph advances remain about `-1.25px` narrower than Chromium at `12px/600`. Local AWT font enumeration did not find a better installed font for those code points than the current symbol fallback. Treat this as the next V3-04 subtask only if visual glyph centering/rotation still differs after screenshot inspection.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `runClient` text metrics and full resource static runs passed.
- Screenshot-level check:
  - Browser direct screenshot: `run/screenshots/browser/resource-browser-direct-1463x843.png`.
  - Browser crops: `run/screenshots/compare/browser-actions.png`, `run/screenshots/compare/browser-tree-symbols.png`.
  - AUI crops: `run/screenshots/compare/aui-actions.png`, `run/screenshots/compare/aui-tree-symbols.png`.
  - Visual result: `U+25C0`, `U+25B2`, `U+25B8`, and `U+25BE` are centered closely enough for layout/polish work; no wrong rotation was observed in header actions, breadcrumb/sidebar marker, or expanded tree toggles.
- Remaining mismatch: isolated `U+25B8` and `U+25BE` advances remain about `-1.25px` narrower than Chromium at `12px/600`; this is documented as a residual Java2D/Chromium font-raster difference, not a current layout blocker.
- Next task recommendation: continue `RBV-V5-01` header repeating grid visibility.
