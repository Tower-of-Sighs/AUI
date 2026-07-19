# Resource Browser Compatibility Roadmap

Purpose: make `src/main/resources/assets/apricityui/apricity/devtools/resource.html` render and behave closer to a browser, using the current browser/AUI screenshots as the first acceptance target.

This file is optimized for goal-mode execution. Each task has a stable ID, dependencies, expected edits, and acceptance checks.

## Operating Rules

- Work in task order unless a task is explicitly marked independent.
- Keep each implementation PR-sized: one browser feature, one minimal test page, one compile/check pass.
- Prefer minimal reproduction pages before touching the full resource browser.
- Do not tune `resource.html` CSS to hide framework bugs unless the task explicitly says "page workaround".
- Use `.\gradlew.bat runClient --console plain --no-daemon` to launch the game when a visual check is needed.
- Do not invent a separate screenshot flow first: the client already saves automatic screenshots under `run/screenshots/aui` by default.
- The launch-time test target is configured in `src/main/java/com/sighs/apricityui/event/Test.java`; it automatically opens the target HTML after game startup, waits for the configured interval, then closes the game client process.
- Default launch target: `tests/resource-browser-compat-test.html`.
- Override launch target without editing source when needed: set `APRICITYUI_TEST_DOC_PATH=devtools/resource.html` for the run process, or pass `-Dapricityui.test.docPath=devtools/resource.html` if the JVM property is known to reach the client process.
- Auto-close delay is controlled by `-Dapricityui.test.autoExitSeconds=<seconds>`; use `-1` only for manual interactive debugging.
- After each implementation task:
  - run `.\gradlew.bat compileJava --console plain --no-daemon --offline` when Java changed;
  - run `git diff --check`;
  - update the task status in this file or a follow-up progress note.
- When a task changes rendering behavior, launch with `runClient`, then inspect the newest files in `run/screenshots/aui` for the minimal page and `resource.html`.
- If a task becomes larger than expected, stop at the minimal browser-compatible primitive and leave dependent polish for later IDs.

Status values:

- `[ ]` not started
- `[~]` in progress
- `[x]` complete
- `[!]` blocked or intentionally deferred

## Current Evidence

- Browser screenshot: `img_2.png`
- AUI screenshot: `img_3.png`
- Default automatic screenshot output: `run/screenshots/aui`
- Game launch command for visual validation: `.\gradlew.bat runClient --console plain --no-daemon`
- Auto-open HTML and delayed game shutdown configuration: `src/main/java/com/sighs/apricityui/event/Test.java`
- Default auto-open HTML: `tests/resource-browser-compat-test.html`
- Full resource browser auto-open override: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html` or `-Dapricityui.test.docPath=devtools/resource.html`
- Visual diff notes: `doc/guide/resource-browser-aui-visual-diff.md`
- Visual TODO list: `doc/guide/resource-browser-aui-visual-todo.md`

Known top issues:

1. Header buttons are not pushed to the right by `margin-left:auto`.
2. `::before` / `::after` pseudo elements are not generated as browser pseudo elements.
3. `.content-header::after` does not cover the black border with the purple line.
4. `repeating-linear-gradient(...)` is not handled like a browser background.
5. Browser/CSS viewport mode must be explicit in HTML instead of relying on the legacy `mode=window` name.
6. Text and SVG details differ after structure is fixed.

## Milestone M0: Baseline Harness

Goal: make future fixes measurable without relying only on the full page.

### RB-M0-01: Record Comparison Environment

Status: [x]

Dependencies: none

Expected output:

- A short doc section or note containing:
  - browser viewport size;
  - browser zoom;
  - OS DPI if known;
  - MC window size;
  - MC GUI scale;
  - AUI viewport meta used by `resource.html`;
  - active target HTML and delayed shutdown timing configured in `src/main/java/com/sighs/apricityui/event/Test.java`;
  - automatic screenshot directory: `run/screenshots/aui`;
  - screenshot crop rules.

Acceptance:

- A future run can reproduce the same basic dimensions without guessing.
- A future agent knows to use `runClient` and the latest automatic screenshots instead of asking for a manual screenshot first.

Evidence:

- Browser reference screenshot: `img_2.png`, current file dimensions `2560x1316`.
- Older AUI reference screenshot: `img_3.png`, current file dimensions `2560x1516`.
- Latest verified AUI screenshot: `run/screenshots/aui/2026-07-15_08.52.57.png`, current file dimensions `2560x1476`.
- Latest `runClient` log for `resource.html` showed AUI browser-mode layout viewport `1463x843`.
- `resource.html` currently declares:
  - `<meta name="aui-font-mode" content="web">`;
  - `<meta name="aui-viewport" content="mode=browser">`.
- `src/main/java/com/sighs/apricityui/event/Test.java` controls automated visual runs:
  - default target: `tests/resource-browser-compat-test.html`;
  - full resource browser target: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`;
  - optional JVM property target: `-Dapricityui.test.docPath=devtools/resource.html`;
  - default auto-close delay: `5` seconds through `apricityui.test.autoExitSeconds`;
  - `-Dapricityui.test.autoExitSeconds=-1` disables auto-close for manual interactive checks.
