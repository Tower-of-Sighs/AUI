# Resource Browser Font Short 13px Baseline Residual

Purpose: track the isolated `short13X114625` font raster residual outside the main strict-browser goal file. This issue is intentionally deferred so `doc/guide/resource-browser-visual-todo-2026-07-15.md` can continue on higher-impact browser-parity work.

## Status

- Status: deferred.
- Source task: `RBV-V5-05` in `doc/guide/resource-browser-visual-todo-2026-07-15.md`.
- Scope: one 13px short-text baseline/skip width mismatch in the classifier sweep fixture.
- Impact: low visual impact, high strict-gate impact if the sweep requires `12/12` exact width parity.
- Do not solve this by tuning 12px cutoff action selectors.

## Residual

Sample:

- Fixture: `tests/resource-browser-font-raster-classifier-sweep.html`.
- Sample id: `short13X114625`.
- Text: `5 ITEMS`.
- Font: `Arial, sans-serif`, `13px`, `letter-spacing: 1px`.
- Runtime action selected by `strictRuntimeV1`: `skip`.
- Browser width: `98`.
- AUI baseline/skip width: `97`.
- Delta: `-1`.

Neighbor controls:

- `short13X114375`: browser `98`, AUI baseline `98`, exact.
- `short13X114500`: browser `98`, AUI baseline `98`, exact.
- The failure is position-sensitive, not a general 13px short-text failure.

## Evidence

Primary logs:

- Browser oracle: `run/resource-browser-font-raster-classifier-sweep-browser-last.log`.
- Browser screenshot: `run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png`.
- Baseline columns: `run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log`.
- Runtime classifier probe: `run/resource-browser-font-runtime-classifier-probe-with-sweep.log`.
- Apply-action probe: `run/resource-browser-font-apply-action-probe-runtime-selector.log`.

Key recorded result from the runtime selector probe:

```text
sample=short13X114625 browserWidth=98 baseline=97 baselineError=-1 cutoff1=96 cutoff1Error=-2 cutoff2=95 cutoff2Error=-3 strictRuntimeV1=skip fontSize=13 cssPosition=114.625,564.03125 sourceInkBounds=3,6,97,17 physicalInkRight=298.033749 rightFrac=0.033749 text='5 ITEMS'
```

Interpretation:

- `skip` is the correct action for this row.
- `source-cutoff-1` and `source-cutoff-2` make it worse.
- The residual is a baseline glyph/edge/rasterization issue, not an apply-action-selection issue.

## Reopen Criteria

Reopen this residual only when one of these is true:

- The main `RBV-V5-05` task is otherwise ready to promote and this `-1px` row is the only blocker.
- A new browser-standard text baseline fixture is being built for 13px short text.
- A full `resource.html` comparison shows a visible 13px short-label regression traceable to this residual.

## Next Action When Reopened

Create or run a minimal browser-standard fixture focused on 13px `Arial` short text around x positions `114.375`, `114.5`, and `114.625`.

The first hypothesis should explain why `short13X114625` is `-1` while the two neighboring positions are exact, without changing 12px long-text cutoff behavior.

Useful starting commands:

```powershell
.\scripts\resource_browser_font_apply_action_probe.ps1 `
  -OutLog run\resource-browser-font-apply-action-probe-runtime-selector.log

.\scripts\resource_browser_font_raster_columns.ps1 `
  -BrowserImage 'run/screenshots/browser/resource-browser-font-raster-classifier-sweep-1463x843-dsf-aui.png' `
  -BrowserMetricsLog 'run/resource-browser-font-raster-classifier-sweep-browser-last.log' `
  -AuiMetricsLog 'run/resource-browser-font-raster-classifier-sweep-aui-baseline-texture-gutter-last.log' `
  -AuiImage 'run/screenshots/aui/2026-07-15_20.43.10.png' `
  -OutLog 'run/resource-browser-font-raster-classifier-sweep-columns-baseline-texture-gutter.log' `
  -SampleIds 'short13X114375,short13X114500,short13X114625' `
  -GlyphDarknessThreshold 20
```

Do not edit `src/main/resources/assets/apricityui/apricity/devtools/resource.html` for this residual.
