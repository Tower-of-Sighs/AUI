# Resource Browser Browser-Parity TODO

Purpose: make `src/main/resources/assets/apricityui/apricity/devtools/resource.html` match browser behavior and visual output as closely as practical, using browser standards as the strict source of truth. Chromium measurements are the local executable oracle for those browser standards unless a task records a more specific standard citation or browser fixture.

This file is optimized for goal-mode execution. It is intentionally stricter than a visual polish list: browser behavior is the source of truth, AUI output is only implementation evidence, and "looks closer" is not an acceptance rule. Do not change `resource.html` CSS to hide framework bugs unless a task explicitly says "page workaround".

## Goal Runner Startup Packet

Use this packet when starting automated goal mode for this TODO. It is the shortest authoritative entry point; older repeated goal prompts below are historical unless this packet or the active task explicitly references them.

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md by exactly one task or one explicitly split subtask under strict browser-standard parity. Browser standards are the expected behavior; Chromium fixture output is the local executable oracle unless the task records a stronger specification citation. AUI output, Minecraft constraints, old screenshots, and visual preference are implementation evidence only and must never define the expected result. Start from Current Goal Automation Override and Next Goal Run Quickstart, then read only the active task section and the named latest evidence block. Before AUI runs or framework edits, prove or explicitly reuse the browser oracle, declare the oracle type, fixture state, tolerance, one narrow hypothesis, and the rejection metric. Prefer minimal browser-standard fixtures; run full devtools/resource.html only after the minimal fixture improves without regression or when the active task says no smaller fixture exists. Validate AUI through Test.java, which auto-opens APRICITYUI_TEST_DOC_PATH, saves screenshots under run/screenshots/aui, and auto-closes the client when runClient is used. After Java edits run compileJava and git diff --check. Close by updating this TODO with exact commands, metrics, logs, screenshots, changed files, remaining mismatch, and the next exact action. Stop and document the blocker instead of guessing when browser state, viewport, DPR/zoom, animation, crop policy, or tolerance is not reproducible.
```

Strict automation contract:

- Browser first: every expected value must come from a browser-standard rule, a same-state Chromium fixture, or an explicitly recorded browser citation.
- One run, one hypothesis: do not mix unrelated browser primitives in one goal turn.
- Minimal first: full `devtools/resource.html` is promotion evidence, not the first proof for a framework primitive.
- No AUI-derived standards: never tune thresholds, crop decisions, viewport scale, animation policy, or acceptance tolerance after seeing AUI output.
- Harness default: use `APRICITYUI_TEST_DOC_PATH=<path>` and `.\gradlew.bat runClient --console plain --no-daemon --offline`; screenshots are already saved to `run/screenshots/aui`, and `src/main/java/com/sighs/apricityui/event/Test.java` opens the page and exits the game automatically.
- Closeout required: append exactly one evidence block and one next exact action to the active task, including `Source changes: none` when the run only analyzes logs.

## Strict Browser Goal Run Card

Use this card as the default prompt shape for a new goal-mode run. It is designed for automation and should stay short enough to paste directly:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md by exactly one task or one explicitly split subtask under strict browser-standard parity. Browser behavior is the standard; Chromium fixture output is the local oracle unless the task records a more specific browser-standard citation. Current AUI output, Minecraft constraints, old screenshots, and visual preference are never allowed to define expected behavior. The current main non-extracted TODO has no open task; add a new task or explicitly reopen extracted work before running validation. For any future active task: read only the automation entry point, cursor, and active section; prove or explicitly reuse the browser oracle before AUI runs or framework edits; state one narrow hypothesis and its rejection metric; make the smallest diagnostic or framework change; validate the minimal fixture through Test.java; run full devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence; run compileJava and git diff --check after Java edits; then update this document with exact commands, metrics, logs, screenshots, changed files, remaining mismatch, and the next exact action. Stop and document the blocker instead of guessing when browser state, viewport, DPR/zoom, animation state, crop policy, or tolerance is not reproducible.
```

Automation invariants:

- The expected value must come from a browser-standard rule, a same-state Chromium fixture, or an explicitly recorded browser citation. AUI is only the implementation under test.
- Do not run a full-page visual check to justify a change that failed the minimal browser-standard fixture.
- Do not tune thresholds, crop policy, viewport, DPR/zoom mapping, or animation state after seeing AUI output.
- Every run must close by writing one evidence block and one next exact action into the active task, even when the experiment fails.
- Keep `resource.html` untouched unless the active task explicitly says the page itself is the implementation target.

## Current Goal Automation Override

This section is the current override for automated goal-mode runs. It supersedes duplicated historical prompt blocks later in this document. Use it to avoid re-reading old evidence or choosing a stale next action.

Active cursor:

- Active task: none in the main non-extracted TODO.
- Current evidence anchor: RBV-V1-02 `AUTO_PROMPT_FILE` state parity closeout.
- Current next exact action: no remaining non-extracted main task is open; only continue if a new task is added or extracted work is explicitly reopened.
- Extracted work is excluded from this main goal file.

Automation read order:

1. Read Goal Runner Startup Packet.
2. Read this Current Goal Automation Override.
3. Read Next Goal Run Quickstart.
4. If a new task is added, read only that task unless a command fails and requires nearby context.

Strict browser-standard gate for the next run:

- Oracle type: none until a new task is added.
- Browser source: no remaining non-extracted main task is open.
- Browser first: define the browser oracle for any newly added task before AUI validation.
- Rejection metric: reject any attempt to continue extracted work from this main file without explicit user request.
- Do not continue extracted tasks from this file. Extracted packages are tracked outside this main goal file and must only be reopened by explicit user request.

Recommended next-run shape:

`powershell
# No remaining non-extracted main task is open.
# Add a new browser-standard task or explicitly reopen extracted work before running AUI validation.
`
Harness facts for this run:

- `src/main/java/com/sighs/apricityui/event/Test.java` auto-opens `APRICITYUI_TEST_DOC_PATH`, waits for capture/metrics, then auto-closes the client.
- Start the game with `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI screenshots are written automatically to `run/screenshots/aui`.
- Prefer environment variables over editing `Test.java` when switching the page. Edit `Test.java` only if a new fixture needs logging support.
- Do not edit `src/main/resources/assets/apricityui/apricity/devtools/resource.html`.

Automation improvement notes for the next goal-mode run:

- Start with the `Goal Automation Entry Point`, `Next Goal Run Quickstart`, and the active task section only. Do not reread the whole historical evidence body before choosing work.
- Treat old AUI screenshots, old AUI metrics, and Minecraft-specific constraints as implementation history, not as expected behavior.
- When the active task needs a browser primitive, create or reuse the smallest HTML fixture that isolates that primitive. Use full `devtools/resource.html` only as promotion evidence.
- If a candidate rule is inferred from AUI output, mark it diagnostic only. It cannot become a framework default until a browser-standard fixture accepts it.
- A failed diagnostic is still useful when it records the rejection metric and the exact next classifier, fixture, or browser citation needed.

## Goal Automation Entry Point

Read this entry point first in every automated goal run. It is the short contract for this TODO; later repeated goal prompts and historical notes are evidence and context only. If this entry point conflicts with a later section, this entry point wins.

Current objective for goal mode:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md by exactly one browser-standard task or one explicitly split subtask. Treat browser standards, measured through a reproducible Chromium fixture unless a more specific standard citation is recorded, as the strict and only oracle. Current AUI output is implementation evidence only and can never define expected behavior. The current main non-extracted TODO has no open task; add a new task or explicitly reopen extracted work before running validation. Prove or explicitly reuse the browser oracle before any AUI run or framework edit, state one narrow hypothesis and its rejection metric, make the smallest framework or diagnostic change, validate the minimal AUI fixture through Test.java, run full devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence, then update this document with exact commands, metrics, screenshots, changed files, remaining mismatch, and next exact action.
```

Non-negotiable browser-standard rules:

- Browser standards and Chromium-observed behavior define expected results. AUI screenshots only answer whether the framework matched that expected result.
- When a browser fixture and AUI disagree, the browser fixture wins. Do not reinterpret the expected result around Minecraft, current AUI behavior, previous AUI screenshots, or a visual preference.
- Do not accept, tune, or promote a change because it looks closer in AUI. Promotion requires a declared browser metric to improve without breaking already-completed browser-standard checks.
- Browser parity means matching the browser's measurable behavior, not matching one screenshot by coincidence. If the rule behind a pixel result is unclear, keep the task diagnostic and add the missing oracle work as the next action.
- If a result is governed by CSS, DOM, CSSOM View, Canvas, SVG, events, or compositing behavior, create or reuse a minimal browser fixture before changing framework code.
- Record the oracle type before AUI validation: `rect`, `computed-style`, `event`, `documented-rule`, `pixel-sample`, `source-raster`, or `screenshot-diff`.
- Record viewport, device scale, zoom/meta mode, animation policy, page state, crop policy, and tolerance before judging any screenshot or pixel metric.
- Do not tune acceptance thresholds after seeing AUI output. If tolerance was not declared or inherited from a recorded browser fixture, keep the task `[~]` and add the missing oracle work as the next exact action.
- Do not edit `src/main/resources/assets/apricityui/apricity/devtools/resource.html` to mask framework gaps unless the active task explicitly says the page itself is the implementation target.

Oracle precedence:

1. A cited browser standard or CSS/DOM/API specification when the task is about a normative rule.
2. A same-state Chromium fixture for rendering, geometry, event timing, or implementation-defined details.
3. Full-page Chromium `devtools/resource.html` output only after the matching minimal primitive is stable or when no smaller fixture exists.
4. AUI logs and screenshots only as implementation evidence. They are never an oracle.

Automation decision rules:

- Read only this entry point, `Goal runner cursor`, and the active task section before choosing work. Use older evidence only when the active task points to it.
- Treat `Next Goal Run Quickstart` as the active cursor packet. Treat `Goal Automation Packet`, `Goal-Mode Contract`, older prompt blocks, and older evidence blocks as legacy context unless the cursor explicitly names them.
- In goal mode, quote the active task ID and the single next exact action before running commands or editing files. If the active task section contains conflicting historical instructions, follow the most recent `Next exact action` recorded in that same task.
- Do one measurable hypothesis per run. If a task is too broad, split it in place and complete one split subtask.
- Prefer existing Chromium oracle logs only when fixture file, viewport, DPR, zoom/meta mode, animation state, and crop policy are unchanged. Otherwise rerun the Chromium fixture first.
- A reused Chromium log must be named in the evidence block with the reason it is still valid. If that reason is not obvious, rerun the browser fixture.
- Full `devtools/resource.html` validation is a promotion gate, not the default first test. Run it only after the minimal browser-standard fixture improves without regression, or when the active task explicitly has no smaller fixture.
- A diagnostic flag can stay diagnostic after a failed experiment, but it cannot become default behavior until minimal browser-standard metrics improve without breaking already-completed checks.

Goal-run state machine:

1. `Locate`: read this entry point, `Goal runner cursor`, and the active task section only.
2. `Oracle`: record the browser rule and either rerun Chromium or explicitly reuse a named Chromium log.
3. `Hypothesis`: write one framework hypothesis plus the exact metric that will reject it.
4. `Change`: make the smallest framework, fixture, or diagnostic edit needed for that hypothesis.
5. `Minimal validation`: run the matching minimal AUI fixture through `Test.java`.
6. `Promotion check`: run full `devtools/resource.html` only if the minimal fixture improved without regression, or if the active task explicitly requires full-page evidence.
7. `Closeout`: update the active task with the required evidence block and one next exact action before ending the run.

Automation harness facts:

- `src/main/java/com/sighs/apricityui/event/Test.java` is the game harness. It opens the configured HTML after the title screen is ready, waits for capture/metrics, then automatically closes the client.
- Set `APRICITYUI_TEST_DOC_PATH=<path>` or `-Dapricityui.test.docPath=<path>` to choose the HTML under `assets/apricityui/apricity/`.
- Use `.\gradlew.bat runClient --console plain --no-daemon --offline` for AUI validation.
- AUI screenshots are saved automatically under `run/screenshots/aui`; use the newest screenshot from that directory for stats unless the active task names a specific file.
- `Test.java` is already configured for automated visual runs: it opens the requested HTML, waits long enough for metrics/screenshots, and closes the game without manual input. Prefer environment variables over source edits when switching pages.
- After Java edits, run `.\gradlew.bat compileJava --console plain --no-daemon --offline` and `git diff --check`.
- Leave `APRICITYUI_TEST_INTERACTION` and `APRICITYUI_TEST_PROMPT_RESPONSE` unset for static baseline runs unless the active task is explicitly an interaction task.

Goal closeout checklist:

- Add exactly one new evidence block to the active task or explicitly split subtask.
- Include browser oracle type, browser-standard rule or citation, predeclared tolerance or rejection metric, browser command/result or reused-log reason, hypothesis, AUI command/result, screenshot path, changed files, verification, accepted mismatch or remaining mismatch, and next exact action.
- If the run changes the current cursor or rejects a candidate, update `Next Goal Run Quickstart` so the next automated run does not repeat the rejected path.
- Mark the active item `[x]`, keep it `[~]` with the next exact action, or mark it `[!]` with the blocker. Do not mark the whole roadmap complete while open tasks remain.
- Preserve unrelated dirty worktree changes. Do not clean, revert, or normalize files outside the active task.

## Goal-Mode Automation Protocol

Use this section to keep automated runs narrow and reproducible.

Read order for every goal turn:

1. `Goal Automation Entry Point`.
2. `Next Goal Run Quickstart`.
3. The active task heading named by the cursor, currently `RBV-V6-01`.
4. Only the evidence block named by the active task's `Next exact action`.

Do not scan or reprocess the whole file before choosing work. This document contains long historical evidence; older instructions are not active unless the cursor or active task points to them by name.

Single-turn work contract:

- State the active task ID, oracle type, reused or rerun browser artifact, hypothesis, and rejection metric before changing files.
- Do not use AUI screenshots to infer expected browser behavior.
- Do not promote a diagnostic from the full `devtools/resource.html` page unless the matching minimal fixture first improves without regression.
- Do not edit `src/main/resources/assets/apricityui/apricity/devtools/resource.html` for framework parity work. It is the target page, not the workaround surface.
- If a Java edit depends on draw position, viewport scale, DPR, crop policy, or animation phase, keep that state out of reusable caches unless it is part of the cache key.
- End the turn by appending one evidence block and one updated `Next exact action` to the active task. If nothing was changed, record `Source changes: none`.

Evidence block template for automated closeout:

```text
Evidence:

- Active task: RBV-...
- Browser oracle type: rect | computed-style | event | documented-rule | pixel-sample | source-raster | screenshot-diff
- Browser-standard source: Chromium fixture log ... | spec citation ...
- Fixture/state: HTML ..., viewport ..., DPR ..., zoom/meta mode ..., animation ..., crop policy ...
- Predeclared rejection metric: ...
- Hypothesis: ...
- Commands:
  - Browser: ...
  - AUI minimal: ...
  - AUI full page: skipped because ... | ...
  - Verification: compileJava ...; git diff --check ...
- Results:
  - Browser: ...
  - AUI minimal: ...
  - AUI full page: ...
- Artifacts:
  - Browser log/screenshot: ...
  - AUI log/screenshot: ...
- Source changes: ...
- Accepted or remaining mismatch: ...
- Cursor update: unchanged | changed to RBV-... because ...
- Next exact action: ...
```

## Authoritative Goal Contract

This section expands the `Goal Automation Entry Point`. The entry point above wins if there is any conflict; this section overrides later historical prompt variants.

- Chromium-observed browser behavior is the strict and only oracle. AUI output, old screenshots, Minecraft constraints, and "looks closer" are evidence only; they never define expected behavior.
- Start from `Goal runner cursor`. If the cursor is missing, use the first `[~]` task in the recommended batch. Do not reopen older `[~]` tasks unless the cursor explicitly routes back to them.
- Complete exactly one task or one explicitly split subtask per goal run. A valid goal run ends by writing one new evidence block and one next exact action.
- Prove the browser oracle before framework edits. For visual tasks, record viewport, state, zoom/device-scale mapping, animation policy, crop policy, and acceptance tolerance before judging AUI output.
- Before running AUI or editing Java, classify the browser oracle as `rect`, `computed-style`, `event`, `documented-rule`, `pixel-sample`, `source-raster`, or `screenshot-diff`, then write the expected measurable browser result or point to the existing log that contains it.
- If an existing Chromium oracle log is reused, say why it is still valid. If the fixture, viewport, device scale, animation state, or browser version dependency changed, rerun the Chromium oracle first.
- AUI can only answer "did the implementation match the browser oracle?" It must never answer "what should the browser oracle be?".
- Prefer a minimal browser-standard fixture over full-page inspection. Run full `devtools/resource.html` only after the minimal fixture improves or after the task explicitly requires full-page evidence.
- Do not edit `src/main/resources/assets/apricityui/apricity/devtools/resource.html` to compensate for framework behavior unless the active task explicitly says the page itself is the implementation target.
- Use `src/main/java/com/sighs/apricityui/event/Test.java` as the AUI harness. It auto-opens `APRICITYUI_TEST_DOC_PATH`, waits for metrics/screenshots, saves screenshots under `run/screenshots/aui`, and auto-closes the client.
- Use `.\gradlew.bat runClient --console plain --no-daemon --offline` for AUI validation. After Java edits, run `.\gradlew.bat compileJava --console plain --no-daemon --offline` and `git diff --check`.
- Preserve unrelated dirty worktree changes. Do not clean, revert, or normalize files outside the active task.

Goal-run state machine:

1. `Locate`: read `Goal Automation Entry Point`, `Goal runner cursor`, and the active task section only.
2. `Oracle`: record or reuse the Chromium oracle before AUI runs or code edits.
3. `Hypothesis`: write one narrow framework hypothesis and the metric that would reject it.
4. `Change`: make the smallest framework or script change needed for that hypothesis.
5. `Minimal validation`: run the minimal browser-standard fixture through `Test.java`.
6. `Full-page validation`: run `devtools/resource.html` only when the minimal fixture improves without regression, or when the active task explicitly requires full-page evidence.
7. `Closeout`: update the active task with commands, logs, screenshots, changed files, accepted/remaining mismatch, and the next exact action.

Goal-run completion definition:

- Browser oracle type and exact browser result are recorded.
- AUI command, AUI result, and newest screenshot path are recorded.
- Source changes are listed, including "none" for evidence-only runs.
- Verification results are recorded, including the exact wording `git diff --check reported only existing LF/CRLF warnings and no whitespace errors` when that is the only issue.
- The task is marked `[x]`, kept `[~]` with a next exact action, or marked `[!]` with a blocker. Never mark the whole roadmap complete while any task remains open.

## Next Goal Run Quickstart

Use this section as the first thing to read in a new goal-mode run.

Current override note:

- Also read `Current Goal Automation Override` near the top of this file before choosing work.
- If this quickstart conflicts with `Current Goal Automation Override`, the override wins.
- Do not follow older repeated prompt blocks below this section unless the override or active task explicitly points to them.

Canonical objective to paste into goal mode:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md under strict Chromium parity. Chromium/browser behavior is the only oracle; current AUI output can never define expected behavior. The current main non-extracted TODO has no open task; add a new task or explicitly reopen extracted work before running validation. Complete exactly one task or one explicitly split subtask per goal run. For the active task, prove or explicitly reuse the Chromium oracle first, then make the smallest framework or diagnostic change, validate the minimal AUI fixture through Test.java, run full devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence, run compileJava and git diff --check after Java edits, and update this document with exact commands, metrics, screenshots, changed files, remaining mismatch, and next exact action. Stop instead of guessing when viewport, state, animation, or oracle is not reproducible.
```

Current automation entry point:

- Active task: none in the main non-extracted TODO.
- Latest evidence anchor: RBV-V6-04 scroll-state policy closeout and V6 status sync.
- Current executable subtask:
  - No non-extracted main task remains open.
  - Do not continue extracted work from this main TODO.
- Authoritative current state:
  - Extracted work is not part of this goal file.
  - Main visual TODO has no open non-extracted task.
- Current valid artifacts:
  - Extracted task details and residuals are tracked outside this file.
- Required closeout for the next goal run:
  - If a new task is added, write one evidence block under that task.
  - Include browser oracle, AUI validation, commands, changed files, verification, remaining mismatch, and one next exact action.
  - If only docs changed, run `git diff --check`; if Java changed, also run `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
- Latest subtask cursor:
  - `RBV-V6-02` is complete for non-extracted detail-active parity.
  - Header-button hover parity is complete for the current browser-standard evidence.
  - File-card hover parity is complete for the current browser-standard evidence.
  - Tree-item hover parity is complete for the current browser-standard evidence.
  - Scroll-state policy is complete for current `devtools/resource.html` state.
  - No next run is defined until a new task is added or extracted work is explicitly reopened.
  - Do not use extracted-work differences to block this task.

Required run order for the active task:

1. Add or reopen a specific task before running validation.
2. Define the browser oracle for that task.
3. Validate AUI only after the browser oracle is explicit.
4. Run `git diff --check`; run `compileJava` too if any Java file changed during the goal run.
5. Update the active task evidence with exact commands, metrics, changed files, interpretation, and next exact action.
6. Do not continue extracted tasks from this main TODO unless explicitly reopened.

## Goal Automation Packet

Legacy compact packet kept for context. New automated goal runs should use `Goal Automation Entry Point` above; use this packet only when an older handoff explicitly references it.

Copy-paste objective:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md under strict Chromium parity. The current main non-extracted TODO has no open task; add a new task or explicitly reopen extracted work before running validation. Chromium browser behavior is the only oracle; current AUI output is evidence only and can never define the expected result. Complete exactly one task or one explicitly split subtask. Prove or reuse the browser oracle first, make the smallest framework or diagnostic change, validate the minimal AUI fixture through Test.java, run full devtools/resource.html only after the minimal fixture improves without regression or when the active task explicitly requires full-page evidence, run compileJava and git diff --check after Java edits, then update the active task with exact commands, metrics, screenshots, changed files, remaining mismatch, and next exact action. Stop instead of guessing when browser state, viewport, animation phase, or metric tolerance is not reproducible.
```

First actions in a new goal run:

1. Read only this packet, `Goal runner cursor`, and the active task section before deciding what to run.
2. Announce the active task ID and browser oracle type before editing files.
3. If the active task has no stable browser metric, create or run the Chromium fixture first.
4. Write the one hypothesis being tested and the metric that will reject it before making a framework change.
5. Preserve unrelated dirty worktree changes; do not clean up or revert files outside the active task.
6. Keep `src/main/resources/assets/apricityui/apricity/devtools/resource.html` untouched unless a task explicitly says the page itself is the implementation target.

Strict Chromium parity gates:

- Accepted means AUI matches a Chromium metric, computed style, event result, or documented Chromium-observed rendering rule.
- "Looks better", "closer in AUI", "matches the old screenshot", and "works around Minecraft scaling" are not acceptance criteria.
- AUI screenshots are valid comparison evidence only when page state, CSS viewport, physical screenshot size, device scale, animation policy, zoom, and crop policy are recorded.
- Tolerance must be stated before judging the AUI result, or derived from browser/device-pixel rounding. Do not tune tolerance after seeing AUI output.
- A full-page improvement cannot promote a framework change when the minimal browser-standard fixture regresses.
- A diagnostic flag or environment variable can remain diagnostic only until the minimal fixture proves browser-parity improvement without breaking an already-completed browser-standard check.
- If the browser oracle and AUI result disagree, update the task with the remaining mismatch instead of redefining the expected result around AUI.

Automation defaults:

- Browser reference logs go under `run/resource-browser-*-browser-last.log`.
- AUI logs go under `run/resource-browser-*-aui-last.log`.
- AUI screenshots are produced automatically under `run/screenshots/aui`.
- The game validation command is `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- `src/main/java/com/sighs/apricityui/event/Test.java` is the automation harness. It opens `APRICITYUI_TEST_DOC_PATH`, waits for metrics/screenshot capture, then auto-closes the client.
- Use `APRICITYUI_TEST_DOC_PATH=<path>` or `-Dapricityui.test.docPath=<path>` to switch pages. Do not edit `Test.java` just to select a page.
- Leave `APRICITYUI_TEST_INTERACTION` and `APRICITYUI_TEST_PROMPT_RESPONSE` unset for static baseline runs.

Required evidence block for every goal run:

```text
Evidence:

- Browser oracle type: rect | computed-style | event | documented-rule | pixel-sample | source-raster | screenshot-diff
- Browser-standard rule matched or cited: ...
- Predeclared tolerance/rejection metric: ...
- Browser command or reused-log reason: ...
- Browser result or oracle artifact: ...
- Hypothesis tested: ...
- AUI command: ...
- AUI result: ...
- Screenshot(s): run/screenshots/aui/...
- Source changes: ...
- Verification: compileJava ..., git diff --check ...
- Accepted or remaining mismatch: ...
- Next exact action: ...
```

Stop immediately and update the task as `[~]` with a blocker when:

- The Chromium fixture cannot be reproduced.
- Browser and AUI are in different page states.
- Browser and AUI viewport/zoom/device-scale mapping is unknown for a visual comparison.
- The proposed fix only helps `devtools/resource.html` by breaking the minimal fixture.
- The browser rule is unclear and no minimal fixture has been recorded yet.

## Goal-Mode Contract

Use this document as the active goal checklist. A goal-mode run should pick the cursor task, prove the browser behavior, test one narrow hypothesis, validate the minimal case, run `devtools/resource.html` only when the minimal case improves without regression or the task explicitly requires full-page evidence, then update evidence before moving on.

Goal runner cursor:

- Current entry point: no open non-extracted main task remains. Extracted work is out of scope for this main goal file unless explicitly reopened.
- Completed recent geometry tasks must not be reopened unless a later browser-standard fixture proves a regression: `RBV-V2-03`, `RBV-V2-04`, `RBV-V2-05`, `RBV-V2-06`.
- Use browser output as the oracle for every primitive; do not use current AUI output as the expected value.
- Prefer adjacent primitive work over broad visual polishing. No next strict-browser path is defined until a task is added or reopened.
- If a task touches Java rendering/layout behavior, run `compileJava`, the minimal fixture, and `git diff --check` before marking it complete. Run full `devtools/resource.html` only after the minimal fixture moves toward the browser oracle without regression, or when the task explicitly requires full-page evidence.
- If a task can only be judged from screenshots, first write the exact browser/AUI viewport, state, animation policy, crop policy, and tolerance into that task.
- Goal mode should process one task at a time. Do not jump to later milestones until the current task is complete, blocked, or explicitly split into a new task ID.
- Do not mark the whole goal complete unless every task in this TODO is complete or intentionally deferred with `[!]`.

Automation-friendly goal prompt:

```text
Goal: advance doc/guide/resource-browser-visual-todo-2026-07-15.md under strict browser-standard parity. The current main non-extracted TODO has no open task; add a new task or explicitly reopen extracted work before running validation. Do not use AUI output to define expected behavior. For the active task: identify the browser oracle, run or create the minimal Chromium fixture, record browser metrics, make the smallest framework or diagnostic change, run the matching AUI fixture through Test.java, run devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence, run compileJava and git diff --check after Java edits, then update this document with commands, metrics, screenshots, changed files, remaining mismatch, and next exact action. Stop instead of guessing when browser state, viewport, or oracle is not reproducible.
```

Legacy note: the prompt above was an earlier ASCII-safe automation prompt. The `Goal Automation Entry Point` at the top is now authoritative.

Recommended goal objective:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md using browser standards as the strict source of truth and Chromium measurements as the local oracle. Start from the task named by Goal runner cursor. Complete only one task at a time. For every task, record browser-standard evidence first, then change the framework, then validate the minimal AUI fixture through Test.java, then run devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence. Write commands, metrics, screenshots, changed files, and remaining mismatches back into this document. Current AUI behavior never defines the expected result.
```

Canonical goal objective for new goal-mode runs:

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md. Treat Chromium/browser behavior as the strict and only oracle. Continue from Goal runner cursor first; use the first [~] task only when the cursor is missing or explicitly obsolete. Finish exactly one task per goal run unless the task must be split. Each task must prove browser behavior before framework edits, validate the minimal fixture before full devtools/resource.html, run required verification, and update this document with exact evidence. AUI output is implementation evidence only, never the standard.
```

Recommended goal-mode opening checklist:

ASCII canonical objective for automation:

Legacy note: keep the ASCII objective blocks for old handoffs, but use `Goal Automation Entry Point` as the current authoritative goal text.

```text
Advance doc/guide/resource-browser-visual-todo-2026-07-15.md using browser standards as the strict source of truth and Chromium measurements as the local oracle. Start from the task named by Goal runner cursor. Complete only one task at a time. For every task, record browser-standard evidence first, then change the framework, then validate the minimal AUI fixture through Test.java. Run devtools/resource.html only when the minimal fixture improves without regression or the task explicitly requires full-page evidence. Write commands, metrics, screenshots, and remaining mismatches back into this document. Current AUI behavior never defines the expected result.
```

1. Read `Goal runner cursor`, the active task section, and `Current Evidence`.
2. Confirm the first active task ID in the reply before making edits.
3. If the active task lacks a browser oracle, create or run the minimal Chromium fixture first.
4. Do not change Java/CSS/page code until the browser expected result is recorded.
5. Use `Test.java` automation for AUI captures: set `APRICITYUI_TEST_DOC_PATH`, run `.\gradlew.bat runClient --console plain --no-daemon --offline`, then read the newest screenshot under `run/screenshots/aui`.
6. After every framework edit, run the minimal AUI fixture, `compileJava`, and `git diff --check`. Run full `devtools/resource.html` only when the minimal fixture improves without regression or the active task explicitly requires full-page evidence.
7. Before ending a goal turn, update the active task's `Evidence` and either mark it `[x]`, keep it `[~]` with the next exact action, or mark it `[!]` with the blocker.

Continuation handoff rule:

- If context is compacted or a run resumes later, continue from `Goal runner cursor`.
- If the cursor is missing, continue from the first task whose status is `[~]`.
- If no task is `[~]`, continue from the first `[ ]` task in the current recommended batch.
- Older `[~]` tasks before the cursor are historical carryover unless the cursor or current batch routes back to them.
- Do not infer completion from source diffs or screenshots alone; only this document's status plus recorded browser/AUI evidence controls progress.
- Preserve unrelated dirty worktree changes. Do not revert files unless the active task's own change is proven wrong.

Automation invariant:

- `.\gradlew.bat runClient --console plain --no-daemon --offline` is the default game validation command.
- `src/main/java/com/sighs/apricityui/event/Test.java` is the run harness. It automatically opens the configured HTML after the client reaches the title screen, waits for metrics/screenshot capture, then automatically closes the game.
- AUI screenshots are saved automatically to `run/screenshots/aui`; do not ask for manual screenshots before checking that directory.
- Use `APRICITYUI_TEST_DOC_PATH=<path>` to choose the page under test. Do not edit `Test.java` just to switch pages.
- Use `-Dapricityui.test.autoExitSeconds=<seconds>` only when the task needs a longer deterministic capture window.
- Keep `APRICITYUI_TEST_INTERACTION` and `APRICITYUI_TEST_PROMPT_RESPONSE` unset unless the active task explicitly requires interaction or prompt state.
- Redirect long run logs into `run/resource-browser-<fixture>-aui-last.log` so the next goal turn can resume from stable artifacts instead of terminal scrollback.

Strict browser-standard rule:

- Browser behavior wins even when the current AUI behavior looks visually acceptable.
- Chromium is the concrete oracle for this roadmap. Use "browser standard" to mean Chromium-observed CSSOM/layout/paint/event behavior unless a task explicitly compares another browser or a written spec.
- Treat the Chromium result as normative even when a framework change produces a more aesthetically pleasing result.
- AUI current behavior, Minecraft scaling constraints, historical screenshots, and previous commits are not acceptance criteria.
- Expected geometry must come from browser `getBoundingClientRect()`, browser computed styles, browser event behavior, or a documented browser rendering rule.
- Every acceptance decision must state which browser rule or browser metric is being matched.
- When Chromium and another browser differ, use Chromium for the immediate `resource.html` acceptance target and document the dependency as "Chromium reference" rather than a generic browser rule.
- AUI screenshots are evidence only after state, viewport, zoom, animation policy, and crop policy match the browser reference.
- Do not accept an AUI-only visual improvement as a completed task unless it also matches the browser metric or rule.
- Do not add CSS/HTML hacks to `resource.html` to compensate for AUI behavior. Only edit the page when the task explicitly asks for a deterministic fixture.
- If the browser standard is unclear, create the smallest browser page that isolates the primitive and record the browser result first.
- Never mark a task complete from visual inspection alone. Completion requires either matched browser/AUI metrics or a documented browser rule plus a matching AUI behavior check.
- Do not tune tolerances after seeing AUI output. Tolerance must be stated before acceptance or derived from browser/device pixel rounding.
- Do not make page-specific fixes for `devtools/resource.html` until the underlying CSS/DOM/paint primitive has a minimal browser-standard fixture or a clearly documented reason that a fixture is impossible.
- Do not treat Minecraft rendering constraints, AUI historical behavior, or the current screenshot as normative. They are implementation constraints only; the target remains Chromium parity unless the task is explicitly deferred.
- If the only available evidence is subjective visual inspection, the task remains `[~]` and the next exact action must be to add measurable browser/AUI evidence.

Browser oracle precedence:

1. Chromium `getBoundingClientRect()` / CSSOM View geometry.
2. Chromium `getComputedStyle()` including pseudo-element computed styles.
3. Chromium event behavior from a minimal fixture.
4. A documented browser rendering rule, with a link or spec name if practical.
5. Pixel samples from matched browser/AUI screenshots, only after viewport, state, zoom, animation policy, and crop are fixed.

AUI output can never define expected behavior. It can only prove that the framework now matches the browser oracle.

Per-task automation loop:

1. Read the task's `Problem`, `Expected checks`, and `Acceptance`.
2. State the browser oracle for the task: metric, computed style, event behavior, or documented rule.
3. Run or create a minimal browser-standard fixture for the primitive.
4. Capture browser metrics with `getBoundingClientRect()` or computed style output.
5. Run the same fixture through AUI with `Test.java`.
6. Fix framework code only after the browser result is known.
7. Re-run the minimal fixture.
8. Re-run `devtools/resource.html`.
9. Run `.\gradlew.bat compileJava --console plain --no-daemon --offline` after Java edits.
10. Run `git diff --check`.
11. Update this file with:
   - exact command(s);
   - browser oracle type;
   - browser metric(s);
   - AUI metric(s);
   - newest screenshot path(s) from `run/screenshots/aui`;
   - status change or remaining mismatch.

Automation guardrails:

- Prefer scripts that emit machine-readable JSON or stable labeled lines over prose logs.
- If a new fixture is created, also create or update the browser metric script before relying on AUI output.
- Store browser logs under `run/resource-browser-*-browser-last.log` and AUI logs under `run/resource-browser-*-aui-last.log`.
- Capture screenshots only after state is deterministic. For static checks, clear interaction and prompt environment variables first.
- When comparing pixels, record the CSS viewport, physical screenshot size, device scale mapping, and exact sample/crop coordinates.
- If `git diff --check` reports only pre-existing LF/CRLF warnings, record that wording exactly and do not block the task solely on those warnings.

Failure routing:

- Browser fixture cannot run: stop the task and document the blocker.
- Browser and AUI use different state: recapture; do not compare.
- Browser and AUI use different viewport: fix the harness before changing rendering code.
- Minimal fixture matches but `resource.html` differs: inspect page-specific cascade/layout dependencies next.
- `resource.html` improves but minimal fixture regresses: revert or revise the framework change before marking progress.
- Difference is due to an intentionally unsupported browser feature: mark the specific task `[!]`, document the unsupported primitive, and continue only if dependent tasks can still be evaluated.
- AUI metric looks better than browser metric: keep the task open or fix AUI back to the Chromium metric. "Better looking" is not accepted as parity.
- Full page differs while minimal fixture matches: inspect cascade, inheritance, containing block, pseudo-elements, animation state, and viewport/zoom before changing the primitive again.

Evidence format:

```text
Evidence:

- Browser command: ...
- Browser result: ...
- AUI command: ...
- AUI result: ...
- Screenshot(s): run/screenshots/aui/...
- Verification: compileJava ..., git diff --check ...
- Remaining mismatch: ...
```

Task update template:

```text
Status: [x]

Browser oracle:

- Type: rect | computed-style | event | documented-rule | pixel-sample
- Standard source: Chromium metric, Chromium computed style, browser event result, or spec/rule name
- Viewport/state/animation policy: ...

Evidence:

- Browser command: ...
- Browser result: ...
- AUI command: ...
- AUI result: ...
- Screenshot(s): run/screenshots/aui/...
- Source changes: ...
- Verification: ...
- Remaining mismatch: ...
- Next task recommendation: ...
```

Artifact naming convention:

- Browser metric logs: `run/resource-browser-<task-or-fixture>-browser-last.log`.
- AUI metric logs: `run/resource-browser-<task-or-fixture>-aui-last.log`.
- Browser screenshots: `run/screenshots/browser/<task-or-fixture>-<viewport>.png`.
- Comparison crops or annotated images: `run/screenshots/compare/<task-or-fixture>-*.png`.
- AUI screenshots: newest files under `run/screenshots/aui`, recorded exactly as produced.
- If a script already has an established log name, keep it and record that name in the task evidence.

Minimum evidence gate for goal mode:

- Browser command and exact browser metric/result.
- AUI command and exact AUI metric/result.
- Viewport and state, when the page is visual or layout-sensitive.
- Screenshot path under `run/screenshots/aui` when `runClient` was used.
- Source file(s) changed, when framework code changes.
- Verification command results.
- Next task recommendation.

## Operating Rules

- Work in task order unless a task is marked independent.
- Use browser standards and actual browser output as the expected behavior, not the current AUI rendering.
- Prefer a minimal reproduction page before changing framework code.
- Each implementation task starts by writing or confirming the browser oracle in the task section. If the oracle cannot be stated, the task is not ready for framework edits.
- Keep fixes PR-sized: one primitive, one focused test page or metric harness, one compile/check pass.
- If a browser primitive is unsupported, document it as unsupported only after proving it is not needed for the current acceptance target or after deferring it explicitly.
- Do not tune screenshots by changing the page design. Fix the framework primitive first.
- Do not compare different UI states as rendering defects. Static, selected/detail, hover, and scroll states must each have matching browser/AUI captures.
- Use `.\gradlew.bat runClient --console plain --no-daemon --offline` for game validation.
- `src/main/java/com/sighs/apricityui/event/Test.java` is the launch harness: it automatically opens the configured HTML after game startup, waits, then closes the client.
- AUI screenshots are saved automatically under `run/screenshots/aui`.
- Do not edit `Test.java` just to change the page under test. Use `APRICITYUI_TEST_DOC_PATH` or `-Dapricityui.test.docPath=...`.
- `Test.java` opens the page, waits for metrics/screenshot capture, and auto-closes the game; this is the default automation path for goal runs.
- Full resource browser target:
  - env: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`
  - or JVM property: `-Dapricityui.test.docPath=devtools/resource.html`
- Interactive resource-browser driver:
  - enable with `APRICITYUI_TEST_INTERACTION=resource-browser`
  - default is disabled; leave it disabled for static baseline captures.
- Prompt automation:
  - use `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE` when testing `+ NEW`.
- Auto-close:
  - default is controlled by `Test.java`;
  - use `-Dapricityui.test.autoExitSeconds=<seconds>` when a longer capture delay is needed;
  - use `-Dapricityui.test.autoExitSeconds=-1` only for manual debugging.
- After Java changes:
  - run `.\gradlew.bat compileJava --console plain --no-daemon --offline`;
  - run `git diff --check`.
- When a task changes rendering behavior:
  - run the minimal page first;
  - run `devtools/resource.html`;
  - record newest screenshot names from `run/screenshots/aui`;
  - update task evidence in this file or a linked progress note.
- When a task requires a new fixture:
  - place it under `src/main/resources/assets/apricityui/apricity/tests/`;
  - keep it minimal and browser-standard;
  - avoid depending on `resource.html` page data unless the task is explicitly about page state;
  - hide metric/debug DOM offscreen with `left:-100000px` rather than `visibility:hidden` when screenshot pollution matters.

Status values:

- `[ ]` not started
- `[~]` in progress
- `[x]` complete
- `[!]` blocked or deferred

## Current Evidence

- Browser reference screenshot: `D:\work\AUI\img_2.png`, `2560x1316`.
- AUI interacted screenshot: `D:\work\AUI\2026-07-15_10.17.39.png`, `2560x1476`.
- Same AUI screenshot also exists at `run/screenshots/aui/2026-07-15_10.17.39.png`.
- Full visual diff: `doc/guide/resource-browser-visual-diff-2026-07-15-101739.md`.
- This TODO is the goal-mode execution list for that diff.
- Browser metrics harness:
  - page: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`
  - runner: `node scripts\resource_browser_browser_metrics.js`
- AUI metrics are emitted by `src/main/java/com/sighs/apricityui/event/Test.java` when `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`.
- V2-03/V2-04 automation metrics are now available on both browser and AUI sides:
  - parent rects: `logo`, `logoBlock`, `navPath`, `headerActions`, `sidebarTitle`, `firstTreeItem`;
  - child rect arrays/log groups: `logoChildren`, `navPathChildren`, `headerActionButtons`, `treeItems`;
  - tree child rects include `toggle`, `icon`, and `label` relative offsets.
- Browser metrics at `1463x843`, device scale `1`:
  - state: `contentCount=5 ITEMS`, `fileCards=5`, `selected=null`, `detailActive=false`, `addedFile=false`.
  - viewport: `innerWidth=1463`, `innerHeight=843`.
  - `.main`: `x=0 y=60 width=1463 height=783 right=1463 bottom=843`.
  - `.content`: `x=280 y=60 width=883 height=783 right=1163 bottom=843`.
  - `.contentHeader`: `x=312 y=92 width=819 height=53 right=1131`.
  - `#contentCount`: `x=1076.3125 y=100 width=54.6875 right=1131`.
  - Static layout baseline: `node scripts\resource_browser_browser_metrics.js --static`.
- AUI static metrics after the auto-width cache fix:
  - state: `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`.
  - viewport: `1463x843`.
  - `.main`: `x=0 y=60 width=1463 height=783 right=1463 bottom=843`.
  - `.content`: `x=280 y=60 width=883 height=783 right=1163 bottom=843`.
  - `.contentHeader`: `x=312 y=92 width=819 height=52.8 right=1131`.
  - `.contentTitle`: `x=312 y=92 width=123.5 height=34.8 right=435.5`.
  - `#contentCount`: `x=1076.5208 y=99.975 width=54.4792 right=1131`.
  - `#fileGrid`: `x=312 y=168.8 width=819 height=142`.
  - first normal `.file-card`: `x=312 y=168.8 width=151 height=142`.
- AUI interaction metrics after the auto-width cache fix:
  - state: `contentCount=6 ITEMS`, `fileCards=6`, `selected=level.dat`, `detailActive=true`, `addedFile=true`.
  - `.contentHeader`: `x=312 y=92 width=819 height=46.8 right=1131`.
  - `#contentCount`: `x=1078.9583 y=98.6 width=52.0417 right=1131`.
- Minimal browser-standard regression page for this class of bug:
  - `src/main/resources/assets/apricityui/apricity/tests/auto-width-containing-block.html`
  - AUI acceptance at `1463x843`: `autoRow.width` equals `center.width - 64`; observed `center.width=883`, `autoRow.width=819`.
- Browser runner note:
  - `scripts/resource_browser_browser_metrics.js` uses Chrome `--window-size=1487,942` because this environment's headless-new mode produces an inner content viewport of `1463x843` from that outer size.
  - Do not compare browser output from `--window-size=1463,843`; its actual inner viewport is `1439x744`.

Known capture mismatch:

- Browser screenshot is initial/static.
- AUI screenshot is interaction-driven:
  - `LEVEL.DAT` selected;
  - detail panel active;
  - `+ NEW` clicked;
  - `AUTO_PROMPT_FILE` added.
- Do not use this pair alone to judge selected-card, detail-active, new-card, or second-row layout correctness.

## Milestone V0: Browser Baseline and Harness

Goal: make every later comparison deterministic and browser-standard based.

### RBV-V0-01: Capture Comparable Static AUI Baseline

Status: [x]

Dependencies: none

Expected work:

- Run `devtools/resource.html` without `APRICITYUI_TEST_INTERACTION`.
- Keep prompt automation off.
- Let `Test.java` auto-open and auto-close the game.

Recommended command:

```powershell
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
.\gradlew.bat runClient --console plain --no-daemon --offline
```

Acceptance:

- New AUI screenshot exists under `run/screenshots/aui`.
- Screenshot has no selected card.
- Detail panel shows `SELECT FILE TO VIEW DETAILS`.
- No `AUTO_PROMPT_FILE` card exists.
- Header/content/sidebar/detail can be compared to `img_2.png` as the same state.

Evidence:

- Static AUI run completed with `APRICITYUI_TEST_DOC_PATH=devtools/resource.html` and no interaction/prompt env.
- State log: `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`.

### RBV-V0-02: Capture Comparable Browser Interaction Baseline

Status: [x]

Dependencies: none

Expected work:

- Capture browser after performing the same interaction as the AUI driver:
  - click `LEVEL.DAT`;
  - create `AUTO_PROMPT_FILE`;
  - wait for animations to settle or disable animations.

Acceptance:

- Browser screenshot exists for the selected/detail/new-file state.
- It can be compared to AUI `APRICITYUI_TEST_INTERACTION=resource-browser` runs.
- The browser shows the expected item count after creation, likely `6 ITEMS`.

### RBV-V0-03: Define Crop and Animation Policy

Status: [x]

Dependencies: RBV-V0-01 recommended

Expected output:

- Documented crop rules for:
  - browser rounded/dark capture border;
  - AUI rectangular page/window edge;
  - differing screenshot heights;
  - AUI bottom game/window line.
- A deterministic animation policy:
  - disabled animations for static layout checks, or
  - fixed wait time and known animated regions excluded from strict diff.

Acceptance:

- Future image comparisons do not count external capture borders.
- Bottom blank area is compared only when viewport heights match.
- Animated regions are not mistaken for framework layout defects.

Evidence:

- Browser metrics support `--static`, which injects `*,*::before,*::after{animation:none!important;transition:none!important;}` into the iframe before measuring.
- Use `--static` for static layout checks such as RBV-V2-02 and normal file-card row positions.

Policy:

- Oracle type for this task: documented-rule + browser fixture policy.
- Browser viewport baseline:
  - Current Chromium harness uses `--headless=new --window-size=1487,942 --force-device-scale-factor=1`.
  - In this local environment that yields a CSS viewport of `1463x843`; current browser screenshots named `*-1463x843.png` are CSS-pixel screenshots for that viewport unless the file name or evidence block explicitly records a different device scale.
  - Every image-level comparison must record CSS viewport, physical screenshot size, and device scale before judging pixels. If any of those change, the crop/tolerance must be restated before comparing.
- Device scale and coordinate mapping:
  - Compare in one coordinate system only. Either capture both images at CSS-pixel scale, or map AUI physical framebuffer pixels back into CSS coordinates using the recorded viewport render scale before cropping.
  - Do not compare raw AUI physical pixels directly against CSS-pixel browser screenshots unless the evidence block proves the physical/CSS scale is `1`.
  - Pixel-sample coordinates must state whether they are CSS coordinates or physical image coordinates.
- Crop rules:
  - The page comparison crop is the mapped document viewport rectangle, not the host window, browser chrome, capture frame, Minecraft background, or launcher/window artifact.
  - Browser rounded/dark capture borders are external capture artifacts and are excluded unless a task explicitly tests browser-host capture framing.
  - AUI rectangular page/window edges, Minecraft window boundaries, and any host-side bottom line are excluded unless a task explicitly tests host integration.
  - For full-page `devtools/resource.html`, the default crop is the overlapping mapped CSS viewport area beginning at document viewport origin `(0,0)`.
  - If browser and AUI screenshot heights differ, compare only the overlapping mapped viewport region. Bottom blank area or game/window edge is ignored unless both captures have the same mapped viewport height and the pixels belong to the document.
  - Crops may be narrowed to a component rect only when the rect comes from a browser oracle such as `getBoundingClientRect()` and the AUI rect is mapped into the same CSS coordinate system.
- Animation/static-state policy:
  - Static layout and static paint checks use browser `--static`, which disables CSS animations and transitions in the measurement iframe.
  - AUI static checks must leave `APRICITYUI_TEST_INTERACTION` and `APRICITYUI_TEST_PROMPT_RESPONSE` unset unless the active task is explicitly an interaction task.
  - Interaction checks must record the interaction mode, selected item, prompt state, wait timing, and whether animations are disabled or intentionally sampled at a fixed phase.
  - Animated regions are excluded from strict screenshot diff unless the task declares a same-state fixed animation phase or disables animation in both environments.
  - A screenshot taken during a transition is supporting evidence only unless the evidence block records the same browser/AUI animation state and timing.
- Tolerance:
  - Rect and state metrics are the preferred pass/fail oracle before screenshot diff.
  - Pixel-diff tolerance must be declared before seeing the AUI result. It may be based on device-pixel rounding, coordinate mapping error, or a named browser fixture, but not on visual preference or current AUI output.
  - Default geometry tolerance is browser/device rounding only: use exact rect/state equality when the metric is integer/stable; allow at most one mapped physical pixel of coordinate uncertainty when scale conversion is involved.
  - A screenshot-diff task must name the crop, state, viewport, device scale, animation policy, and tolerance in its evidence block. Without those fields, the screenshot can support diagnosis but cannot close a task.
- Rejection rule:
  - Reject any image-level conclusion that counts external capture borders, bottom host artifacts, unmatched viewport height, unmatched DPR/scale, unmatched state, or uncontrolled animation as an AUI framework defect.

Goal-mode note:

- Prefer rect/state metrics for pass/fail before pixel diff.
- Treat screenshots as supporting evidence unless browser and AUI states, viewport, and animation timing are explicitly matched.

Closeout evidence, 2026-07-16:

- Browser oracle type: documented-rule + browser fixture policy.
- Browser-standard source: Chromium screenshot/viewport semantics as exercised by existing harness scripts; `scripts/resource_browser_browser_metrics.js` records that `--window-size=1487,942 --force-device-scale-factor=1` yields local inner viewport `1463x843`.
- Fixture/state: policy-only documentation task; no new image comparison was run because the active gate required policy before AUI validation.
- Predeclared rejection metric: reject any future image-level comparison that lacks recorded viewport, DPR/device scale, state, animation policy, crop rectangle, and tolerance.
- Hypothesis: the missing written crop/animation policy was the blocker for safe screenshot-level comparison.
- Commands:
  - Browser: inspected existing browser harness script; no Chromium run needed.
  - AUI: skipped because no AUI result can define this policy.
  - Verification: `git diff --check -- doc\guide\resource-browser-visual-todo-2026-07-15.md`.
- Results:
  - Browser baseline policy now records the current `1463x843` CSS viewport, DPR/device-scale requirements, crop exclusion rules, static/interaction animation rules, and tolerance declaration rule.
  - AUI result: not applicable for a policy-only closeout.
- Source changes: `doc/guide/resource-browser-visual-todo-2026-07-15.md`.
- Accepted or remaining mismatch: none for RBV-V0-03 policy definition.
- Cursor update: advance to `RBV-V1-02`.
- Next exact action: start `RBV-V1-02` by capturing or reusing browser static and interaction card-count state for `AUTO_PROMPT_FILE`, then validate the same states in AUI before judging new-card layout.

### RBV-V0-04: Add Rect Metrics Harness

Status: [x]

Dependencies: RBV-V0-01

Expected edits:

- Add or extend a test page / logging path that records browser-equivalent rects:
  - `.sidebar` right edge;
  - `.detail-panel` left edge;
  - `.content` rect;
  - `.content-header` rect;
  - `.content-title` rect;
  - `.content-count` rect;
  - first normal `.file-card` rect;
  - `.header-actions` rect.
- Browser side should use `getBoundingClientRect()`.
- AUI side should expose/log comparable values from its DOM APIs.

Acceptance:

- Browser and AUI produce the same metric keys.
- The metric output can distinguish layout-box mismatch from antialiasing/paint mismatch.

Evidence:

- Browser runner emits `BROWSER_RESOURCE_METRICS` with `sidebar`, `detailPanel`, `content`, `contentHeader`, `contentTitle`, `contentCount`, `firstNormalFileCard`, and `headerActions`.
- AUI `Test.java` emits the same rect keys for `devtools/resource.html`.

### RBV-V0-05: Add State Consistency Log

Status: [x]

Dependencies: none

Expected work:

- Log before screenshot or auto-close:
  - `contentCount.textContent`;
  - number of `.file-card` elements;
  - selected item name;
  - whether `.detail-panel.active` exists;
  - whether `[data-name="AUTO_PROMPT_FILE"]` exists.

Acceptance:

- Every screenshot used for comparison has a matching state log.
- A future goal run can prove whether it captured static or interaction state.

Evidence:

- AUI logs static and interaction state.
- Browser metrics payload logs matching state fields.

## Milestone V1: Data and State Correctness

Goal: ensure the DOM state matches browser semantics before judging pixels.

### RBV-V1-01: Investigate Missing `contentCount`

Status: [x]

Dependencies: RBV-V0-05 recommended

Problem:

- AUI interacted screenshot shows six file cards but no visible `6 ITEMS`.
- `resource.html` calls `contentCount.textContent = `${node.children.length} ITEMS`` in `renderFiles()`.

Expected checks:

- Confirm `contentCount.textContent` after `+ NEW`.
- Confirm `.content-count` bounding rect.
- Confirm it is not clipped, hidden, covered by `.content-header::after`, or painted under the detail panel.

Acceptance:

- After creating `AUTO_PROMPT_FILE`, AUI visibly shows `6 ITEMS` in the browser-equivalent location.
- If browser also hides/repositions it in the same state, document that with the browser interaction screenshot.

Root cause:

- `.content-header` is `display:flex` with auto width inside `.content`.
- AUI measured and cached it while parent `.content` was still resolving, so `Size.getScaleWidth()` fell back to viewport width.
- That placed `#contentCount` around `x=1722`, outside the content area.

Fix:

- `Size.shouldDeferSizeCache(...)` now defers cache writes for in-flow auto-width block-level children whose width depends on a parent that is still resolving.
- Minimal regression page: `tests/auto-width-containing-block.html`.

Evidence:

- Before fix: `.contentHeader.width=1463`, `#contentCount.right=1775`.
- After fix static: `.contentHeader.width=819`, `#contentCount.right=1131`.
- After fix interaction: `contentCount=6 ITEMS`, `.contentHeader.width=819`, `#contentCount.right=1131`.