- Automatic screenshots are saved under `run/screenshots/aui`.
- Browser and AUI screenshots do not share the same full-image height, so machine comparison should crop to the page content viewport before computing visual diffs. Whole-image dimensions are useful run metadata, not a direct pixel-diff basis.
- OS DPI/browser zoom/MC GUI scale are not fully encoded in the screenshots. Treat the logged AUI viewport, screenshot dimensions, and `aui-viewport` meta as the reproducible baseline until a manual browser capture script records the missing browser-side values.

### RB-M0-02: Add Minimal Compatibility Test Page

Status: [x]

Dependencies: none

Expected edits:

- Add a focused HTML test page under `src/main/resources/assets/apricityui/apricity/tests/`.
- Point `src/main/java/com/sighs/apricityui/event/Test.java` at this page when this minimal page is the active visual target.
- Include isolated sections for:
  - flex auto margin;
  - `::before` / `::after`;
  - absolute pseudo with `bottom:-2px` over border;
  - `repeating-linear-gradient`;
  - grid `repeat(auto-fill, minmax(140px, 1fr))`;
  - text metrics;
  - SVG opacity/stroke.

Acceptance:

- Page loads in AUI.
- Starting `runClient` opens the configured page automatically through `Test.java`.
- The game shuts down after the configured delay in `Test.java`, or the task documents why the delay should be changed.
- A screenshot appears in `run/screenshots/aui`.
- Each section has a clear label or deterministic visual target.
- It can be used before changing `resource.html`.

Evidence:

- Added `src/main/resources/assets/apricityui/apricity/tests/resource-browser-compat-test.html`.
- Updated `src/main/java/com/sighs/apricityui/event/Test.java` to open `tests/resource-browser-compat-test.html`.

## Milestone M1: Layout Structure

Goal: fix the large structural mismatch before polishing pixels.

### RB-M1-01: Flex Main-Axis Auto Margin

Status: [x]

Dependencies: RB-M0-02 recommended, not blocking

Problem:

- `.header-actions { margin-left:auto; }` does not push actions to the right.
- Browser buttons begin near `x=2070`; AUI buttons begin near `x=989`.

Expected edits:

- Implement flex item auto margin participation in `Flex.java`.
- Use `Box.java` margin parsing rather than ad hoc string checks if possible.
- Cover row and column axes:
  - `margin-left:auto` / `margin-right:auto` for row;
  - `margin-top:auto` / `margin-bottom:auto` for column.

Rules:

- Auto margins consume positive free space before `justify-content`.
- Multiple auto margins split free space.
- Negative free space should not create negative auto margins.
- Existing `gap`, `flex-grow`, `flex-shrink`, and `justify-content:space-between` behavior must not regress.

Acceptance:

- Minimal flex auto-margin test matches browser behavior.
- `resource.html` Header buttons move to the right.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passes.

Evidence:

- `Box.java` preserves `auto` margin state while keeping the numeric margin contribution at zero for base sizing.
- `Flex.java` distributes positive main-axis free space across row/column auto margins before `justify-content`.
- `Selector.java` now preserves final style insertion order so lower-specificity `margin` shorthands do not randomly override higher-specificity `margin-left:auto` longhands.
- Verified with `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
- Verified visually through `.\gradlew.bat runClient --console plain --no-daemon --offline`; latest screenshot showed `.actions` pushed to the topbar right edge.

### RB-M1-02: Grid Auto-Fill Minmax Fr Calibration

Status: [x]

Dependencies: RB-M0-02

Problem:

- Framework has basic grid support, but `repeat(auto-fill, minmax(140px, 1fr))` must be verified against the file card grid.

Expected edits:

- Tighten `Grid.java` auto-repeat sizing if the minimal test proves mismatch.
- Ensure auto-fill count uses content-box width and gap.
- Ensure `minmax(140px, 1fr)` satisfies min first, then distributes remaining space.

Acceptance:

- Minimal grid test has browser-like column count, column width, and gap.
- File cards in `resource.html` keep one row with matching approximate start/end positions.
- Compile passes if Java changed.

Evidence:

- Existing `Grid.java` supports `repeat(auto-fill, minmax(140px, 1fr))`, auto-repeat count from available content width plus gap, and fr distribution after minimum sizes.
- `Grid.computeContentSize(...)` uses `getRenderChildren()`, so generated pseudo elements do not accidentally bypass grid sizing paths.
- Verified with `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
- Minimal browser compatibility screenshot `run/screenshots/aui/2026-07-15_08.42.23.png` keeps the card grid in one row.
- Full resource browser launched with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`; log showed `path=devtools/resource.html viewport=1463x843`.
- Full resource browser screenshot `run/screenshots/aui/2026-07-15_08.45.33.png` shows the five file cards in one row with stable gaps.
- Remaining sidebar tree icon/text differences are not caused by the file-card grid auto-fill behavior and should be tracked under later full-page visual acceptance.

## Milestone M2: Pseudo Elements

Goal: support the browser primitive used throughout this page.

### RB-M2-01: Selector Support for `::before` and `::after`

Status: [x]

Dependencies: RB-M0-02

Problem:

- `Selector` currently parses pseudo names as match conditions; it does not create pseudo elements.
- `::before` and `::after` rules therefore cannot render browser-like generated boxes.

Expected edits:

- Extend selector parsing to recognize pseudo-elements separately from pseudo-classes.
- Support both `::before` / `::after` and compatibility aliases `:before` / `:after`.
- Do not treat pseudo-elements as normal pseudo-class match failures.

Acceptance:

- CSS rules targeting `element::before` and `element::after` can be associated with generated internal boxes.
- Existing pseudo-classes `:hover`, `:active`, `:focus`, etc. still work.

Evidence:

- `Selector.java` now parses `::before` / `::after` and compatibility aliases `:before` / `:after` as pseudo-elements instead of pseudo-class match failures.
- Normal selector matching skips pseudo-element selectors, keeping `matches()` / DOM selector behavior on real elements unchanged.
- `Selector.matchPseudoElementCSS(...)` exposes pseudo-element rule matching for generated internal boxes.

### RB-M2-02: Generated Pseudo Element Boxes

Status: [x]

Dependencies: RB-M2-01

Expected edits:

- Generate internal render/layout boxes for `::before` and `::after`.
- Support at least `content:''` and text content later if needed.
- Pseudo boxes inherit from their originating element.
- They participate in layout and paint order:
  - `::before` before normal children;
  - `::after` after normal children.
- Avoid exposing pseudo boxes as normal DOM children to JS unless intentionally documented.

Acceptance:

- `.logo-block::after` white square appears.
- `.nav-path .current::after` active underline appears.
- `.sidebar-title::after` still appears.
- No DOM query behavior unexpectedly changes for normal elements.

Evidence:

- `Element` now creates cached internal `::before` / `::after` render elements only when computed `content` generates a pseudo box.
- Generated pseudo elements are exposed through render-only child views, not normal `children` / `childNodes`.
- Normal flow, flex, grid, text-run rendering, and paint-list building now use render-only child views where layout/paint needs pseudo elements.
- Latest minimal-page screenshots under `run/screenshots/aui`, including `2026-07-15_02.19.56.png`, show logo square, active underline, sidebar/title line, card corner triangles, and detail decoration.

### RB-M2-03: Absolute Pseudo Position and Border Paint Order

Status: [x]

Dependencies: RB-M2-02

Problem:

- Browser paints `.content-header::after` as a purple line over the black `border-bottom`; AUI currently shows black.

Expected edits:

- Ensure pseudo elements with `position:absolute` use the correct containing block.
- Support negative offsets such as `bottom:-2px`.
- Ensure border and pseudo child paint order matches browser enough for this case.

Acceptance:

- `.content-header::after` covers the black border and final line is purple.
- Minimal absolute pseudo + border test matches browser.

Evidence:

- Absolute positioning now resolves non-fixed containing blocks against the parent padding box, which matches the `.content-header::after { bottom:-2px }` case.
- Paint order places generated pseudo elements after the originating element border, allowing the purple line to cover the black border.
- Verified with `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
- Verified visually with `.\gradlew.bat runClient --console plain --no-daemon --offline`; latest screenshot `run/screenshots/aui/2026-07-15_02.19.56.png` shows the content header line as purple without the black border showing through.

### RB-M2-04: Pseudo Element State and Animation

Status: [x]

Dependencies: RB-M2-02, RB-M2-03

Expected edits:

- Ensure host state selectors apply to pseudo elements:
  - `.file-card:hover::before`
  - `.file-card.selected::after`
  - `.action-btn:hover::before`
