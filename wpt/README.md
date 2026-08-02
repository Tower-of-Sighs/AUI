# AUI Layout WPT Automation

## Purpose

This document defines how ApricityUI uses the Web Platform Tests (WPT) layout
corpus as a browser-compatibility oracle. WPT is upstream test data, not an
executable test suite for AUI: most WPT pages assume a full browser, a web
server, the WPT runner, and APIs that AUI intentionally does not implement.

The goal is to turn supported layout cases into deterministic AUI regression
tests while retaining their WPT path, revision, and browser result as
traceability data.

## Local Corpus

The sparse checkout is intentionally local-only and ignored by Git:

```text
wpt/corpus/
```

Pinned upstream revision:

```text
a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
```

Downloaded layout scope:

| Area | WPT directory |
| --- | --- |
| CSS 2 visual formatting and positioning | `css/CSS2` |
| Box model, display and inline layout | `css/css-box`, `css/css-display`, `css/css-inline` |
| Alignment, flex and grid | `css/css-align`, `css/css-flexbox`, `css/css-grid` |
| Sizing, positioning, overflow and containment | `css/css-sizing`, `css/css-position`, `css/css-overflow`, `css/css-contain` |
| Fragmentation, columns and tables | `css/css-break`, `css/css-multicol`, `css/css-tables` |
| Writing direction and transform geometry | `css/css-writing-modes`, `css/css-transforms` |
| Layout-adjacent value, text and UI rules | `css/css-values`, `css/css-text`, `css/css-ui` |
| Required test resources | `common`, `resources`, `fonts`, `css/support` |

This is the complete local layout scope for the first integration. It excludes
unrelated specifications such as WebGL, media, service workers, IndexedDB and
networking. A sparse checkout does not imply that every selected directory is
currently supported by AUI.

At the pinned revision, this checkout contains 19,873 HTML/XHTML test documents
in the selected CSS directories (54.2 MB including their checked-out support
files). The count is an integrity signal, not a pass count, and will naturally
change when the pin changes.

### Recreate and verify the checkout

The following command sequence recreates the local corpus from an empty
`wpt/corpus/` directory. It is deliberately not part of Gradle or
CI: the corpus is developer-maintained test input, while adopted fixtures and
their browser baselines are versioned in AUI.

```powershell
git clone --filter=blob:none --no-checkout https://github.com/web-platform-tests/wpt.git wpt/corpus
git -C wpt/corpus sparse-checkout init --cone
git -C wpt/corpus sparse-checkout set common css/CSS2 css/css-align css/css-box css/css-break css/css-contain css/css-display css/css-flexbox css/css-grid css/css-inline css/css-multicol css/css-overflow css/css-position css/css-sizing css/css-tables css/css-text css/css-transforms css/css-ui css/css-values css/css-writing-modes css/support fonts resources
git -C wpt/corpus checkout a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
git -C wpt/corpus rev-parse HEAD
git -C wpt/corpus sparse-checkout list
```

The last two commands must print the pinned SHA and every directory in the
scope table. `git check-ignore -v wpt/corpus/.git/HEAD` should identify
`/corpus/` in `wpt/.gitignore`; local WPT data
must never be accidentally committed.

Refresh only as an explicit maintenance change, then update the pinned SHA in
this document and regenerate the case inventory:

```powershell
git -C wpt/corpus fetch origin master
git -C wpt/corpus log --oneline HEAD..origin/master
git -C wpt/corpus merge --ff-only origin/master
git -C wpt/corpus rev-parse HEAD
```

Do not make `git pull` part of ordinary CI. A changing upstream corpus would
make a previously reproducible AUI result non-reproducible.

## Test Model

Each adopted case has one tracked `WptLayoutCase` record, stored under a future
`wpt/config/cases/` directory. The record contains:

```yaml
id: css/css-flexbox/flexbox-align-self-baseline.html
wptRevision: a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
source: wpt/corpus/css/css-flexbox/flexbox-align-self-baseline.html
kind: geometry
status: supported
viewport: { width: 800, height: 600, dpr: 1 }
fontPolicy: ahem-or-specified
oracle: baselines/css-css-flexbox-flexbox-align-self-baseline.json
tolerance: { positionPx: 0.25, sizePx: 0.25 }
owners: [style/Flex.java]
```

`status` is mandatory and is one of:

- `supported`: must pass in Chromium and AUI.
- `expected-unsupported`: kept in the inventory with a precise missing feature.
- `blocked`: AUI could support it, but its WPT dependency cannot yet be adapted.
- `quarantined`: known regression; must include issue/bug reference and expiry.

No case may silently disappear because it fails.

## Three Execution Lanes

### 1. Geometry Lane: the default

Use for most layout cases. Chromium creates a JSON baseline at a fixed viewport;
AUI creates the same JSON from its document and compares it in JUnit.

The probe records only stable values:

```json
{
  "viewport": { "width": 800, "height": 600, "dpr": 1 },
  "nodes": {
    "subject": {
      "rect": [10, 20, 120, 40],
      "computed": {
        "display": "flex",
        "position": "static",
        "boxSizing": "content-box"
      },
      "scroll": [120, 300, 120, 80]
    }
  }
}
```

Probe selectors are case-owned `data-wpt-probe` attributes added only to the
AUI adapter fixture, never written back into WPT. Compare `x`, `y`, `width`,
`height`, `right`, `bottom`, `scrollWidth`, `scrollHeight`, `clientWidth`, and
`clientHeight`; compare integer-like values exactly and geometry with the case
tolerance. Do not compare font raster pixels in this lane.

Suitable first batches: box sizing, normal block flow, absolute/fixed offsets,
flex main/cross-axis alignment, grid tracks/placement, overflow metrics and
scrollbar presence.

### 2. Semantic Lane: testharness conversion

WPT `testharness.js` tests cannot be run directly under Rhino. Extract each
adopted assertion into a declarative probe fixture plus a JUnit assertion.
Chromium is still run first to verify the fixture represents the WPT result.

Examples:

- `getBoundingClientRect` assertions become geometry probes.
- computed-style assertions become the `computed` section of the JSON oracle.
- scroll and hit-testing assertions become Java-side document operations plus
  expected values recorded from Chromium.

Do not emulate `testharness.js`, `testdriver.js`, or browser internals inside
AUI. That would test an adapter rather than layout conformance.

### 3. Visual Lane: only when geometry is insufficient

Use WPT reftests and AUI screenshots for rounded clipping, gradient edges,
paint order, transformed clipping, shadows and other rules where equal boxes
can still produce different pixels.

The browser reference page is rendered by Chromium; AUI renders the adapter
fixture at the same CSS viewport, DPR 1 and named font. Mask known unavoidable
differences such as glyph anti-aliasing. Pixel comparison must report changed
pixel count, changed bounding box and a diff image under ignored `run/`.

Visual results never replace geometry assertions. A visual pass alone cannot
prove scroll metrics, containing blocks, or event-related layout behavior.

## Automation Flow

### Main Entry Point

`wpt/tools/run.mjs` is the single entry point for the WPT workflow. It walks
every configured layout directory, hashes every HTML/XHTML source file, keeps
unchanged case results, and always regenerates `wpt/progress.md`.

```powershell
node wpt/tools/run.mjs --mode=inventory
```

The first run has already produced an inventory of 19,775 layout pages. The
source count differs from the broader corpus file count because support pages
outside the selected layout directories are resources, not candidate cases.
`incremental` and `full` modes now run the Chromium adapter. A browser snapshot
without its corresponding AUI snapshot remains `pending`; a Chromium loading
failure is recorded as `browser-test-failed`, `timeout`, or `infra-blocked`.
The AUI adapter is the next stage and is the only component allowed to turn a
browser-passed case into `pass` or `layout-mismatch`.

### Inventory

The implemented inventory stage walks only the downloaded directories and
emits `wpt/output/inventory.json` with:

- WPT path and test type: `testharness`, `reftest`, `manual`, or unsupported.
- referenced support files and external server dependencies.
- CSS features found in the page.
- candidate AUI subsystem based on path.

This inventory is advisory; a reviewed manifest decides what enters CI.

### Browser Baseline

The same task adds `wpt/tools/capture-baseline.mjs`. It launches installed Chrome or
Edge through DevTools Protocol, uses a fresh profile, fixes viewport and DPR,
waits for fonts and animation settling, then writes the probe JSON. Existing
Chromium scripts in `tools/` and `scripts/` establish the local executable
discovery pattern and should be reused.

Baseline generation is a reviewed developer command, not a CI step:

```powershell
node wpt/tools/capture-baseline.mjs --case css/css-flexbox/example.html
```

The command must fail if Chromium reports a test failure. It writes the WPT SHA,
Chromium version, viewport, DPR, font policy and capture timestamp alongside the
oracle. Rebaseline only after reviewing a browser change or a fixture error;
never to make AUI failures disappear.