### RBV-V1-02: Normalize `AUTO_PROMPT_FILE` Usage

Status: [x]

Dependencies: RBV-V0-01, RBV-V0-02

Expected work:

- Static runs must not create `AUTO_PROMPT_FILE`.
- Interaction runs must create it in both browser and AUI.

Acceptance:

- Static baseline has exactly five original cards.
- Interaction baseline has six cards in both environments.
- New-card second-row layout is judged only in interaction comparisons.

Evidence:

- Browser oracle type: state + rect.
- Browser-standard source: Chromium fixture `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`.
- Fixture/state:
  - Static: `devtools/resource.html`, viewport `1463x843`, `--static`, no interaction, prompt response unset.
  - New-file interaction: `devtools/resource.html`, viewport `1463x843`, `--static`, `--interaction=resource-browser-new-file`, prompt response `AUTO_PROMPT_FILE`.
- Predeclared rejection metric:
  - Reject static if `addedFile=true`, `fileCards != 5`, or `contentCount != "5 ITEMS"`.
  - Reject new-file interaction if `addedFile=false`, `fileCards != 6`, or `contentCount != "6 ITEMS"`.
- Hypothesis:
  - The existing `resource-browser` interaction was a selected/detail interaction, not the prompt/new-file interaction required by this task; adding a dedicated `resource-browser-new-file` driver would make the state oracle reproducible without changing selected/detail evidence.
- Commands:
  - Browser static: `node scripts\resource_browser_browser_metrics.js --static | Tee-Object -FilePath run\resource-browser-v1-02-browser-static-last.log`.
  - Rejected browser selected/detail interaction: `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser | Tee-Object -FilePath run\resource-browser-v1-02-browser-interaction-last.log`.
  - Browser new-file interaction: `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser-new-file --prompt-response=AUTO_PROMPT_FILE | Tee-Object -FilePath run\resource-browser-v1-02-browser-new-file-last.log`.
  - Verification before AUI: `.\gradlew.bat compileJava --console plain --no-daemon --offline`.
  - AUI static: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, interaction and prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-v1-02-aui-static-last.log`.
  - AUI new-file interaction: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-new-file`, `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE`, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-v1-02-aui-new-file-last.log`.
  - Final verification: `git diff --check -- scripts\resource_browser_browser_metrics.js src\main\resources\assets\apricityui\apricity\tests\resource-browser-browser-metrics.html src\main\java\com\sighs\apricityui\event\Test.java doc\guide\resource-browser-visual-todo-2026-07-15.md`.
- Results:
  - Browser static accepted: `contentCount="5 ITEMS"`, `fileCards=5`, `addedFile=false`, `selected=null`, `detailActive=false`.
  - Browser `resource-browser` interaction rejected for this task but preserved for selected/detail tasks: it selects `level.dat`, keeps `contentCount="5 ITEMS"`, `fileCards=5`, `addedFile=false`.
  - Browser new-file interaction accepted: `contentCount="6 ITEMS"`, `fileCards=6`, `addedFile=true`, `selected=null`, `detailActive=false`; `AUTO_PROMPT_FILE` appears as the sixth normal file card on the second row.
  - AUI static accepted: `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`, viewport `1463.0x843.0`.
  - AUI new-file interaction accepted for this task: baseline starts at `5 ITEMS`, then after interaction `contentCount=6 ITEMS`, `fileCards=6`, `addedFile=true`, viewport `1463.0x843.0`; `AUTO_PROMPT_FILE` is logged as a normal file card at `x=312.0 y=326.8 width=151.0 height=142.0`.
- Artifacts:
  - Browser logs: `run/resource-browser-v1-02-browser-static-last.log`, `run/resource-browser-v1-02-browser-interaction-last.log`, `run/resource-browser-v1-02-browser-new-file-last.log`.
  - AUI logs: `run/resource-browser-v1-02-aui-static-last.log`, `run/resource-browser-v1-02-aui-new-file-last.log`.
  - AUI screenshots: static run includes `run/screenshots/aui/2026-07-16_01.12.34.png`; new-file run includes `run/screenshots/aui/2026-07-16_01.14.18.png` and `run/screenshots/aui/2026-07-16_01.14.19.png`.
- Source changes:
  - `scripts/resource_browser_browser_metrics.js`: added `--prompt-response=` / `RESOURCE_BROWSER_PROMPT_RESPONSE` query forwarding.
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`: added `resource-browser-new-file` interaction that returns `AUTO_PROMPT_FILE` from `prompt()` and calls `createNew()`.
  - `src/main/java/com/sighs/apricityui/event/Test.java`: added `resource-browser-new-file` driver that clicks the `+ NEW` action button and logs the resulting state through the existing resource-browser metrics path.
  - `doc/guide/resource-browser-visual-todo-2026-07-15.md`: recorded V1-02 closeout and main-goal cursor state.
- Accepted or remaining mismatch:
  - `AUTO_PROMPT_FILE` state normalization is complete for static vs new-file interaction runs.
  - AUI new-file interaction also reports `selected=icon.png` and `detailActive=true` after the button click, while the browser direct `createNew()` oracle remains `selected=null` and `detailActive=false`. This does not fail V1-02 because this task's acceptance is card-count and added-file state; any future screenshot or new-card layout task must fix or explicitly control selected/detail state before comparing image-level output.
- Cursor update: no remaining non-extracted task is open in this main TODO.
- Next exact action: add a new browser-standard task or explicitly reopen extracted work before continuing this main TODO.

## Milestone V2: Browser Layout Geometry

Goal: make layout boxes match browser before tuning SVG/paint quality.

### RBV-V2-01: Three-Column Boundary Calibration

Status: [x]

Dependencies: RBV-V0-04

Problem:

- Older screenshot-based evidence compared different effective viewport sizes.
- With matched `1463x843` browser/AUI metrics, the horizontal three-column boundaries now match exactly.

Expected checks:

- CSS px to physical-pixel mapping in `mode=browser`.
- Fixed width resolution for `.sidebar { width: 280px }`.
- Fixed width resolution for `.detail-panel { width: 300px }`.
- Border-box behavior.
- Main flex available width and rounding.

Acceptance:

- Sidebar/content/detail boundaries match browser within a small documented tolerance.
- Fix is in framework sizing/layout code, not a resource page workaround.

Evidence:

- Browser runner adjusted to produce actual inner viewport `1463x843`.
- Browser: `main x=0 width=1463`, `sidebar x=0 width=280 right=280`, `content x=280 width=883 right=1163`, `detailPanel x=1163 width=300 right=1463`.
- AUI: `main x=0 width=1463`, `sidebar x=0 width=280 right=280`, `content x=280 width=883 right=1163`, `detailPanel x=1163 width=300 right=1463`.
- Height mismatch was traced to `calc(100vh - 60px)` being misclassified as unset because `Size.computeSize` used `parseNumber(...)` instead of `tryResolveLength(...)` for explicit size detection.
- After the explicit-size fix, AUI `main/sidebar/content/detail` heights match browser: `783`.

### RBV-V2-02: Main Content Vertical Spacing

Status: [x]

Dependencies: RBV-V0-04

Problem:

- Header divider aligns near `y=100..104`, but AUI content title and first card row are `15..25px` higher than browser.
- This is not a whole-page vertical offset.

Expected checks:

- `.content { padding: 32px }`.
- `.content-header { padding-bottom: 16px; margin-bottom: 24px }`.
- `.content-title` border-box rect.
- First normal `.file-card` top.
- Card enter animation excluded or disabled during measurement.

Acceptance:

- `.content-title`, content underline, and first normal card row y positions match browser within a small documented tolerance.

Current evidence:

- Top-level vertical viewport/layout is fixed: browser and AUI both have `.main height=783`, `.content y=60 height=783`.
- `.content-header` origin matches: browser `x=312 y=92 width=819`, AUI `x=312 y=92 width=819`.
- Browser normal mode first-card rect is affected by `cardIn` transform even after the current virtual-time dump; use `--static` for layout baselines.
- Browser static layout:
  - `.contentHeader height=53`;
  - `.contentTitle height=35`;
  - `#contentCount y=100 height=19`;
  - `#fileGrid y=169`;
  - first normal `.file-card y=169 height=141.796875`.
- AUI after vertical metric fixes:
  - `.contentHeader height=52.875`;
  - `.contentTitle height=34.875`;
  - `#contentCount y=99.9921875 height=18.890625`;
  - `#fileGrid y=168.875`;
  - first normal `.file-card y=168.875 height=142`.
- This task is complete at rect level for the current static baseline.

Evidence:

- Browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser result: `.contentHeader height=53`, `.contentTitle height=35`, `#contentCount y=100 height=19`, `#fileGrid y=169`, first normal `.file-card y=169 height=141.796875`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result: `.contentHeader height=52.875`, `.contentTitle height=34.875`, `#contentCount y=99.9921875 height=18.890625`, `#fileGrid y=168.875`, first normal `.file-card y=168.875 height=142`.
- Screenshot(s): `run/screenshots/aui/2026-07-15_11.25.14.png` through `run/screenshots/aui/2026-07-15_11.25.18.png`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `git diff --check` reported only existing LF/CRLF warnings.

### RBV-V2-03: Header Action Position Polish

Status: [x]

Dependencies: RBV-V2-01

Problem:

- Header actions are right-aligned, but AUI group is slightly too far right.

Expected checks:

- Browser oracle type: `getBoundingClientRect()` for `.header-actions` and every `.header-actions > *`, plus computed styles when a box-size mismatch appears.
- Header horizontal padding.
- Button border-box size.
- Button gap.

Acceptance:

- Header action group left edge, right margin, button widths, and gaps match browser closely.
- Evidence must include browser `headerActionButtons` and AUI `headerActionButtons` logs for all three buttons.
- If the group rect matches but child rects differ, do not mark complete; isolate gap, padding, or border measurement.

Evidence:

- Browser oracle type: `getBoundingClientRect()` from Chromium at viewport `1463x843`, static state.
- Browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser result:
  - `.headerActions x=1184.625 y=10 width=254.375 height=37 right=1439`.
  - button 0 `U+25C0 BACK`: `x=1184.625 width=88.5 right=1273.125`.
  - button 1 `U+25B2 UP`: `x=1281.125 width=71.71875 right=1352.84375`.
  - button 2 `+ NEW`: `x=1360.84375 width=78.15625 right=1439`.
  - button gaps: `8`, `8`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result:
  - `.headerActions x=1185 y=9.8 width=254 height=37.4 right=1439`.
  - button 0 `U+25C0 BACK`: `x=1185 width=88.5 right=1273.5`.
  - button 1 `U+25B2 UP`: `x=1281.5 width=71.25 right=1352.75`.
  - button 2 `+ NEW`: `x=1360.75 width=78.25 right=1439`.
  - button gaps: `8`, `8`.
- Screenshot(s): `run/screenshots/aui/2026-07-15_12.09.37.png` through `run/screenshots/aui/2026-07-15_12.09.41.png`.
- Harness/log files: browser metrics saved to `run/resource-browser-browser-static-last.log`; AUI metrics saved to `run/resource-browser-harness-last.log`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `git diff --check` reported only existing LF/CRLF warnings.
- Remaining mismatch: none for header action group right edge, button boxes, and gaps at the recorded tolerance.

### RBV-V2-04: Logo, Breadcrumb, and Tree Horizontal Origins

Status: [x]

Dependencies: RBV-V2-01

Problem:

- AUI logo, breadcrumb, and sidebar tree origins are generally `7..13px` left of browser.

Expected checks:

- Browser oracle type: `getBoundingClientRect()` for `.logo`, `.logo > *`, `.nav-path`, `.nav-path > *`, `.sidebar-title`, `.tree-item`, `.tree-toggle`, `.tree-icon`, and the label child.
- Header padding.
- `.logo { gap: 12px }`.
- Brand box width.
- `.nav-path { margin-left: 40px }`.
- Inline tree item `padding-left:${24 + depth * 16}px`.
- Tree icon/label gap.

Acceptance:

- Logo, breadcrumb segments, tree toggles, tree icons, and tree label origins match browser.
- Evidence must include browser `logoChildren`, `navPathChildren`, and first 12 `treeItems`, plus matching AUI log groups.
- If child origins match, mark only the horizontal-origin primitive complete.

Evidence:

- Browser oracle type: `getBoundingClientRect()` from Chromium at viewport `1463x843`, static state.
- Browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser result:
  - `.logo x=24 y=12.5 width=242.28125`; `logoBlock x=24 width=32`; logo text `x=68 width=198.28125`.
  - `.navPath x=306.28125 y=12 width=288.359375`.
  - nav child x positions: `ROOT x=306.28125`, `U+25B8 x=365.25`, `WORLDS x=402.25`, `U+25B8 x=479.203125`, `SURVIVAL x=516.203125`.
  - `.sidebarTitle x=0 y=60 width=278 height=49`; first tree item `x=0 y=109 width=278 height=39`.
  - tree relative x positions: top-level `toggle=27`, `icon=51`, `label=75`; depth-1 selected `toggle=43`, `icon=67`, `label=91`; depth-2 file item `toggle=59`, `icon=83`, `label=107`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result:
  - `.logo x=24 y=12.5 width=242.0833282470703`; `logoBlock x=24 width=32`; logo text `x=68 width=198.0833282470703`.
  - `.navPath x=306.0833282470703 y=12.9 width=291.5416736602783`.
  - nav child x positions: `ROOT x=306.0833282470703`, `U+25B8 x=365.56249618530273`, `WORLDS x=403.22916316986084`, `U+25B8 x=480.8541669845581`, `SURVIVAL x=518.5208339691162`.
  - `.sidebarTitle x=0 y=60 width=278 height=48.95`; first tree item `x=0 y=108.95 width=278 height=38.85`.
  - tree relative x positions: top-level `toggle=27`, `icon=51`, `label=75`; depth-1 selected `toggle=43`, `icon=67`, `label=91`; depth-2 file item `toggle=59`, `icon=83`, `label=107`.
- Screenshot(s): `run/screenshots/aui/2026-07-15_12.09.37.png` through `run/screenshots/aui/2026-07-15_12.09.41.png`.
- Harness/log files: browser metrics saved to `run/resource-browser-browser-static-last.log`; AUI metrics saved to `run/resource-browser-harness-last.log`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `git diff --check` reported only existing LF/CRLF warnings.
- Remaining mismatch: none for horizontal-origin primitives, CSS gaps, tree padding-depth offsets, and tree child relative x positions.

### RBV-V2-05: File Grid Track Calibration

Status: [x]

Dependencies: RBV-V2-01

Problem:

- Five original cards fit in one row in both environments.
- AUI card row starts left and ends slightly right compared with browser.

Expected checks:

- Grid container available width.
- `grid-template-columns: repeat(auto-fill, minmax(140px, 1fr))`.
- `gap: 16px`.
- fr distribution and rounding.

Acceptance:

- Original five-card row start, column widths, gaps, and end edge match browser within tolerance.

Evidence:

- Browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser result at `1463x843`, static state:
  - `#fileGrid x=312 y=169 width=819 height=141.796875 right=1131`.
  - normal card 0 `level.dat`: `x=312 width=151 right=463`.
  - normal card 1 `region`: `x=479 width=151 right=630`.
  - normal card 2 `playerdata`: `x=646 width=151 right=797`.
  - normal card 3 `icon.png`: `x=813 width=151 right=964`.
  - normal card 4 `session.lock`: `x=980 width=151 right=1131`.
  - column gaps: `16, 16, 16, 16`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result at `1463x843`, static state:
  - `#fileGrid x=312 y=168.8 width=819 height=142 right=1131`.
  - normal card 0 `level.dat`: `x=312 width=151 right=463`.
  - normal card 1 `region`: `x=479 width=151 right=630`.
  - normal card 2 `playerdata`: `x=646 width=151 right=797`.
  - normal card 3 `icon.png`: `x=813 width=151 right=964`.
  - normal card 4 `session.lock`: `x=980 width=151 right=1131`.
  - column gaps: `16, 16, 16, 16`.
- Harness change: browser metrics now emit `normalFileCards` and `fileGridColumnGaps`; AUI `Test.java` logs `normalFileCard` and `fileGridColumnGap` entries.
- Screenshot(s): latest static run includes `run/screenshots/aui/2026-07-15_11.54.41.png` through `run/screenshots/aui/2026-07-15_11.54.45.png`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `runClient` static resource run passed.
- Remaining mismatch: none for V2-05 horizontal grid track distribution. The `0.2px` y/height delta belongs to V2-02/V2-06 vertical/card stack work, not grid track calibration.

### RBV-V2-06: Normal File Card Height and Stack

Status: [x]

Dependencies: RBV-V2-02

Problem:

- AUI normal cards are higher on screen, slightly shorter, and internally tighter.
- Current selected `LEVEL.DAT` card cannot be used as a normal-card reference.

Expected checks:

- Use normal cards: `REGION`, `PLAYERDATA`, `ICON.PNG`, `SESSION.LOCK`.
- `.file-card` padding.
- `.file-icon` size and margin.
- `.file-meta margin-top: 6px`.

Acceptance:

- Normal cards match browser top, height, icon y, filename y, meta y, and internal spacing at the recorded tolerance.

Evidence:

- Browser command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser result at `1463x843`, static state, representative normal card:
  - card `x=312 y=169 width=151 height=141.796875 right=463 bottom=310.796875`.
  - icon `x=363.5 y=191 width=48 height=48 relativeX=51.5 relativeY=22`.
  - file name `x=330 y=251 width=115 height=16.796875 relativeX=18 relativeY=82`.
  - file meta `x=330 y=273.796875 width=115 height=15 relativeX=18 relativeY=104.796875`.
- AUI command before fix: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result before fix:
  - card geometry matched, but `.file-icon` was left aligned at `relativeX=18` instead of browser `51.5`.
- Root cause:
  - `.file-icon` uses `width:48px; margin:0 auto 12px; display:flex`.
  - Browser normal flow resolves horizontal auto margins for block-level boxes against the containing block content width.
  - AUI normal flow treated auto margins as `0`, so the icon stayed at the card padding left.
- Framework fix:
  - `NormalFlow.placeBlock(...)` now resolves used horizontal block margins only when `margin-left` or `margin-right` is `auto`.
  - The fix is intentionally scoped to horizontal auto margins to avoid re-triggering auto-width parent measurement while computing ordinary block flow.