- Ensure pseudo elements can receive animation/transition computed styles.

Acceptance:

- File card hover/selected decorations render.
- Header scanline can run once background support is available.

Progress:

- Pseudo elements now register animation specs and transitions when their generated style is synchronized.
- Pseudo elements preserve their previous computed style across host state invalidation, so pseudo transitions can compare old/new styles instead of jumping from the target style to itself.
- Motion cache invalidation now includes `position`, `top`, `right`, `bottom`, and `left`, so positional animation can invalidate cached offsets.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- `git diff --check` reported only LF/CRLF normalization warnings.
- Minimal test screenshot `run/screenshots/aui/2026-07-15_08.20.11.png` shows selected file-card pseudo decorations.
- Consecutive screenshots from the same `runClient` session show animation changes:
  - `2026-07-15_08.20.09.png` -> `2026-07-15_08.20.10.png`: topbar-rest diff `28 / 55440`, logo diff `21 / 2160`;
  - `2026-07-15_08.20.10.png` -> `2026-07-15_08.20.11.png`: topbar-rest diff `32 / 55440`, logo diff `75 / 2160`.
- Hover pseudo selectors share the same host-state matching path as selected pseudo selectors; interactive hover is still worth spot-checking manually when doing the final full-page pass.

## Milestone M3: Backgrounds and Graphics

Goal: restore the missing decorative layers after pseudo elements exist.

### RB-M3-01: Repeating Linear Gradient

Status: [x]

Dependencies: RB-M0-02

Problem:

- `Background.java` recognizes `linear-gradient` but not `repeating-linear-gradient`.
- Header grid uses a 1px transparent-purple repeating gradient.

Expected edits:

- Parse `repeating-linear-gradient(...)`.
- Support transparent/rgba stops.
- Render repeated stops without excessive per-pixel sampling cost.

Acceptance:

- Header grid in minimal test is visible.
- Header grid in `resource.html` becomes visible.
- Rendering does not introduce obvious frame-time regression.

Evidence:

- Implemented parsing/rendering support for `repeating-linear-gradient(...)`, including axis-aligned repeated stop tiles.
- Added `inset` shorthand expansion and absolute stretch handling needed by the topbar grid overlay.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- `git diff --check` reported only LF/CRLF normalization warnings.
- Minimal test screenshot `run/screenshots/aui/2026-07-15_02.30.13.png` showed the topbar repeating grid.
- Full resource browser launched through `Test.java` with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`; log showed `path=devtools/resource.html`.
- Full resource browser screenshot `run/screenshots/aui/2026-07-15_08.09.52.png` shows the header repeating grid visible across the topbar.

### RB-M3-02: Gradient Animation on Pseudo Elements

Status: [x]

Dependencies: RB-M2-04, RB-M3-01

Expected edits:

- Verify `linear-gradient` as pseudo background animates with `left:-100% -> 100%`.
- Fix only if minimal test shows framework bug after pseudo support.

Acceptance:

- Header scanline moves with `6s linear infinite`.

Evidence:

- Pseudo elements participate in `MotionTrack` animation registration.
- Position-property animation invalidates cached offsets through `MotionTrack`.
- Default compatibility page uses `.topbar::after { animation: scanline 4s linear infinite; }`; consecutive screenshots from `run/screenshots/aui` show non-logo topbar pixel differences, proving the pseudo scanline is moving.
- A fixed-time or disabled-animation screenshot can still be compared.

### RB-M3-03: SVG Opacity and Stroke Polish

Status: [x]

Dependencies: M1 and M2 should be mostly complete

Expected edits:

- Inspect SVG rendering path for opacity and stroke alignment.
- Focus on:
  - `ICON.PNG` icon `opacity="0.2"`;
  - black document/image/lock strokes;
  - viewBox scaling into `40x40` inside `48x48`.

Acceptance:

- SVG icons are not visibly blocky or too thick compared with browser.
- No regression in other SVG tests.

Evidence:

- `Svg.java` now applies SVG `opacity`, `fill-opacity`, and `stroke-opacity` when rasterizing fill/stroke colors.
- `Svg.java` enables AWT antialiasing/render-quality/stroke-control hints for vector rasterization.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- `git diff --check` reported only LF/CRLF normalization warnings.
- Full resource browser launched through `Test.java` with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`.
- Full resource browser screenshot `run/screenshots/aui/2026-07-15_08.35.16.png` shows SVG icons still rendering, with `ICON.PNG` image details visible instead of the previous block-like fill.

## Milestone M4: Viewport and CSS Pixels

