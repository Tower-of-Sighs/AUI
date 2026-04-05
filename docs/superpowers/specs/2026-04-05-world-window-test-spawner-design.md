# World Window Test Spawner Design

Date: 2026-04-05

## Summary
Add a small, removable, client-side test hook that spawns a world-space UI window when a player right-clicks a block. This is only active in dev/IDE runtime and is intended for manual acceptance testing of world rendering (z-fighting fix).

## Goals
- Provide a quick, repeatable manual test: right-click a block, see a world-window UI appear.
- Limit scope to development/testing runs only.
- Keep the change isolated in a single new class for easy removal after acceptance.

## Non-Goals
- No production feature or user-facing config.
- No automation or formal unit tests.
- No persistence or save/load behavior.

## Trigger and Gating
- Event: Forge `PlayerInteractEvent.RightClickBlock` (client side only).
- Gate: `SharedConstants.IS_RUNNING_IN_IDE` must be true; otherwise handler returns immediately.

## Spawn Behavior
- Use a fixed test document path, e.g. `apricityui/test/world_window_test.html`.
- Fixed size: `width=240`, `height=140` (pixels in UI space).
- Fixed distance cap: `maxDistance=8` (blocks).
- Spawn position: at the clicked `BlockPos`, offset slightly along the clicked face normal to avoid embedding in the block.
- Orientation: face normal aligned with the clicked face so the window is flush to the block face.

## UX Notes
- If repeated right-clicks spawn multiple windows, that is acceptable for test use. Optionally, we may clear previous test windows to avoid clutter (left as implementation choice).

## Acceptance Criteria
- In dev/IDE run, right-clicking a block creates a visible world window at the clicked location.
- In non-IDE runs, right-clicking a block does nothing.
- The new behavior is fully contained in a single new class and can be deleted without touching other files.

## Files (planned)
- `src/main/java/com/sighs/apricityui/instance/WorldWindowTestSpawner.java`
- `docs/superpowers/specs/2026-04-05-world-window-test-spawner-design.md`