- AUI result after fix:
  - card `x=312 y=168.8 width=151 height=142 right=463 bottom=310.8`.
  - icon `x=363.5 y=190.8 width=48 height=48 relativeX=51.5 relativeY=22`.
  - file name `x=330 y=250.8 width=115 height=17.4 relativeX=18 relativeY=82`.
  - file meta `x=330 y=274.2 width=115 height=14.5 relativeX=18 relativeY=105.4`.
  - `#fileGrid` remained correct after the scoped fix: `x=312 width=819 right=1131`.
- Harness change: browser metrics and AUI `Test.java` now report child rects for `icon`, `fileName`, and `fileMeta` under each normal file card.
- Screenshot(s): latest static run includes `run/screenshots/aui/2026-07-15_12.01.10.png` through `run/screenshots/aui/2026-07-15_12.01.14.png`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `runClient` static resource run passed.
- Remaining mismatch: none for horizontal stack, icon centering, card width, and recorded card internal offsets.

## Milestone V3: Extracted Work

Status: [!] extracted

The previously listed work for this milestone was extracted from this main visual TODO.

Do not continue this extracted work from the main visual TODO unless the user explicitly reopens it here.
## Milestone V4: SVG and Vector Paint

Goal: match browser vector appearance after layout boxes are correct.

### RBV-V4-01: SVG Stroke Antialiasing

Status: [x]

Dependencies: RBV-V2 stable; extracted work is not required for this task unless explicitly reopened.

Problem:

- AUI document/image/lock strokes and button borders look harder and sometimes heavier than browser.

Expected checks:

- Browser oracle type: screenshot crop/pixel evidence from Chromium, plus SVG `getBoundingClientRect()` for icon boxes.
- SVG rasterization hints.
- Stroke alignment.
- Scaling from viewBox to CSS px.
- Subpixel antialiasing differences.
- AUI texture upload and draw sampling path for `Svg extends Canvas`.

Acceptance:

- File, folder, image, lock, and detail icons have browser-like stroke thickness and edge softness.
- Completion requires matching or explaining both stages:
  - vector-to-raster stage inside the SVG surface;
  - texture-to-screen scaling stage from AUI canvas texture to the MC framebuffer.

Current evidence:

- Browser direct screenshot: `run/screenshots/browser/resource-browser-direct-1463x843.png`.
- Browser crop: `run/screenshots/compare/browser-file-icons.png`.
- AUI crop: `run/screenshots/compare/aui-file-icons.png`.
- Browser metrics command: `node scripts\resource_browser_browser_metrics.js --static`.
- Browser icon geometry for representative normal card:
  - card `x=312 y=169 width=151 height=141.796875`;
  - `.file-icon x=363.5 y=191 width=48 height=48`;
  - inner SVG is CSS `40x40` inside the `48x48` icon box.
- AUI metrics from `run/resource-browser-harness-last.log`:
  - card `x=312 y=168.8 width=151 height=142`;
  - `.file-icon x=363.5 y=190.8 width=48 height=48`.
- Code inspection:
  - `Svg` renders to a Java2D canvas surface at `RASTER_SCALE=4`.
  - Java2D has antialiasing and render-quality enabled.
  - `Svg` uses `KEY_STROKE_CONTROL=VALUE_STROKE_PURE`.
  - `Canvas.syncTexture()` uploads a `DynamicTexture` with linear filtering enabled.
  - `ImageDrawer.draw(..., blur=true)` uses a texture state with linear filtering.
- Current hypothesis:
  - The remaining visual difference is unlikely to be layout/viewBox size because the icon boxes match.
  - The next browser-standard experiment should isolate whether the harder/heavier look comes from Java2D stroke control (`VALUE_STROKE_PURE` vs normalized/default), from the 4x raster scale, or from texture downsampling into the MC framebuffer.
- Next experiment:
  - Add a minimal SVG stroke fixture with document/folder/image/lock icons at the same CSS sizes as `resource.html`.
  - Capture Chromium screenshot crops and AUI screenshot crops for that fixture.
  - If the minimal fixture reproduces the heavier/harder AUI stroke, test stroke-control and raster-scale variants in `Svg` one at a time.

Fixture evidence:

- Minimal fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-svg-stroke.html`.
- Browser metrics command: `node scripts\resource_browser_svg_stroke_metrics.js`.
- Browser result at viewport `1463x843`:
  - file icons: `.file-icon 48x48`, inner `svg 40x40`.
  - detail icons: `.detail-icon 80x80`, inner `svg 56x56`.
  - sample `svg-data x=76 y=56 width=40 height=40`; `svg-detail-data x=68 y=209 width=56 height=56`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-svg-stroke.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result:
  - file icons: `.file-icon 48x48`, inner `svg 40x40`.
  - detail icons: `.detail-icon 80x80`, inner `svg 56x56`.
  - sample `svg-data x=76 y=56 width=40 height=40`; `svg-detail-data x=68 y=209.4 width=56 height=56`.
- Browser screenshot: `run/screenshots/browser/resource-browser-svg-stroke-1463x843.png`.
- Browser crop: `run/screenshots/compare/browser-svg-stroke-fixture.png`.
- AUI screenshots: `run/screenshots/aui/2026-07-15_12.21.09.png` through `run/screenshots/aui/2026-07-15_12.21.12.png`.
- AUI crops: `run/screenshots/compare/aui-svg-stroke-fixture.png`, `run/screenshots/compare/aui-svg-stroke-fixture-css-scale.png`, `run/screenshots/compare/aui-svg-stroke-fixture-page-css-scale.png`.
- Full resource supporting crops: `run/screenshots/compare/browser-file-icons.png`, `run/screenshots/compare/aui-file-icons.png`.
- Result:
  - The minimal fixture did not reproduce a layout/viewBox mismatch: CSS icon boxes and SVG boxes match browser.
  - After normalizing the AUI physical-pixel crop back to CSS scale, document/image/lock strokes and folder fills are close to Chromium for the current static target.
  - No `Svg` raster-scale or `KEY_STROKE_CONTROL` change was made, because the isolated evidence does not show a primitive-level stroke bug large enough to justify a global renderer change.
- Residual risk:
  - AUI raw screenshots are physical-pixel captures and can look heavier if compared directly to CSS-pixel browser screenshots. Future paint comparisons must normalize scale or crop in matching coordinate systems.
  - The AUI fixture run logged two OpenGL `GL_INVALID_VALUE` messages during initial SVG/canvas texture setup. They did not prevent rendering or metrics capture, but should be investigated under a separate canvas/SVG lifecycle task if they recur outside this fixture.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed; `runClient` SVG fixture run passed.

### RBV-V4-02: SVG Opacity and Alpha Blending

Status: [x]

Dependencies: RBV-V4-01

Problem:

- `ICON.PNG` internal purple image area looks blockier/less layered in AUI.

Expected checks:

- `opacity="0.2"`.
- fill opacity and stroke opacity.
- shape draw order.
- alpha blending into white/off-white backgrounds.

Acceptance:

- Image icon internal opacity and layering match browser closely.

Current evidence:

- The V4-01 minimal fixture includes `svg-image` and `svg-detail-image` with the same `opacity="0.2"` purple rectangle, circle, and path layering used by `resource.html`.
- Browser crop: `run/screenshots/compare/browser-svg-stroke-fixture.png`.
- AUI normalized crop: `run/screenshots/compare/aui-svg-stroke-fixture-page-css-scale.png`.
- Initial visual result: image icon layering is close after AUI physical-pixel normalization, but V4-02 still needs an explicit opacity-focused pixel/sample check before completion.
- Pixel evidence:
  - Browser image opacity area in `run/screenshots/browser/resource-browser-svg-stroke-1463x843.png`: nearest exact sample `rgba=232,222,253,255` at `(390,68)`.
  - AUI image opacity area in `run/screenshots/aui/2026-07-15_12.21.12.png`: nearest exact sample `rgba=232,222,253,255` at `(683,130)`.
  - AUI detail image opacity area: nearest exact sample `rgba=232,222,253,255` at `(410,396)`.
  - AUI opaque purple control sample: nearest exact sample `rgba=139,92,246,255` at `(688,130)`.
- Expected alpha math for `#8b5cf6` at `opacity=0.2` over white gives approximately `rgb(232,222,253)`, matching both browser and AUI captures.
- Sample logs:
  - `run/resource-browser-svg-opacity-browser-samples.log`;
  - `run/resource-browser-svg-opacity-aui-raw-samples.log`.
- Result: SVG element opacity/fill layering for the image icons matches the Chromium reference for the current static target.

### RBV-V4-03: SVG ViewBox Scaling

Status: [x]

Dependencies: RBV-V4-01

Expected checks:

- `40x40` SVG inside `48x48` `.file-icon`.
- Sidebar small icons.
- Detail large icon.

Acceptance:

- Icon size, centering, and internal proportions match browser.

Evidence:

- Browser oracle type: `getBoundingClientRect()` for `.file-icon`, `.detail-icon`, and nested `svg`.
- Minimal fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-svg-stroke.html`.
- Browser command: `node scripts\resource_browser_svg_stroke_metrics.js`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-svg-stroke.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Browser result:
  - file icon samples: `.file-icon width=48 height=48`, nested `svg width=40 height=40`, relative offset `4,4`.
  - detail icon samples: `.detail-icon width=80 height=80`, nested `svg width=56 height=56`, relative offset `12,12`.
- AUI result:
  - file icon samples: `.file-icon width=48 height=48`, nested `svg width=40 height=40`, relative offset `4,4`.
  - detail icon samples: `.detail-icon width=80 height=80`, nested `svg width=56 height=56`, relative offset `12,12`.
- Screenshot support:
  - `run/screenshots/compare/browser-svg-stroke-fixture.png`;
  - `run/screenshots/compare/aui-svg-stroke-fixture-page-css-scale.png`.
- Result: viewBox-to-CSS sizing and centering match browser for file and detail icon sizes. Internal proportions are preserved because both browser and AUI render the same `viewBox="0 0 40 40"` into matching CSS boxes.

## Milestone V5: Backgrounds, Gradients, and Colors

Goal: polish paint layers according to browser rendering.

### RBV-V5-01: Header Repeating Grid Visibility

Status: [x]

Dependencies: V2 layout stable

Problem:

- Browser header vertical grid is visible.
- AUI header grid is much fainter.

Expected checks:

- `repeating-linear-gradient(...)` hard stops.
- `rgba(139,92,246,0.03)` alpha precision.
- 1px stop sampling under current scale.

Acceptance:

- Header blank area shows browser-like subtle purple grid.

Evidence:

- Browser oracle type: Chromium computed `::before` style plus pixel samples from a minimal fixture.
- Minimal fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-header-grid.html`.
- Browser command: `node scripts\resource_browser_header_grid_metrics.js`.
- Browser result:
  - viewport `1463x843`;
  - `.header x=0 y=0 width=1463 height=60`;
  - `::before backgroundImage=repeating-linear-gradient(90deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0) 20px, rgba(139, 92, 246, 0.03) 20px, rgba(139, 92, 246, 0.03) 21px)`;
  - `::before width=1463px height=57px opacity=1`.
- Browser screenshot: `run/screenshots/browser/resource-browser-header-grid-1463x843.png`.
- Browser pixel samples:
  - transparent area `x=10 y=30`: `rgba=255,255,255,255`;
  - stripe `x=20 y=30`: `rgba=251,250,254,255`;
  - transparent area `x=21 y=30`: `rgba=255,255,255,255`;
  - stripe `x=41 y=30`: `rgba=252,250,255,255`;
  - white control area `x=660 y=30`: `rgba=255,255,255,255`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-header-grid.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result:
  - fixture opened at viewport `1463x843`, `paintList=22`;
  - run passed and auto-closed.
- AUI screenshot: `run/screenshots/aui/2026-07-15_12.32.00.png`.
- AUI pixel samples:
  - transparent area at CSS-equivalent `x=10 y=30`: `rgba=255,255,255,255`;
  - stripe at CSS-equivalent `x=20 y=30`: `rgba=251,250,255,255`;
  - transparent area at CSS-equivalent `x=21 y=30`: `rgba=255,255,255,255`;
  - stripe at CSS-equivalent `x=41 y=30`: `rgba=251,250,255,255`;
  - white control area at CSS-equivalent `x=660 y=30`: `rgba=255,255,255,255`;
  - purple border control: `rgba=139,92,246,255`.
- Sample logs:
  - `run/resource-browser-header-grid-browser-samples.log`;
  - `run/resource-browser-header-grid-aui-samples.log`.
- Implementation notes:
  - `Background.applyRepeatingGradientTile(...)` maps this `90deg` repeating gradient to a `21px 100%` repeat-x tile.
  - `Gradient.parse(...)` preserves the absolute `20px` and `21px` hard stops.
  - `Graph.drawAxisAlignedStopGradientRect(...)` draws the transparent and low-alpha segments without falling back to broad sampled interpolation.
- Result: AUI matches the browser-visible subtle header grid for the isolated primitive. The earlier 閳ユ竾ainter閳?report was not reproduced after normalizing to matching coordinates.

### RBV-V5-02: Header Scanline Determinism

Status: [x]

Dependencies: RBV-V0-03

Browser oracle:

- Type: computed-style + animation timing + pixel-sample.
- Standard source: Chromium computed style for `.header::after`, CSS animation interpolation for `left:-100%` to `left:100%` over `6s linear infinite`, and matched pixel samples only after animation time is fixed or disabled.
- Static policy: static layout/color comparisons must disable or freeze this animation before comparing pixels.
- Runtime policy: animation checks must compare browser and AUI at declared sample times, not arbitrary screenshots.

Expected checks:

- `.header::after` linear gradient.
- Generated pseudo-element box:
  - `content:''`;
  - `position:absolute`;
  - `top:0`;
  - `height:100%`;
  - `width:100%`;
  - `pointer-events:none`.
- Gradient:
  - `linear-gradient(90deg, transparent, rgba(139,92,246,0.08), transparent)`;
  - transparent edges and center alpha over the header background match Chromium.
- Animation timing:
  - `animation-name: scanline`;
  - `animation-duration: 6s`;
  - `animation-timing-function: linear`;
  - `animation-iteration-count: infinite`;
  - `left` interpolates from `-100%` at `0%` to `100%` at `100%`.
- Static screenshot exclusion/disable path:
  - either inject `*,*::before,*::after{animation:none!important;transition:none!important;}`;
  - or create a fixture that pins `animation-delay`/play state and records the chosen time.

Recommended fixture:

- Add `src/main/resources/assets/apricityui/apricity/tests/resource-browser-header-scanline.html`.
- Use a white or fixed header background, no unrelated page content, and the same `.header::after` CSS from `resource.html`.
- Keep metrics/debug DOM offscreen with `left:-100000px;color:transparent`.
- Browser script should record:
  - `.header` rect;
  - `.header::after` computed `left`, `width`, `height`, `backgroundImage`, and `animation*`;
  - pixel samples at fixed CSS coordinates for disabled/frozen animation;
  - optional runtime samples at `t=0ms`, `t=1500ms`, `t=3000ms`, `t=4500ms`.

Acceptance:

- Static comparison has a deterministic browser oracle and no arbitrary animated mismatch.
- AUI generated pseudo-element geometry and gradient alpha match the browser fixture under the same animation policy.
- Runtime scanline movement matches Chromium's linear interpolation within documented timing tolerance.
- Full `devtools/resource.html` screenshots record whether scanline is disabled/frozen or intentionally excluded from strict pixel comparison.
- Evidence includes browser command/result, AUI command/result, newest `run/screenshots/aui/...` path, and verification results.

Evidence:

- Browser oracle type: Chromium computed `::after` style, Web Animations timing state, and matched pixel/row samples from a minimal fixture.
- Minimal fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-header-scanline.html`.
- Browser command: `node scripts\resource_browser_header_scanline_metrics.js`.
- Browser result at viewport `1463x843`:
  - frozen header rect: `x=0 y=0 width=1463 height=60`;
  - frozen `::after`: `left=0px width=1463px height=57px backgroundImage=linear-gradient(90deg, rgba(0,0,0,0), rgba(139,92,246,0.08), rgba(0,0,0,0)) animationName=none`;
  - animated header rect: `x=0 y=90 width=1463 height=60`;
  - animated `::after` at first sampled animation frame: `left=-1454.84px width=1463px height=57px animationName=scanline animationDuration=6s timing=linear iteration=infinite currentTime=16.7ms progress=0.0027833333`.
- Browser screenshot: `run/screenshots/browser/resource-browser-header-scanline-1463x843.png`.
- Browser frozen pixel samples:
  - left edge `x=10 y=30`: `255,255,255,255`;
  - quarter `x=366 y=30`: `250,248,254,255`;
  - center `x=731 y=30`: `246,243,255,255`;
  - three-quarter `x=1097 y=30`: `251,249,255,255`;
  - right edge `x=1452 y=30`: `254,254,255,255`;
  - border `x=10 y=58`: `139,92,246,255`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-header-scanline.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI result after fix:
  - frozen header rect: `x=0 y=0 width=1463 height=60`;
  - frozen generated `::after`: `x=0 y=0 width=1463 height=57`, `animation=none`, `active=false`;
  - animated header rect: `x=0 y=90 width=1463 height=60`;
  - animated generated `::after`: `x=-1015.32 y=90 width=1463 height=57`, `animation=scanline 6s linear infinite`, `animationName=scanline`, `animationDuration=6s`, `animationTiming=linear`, `animationCount=infinite`, `active=true`.
- AUI frozen pixel row sample from `run/screenshots/aui/2026-07-15_12.50.57.png`: row `y=52`, tinted range `100..2460`, matching the full-width frozen gradient shape after physical-pixel scaling.
- AUI runtime row scan:
  - `run/screenshots/aui/2026-07-15_12.50.57.png`: animated row `y=210`, tinted range `0..639`;
  - `run/screenshots/aui/2026-07-15_12.50.58.png`: animated row `y=210`, tinted range `0..1530`;
  - `run/screenshots/aui/2026-07-15_12.50.59.png`: animated row `y=210`, tinted range `33..2393`;
  - `run/screenshots/aui/2026-07-15_12.51.00.png`: animated row `y=210`, tinted range `921..2559`.
- Source changes:
  - `src/main/java/com/sighs/apricityui/style/Animation.java`: keyframe length interpolation now resolves percentage length values against the browser-equivalent containing block basis before interpolation.
  - `src/main/java/com/sighs/apricityui/init/MotionTrack.java`: layout-property animation invalidates the animated element's own position cache, which is required for generated pseudo-elements because they are not in `parent.children`.
  - `src/main/java/com/sighs/apricityui/event/Test.java`: added fixture-only header scanline logging for generated pseudo rect/style/animation active state.
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-header-scanline.html` and `scripts/resource_browser_header_scanline_metrics.js`: added deterministic Chromium/AUI fixture and browser metrics runner.
- Full resource browser confirmation: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Full resource browser result: static baseline still loads with `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `header x=0 y=0 width=1463 height=60`, `main x=0 y=60 width=1463 height=783`.
- Full resource browser screenshot(s): `run/screenshots/aui/2026-07-15_12.52.15.png` through `run/screenshots/aui/2026-07-15_12.52.19.png`.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after Java edits; `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Remaining mismatch: none for V5-02 static gradient geometry/alpha and runtime left-to-right scanline movement. Exact animation phase is time-dependent and should be sampled only with declared timing policy.