Goal: separate MC window semantics from browser CSS viewport semantics.

### RB-M4-01: Define Viewport Modes

Status: [x]

Dependencies: RB-M1-01 recommended

Expected output:

- Update docs or code comments defining:
  - `gui` mode;
  - `window` mode;
  - proposed `browser` or `css` mode.

Acceptance:

- `mode=window` is no longer expected to mean browser CSS viewport if it cannot.
- There is a clear target for `100vh`, `100vw`, and CSS px.

Evidence:

- `doc/en/guide.md` now defines `gui`, `browser/css/web`, `window/native`, `screen/fullscreen`, and `fixed`.
- `mode=browser`, `mode=css`, and `mode=web` mean browser-like CSS viewport: framebuffer size converted through the OS/GLFW content scale, rendered back into the current Minecraft GUI coordinate space.
- `mode=window` and `mode=native` are documented as compatibility aliases for the same browser/CSS viewport behavior; new pages should prefer `mode=browser` when matching browser CSS sizing is the intent.
- `Test.java` remains the visual harness owner: it auto-opens the configured HTML and auto-closes the game client after the configured delay.

### RB-M4-02: Implement Browser/CSS Viewport Mode

Status: [x]

Dependencies: RB-M4-01

Expected edits:

- Add `mode=browser` or `mode=css` to viewport meta handling.
- Make CSS px and `vh/vw` resolve against a browser-like content viewport.

Acceptance:

- The same fixed CSS width/height test resolves predictably in browser mode.
- `resource.html` can opt into this mode without breaking gui/window modes.

Evidence:

- `ApricityViewport.resolveBase(...)` now accepts `mode=browser`, `mode=css`, and `mode=web`.
- `mode=window` and `mode=native` remain compatibility aliases, so existing documents keep their previous behavior.
- `resource.html` and `tests/resource-browser-compat-test.html` now opt into `<meta name="aui-viewport" content="mode=browser">`.

### RB-M4-03: Verify `calc()`, `vh/vw`, `rem/em`

Status: [x]

Dependencies: RB-M4-02

Expected checks:

- `body { height:100vh }`
- `.main { height:calc(100vh - 60px) }`
- root font basis for `rem` and element font basis for `em`.

Acceptance:

- Header/Main/Sidebar/Detail sizes no longer have unexplained scale drift.

Evidence:

- `Size.java` already resolves `vw` and `vh` against `Size.getWindowSize()`, which is overridden by the active document viewport during document refresh.
- `Size.java` resolves `rem` through the document root font basis and `em` through the current element font basis.
- `Size.java` resolves simple additive/subtractive `calc(...)` expressions, including the page pattern `calc(100vh - 60px)`.
- Verified with `.\gradlew.bat runClient --console plain --no-daemon --offline`: `Test.java` auto-opened `tests/resource-browser-compat-test.html`, then auto-closed the game client after the default delay.
- Run log showed `path=tests/resource-browser-compat-test.html viewport=1463x843`.
- Latest validation screenshot: `run/screenshots/aui/2026-07-15_08.42.23.png`.
- This verifies the basic browser viewport unit chain. Full `resource.html` pixel comparison remains part of M7 acceptance.

## Milestone M5: Text and Fonts

Goal: reduce remaining visual mismatch after layout is correct.

### RB-M5-01: Decide Chakra Petch Strategy

Status: [x]

Dependencies: M1/M2 preferably complete

Problem:

- `resource.html` requests `Chakra Petch` but does not load it.

Options:

- Add a real `@font-face` asset.
- Map fallback to a known AUI/browser-equivalent font.
- Document that the page has environment-dependent font output.

Acceptance:

- Font source is deterministic for this test page.

Evidence:

- `Font.java` now handles `Chakra Petch` explicitly.
- Resolution order:
  - installed `Chakra Petch`;
  - installed `Bahnschrift`;
  - installed `Agency FB`;
  - Java logical `SansSerif`.
- This keeps `resource.html` and the compatibility page source unchanged while avoiding an accidental fallback to the framework default `Microsoft YaHei`.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- Full resource browser launched with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`.
- Latest validation screenshot: `run/screenshots/aui/2026-07-15_08.49.15.png`; text uses the narrower web-like fallback instead of the previous default fallback.
- Exact browser matching still depends on whether the browser side uses real `Chakra Petch`, installed `Bahnschrift`, or another sans-serif fallback; M5-02/M5-03 track metric-level tuning.

### RB-M5-02: Letter Spacing and Text Width

Status: [x]

Dependencies: RB-M5-01

Expected edits:

- Fix only after font source is deterministic.
- Use samples:
  - `MINE//EXPLORER`
  - `DIRECTORIES`
  - `SELECT FILE TO VIEW DETAILS`
  - file names.

