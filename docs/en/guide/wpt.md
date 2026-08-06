# WPT Layout Comparison

`wpt/` treats Web Platform Tests CSS layout pages as the baseline: the same HTML first has element geometry snapshots captured in Chromium, then is parsed and captured by the AUI client, and the two sides compare tag/id and rectangles in DOM order (threshold 0.25 CSS pixels).

**`pass` only means "AUI and the browser's geometry snapshots match"** — it does not mean passing the WPT spec tests. testharness assertions, reftest reference pages, and JS semantics are all out of scope.

## One-time setup: corpus

`wpt/corpus` is a pinned-revision WPT sparse checkout (gitignored — you must fetch it yourself):

```powershell
git clone --filter=blob:none --no-checkout https://github.com/web-platform-tests/wpt.git wpt/corpus
git -C wpt/corpus sparse-checkout init --cone
git -C wpt/corpus sparse-checkout set common css/CSS2 css/css-align css/css-box css/css-break css/css-contain css/css-display css/css-flexbox css/css-grid css/css-inline css/css-multicol css/css-overflow css/css-position css/css-sizing css/css-tables css/css-text css/css-transforms css/css-ui css/css-values css/css-writing-modes css/support fonts resources
git -C wpt/corpus checkout a6f29b0bedaf3f1edba7b6739127fe8e713bfcb3
```

The revision is pinned in `wptRevision` in `wpt/config/runner.json`. Don't routinely `git pull` — if upstream changes, results are no longer reproducible.

The environment also needs: Node.js, Chrome/Chromium/Edge (set `CHROME_PATH` if none is found), and Java 17 + Gradle wrapper.

## Running

From the repository root:

```powershell
node wpt/tools/run.mjs --mode inventory              # scan and build the inventory only; no browser or MC
node wpt/tools/run.mjs --mode incremental            # daily use: only run new/changed/not-yet-passed cases
node wpt/tools/run.mjs --mode incremental --limit 20 # small batches for debugging
node wpt/tools/run.mjs --mode full                   # check everything (still reuses already-passed snapshots)
```

- `--limit N` takes the first N entries after sorting by path — it does **not** select a specific file, and there is no random sampling. To narrow the scope, change `layoutDirectories` in the config or write a custom `--config`;
- Incremental reuse rule: if the sourceHash is unchanged and both the browser and AUI stages have passed, those stages are skipped;
- To fully re-capture: stop all related processes, delete `wpt/output/`, then run full. This discards historical results and is not a daily operation.

Execution chain: scan and build inventory → browser stage (local HTTP server + headless Chrome probe) → cases that pass the browser enter the AUI stage (`gradlew runWptClient` starts an MC client and parses them in batches) → compare and write results. If you kill it midway, results may contain some new entries while the summary is still from the previous run.

## Reading results

| File | Content |
| --- | --- |
| `wpt/progress.md` | Human-readable progress table (auto-generated — don't edit by hand) |
| `wpt/output/results.json` | Per-case results; read this for machine processing |
| `wpt/output/run-summary.json` | Summary of the most recent run |
| `wpt/output/browser/<hash>.json` | Browser snapshots |
| `wpt/runtime/logs/latest.log` | AUI client log (check this to troubleshoot the AUI side; search for `[AUI WPT]`) |

Final statuses:

| Status | Meaning |
| --- | --- |
| `pass` | Snapshots from both sides match |
| `layout-mismatch` | Both have snapshots, but node count / tag / id / rectangles exceed the threshold |
| `aui-runtime-unsupported` | AUI threw an exception, didn't finish parsing, or didn't send results back |
| `browser-test-failed` | Chrome failed to load or the probe failed |
| `infra-blocked` | The page needs the WPT server or external network; skipped intentionally (**not a layout failure**) |
| `timeout` | Some stage timed out |
| `pending` | Not run yet |

**Standard procedure for investigating a mismatch**: find the case in results → read `reason` (e.g. `node-count browser=7 aui=14`, `node-3 rect[2] browser=120 aui=119.75`) → compare the snapshot in `wpt/output/browser/` against the aui snapshot embedded in results, and find the first difference in node order.

For node-count / identity differences, check HTML parsing, head nodes, hidden nodes, and default styles first — **don't jump straight to changing the layout algorithm**. The AUI side currently does not filter style/script/link nodes while the browser side does, so visually identical pages can still mismatch.

Page classification is a source-regex heuristic: pages containing testharness.js are marked testharness; pages containing server-dependency patterns (`.sub.`, `?pipe=`, testdriver.js, etc.) or external HTTP resources are marked infra-blocked. These pages never enter the browser batch.

## Common problems

**Reports "layout directory missing"**: the corpus wasn't fetched or the sparse checkout is missing directories; check with `git -C wpt/corpus sparse-checkout list`.

**Browser not found**: set `CHROME_PATH` to the actual exe.

**Large numbers of infra-blocked**: that's the classification result, not Chrome crashing. The current implementation does not start a WPT server itself.

**AUI wrote no results**: when running `./gradlew.bat runWptClient` manually, if the `AUI_WPT_CLIENT_INPUT/OUTPUT` environment variables are not set, it will not enter the WPT flow — this is expected; the runner sets them normally. For real problems, check `wpt/runtime/logs/latest.log`.

**Large numbers of aui-runtime-unsupported**: analyze using both `aui.reason` in results and the runtime log: pages using CSS that AUI hasn't implemented, exceptions during refresh, interrupted batches, and watchdog triggers are all different problems.

## Notes

- Don't run multiple full jobs at once;
- Pin the revision, viewport, and browser version, or results won't be reproducible;
- `wpt/output/` is a rebuildable cache and `wpt/runtime/` contains logs and world data — commit neither;
- Before clearing output, make sure no Node/Gradle/MC processes are running.