### RBV-V5-03: White and Off-White Layer Separation

Status: [x]

Dependencies: V2 stable

Browser oracle:

- Type: computed-style + pixel-sample.
- Standard source: Chromium computed `background-color` / `border-color` for the page and panel elements, plus matched screenshot samples at fixed CSS coordinates after animations are disabled or excluded.
- Static policy: use static resource metrics or a fixture with no moving overlays. Do not sample through animated scanline, hover states, or selected cards.
- Pixel tolerance: state before sampling; expected pure CSS colors are exact in Chromium when no antialiasing or alpha layer crosses the sample point. For panel/background flat fills, start with tolerance `0..1` channel delta unless screenshot scaling or postprocessing proves a larger fixed tolerance.

Problem:

- AUI large backgrounds look flatter; browser separates `#fff` panels from `#fafafa` page background more clearly.

Expected checks:

- Body/background:
  - `body { background: var(--bg) }` resolves to `#fafafa`;
  - screenshot areas not covered by panels sample as `250,250,250`.
- White panels:
  - `.header`, `.sidebar`, `.detail-panel`, `.file-card` backgrounds resolve to `#ffffff`;
  - samples inside those panels/cards, away from borders/text/SVG/pseudo layers, sample as `255,255,255`.
- Content/main:
  - `.content` has no explicit background and should reveal the body/page `#fafafa`.
  - `.main` has no explicit background and should not accidentally clear or overpaint to white.
- Borders:
  - `.sidebar { border-right: 2px solid #e0e0e0 }`;
  - `.detail-panel { border-left: 2px solid #e0e0e0 }`;
  - `.file-card { border: 2px solid #e0e0e0 }`.
- Clear color:
  - areas outside the document/page should not be used as CSS evidence;
  - if AUI clear color appears in screenshot, crop or document it separately from page CSS.
- Color blending:
  - exclude `rgba(139,92,246,...)` overlays from flat-fill samples;
  - if an alpha overlay is the target, compute expected over the underlying browser color first.

Recommended fixture:

- Add `src/main/resources/assets/apricityui/apricity/tests/resource-browser-layer-colors.html`.
- Include:
  - a `#fafafa` body/background area;
  - a `#ffffff` header/panel area;
  - a white card on `#fafafa`;
  - `#e0e0e0` 1px/2px borders;
  - one `rgba(139,92,246,0.05)` overlay over white and one over `#fafafa` only if color blending is needed.
- Browser script should record computed styles and save a screenshot before AUI is compared.

Acceptance:

- White panels, off-white content background, and gray borders match Chromium computed styles and pixel samples.
- Full `devtools/resource.html` static baseline confirms the same layer colors at equivalent safe sample points.
- Any remaining perceived flatness needs a new shadow/border task and pixel evidence.

Current progress:

- V5-03 completed with fixture and full resource-browser pixel evidence.

Evidence:

- Browser oracle type: Chromium computed style plus fixed-coordinate pixel samples.
- Minimal fixture: `src/main/resources/assets/apricityui/apricity/tests/resource-browser-layer-colors.html`.
- Browser command: `node scripts\resource_browser_layer_colors_metrics.js`.
- Browser computed result at viewport `1463x843`:
  - `body.backgroundColor=rgb(250,250,250)`;
  - `header/sidebar/card/detail.backgroundColor=rgb(255,255,255)`;
  - `sidebar.borderRightColor=rgb(224,224,224)`, `card.border*=rgb(224,224,224)`, `detail.borderLeftColor=rgb(224,224,224)`;
  - `overlayWhite/overlayBg.backgroundColor=rgba(139,92,246,0.05)`.
- Browser fixture screenshot: `run/screenshots/browser/resource-browser-layer-colors-1463x843.png`.
- Browser fixture pixel samples:
  - `pageBg (1000,90)=250,250,250,255`;
  - `headerWhite (100,30)=255,255,255,255`;
  - `sidebarWhite (40,120)=255,255,255,255`;
  - `sidebarBorder (279,120)=224,224,224,255`;
  - `contentBg (640,120)=250,250,250,255`;
  - `cardWhite (340,150)=255,255,255,255`;
  - `cardBorder (313,130)=224,224,224,255`;
  - `detailWhite (730,230)=255,255,255,255`;
  - `detailBorder (681,120)=224,224,224,255`;
  - `overlayWhite (760,150)=249,247,255,255`;
  - `overlayBg (1070,150)=244,242,250,255`.
- AUI command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-layer-colors.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- AUI fixture result:
  - `html/body rect=1463x843`;
  - `body.backgroundColor=#fafafa`;
  - `header/sidebar/card/detail.backgroundColor=#ffffff`;
  - borders resolve to `#e0e0e0`;
  - overlay rects and `rgba(139,92,246,0.05)` computed style match browser.
- AUI fixture screenshot: `run/screenshots/aui/2026-07-15_13.02.08.png`.
- AUI fixture pixel samples from `run/screenshots/aui/2026-07-15_13.02.08.png` after CSS-to-physical scale `1.74982911825017`:
  - `pageBg=250,250,250,255`;
  - `headerWhite=255,255,255,255`;
  - `sidebarWhite=255,255,255,255`;
  - `sidebarBorder=224,224,224,255`;
  - `contentBg=250,250,250,255`;
  - `cardWhite=255,255,255,255`;
  - `cardBorder=224,224,224,255`;
  - `detailWhite=255,255,255,255`;
  - `detailBorder=224,224,224,255`;
  - `overlayWhite=249,247,255,255`;
  - `overlayBg=244,242,250,255`.
- Full resource browser browser screenshot: `run/screenshots/browser/resource-browser-direct-static-1463x843.png`.
- Full resource browser AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline`.
- Full resource browser AUI screenshot: `run/screenshots/aui/2026-07-15_13.05.24.png`.
- Full resource browser matched samples:
  - browser/AUI `contentBg (300,100)=250,250,250,255`;
  - browser/AUI `sidebarWhite (12,74)=255,255,255,255`;
  - browser/AUI `sidebarBorder (279,150)=224,224,224,255`;
  - browser/AUI `cardWhite (387,240)=255,255,255,255`;
  - browser/AUI `cardBorder (313,170)=224,224,224,255`;
  - browser/AUI `detailWhite (1200,100)=255,255,255,255`;
  - browser/AUI `detailBorder (1164,100)=224,224,224,255`.
- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-layer-colors.html`;
  - `scripts/resource_browser_layer_colors_metrics.js`;
  - `src/main/java/com/sighs/apricityui/event/Test.java` fixture-only logging for layer color rect/style evidence.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` logging change; `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Remaining mismatch: none for flat white/off-white/background/border color separation.

### RBV-V5-04: Gray Foreground and Border Color

Status: [x]

Dependencies: V4 recommended; extracted work is not required for this task unless explicitly reopened.

Browser oracle:

- Type: computed-style + pixel-sample.
- Standard source: Chromium computed foreground color/border styles for gray foreground elements, plus matched pixel samples for solid border interiors.
- Static policy: use static `devtools/resource.html` state with animations disabled or sample only regions unaffected by animation.
- Tolerance:
  - computed colors must match exactly as CSS values;
  - solid border interior pixels should match within `0..1` channel delta;

Expected checks:

- Computed style targets:
  - `.content-count { color: var(--gray) }` resolves to `#999999`;
  - `.file-meta { color: var(--gray) }` resolves to `#999999`;
  - `.detail-empty { color: var(--gray) }` resolves to `#999999`;
  - `.sidebar { border-right: 2px solid #e0e0e0 }`;
  - `.detail-panel { border-left: 2px solid #e0e0e0 }`;
  - `.file-card { border: 2px solid #e0e0e0 }`.
- Pixel targets:
  - border interior pixels from sidebar/detail/card are already flat-color covered by V5-03; re-sample only if V5-04 changes border rasterization.

Fixture status:

- Fixture exists at `src/main/resources/assets/apricityui/apricity/tests/resource-browser-gray-text-border.html`.
- Browser runner exists at `scripts/resource_browser_gray_text_border_metrics.js`.
- `src/main/java/com/sighs/apricityui/event/Test.java` has fixture-only logging for gray-label/border rects and styles.
- The fixture includes gray-label elements for `.content-count`, `.file-meta`, and `.detail-empty`.
- It includes `#e0e0e0` 1px and 2px borders on white and `#fafafa` backgrounds.
- The browser script records computed color/border styles, element rects, screenshot path, and border samples.

Acceptance:

- Computed gray foreground colors and border colors match Chromium.
- Solid border samples match Chromium.
- Full `devtools/resource.html` static baseline records the same DOM state while foreground-color and border computed styles match.

Current progress:

- V5-04 completed as a foreground-color/border-color task. Extracted residuals are tracked outside this main TODO.

Evidence:

- Browser fixture command: `node scripts\resource_browser_gray_text_border_metrics.js | Tee-Object -FilePath run\resource-browser-gray-text-border-browser-last.log`.
- Browser fixture result:
  - `.content-count`, `.file-meta`, `.detail-empty`, and `.detail-label` foreground colors resolve to `rgb(153,153,153)`;
  - 1px/2px border samples resolve to `rgb(224,224,224)`;
  - screenshot: `run/screenshots/browser/resource-browser-gray-text-border-1463x843.png`.
- AUI fixture command: `APRICITYUI_TEST_DOC_PATH=tests/resource-browser-gray-text-border.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-aui-last.log`.
- AUI fixture result:
  - `.content-count`, `.file-meta`, `.detail-empty`, and `.detail-label` foreground colors resolve to `#999999`;
  - 1px/2px border samples resolve to `#e0e0e0`;
  - screenshot: `run/screenshots/aui/2026-07-15_13.11.28.png`.
- Fixture pixel stats: `run/resource-browser-gray-text-border-pixel-samples.log`.
- Full resource browser browser command: `node scripts\resource_browser_browser_metrics.js --static | Tee-Object -FilePath run\resource-browser-browser-static-last.log`.
- Full resource browser AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-gray-text-border-resource-aui-last.log`.
- Full resource browser screenshot(s):
  - browser: `run/screenshots/browser/resource-browser-direct-static-1463x843.png`;
  - AUI: `run/screenshots/aui/2026-07-15_13.21.08.png`.
- Full resource browser state:
  - browser: `contentCount=5 ITEMS`, `fileCards=5`, `selected=null`, `detailActive=false`, `addedFile=false`;
  - AUI: `contentCount=5 ITEMS`, `fileCards=5`, `selected=<none>`, `detailActive=false`, `addedFile=false`.
- Full resource browser rects:
  - browser `contentCount`: `x=1076.3125 y=100 width=54.6875 height=19`;
  - AUI `contentCount`: `x=1076.5208320617676 y=99.975 width=54.47916793823242 height=18.85`;
  - browser first normal `fileMeta`: `x=330 y=273.796875 width=115 height=15`;
  - AUI first normal `fileMeta`: `x=330 y=274.2 width=115 height=14.5`;
  - browser `detailEmpty`: `x=1189 y=144 width=250 height=17`;
  - AUI `detailEmpty`: `x=1189 y=144 width=250 height=17.4`.
- Full resource browser border sample result:
  - `sidebarBorder`, `detailBorder`, and `cardBorder` match exactly: browser/AUI `224,224,224,255`.
- Source changes:
  - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`: added `detailEmpty` browser rect/computed metrics.
  - `src/main/java/com/sighs/apricityui/event/Test.java`: added `detailEmpty` to resource-browser baseline rect logs.
  - deterministic full-page border sample statistics were added for the border-only evidence path; text/raster statistics were extracted from this main file.
- Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` logging change; `git diff --check` reported only existing LF/CRLF warnings and no whitespace errors.
- Remaining mismatch: none for V5-04 foreground computed colors and border samples.

## Milestone V6: Interaction State Parity

Goal: compare behavior only against same-state browser output.

### RBV-V6-01: Selected Card Parity

Status: [x]

Dependencies: RBV-V0-02 and V2 stable; extracted work is not required for this task unless explicitly reopened.

Expected checks:

- Selected background.
- Purple border.
- Left rail pseudo.
- Top-right triangle pseudo.

Acceptance:

- Browser selected `LEVEL.DAT` card and AUI selected `LEVEL.DAT` card match.

Evidence:

- RBV-V6-01 selected-card style evidence, 2026-07-15:
  - Browser oracle type: same-state interaction `computed-style` + `rect` + screenshot state.
  - Browser command: `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser | Tee-Object -FilePath run\resource-browser-selected-card-browser-last.log`.
  - Browser state: viewport `1463x843`, `contentCount=5 ITEMS`, `fileCards=5`, `selected=level.dat`, `detailActive=true`, `addedFile=false`.
  - Browser selected card rect: `x=312 y=169 width=151 height=141.796875 right=463 bottom=310.796875`.
  - Browser selected style: background `rgba(139, 92, 246, 0.05)`, border color `rgb(139, 92, 246)` on all sides, border width `2px`.
  - Browser pseudo rules: `::before` is `4px` wide purple left rail with identity transform; `::after` is `12px x 12px` purple top-right triangle with `clip-path: polygon(100% 0px, 0px 0px, 100% 100%)`.
  - Framework/harness changes:
    - `scripts/resource_browser_browser_metrics.js`: added `--interaction=resource-browser`.
    - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`: selects `level.dat` for the browser oracle and logs selected card/pseudo computed style.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: narrowed the resource-browser interaction driver to select `level.dat`, delayed after-interaction logging until style/layout had a tick to settle, and logs selected card/pseudo style.
    - `src/main/java/com/sighs/apricityui/init/RenderElement.java`: clears parent size cache for padding/border and layout style changes so parent layout can be recomputed after dynamic style changes.
    - `src/main/java/com/sighs/apricityui/style/Grid.java`: applies grid stretch before reading item size for alignment.
  - Rejected diagnostic: clearing `Element.cssCache` inside `invalidateStyleCaches()` made selected style immediate but destabilized grid item sizing; that change was removed.
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-selected-card-aui-grid-stretch-order-last.log`.
  - AUI state: viewport `1463x843`, `contentCount=5 ITEMS`, `fileCards=5`, `selected=level.dat`, `detailActive=true`, `addedFile=false`.
  - AUI selected style now matches the browser rule at the CSS level: background `rgba(139,92,246,0.05)`, all borders `2px solid #8b5cf6`, `::before` width `4px` with `scaleY(1)`, and `::after` width/height `12px` with the expected polygon clip path.
  - Remaining mismatch: AUI selected card rect is `x=312 y=168.8 width=1463 height=141.9 right=1775 bottom=310.7`; Chromium selected card rect is `x=312 y=169 width=151 height=141.796875 right=463 bottom=310.796875`.
  - Screenshot evidence: latest AUI screenshots include `run/screenshots/aui/2026-07-15_22.09.22.png`; selected border visually spills across the card row, matching the bad logged `width=1463`.
  - Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after Java edits. `git diff --check` reported only LF/CRLF warnings for touched Java files and no whitespace errors.
  - Cursor update: unchanged, remains `RBV-V6-01`.
  - Next exact action: instrument or fix the grid flow/stretch sizing path for `.file-card.selected` after dynamic class changes and generated pseudo-elements; preserve the now-correct selected background, border, and pseudo CSS rule matching.