Acceptance:

- Text widths are close to browser for these samples.

Evidence:

- Font source is now deterministic enough for metric comparison through RB-M5-01.
- Existing text rendering supports `letter-spacing` in both measurement and custom-font texture rendering.
- Latest full resource browser screenshot after the font mapping: `run/screenshots/aui/2026-07-15_08.52.57.png`.
- Added `tests/resource-browser-text-metrics.html` as a direct browser/AUI width harness.
- The harness avoids unsupported `width:max-content` and measures `inline-block` text boxes instead, so `getBoundingClientRect()` reports the rendered text box instead of the parent layout width.
- Chrome headless baseline:
  - `MINE//EXPLORER`: `198.28`;
  - `DIRECTORIES`: `105.45`;
  - `SELECT FILE TO VIEW DETAILS`: `193.72`;
  - `ICON.PNG`: `63.03`;
  - `SOUNDS.JSON`: `92.69`;
  - `README.TXT`: `78.78`.
- AUI validation run with `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-text-metrics.html`:
  - `MINE//EXPLORER`: `195.17`;
  - `DIRECTORIES`: `100.81`;
  - `SELECT FILE TO VIEW DETAILS`: `193.25`;
  - `ICON.PNG`: `60.0`;
  - `SOUNDS.JSON`: `88.75`;
  - `README.TXT`: `76.5`.
- The largest remaining width drift is the smaller bold uppercase file/sidebar samples, roughly `3px` to `4.6px`; the main detail sample is within `0.5px`, and the logo is within `3.2px`.
- `Window.CSSStyleDeclaration` now exposes JavaBean getters for common computed-style properties, so JS `getComputedStyle(node).fontSize` / `fontWeight` / `letterSpacing` / `fontFamily` match the values returned through `getPropertyValue(...)`.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the computed-style bridge change.

### RB-M5-03: Line Height and Vertical Metrics

Status: [x]

Dependencies: RB-M5-01

Expected edits:

- Align `line-height:1.4`, `normal`, ascent/descent, and text box height behavior where feasible.

Acceptance:

- File card contents no longer look vertically compressed/up-shifted.

Evidence:

- `Text.calculateLineHeight(...)` now resolves `normal` and invalid line-height values to `1.2 * font-size`, which is closer to browser default line-height behavior than the previous `font-size + 2` rule.
- Explicit numeric multipliers such as `line-height:1.4`, percentages, and absolute lengths still use their declared values.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- Full resource browser launched with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`.
- Latest validation screenshot: `run/screenshots/aui/2026-07-15_08.52.57.png`; title, button, and file-card text remain vertically stable after the change.

## Milestone M6: Interaction APIs

Goal: make non-static parts of the page usable.

### RB-M6-01: Prompt API

Status: [x]

Dependencies: none

Problem:

- `+ NEW` uses `prompt('ENTER NAME:')`.

Options:

- Implement `window.prompt()` as a simple modal.
- Add an AUI devtools prompt bridge.
- Replace page usage with an AUI-native input flow.

Acceptance:

- Clicking `+ NEW` does not throw.
- Entered name appears as a new file card.

Progress:

- `ApricityJS` now injects a `prompt()` / `window.prompt()` polyfill before page scripts.
- The polyfill preserves browser-like synchronous return semantics:
  - Java/Swing bridge available: uses `javax.swing.JOptionPane.showInputDialog(...)`;
  - user cancel: returns `null`;
  - Java bridge unavailable: returns the supplied default value or an empty string, so scripts do not throw because `prompt` is undefined.
- `Window.getTestPromptResponse()` exposes `apricityui.test.promptResponse` / `APRICITYUI_TEST_PROMPT_RESPONSE` to the polyfill, so automated runs can verify prompt flows without a manual modal.
- Inline `on*` attributes are installed as event listeners during DOM initialization. This covers the resource browser's `onclick="createNew()"` path and normal `element.click()` dispatch.
- Added `tests/prompt-api-test.html`, which exercises:
  - `setTimeout(...)`;
  - `document.getElementById(...).click()`;
  - inline `onclick`;
  - `prompt(...)`;
  - `document.createElement(...)`;
  - `appendChild(...)`;
  - `textContent` updates.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed.
- Full resource browser launched with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`; page loaded and auto-closed without JS startup failure after the polyfill was injected.
- Latest validation run log showed `path=devtools/resource.html viewport=1463x843`.
- Full resource browser was re-run after inline event handler support was added:
  - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`;
  - `.\gradlew.bat runClient --console plain --no-daemon --offline`;
  - log showed `path=devtools/resource.html viewport=1463x843 paintList=112 dirty=23`;
  - no Rhino/JS exception appeared before the automatic shutdown.
  - latest full-page startup screenshot: `run/screenshots/aui/2026-07-15_09.34.22.png`.
- Scripted validation passed with:
  - `APRICITYUI_TEST_DOC_PATH=tests/prompt-api-test.html`;
  - `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE`;
  - `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Latest prompt validation screenshot: `run/screenshots/aui/2026-07-15_09.32.34.png`, showing `ADDED: AUTO_PROMPT_FILE` and a new `AUTO_PROMPT_FILE` card.