### AUI Fast Regression

The first adopted batch adds a parameterized JUnit class:

```text
src/test/java/com/sighs/apricityui/wptlayout/WptLayoutCaseTest.java
```

For every `supported` manifest record it loads the adapted fixture using
`TestDocumentFactory`, commits layout, collects the same snapshot and diffs it
against the checked-in Chromium baseline. It runs with the normal unit suite:

```powershell
.\gradlew.bat test --tests com.sighs.apricityui.wptlayout.WptLayoutCaseTest --console plain --no-daemon
```

The JUnit failure message must include WPT path, AUI subsystem owner, expected
and actual property, tolerance, and the baseline file path.

### AUI Client and Visual Regression

Cases that depend on Minecraft rendering, font upload, clipping stencil state,
or input routing also get an AUI client fixture under:

```text
wpt/config/client-fixtures/
```

The client harness writes one JSON line per case to the existing test log.
An external comparison tool parses those lines and creates visual diffs under
`wpt/output/`; both output locations remain ignored. Run this lane on
nightly/manual compatibility jobs, not on every Java edit.

## Admission Rules

1. Copy or adapt the smallest WPT fixture into AUI test resources; preserve
   the original WPT path and license header/reference in the manifest.
2. Run the original or equivalent probe in Chromium and record the baseline.
3. Add the JUnit geometry assertion before changing engine code.
4. Add a visual case only if the geometry oracle cannot distinguish correctness.
5. Mark unsupported WPT behavior explicitly with the missing CSS/HTML feature.
6. A regression fix must add or unquarantine at least one case.

## Initial Rollout Order

| Batch | WPT areas | AUI owners | Gate |
| --- | --- | --- | --- |
| 1 | `css-box`, `css-sizing`, `CSS2/box_display` | `Box`, `Size`, `NormalFlow` | Geometry |
| 2 | `css-position`, `CSS2/positioning` | `Position`, `LayoutCommit` | Geometry |
| 3 | `css-flexbox`, `css-align` | `Flex`, `Size` | Geometry |
| 4 | `css-grid` | `Grid`, `Size` | Geometry |
| 5 | `css-overflow`, `css-contain` | `ScrollModel`, `Mask`, `Drawer` | Geometry plus client |
| 6 | `css-inline`, `css-text`, `css-writing-modes` | `NormalFlow`, `Text` | Geometry plus targeted visual |
| 7 | `css-transforms`, `css-multicol`, `css-tables`, `css-break` | `Transform`, layout engine | Explicit supported/unsupported inventory |

Start with 10-20 cases per batch. The objective is diagnostic coverage of each
algorithmic branch, not an untriaged import of thousands of red tests.

## Pass Criteria

A supported geometry case passes only when Chromium baseline capture succeeds,
AUI snapshot matches within tolerance, and the normal JUnit suite is green. A
supported visual case additionally meets its pixel threshold. The dashboard
must report counts by `supported`, `expected-unsupported`, `blocked`, and
`quarantined`; a raw percentage without those categories is misleading.

## Current Implementation Boundary

This change downloads and pins the source corpus, records the execution design,
and keeps the corpus outside version control. It intentionally does not claim
that all 19,873 pages execute in AUI today, nor does it add a synthetic
`all-WPT` Gradle task that would produce untriaged failures. The first
deliverable after this document is the inventory tool and a reviewed batch of
10-20 geometry fixtures; only `supported` manifest cases become mandatory
JUnit regressions.

## Directory Ownership

All WPT automation belongs under `wpt/`:

| Path | Ownership |
| --- | --- |
| `corpus/` | Ignored sparse checkout of the upstream WPT revision. |
| `tools/` | Tracked inventory, browser, AUI, comparison and report executors. |
| `config/` | Tracked runner configuration, generated-case policy and client fixtures. |
| `output/` | Ignored per-case snapshots, cache, logs and diff artifacts. |
| `progress.md` | Tracked generated compatibility progress table. |

No WPT-specific script, fixture, report, cache or upstream file should be
added outside this directory.

## Known Limits

WPT files may rely on HTML parsing recovery, browser UA styles, fonts, network
server endpoints, `iframe`, testdriver, or APIs absent from AUI. Such cases are
valuable specification references but are not automatically runnable. The
manifest makes that boundary visible and gives each excluded case a future home
instead of presenting AUI as fully browser-conformant.