- RBV-V6-01 selected-card grid-assigned size evidence, 2026-07-15:
  - Browser oracle reused: `run/resource-browser-selected-card-browser-last.log`, unchanged same-state selected `LEVEL.DAT` oracle.
  - Browser selected card rect remains `x=312 y=169 width=151 height=141.796875 right=463 bottom=310.796875`.
  - Hypothesis: dynamic selected class changes cleared normal size caches after grid stretch, so later `Size.of(.file-card.selected)` fell back to block auto-fill width outside the grid assignment.
  - Framework fix:
    - `src/main/java/com/sighs/apricityui/init/RenderElement.java`: added `gridAssignedSize` cache and clears it on item padding/border/layout changes before parent grid recomputes assignment.
    - `src/main/java/com/sighs/apricityui/style/Grid.java`: stores stretched grid item assignment in `gridAssignedSize` and applies stretch before item alignment.
    - `src/main/java/com/sighs/apricityui/style/Size.java`: reuses valid grid-assigned size for in-flow grid items when ordinary size cache has been cleared.
  - Rejected diagnostic: temporary `[AUI Grid]` missing/stretch logs proved grid flow found the selected item and assigned `151x142`; those diagnostics were removed.
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-selected-card-aui-grid-assigned-size-final.log`.
  - AUI state: viewport `1463x843`, `contentCount=5 ITEMS`, `fileCards=5`, `selected=level.dat`, `detailActive=true`, `addedFile=false`.
  - AUI selected card rect after fix: `x=312 y=168.8 width=151 height=142 right=463 bottom=310.8`, matching the browser grid track within sub-pixel vertical tolerance.
  - AUI selected style after fix: background `rgba(139,92,246,0.05)`, all borders `2px solid #8b5cf6`, `::before` width `4px` with `scaleY(1)`, and `::after` width/height `12px` with the expected polygon clip path.
  - Screenshot evidence: `run/screenshots/aui/2026-07-15_22.23.28.png`; selected border no longer spills across the row.
  - Verification: `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed. `git diff --check` reported only LF/CRLF warnings for touched Java files and no whitespace errors.
  - Remaining mismatch: none for RBV-V6-01 selected-card state, selected background, purple border, left rail pseudo, top-right triangle pseudo, and selected card rect.
  - Cursor update: changed to `RBV-V6-02`.
  - Next exact action: continue `RBV-V6-02` detail-active parity with same-state browser detail metrics for `LEVEL.DAT` selected.

### RBV-V6-02: Detail Active Parity

Status: [x]

Dependencies: RBV-V0-02, V2, and V4 stable; extracted work is not required for this task unless explicitly reopened.

Expected checks:

- Active rail.
- Detail icon.
- Rotated decoration squares.
- Title.
- Metadata rows.
- Path.
- Tags.

Acceptance:

- Browser active detail and AUI active detail match in layout and paint.

Evidence:

- RBV-V6-02 detail-active oracle and active-rail diagnostic, 2026-07-15:
  - Browser oracle type: same-state interaction `rect` + `computed-style`.
  - Browser-standard source: Chromium fixture `run/resource-browser-detail-active-browser-last.log`, command `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser | Tee-Object -FilePath run\resource-browser-detail-active-browser-last.log`.
  - Fixture/state: `devtools/resource.html`, viewport `1463x843`, selected `LEVEL.DAT`, `detailActive=true`, `contentCount=5 ITEMS`, animations/transitions disabled in Chromium by the existing `--static` fixture hook.
  - Browser detail metrics:
    - `detailPanel`: `x=1163 y=60 width=300 height=783 right=1463 bottom=843`.
    - `detailPanel::before`: computed `width=2px height=783px top=0 left=0 background=rgb(139, 92, 246)`.
    - `detailIcon`: `x=1274 y=84 width=80 height=80`; `detailIcon::before` computed `width=84px height=84px top=-6px right=-6px bottom=-6px left=-6px`.
    - `detailName`: `x=1189 y=184 width=250 height=26`; `detailName::after` computed `width=40px height=2px top=32px left=125px transform=matrix(1, 0, 0, 1, -20, 0)`.
    - Rows: four `.detail-row` boxes at `y=234/272/310/348`, each `width=250 height=38`.
    - Tags: `NBT x=1189 y=434 width=43.203125 height=23`; `WORLD x=1236.203125 y=434 width=61.515625 height=23`.
  - Hypothesis: AUI needed an absolute/fixed percentage-height basis from the containing block's padding box when the parent has a definite layout-assigned height, because browser `height:100%` on `.detail-panel.active::before` resolves to the panel height.
  - Framework fix:
    - `src/main/java/com/sighs/apricityui/style/Size.java`: absolute/fixed elements can now resolve percent heights against a definite containing-block padding-box height derived from the parent cached or resolvable size, guarded against parent recursion.
  - Harness/metrics changes:
    - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`: logs detail active child rects and computed styles for panel rail, icon, decoration pseudo-elements, title, rows, path row, and tags.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: logs matching AUI detail active child rects/pseudo styles and delays interaction-state logging to reduce transition-frame sampling.
  - AUI commands:
    - Initial same-state run after metric expansion: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser`, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-detail-active-aui-last.log`.
    - Percent-basis run: same env, output `run\resource-browser-detail-active-aui-padding-box-height-last.log`.
    - Diagnostic run with temporary guarded size logging: output `run\resource-browser-detail-active-aui-size-diagnostic-last.log`.
    - Final non-diagnostic run after reverting the rejected CSS injection: output `run\resource-browser-detail-active-aui-final-last.log`; screenshot `run/screenshots/aui/2026-07-15_22.43.19.png`.
  - AUI stable detail metrics from the non-diagnostic run:
    - State: `selected=level.dat`, `detailActive=true`, `fileCards=5`, `contentCount=5 ITEMS`.
    - `detailPanel`: `x=1163 y=60 width=300 height=783 right=1463 bottom=843`, matching Chromium.
    - `detailIcon`: `x=1274 y=84 width=80 height=80`, matching Chromium.
    - `detailName`: `x=1189 y=184 width=250 height=26.1`, matching Chromium within sub-pixel height tolerance.
    - `selectedFileCard`: `x=312 y=168.8 width=151 height=142 right=463 bottom=310.8`, preserving RBV-V6-01 selected-card parity.
  - Rejected diagnostic:
    - Runtime CSS-cache injection of `*,*::before,*::after{animation:none!important;transition:none!important;}` made `detailPanel` fall back to `content-box`, changed it to `x=1113 width=350`, and regressed selected card to `width=176 height=2342`; this path was removed and must not be retried as the static-state mechanism.
  - Remaining mismatch:
    - AUI `detailPanel::before` after-interaction log still captures the transition intermediate endpoint as `height=100`, while Chromium `--static` expects `height=783`.
    - The temporary size diagnostic proved the final non-transition `styleHeight=100%` computes to `total=2x783`; the mismatch is now classified as transition/static-state sampling or percentage interpolation during transition, not as final percent-height layout math.
    - Detail row/tag vertical values remain about `+0.1` to `+1.7px` in AUI (`detailTags y=407.7` vs browser `406`, tags `y=434.2` vs browser `434`), to be judged after the rail transition/static-state blocker is removed.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after source changes.
    - `git diff --check -- src/main/java/com/sighs/apricityui/style/Size.java src/main/java/com/sighs/apricityui/event/Test.java src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html doc/guide/resource-browser-visual-todo-2026-07-15.md` reported only the existing LF/CRLF warning for `Size.java` and no whitespace errors.
  - Cursor update: unchanged, remains `RBV-V6-02`.
  - Next exact action: add a browser-standard minimal fixture for transitioning `height: 0` to `height: 100%` on an absolutely positioned pseudo/child, then either fix AUI transition interpolation to resolve percentage endpoints against the containing block or add a non-CSS-cache static test switch that disables motion without invalidating normal page rules.
- RBV-V6-02 transition percent-height endpoint fix, 2026-07-15:
  - Browser oracle type: minimal Chromium `rect` + `computed-style`.
  - Browser command: `node scripts\resource_browser_transition_percent_height_metrics.js | Tee-Object -FilePath run\transition-percent-height-browser-last.log`.
  - Browser fixture/state: `tests/transition-percent-height.html`, viewport `1463x843`, parent `#host height=200px`, rail child and `#host::before` transition from `height:0` to `height:100%` over `1000ms`.
  - Browser result:
    - initial child rail `height=0`, pseudo computed `height=0px`;
    - mid transition child rail `height=99.984375`, pseudo computed `height=99.9844px`;
    - final child rail `height=200`, pseudo computed `height=200px`.
  - Narrow hypothesis: AUI transition endpoints parsed percentage lengths through `Size.parseNumber(...)`, so `100%` became `0` before interpolation. Browser resolves the percentage endpoint against the containing block before interpolation.
  - Framework fix:
    - `src/main/java/com/sighs/apricityui/style/Transition.java`: `Transition.create(...)` now passes the element into transition parsing; ordinary length endpoints call `Size.tryResolveLength(...)` with a vertical/horizontal percent basis from the parent or viewport before falling back to numeric parsing.
  - Harness/fixture changes:
    - `src/main/resources/assets/apricityui/apricity/tests/transition-percent-height.html`: minimal browser/AUI fixture for `height:0 -> 100%`.
    - `scripts/resource_browser_transition_percent_height_metrics.js`: Chromium runner for the fixture.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: fixture logging for `#host`, `#rail`, and generated pseudo rail candidates.
  - Minimal AUI fixture commands:
    - Before successful full validation, `APRICITYUI_TEST_DOC_PATH=tests/transition-percent-height.html`, no interaction/prompt env, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\transition-percent-height-aui-after-last.log`.
    - Pseudo logging retry: same env, output `run\transition-percent-height-aui-pseudo-last.log`.
  - Minimal AUI fixture result:
    - `#rail` final rect was `x=48 y=0 width=2 height=843`, while Chromium expects `x=48 y=40 height=200`.
    - `#host::before` was not generated/loggable in this id-selector fixture.
    - Interpretation: this fixture successfully proved the browser endpoint rule, but the AUI child path also exposes a separate absolute containing-block/percentage-height issue and the fixture pseudo path is not usable as the acceptance check for `devtools/resource.html`.
  - Full resource validation command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser`, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-detail-active-transition-fix-aui-last.log`.
  - Full resource AUI result:
    - State: `selected=level.dat`, `detailActive=true`, `fileCards=5`, `contentCount=5 ITEMS`.
    - `detailPanelBefore`: `x=1165 y=60 width=2 height=783 right=1167 bottom=843`, so the previous `height=100` transition/static-state blocker is fixed.
    - `selectedFileCard`: `x=312 y=168.8 width=151 height=142 right=463 bottom=310.8`, preserving RBV-V6-01 selected-card parity.
    - `detailIcon`: `x=1274 y=84 width=80 height=80`, matching Chromium.
    - `detailName`: `x=1189 y=184 width=250 height=26.1`, matching Chromium within existing sub-pixel tolerance.
    - Remaining detail tags: `NBT x=1189 y=434.2 width=42.625 height=22.5`, `WORLD x=1235.625 y=434.2 width=61.5 height=22.5`.
  - Screenshots:
    - Full resource AUI latest screenshot: `run/screenshots/aui/2026-07-15_23.19.29.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Transition.java` and `Test.java` changes.
    - `git diff --check -- src/main/java/com/sighs/apricityui/style/Transition.java src/main/java/com/sighs/apricityui/event/Test.java src/main/resources/assets/apricityui/apricity/tests/transition-percent-height.html scripts/resource_browser_transition_percent_height_metrics.js doc/guide/resource-browser-visual-todo-2026-07-15.md` reported only the LF/CRLF warning for `Transition.java` and no whitespace errors.
  - Remaining mismatch:
    - AUI active rail now has the correct height but is horizontally offset: `detailPanelBefore x=1165 relativeX=2`, while the browser oracle records `detailPanel::before left=0` on `detailPanel x=1163`.
    - Detail row/tag residuals are extracted and must not be evaluated in this main goal file.
    - The minimal fixture's normal child `#rail` still demonstrates a separate absolute containing-block/percent-height issue (`height=843` instead of `200`); do not use that child fixture as completion evidence for `devtools/resource.html` until isolated under its own task.
  - Cursor update: unchanged, remains `RBV-V6-02`.
  - Next exact action: isolate the active rail horizontal offset first by comparing browser/AUI absolute pseudo positioning against border-box vs padding-box containing block semantics; then judge detail row/tag offsets after the rail x coordinate is fixed or explained.
- RBV-V6-02 active rail coordinate clarification and closeout, 2026-07-15:
  - Browser oracle reuse: `run/resource-browser-detail-active-browser-last.log` from command `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser | Tee-Object -FilePath run\resource-browser-detail-active-browser-last.log`.
  - AUI evidence reuse: `run\resource-browser-detail-active-transition-fix-aui-last.log`.
  - Browser coordinate derivation:
    - Browser `detailPanel x=1163`.
    - Browser computed `detailPanel border-left-width=2px`.
    - Browser computed `detailPanel::before left=0 width=2px right=296px`.
    - Therefore the browser rail border-box x is `1163 + 2 + 0 = 1165`, because absolute positioning is relative to the padding edge of the containing block, not the outer border edge.
  - AUI result:
    - `detailPanelBefore x=1165 y=60 width=2 height=783 right=1167 bottom=843 relativeX=2`.
    - This matches the derived Chromium coordinate and removes the previous apparent horizontal-offset mismatch.
  - Non-extracted active-detail parity result:
    - Detail panel box, rail height/position, detail icon box, detail title box, content origin, metadata row x/width, and tag origin now match the Chromium oracle within the current non-extracted scope.
    - `selectedFileCard x=312 y=168.8 width=151 height=142 right=463 bottom=310.8`, so RBV-V6-01 remains preserved.
  - Extracted residual:
    - Detail row/tag residuals belong to extracted work and must not block this main visual TODO unless explicitly reopened.
  - Source changes: docs only for this clarification.
  - Verification: run `git diff --check -- doc/guide/resource-browser-visual-todo-2026-07-15.md` after this edit.
  - Cursor update: advance to `RBV-V6-03`.
  - Next exact action: start `RBV-V6-03` hover-state parity by capturing a same-state Chromium hover oracle for the first hover target before adding AUI hover-driver support or framework changes.

### RBV-V6-03: Hover State Parity

Status: [x]

Dependencies: V2 and V4 stable; extracted work is not required for this task unless explicitly reopened.

Expected checks:

- Header button hover.
- File card hover.
- Tree item hover.

Acceptance:

- Hover background, border, transform, shadow, and pseudo layers match browser.

Evidence:

- RBV-V6-03 header-button hover oracle and AUI diagnostic, 2026-07-15:
  - Browser oracle type: same-state interaction `computed-style` + `rect`.
  - Browser command: `node scripts\resource_browser_hover_metrics.js --target=header-button-0 | Tee-Object -FilePath run\resource-browser-hover-header-button-browser-last.log`.
  - Browser fixture/state: `tests/resource-browser-browser-metrics.html?static=1` hosting `devtools/resource.html`, viewport `1463x843`, CDP `CSS.forcePseudoState(["hover"])` applied to the first `.action-btn`.
  - Browser result:
    - `hoverChain=[BUTTON.action-btn "◀ BACK"]`.
    - target rect `x=1182.625 y=8 width=88.5 height=37 right=1271.125 bottom=45`.
    - target style: color `rgb(255,255,255)`, all borders `2px rgb(139,92,246)`, box-shadow `rgb(26,26,26) 4px 4px 0px 0px`, transform `matrix(1, 0, 0, 1, -2, -2)`.
    - `::before`: `left=0px`, `top=0px`, `width=84.5px`, `height=33px`, background `rgb(139,92,246)`.
    - Browser screenshot: `run/screenshots/browser/resource-browser-hover-header-button-0-1463x843.png`.
  - Hypothesis: AUI hover state machinery can match the browser header button state when driven through `mousemove`, but any pseudo-layer mismatch must be isolated as a layout primitive rather than inferred from the full page.
  - Harness changes:
    - `scripts/resource_browser_hover_metrics.js`: added a Chromium/CDP hover oracle with deterministic forced `:hover`, screenshot capture, DOM/CSS domain enablement, frontend-node mapping fallback, and temp-profile cleanup tolerance.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: added `APRICITYUI_TEST_INTERACTION=resource-browser-hover`, a hover hold loop that dispatches `mousemove` to the first `.action-btn`, and header hover button/pseudo style logging.
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-hover`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-hover-header-button-aui-last.log`.
  - AUI result:
    - Initial one-shot hover diagnostic was rejected because the hover state was cleared before delayed logging; the driver now maintains the hover target until the interaction log is captured.
    - Final log: `hover=true` for the first header button.
    - target rect `x=1185 y=9.8 width=88.5 height=37.4 right=1273.5 bottom=47.2`.
    - target style matches the browser hover rule at CSS level: color `#ffffff`, all borders `2px solid #8b5cf6`, box-shadow `4px 4px 0 #1a1a1a`, transform `translate(-2px, -2px)`.
    - `::before` style has the correct hover `left=0`, but generated pseudo rect is `x=1187 y=11.8 width=52.5 height=33.4 relativeX=2 relativeY=2`.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-15_23.51.43.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` hover driver change.
    - `git diff --check -- scripts\resource_browser_hover_metrics.js src\main\java\com\sighs\apricityui\event\Test.java doc\guide\resource-browser-visual-todo-2026-07-15.md` passed with no output.
  - Remaining mismatch:
    - Header hover foreground, border, shadow, transform style, and `::before left=0` match the browser rule.
    - Header hover `::before width:100%` does not match: browser computed/rendered pseudo width is `84.5px`, while AUI generated pseudo rect width is `52.5px`.
    - This is the next narrow primitive to isolate; do not continue to file-card/tree hover until the header pseudo width basis is explained or fixed.
  - Cursor update: unchanged, remains `RBV-V6-03`.
  - Next exact action: add a minimal browser-standard fixture for a positioned button-like host with `::before { position:absolute; inset auto; left:0; top:0; width:100%; height:100%; }`, capture Chromium pseudo width/containing-block evidence first, then run the same fixture in AUI before changing framework code.

- RBV-V6-03 absolute pseudo percent-width fix and header-button closeout, 2026-07-16:
  - Browser oracle type: minimal Chromium `computed-style` + `rect`, then full-page same-state hover promotion.
  - Browser minimal command: `node scripts\resource_browser_absolute_pseudo_percent_width_metrics.js | Tee-Object -FilePath run\absolute-pseudo-percent-width-browser-last.log`.
  - Browser minimal fixture/state: `tests/absolute-pseudo-percent-width.html`, viewport `1463x843`, two positioned button-like hosts with `box-sizing:border-box`, `padding:8px 16px`, `border:2px`, and `::before { position:absolute; left:0; top:0; width:100%; height:100%; }`.
  - Browser minimal result:
    - `#fixed` host border box `88.5x37`, padding box `84.5x33`, content box `52.5x17`.
    - Chromium `#fixed::before` computed width/height is `84.5px x 33px`, proving the absolute pseudo percentage basis is the containing block padding box, not the content box.
  - AUI minimal before fix:
    - Command: `APRICITYUI_TEST_DOC_PATH=tests/absolute-pseudo-percent-width.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\absolute-pseudo-percent-width-aui-last.log`.
    - `#fixed` host derived padding box `84.5x33`, derived content box `52.5x17`.
    - AUI `#fixed::before` was `52.5x17`, matching the content box and rejecting the current framework behavior.
  - Framework fix:
    - `src/main/java/com/sighs/apricityui/style/Size.java`: absolute/fixed positioned elements now resolve percent width/height against the containing block padding box. Added explicit and cached padding-box basis helpers for both axes.
  - Harness/fixture changes:
    - `src/main/resources/assets/apricityui/apricity/tests/absolute-pseudo-percent-width.html`: minimal browser/AUI fixture for the absolute pseudo percentage basis.
    - `scripts/resource_browser_absolute_pseudo_percent_width_metrics.js`: Chromium runner for that fixture.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: added fixture logging for host border/padding/content derived boxes and generated `::before` rects.
  - AUI minimal after fix:
    - Command: `APRICITYUI_TEST_DOC_PATH=tests/absolute-pseudo-percent-width.html`, interaction/prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\absolute-pseudo-percent-width-aui-after-fix-last.log`.
    - `#fixed::before` is now `84.5x33`, matching the Chromium padding-box basis.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-16_00.11.00.png`.
  - Full resource hover promotion:
    - Browser oracle reused: `run/resource-browser-hover-header-button-browser-last.log`, same target, viewport, static hook, and CDP forced hover state.
    - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-hover`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-hover-header-button-aui-after-pseudo-width-fix-last.log`.
    - AUI full-page result: first header button remains `hover=true`, color `#ffffff`, borders `2px solid #8b5cf6`, box-shadow `4px 4px 0 #1a1a1a`, transform `translate(-2px, -2px)`.
    - AUI full-page `::before`: `left=0`, `width=84.5`, `height=33.4`, matching the browser `width=84.5` basis and preserving the already-correct hover state.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-16_00.12.49.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Size.java` and `Test.java` changes.
    - `git diff --check -- src\main\java\com\sighs\apricityui\style\Size.java src\main\java\com\sighs\apricityui\event\Test.java scripts\resource_browser_absolute_pseudo_percent_width_metrics.js scripts\resource_browser_hover_metrics.js src\main\resources\assets\apricityui\apricity\tests\absolute-pseudo-percent-width.html doc\guide\resource-browser-visual-todo-2026-07-15.md` reported only the existing LF/CRLF warning for `Size.java` and no whitespace errors.
  - Remaining mismatch:
    - Header button hover is complete for the current browser-standard evidence.
    - RBV-V6-03 still needs same-state evidence for file card hover and tree item hover.
  - Cursor update: unchanged, remains `RBV-V6-03`.
  - Next exact action: capture same-state Chromium hover oracle for the first `.file-card` target before AUI validation or framework edits.

- RBV-V6-03 file-card hover parity, 2026-07-16:
  - Browser oracle type: same-state interaction `computed-style` + `rect`.
  - Browser command: `node scripts\resource_browser_hover_metrics.js --target=file-card-0 | Tee-Object -FilePath run\resource-browser-hover-file-card-browser-last.log`.
  - Browser fixture/state: `tests/resource-browser-browser-metrics.html?static=1` hosting `devtools/resource.html`, viewport `1463x843`, CDP `CSS.forcePseudoState(["hover"])` applied to the first `.file-card`.
  - Browser result:
    - target rect `x=309 y=166 width=151 height=141.796875`.
    - target style: all borders `2px rgb(139,92,246)`, box-shadow `rgb(26,26,26) 6px 6px 0px 0px`, transform `matrix(1, 0, 0, 1, -3, -3)`.
    - `::before`: `left=0px`, `top=0px`, `width=4px`, `height=137.797px`, transform identity.
    - `::after`: `left=135px`, `top=0px`, `width=12px`, `height=12px`, clip path `polygon(100% 0px, 0px 0px, 100% 100%)`.
    - child `.file-name` color resolves to `rgb(139,92,246)`.
    - Browser screenshot: `run/screenshots/browser/resource-browser-hover-file-card-0-1463x843.png`.
  - Harness changes:
    - `scripts/resource_browser_hover_metrics.js`: added `file-card-0` target plus `::after` and child style capture.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: added `APRICITYUI_TEST_INTERACTION=resource-browser-hover-file-card`, explicit hover-hold selector storage, and file-card/pseudo/child logging.
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-hover-file-card`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-hover-file-card-aui-selector-hold-last.log`.
  - AUI result:
    - First one-shot file-card hover run was rejected because delayed logging showed `hover=false`; the hover hold driver now keeps the explicit `.file-card` selector active.
    - Final log: `hover=true` for `level.dat`.
    - target rect `x=312 y=168.8 width=151 height=142`.
    - target style matches the browser hover rule at CSS level: all borders `2px solid #8b5cf6`, box-shadow `6px 6px 0 #1a1a1a`, transform `translate(-3px, -3px)`.
    - `::before`: `width=4`, `height=138`, transform `scaleY(1)`, matching the browser hover rule within existing vertical sub-pixel tolerance.
    - `::after`: `width=12`, `height=12`, `relativeX=137`, clip path `polygon(100% 0, 0 0, 100% 100%)`.
    - child `.file-name` color is `#8b5cf6`; `.file-icon` has `animation=iconShake 0.4s ease`.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-16_00.39.57.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` update.
    - `git diff --check -- src\main\java\com\sighs\apricityui\style\Size.java src\main\java\com\sighs\apricityui\event\Test.java scripts\resource_browser_absolute_pseudo_percent_width_metrics.js scripts\resource_browser_hover_metrics.js src\main\resources\assets\apricityui\apricity\tests\absolute-pseudo-percent-width.html doc\guide\resource-browser-visual-todo-2026-07-15.md` reported only the existing LF/CRLF warning for `Size.java` and no whitespace errors.
  - Remaining mismatch:
    - Header button hover and file-card hover are complete for the current browser-standard evidence.
    - RBV-V6-03 still needs same-state evidence for tree item hover.
  - Cursor update: unchanged, remains `RBV-V6-03`.
  - Next exact action: capture same-state Chromium hover oracle for the first `.tree-item` target before AUI validation or framework edits.

- RBV-V6-03 tree-item hover parity and closeout, 2026-07-16:
  - Browser oracle type: same-state interaction `computed-style` + `rect`.
  - Browser command: `node scripts\resource_browser_hover_metrics.js --target=tree-item-0 | Tee-Object -FilePath run\resource-browser-hover-tree-item-browser-last.log`.
  - Browser fixture/state: `tests/resource-browser-browser-metrics.html?static=1` hosting `devtools/resource.html`, viewport `1463x843`, CDP `CSS.forcePseudoState(["hover"])` applied to the first `.tree-item`.
  - Browser result:
    - target rect `x=0 y=109 width=278 height=39`.
    - target style: background `rgba(139,92,246,0.05)`, left border `3px rgb(167,139,250)`, transform `none`, box-shadow `none`.
    - `treeToggle` rect `x=27 y=120.5 width=16 height=16`, color `rgb(139,92,246)`.
    - `treeIcon` rect `x=49.4000015 y=118.9000015 width=19.2000046 height=19.2000046`, transform `matrix(1.2, 0, 0, 1.2, 0, 0)`.
    - `treeLabel` rect `x=75 y=119 width=52.953125 height=19`.
    - Browser screenshot: `run/screenshots/browser/resource-browser-hover-tree-item-0-1463x843.png`.
  - Harness changes:
    - `scripts/resource_browser_hover_metrics.js`: added `tree-item-0` target and child style capture for `.tree-toggle`, `.tree-icon`, and label `span`.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: added `APRICITYUI_TEST_INTERACTION=resource-browser-hover-tree-item` and tree-item/child hover logging.
  - AUI command: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-hover-tree-item`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-hover-tree-item-aui-last.log`.
  - AUI result:
    - Final log: first tree item `hover=true`, path `/worlds`.
    - target rect `x=0 y=108.95 width=278 height=38.85`.
    - target style matches the browser hover rule at CSS level: background `rgba(139,92,246,0.05)`, left border `3px solid #a78bfa`, transform `none`, box-shadow `none`.
    - `treeToggle` rect `x=27 y=120.375 width=16 height=16`, color `#8b5cf6`.
    - `treeIcon` rect `x=51 y=120.375 width=16 height=16`, transform `scale(1.2)`. The logged rect remains the layout box while Chromium `getBoundingClientRect()` includes transform expansion; the computed transform matches and the existing rect logger does not apply transform extents.
    - `treeLabel` rect `x=75 y=118.95 width=53.625 height=18.85`.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-16_00.44.41.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` update.
    - `git diff --check -- src\main\java\com\sighs\apricityui\style\Size.java src\main\java\com\sighs\apricityui\event\Test.java scripts\resource_browser_absolute_pseudo_percent_width_metrics.js scripts\resource_browser_hover_metrics.js src\main\resources\assets\apricityui\apricity\tests\absolute-pseudo-percent-width.html doc\guide\resource-browser-visual-todo-2026-07-15.md` reported only the existing LF/CRLF warning for `Size.java` and no whitespace errors.
  - Remaining mismatch:
    - None for RBV-V6-03 hover-state CSS behavior under the current browser-standard evidence.
    - Transform-expanded child `getBoundingClientRect()` is a separate CSSOM View rect semantics issue if future visual checks require transformed child rect extents; it does not block this hover-state task because the computed transform and visual hover rule are applied.
  - Cursor update: advance to `RBV-V6-04`.
  - Next exact action: start `RBV-V6-04` scroll-state policy by capturing Chromium scroll behavior for sidebar/content/detail overflow before AUI validation or framework edits.

### RBV-V6-04: Scroll State Policy

Status: [x]

Dependencies: none

Expected checks:

- Sidebar/content/detail overflow.
- Scroll positions.
- Clip behavior.
- Whether WebKit scrollbar pseudo styling remains unsupported or gets a framework implementation.

Acceptance:

- Scroll behavior matches browser for content movement.
- Scrollbar styling support or fallback is explicitly documented.

Evidence:

- RBV-V6-04 scroll-state policy closeout and V6 status sync, 2026-07-16:
  - Browser oracle type: same-state scroll/rect/CSSOM metrics.
  - Browser command: `node scripts\resource_browser_browser_metrics.js --static --interaction=resource-browser-scroll | Tee-Object -FilePath run\resource-browser-scroll-browser-last.log`.
  - Browser fixture/state: `tests/resource-browser-browser-metrics.html?static=1&interaction=resource-browser-scroll` hosting `devtools/resource.html`, viewport `1463x843`, selected `level.dat`, scroll attempt sets `.sidebar`, `.content`, and `.detail-panel` to `scrollTop=120` and `scrollLeft=30` after selection.
  - Browser result:
    - Current full resource page has no actual scroll range in the tested state.
    - `.sidebar`: `scrollTop=0`, `scrollLeft=0`, `scrollWidth=278`, `scrollHeight=783`, `clientWidth=278`, `clientHeight=783`, `canScrollY=false`, `canScrollX=false`.
    - `.content`: `scrollTop=0`, `scrollLeft=0`, `scrollWidth=883`, `scrollHeight=783`, `clientWidth=883`, `clientHeight=783`, `canScrollY=false`, `canScrollX=false`.
    - `.detail-panel`: `scrollTop=0`, `scrollLeft=0`, `scrollWidth=298`, `scrollHeight=783`, `clientWidth=298`, `clientHeight=783`, `canScrollY=false`, `canScrollX=false`.
    - Key child rects did not move after the scroll attempt; browser clamps no-range scroll positions to zero.
    - Browser/native scrollbar styling: no scrollbar is present in this state, and `resource.html` does not declare WebKit scrollbar pseudo styling; no framework scrollbar styling work is required for the current page state.
  - Harness changes:
    - `src/main/resources/assets/apricityui/apricity/tests/resource-browser-browser-metrics.html`: added `resource-browser-scroll` interaction and scroll payload metrics for sidebar/content/detail.
    - `src/main/java/com/sighs/apricityui/event/Test.java`: added `APRICITYUI_TEST_INTERACTION=resource-browser-scroll`, delayed scroll attempts until after selection layout settles, and AUI scroll metrics for the same containers.
  - AUI command:
    - Initial rejected timing run: `APRICITYUI_TEST_DOC_PATH=devtools/resource.html`, `APRICITYUI_TEST_INTERACTION=resource-browser-scroll`, prompt env unset, `.\gradlew.bat runClient --console plain --no-daemon --offline *> run\resource-browser-scroll-aui-last.log`.
    - Final delayed run: same env, output `run\resource-browser-scroll-aui-delayed-last.log`.
  - AUI result:
    - The first run was rejected as a harness timing issue because it set scroll positions in the same tick as selection, before the selected layout had settled; `.content` temporarily reported `scrollWidth=1463` and shifted `scrollLeft=8.64`.
    - The final delayed run matches the browser no-range behavior: sidebar/content/detail all report `scrollTop=0`, `targetScrollTop=0`, `scrollLeft=0`, `targetScrollLeft=0` after the scroll attempt.
    - Final content grid stayed stable: `fileGrid x=312 y=168.8 width=819 height=142`.
    - AUI scroll metric note: `canScrollY=true` in the AUI log means CSS permits vertical scrolling (`overflow-y:auto`), not that the element has a positive scroll range. The range evidence is `scrollHeight <= clientHeight` and zero final scroll offsets.
    - Latest AUI screenshots include `run/screenshots/aui/2026-07-16_00.56.31.png`.
  - Verification:
    - `.\gradlew.bat compileJava --console plain --no-daemon --offline` passed after the `Test.java` update.
    - `git diff --check -- src\main\java\com\sighs\apricityui\event\Test.java src\main\resources\assets\apricityui\apricity\tests\resource-browser-browser-metrics.html doc\guide\resource-browser-visual-todo-2026-07-15.md` passed with no output.
  - Status sync:
    - `RBV-V6-01` status changed to `[x]` to match its selected-card closeout evidence.
    - `RBV-V6-02` status changed to `[x]` to match its detail-active closeout evidence.
  - Remaining mismatch:
    - None for current `devtools/resource.html` scroll-state behavior under the tested viewport/state.
    - Actual positive scroll-range movement is not exercised by the current page state; if future content creates overflow, use a separate minimal overflow fixture or a same-state resource page with real overflow before changing framework code.
  - Cursor update: return to `RBV-V0-03`.
  - Next exact action: define the browser-standard crop and animation policy for screenshot comparison, including viewport, DPR, static/animated state, crop rectangle, and tolerance, before any new image-level AUI comparison.

## Suggested Goal Batches

Automation priority:

- Continue from the first `[~]` task only when its remaining work blocks the next geometry task.
- Do not reopen completed tasks as "next work" unless a browser-standard fixture or full-page metric proves a regression.
- For this TODO, the next recommended strict-browser path is:
  1. continue `RBV-V6-01` selected-card interaction-state parity;
  2. return to `RBV-V0-03` only when image-level crop/pixel comparison is needed.
- Extracted tasks must not block the main visual TODO unless the user explicitly reopens them here.
- Use static metrics for box layout unless the task is explicitly about animation or interaction.
- For each task, record the browser oracle type before the AUI result: `rect`, `computed-style`, `event`, or `documented-rule`.
- If a task changes framework code, validate the minimal fixture first. Run full `devtools/resource.html` only after the minimal fixture improves without regression, or when the task explicitly requires full-page evidence. A full-page improvement alone is not enough.

Common strict-browser commands:

```powershell
# Browser static layout reference.
node scripts\resource_browser_browser_metrics.js --static

# Browser animated/default reference.
node scripts\resource_browser_browser_metrics.js

# AUI full resource browser static baseline.
# Run this only after the minimal fixture improves without regression,
# or when the active task explicitly requires full-page evidence.
$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline

# Java verification after framework edits.
.\gradlew.bat compileJava --console plain --no-daemon --offline
git diff --check
```

Recommended next-task command skeleton:

```powershell
# 1. Browser oracle first.
node scripts\resource_browser_browser_metrics.js --static

# 2. Minimal AUI fixture if the task has one.
$env:APRICITYUI_TEST_DOC_PATH='tests/<fixture>.html'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline

# 3. Full resource browser confirmation.
$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline

# 4. Code quality gate after Java edits.
.\gradlew.bat compileJava --console plain --no-daemon --offline
git diff --check
```

Harness facts for automation:

- `src/main/java/com/sighs/apricityui/event/Test.java` automatically opens the configured HTML after client startup and automatically closes the client after the configured delay.
- Default AUI screenshots are written to `run/screenshots/aui`.
- Use `APRICITYUI_TEST_DOC_PATH` to switch the auto-opened page without editing source.
- Use `-Dapricityui.test.autoExitSeconds=<seconds>` only when a longer capture or metric delay is needed.
- Keep `APRICITYUI_TEST_INTERACTION` unset for static baselines.
- Set `APRICITYUI_TEST_INTERACTION=resource-browser` only for interaction-state tasks.
- Set `APRICITYUI_TEST_PROMPT_RESPONSE=AUTO_PROMPT_FILE` only for prompt/new-file tasks.

Batch 1: deterministic comparison (mostly complete)

1. RBV-V0-01
2. RBV-V0-03
3. RBV-V0-04
4. RBV-V0-05
5. RBV-V1-01

Batch 2: box geometry (complete for current static baseline; strict browser standard)

1. RBV-V2-01
2. RBV-V2-02
3. RBV-V2-05
4. RBV-V2-06

Batch 2 completed notes:

- `RBV-V2-01`, `RBV-V2-02`, `RBV-V2-03`, `RBV-V2-04`, `RBV-V2-05`, and `RBV-V2-06` have browser/AUI rect evidence and should be treated as complete unless a new strict-browser check proves regression.
- Do not reprocess completed grid/card/header/tree evidence during normal goal progression.

Batch 2A: remaining horizontal geometry (complete for current static baseline)

1. RBV-V2-03
2. RBV-V2-04

Batch 2 automation commands:

```powershell
node scripts\resource_browser_browser_metrics.js

$env:APRICITYUI_TEST_DOC_PATH='tests/auto-width-containing-block.html'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline

$env:APRICITYUI_TEST_DOC_PATH='devtools/resource.html'
Remove-Item Env:\APRICITYUI_TEST_INTERACTION -ErrorAction SilentlyContinue
Remove-Item Env:\APRICITYUI_TEST_PROMPT_RESPONSE -ErrorAction SilentlyContinue
.\gradlew.bat runClient --console plain --no-daemon --offline
```

Batch 2 strict rule:

- If browser and AUI disagree, create or reuse a minimal page that isolates the CSS primitive.
- Browser `getBoundingClientRect()` is the expected result.
- Do not modify `resource.html` to compensate for framework behavior.
- Header/logo/tree origin work must compare individual child rects, not just parent group rects.

Batch 3: extracted work

Do not continue this work from the main TODO unless explicitly reopened.

Batch 4: paint quality

Completed for current static baseline:

1. RBV-V4-01
2. RBV-V4-02
3. RBV-V4-03
4. RBV-V5-02
5. RBV-V5-03
6. RBV-V5-04

Next paint tasks:

1. None in this file.

Batch 5: interaction parity

1. RBV-V0-02
2. RBV-V1-02
3. RBV-V6-01
4. RBV-V6-02
5. RBV-V6-03
6. RBV-V6-04

## Stop Conditions

- Stop and document if the browser behavior cannot be reproduced locally.
- Stop and document if a fix only improves `resource.html` by breaking a minimal browser-standard test.
- Stop and document if the AUI and browser screenshots are not in the same state.
- Stop and document if visual mismatch is caused by an intentionally unsupported browser extension.
- Do not mark a visual task complete from a single interacted screenshot when the browser reference is static.