### RB-M6-02: Scrollbar Styling Policy

Status: [x]

Dependencies: none

Expected output:

- Decide support policy for `::-webkit-scrollbar*`.

Options:

- Implement AUI custom scrollbar pseudo-elements.
- Defer and document unsupported styling.

Acceptance:

- Long content has either styled scrollbars matching CSS or a documented fallback.

Evidence:

- Policy decision: `::-webkit-scrollbar`, `::-webkit-scrollbar-track`, and `::-webkit-scrollbar-thumb` are unsupported CSS extensions for now.
- Normal scrolling remains supported through `overflow`, `scrollTop`, `scrollLeft`, mouse wheel input, and the framework native scroll model.
- `doc/en/guide.md` now documents this fallback and recommends ordinary DOM elements for pages that require fully custom visual scrollbars.
- This intentionally avoids pretending that WebKit scrollbar pseudo-elements are browser-compatible before the framework has real scrollbar pseudo-element boxes.

### RB-M6-03: Hover/Selected/Active Screenshots

Status: [x]

Dependencies: RB-M2-04

Expected output:

- Capture or request screenshots for:
  - Header button hover;
  - file card hover;
  - tree item hover;
  - selected file;
  - detail active panel.

Acceptance:

- Interactions are validated visually, not just by event dispatch.

Evidence:

- Added `tests/resource-browser-interaction-states.html`.
- The page uses the same browser primitives as `resource.html` for the relevant states:
  - `:hover`;
  - `:active`;
  - `.selected`;
  - `.active`;
  - pseudo decorations on hovered/selected file cards.
- The page forces framework element state through `Element.setHover(true)` / `Element.setActive(true)`, so the screenshot validates the actual selector/state rendering path instead of relying only on dispatched events.
- Validated with `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-interaction-states.html` and `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Latest validation screenshot: `run/screenshots/aui/2026-07-15_09.39.47.png`.
- Screenshot contains:
  - Header button hover and active states;
  - tree item hover and selected states;
  - file card hover, selected, and active states;
  - active detail panel with the purple active rail.

## Milestone M7: Full Resource Browser Acceptance

Goal: verify the full page after primitives are fixed.

### RB-M7-01: Static Full-Page Comparison

Status: [x]

Dependencies: M1, M2, M3, M4 mostly complete

Acceptance:

- Header structure matches browser.
- Sidebar width and selected state are close.
- Content header purple line is correct.
- File cards align closely.
- Detail panel position and text are close.

Evidence:

- Latest full resource browser startup run passed with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`.
- Earlier full resource screenshot: `run/screenshots/aui/2026-07-15_09.34.22.png`.
- Startup log showed `path=devtools/resource.html viewport=1463x843 paintList=112 dirty=23`.
- No JS/Rhino exception appeared during the automatic run.
- Direct comparison against `img_2.png` found the remaining major static defect was sidebar tree SVG placement: `.tree-icon` containers were laid out before text, but their child `svg` elements were horizontally centered against the outer row width, so icons painted after labels.
- Added `tests/tree-flex-svg-order-test.html` to isolate this as nested flex sizing:
  - before fix, `.tree-icon` was at `x=74.04`, but its child `svg` was at `x=207.04`;
  - after fix, `.tree-icon` stayed at `x=74.04`, and its child `svg` moved to `x=75.04`.
- `Flex.resolveAvailableMainSize(...)` now only expands row `display:flex` to the containing block width when the flex container has no explicit width. Explicit-width flex containers such as `.tree-icon { width:16px; display:flex; justify-content:center; }` now center children inside their own content box.
- `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the flex sizing fix.
- Full resource browser re-run with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`:
  - latest validation screenshot: `run/screenshots/aui/2026-07-15_10.05.33.png`;
  - log showed `path=devtools/resource.html viewport=1463x843 paintList=112 dirty=23`;
  - sidebar folder/file/lock icons render to the left of labels, matching the browser structure;
  - header actions remain right-aligned;
  - content header purple line, file card row, and detail panel placement remain stable.

### RB-M7-02: Interactive Full-Page Comparison

Status: [x]

Dependencies: RB-M6-03

Acceptance:

- Hover, selected, prompt/new, and scroll states are usable and visually acceptable.

Evidence:

- Prompt/new primitive is validated by `tests/prompt-api-test.html`.
- Latest prompt validation screenshot: `run/screenshots/aui/2026-07-15_09.32.34.png`.
- Hover/selected/active primitives are validated by `tests/resource-browser-interaction-states.html`.
- Latest interaction-state validation screenshot: `run/screenshots/aui/2026-07-15_09.39.47.png`.
- Full resource page loads after inline `on*` event support was added.
- Added an optional `Test.java` interaction driver for full resource browser validation:
  - enable with `APRICITYUI_TEST_INTERACTION=resource-browser` or `-Dapricityui.test.interaction=resource-browser`;
  - it waits for the AUI document to open, clicks the actual `+ NEW` header button, clicks a real `.file-card`, and scrolls `.sidebar` / `.content`;
  - default behavior is unchanged when the interaction driver is not enabled.
- Full resource browser interaction validation passed with:
  - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`;
  - `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE`;
  - `APRICITYUI_TEST_INTERACTION=resource-browser`;
  - `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Validation log:
  - `newClicked=true`;
  - `firstFileClicked=true`;
  - `sidebarScroll=48.0`;
  - `contentScroll=48.0`;
  - `detailActive=true`;
  - `addedFile=true`.
- Latest interaction screenshot: `run/screenshots/aui/2026-07-15_10.12.48.png`, showing:
  - selected `LEVEL.DAT` card;
  - active detail panel;
  - newly added `AUTO_PROMPT_FILE` card from the full page's real `+ NEW` flow.

### RB-M7-03: Performance Regression Check

Status: [x]

Dependencies: M2/M3 especially

Expected checks:

- Pseudo elements do not cause full-tree relayout every frame.
- Header animation does not trigger full style/layout recalculation.
- Repeating gradient rendering is cached or cheap enough.

Acceptance:

- No obvious FPS regression in the resource browser.
- If JFR is available, no new major framework hotspot appears from pseudo/gradient rendering.

Evidence:

- Full resource browser performance smoke run passed with:
  - `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`;
  - `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE`;
  - `APRICITYUI_TEST_INTERACTION=resource-browser`;
  - `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Startup/render log stayed at `paintList=112` and first render `dirty=23`, matching the earlier full-page validation runs instead of growing with generated pseudo elements.
- The interaction driver completed during the same run:
  - `newClicked=true`;
  - `firstFileClicked=true`;
  - `sidebarScroll=48.0`;
  - `contentScroll=48.0`;
  - `detailActive=true`;
  - `addedFile=true`.
- Latest screenshots from the run were saved automatically under `run/screenshots/aui`, including `2026-07-15_10.17.39.png`.
- Code-path check:
  - pseudo element boxes are cached internal render elements and participate in the existing render child list, so they do not rebuild DOM children each frame;
  - `Document.stepMotionRender()` / `MotionTrack.stepRender()` are render-phase only and do not call `Document.markDirty(...)`, so the header scanline animation does not request full style/layout or paint-list rebuild each frame;
  - `Background.of(...)` caches parsed background layers on the element renderer, and `repeating-linear-gradient(...)` is normalized into an intrinsic repeated tile instead of reparsing CSS during draw.
- `git diff --check` reported only LF/CRLF normalization warnings, with no actual whitespace errors.

## Recommended Goal-Mode Batches

Batch A:

1. RB-M0-02
2. RB-M1-01

Batch B:

1. RB-M2-01
2. RB-M2-02
3. RB-M2-03

Batch C:

1. RB-M3-01
2. RB-M2-04
3. RB-M3-02

Batch D:

1. RB-M4-01
2. RB-M4-02
3. RB-M4-03

Batch E:

1. RB-M1-02
2. RB-M5-01
3. RB-M5-02
4. RB-M5-03
5. RB-M3-03

Batch F:

1. RB-M6-01
2. RB-M6-02
3. RB-M6-03
4. RB-M7-01
5. RB-M7-02
6. RB-M7-03

## Stop Conditions

- Stop and document if a task requires broad rewrites outside its listed modules.
- Stop and document if a fix improves `resource.html` but breaks an existing test page.
- Stop and document if screenshots cannot be compared because environment dimensions changed.
- Do not proceed to text/SVG polish while Header auto margin or pseudo elements are still broken.
