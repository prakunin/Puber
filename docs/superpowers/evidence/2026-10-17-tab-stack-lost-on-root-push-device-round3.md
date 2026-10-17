# Device evidence: tab-stack-lost-on-root-push, review round 1 fixes

Build: worktree HEAD b5919b85, dev debug APK, sha256 04a1fa59...08197be, installed with
tools/android-apk-install-preserve (outcome installed, compatible_replace); on-device base.apk sha256 equals the built one.
Device: emulator-5554 (puber_tv_36), leased and released. No playback started, no account mutation.

## Instrumented (make itest, emulator-5554)
- ShowAllScreenContentFocusTest: 5/5 green
- TabHostTelemetryWiringTest: 10/10 green
- TabFlowLifecycleTest: 1/1 green

## AC-1 / AC-2: opener focus after Details + Back
Concerts tab, "Trending" row scrolled to the end, "Show all" (DPAD_CENTER), DPAD_RIGHT into the grid.
- Opener A: DPAD_DOWN x4, DPAD_RIGHT: card "Hades / Ballads of the Underworld" (second visible row, middle column, grid scrolled).
  DPAD_CENTER (Details), KEYCODE_BACK once, 3 s. Observed: the Show all grid (not the tab root), same rows at the same positions,
  focus ring on Hades. PASS.
- Opener B, deep in the grid: DPAD_DOWN x6 more, DPAD_LEFT: card "Valeriy Leontiev / Novaya volna" (left column, many rows below the
  first viewport). Details, Back, 3 s. Observed: same grid position, focus ring on the same card. PASS.

## M2: rail opened right after Back keeps focus
Same opener B. One adb shell call: DPAD_CENTER, sleep 2.5, BACK, sleep 0.4, DPAD_LEFT; wait 4 s.
Observed: rail expanded with "Concerts" focused and the grid still behind it. DPAD_DOWN moved the rail focus to
"Documentary movies" (not into the grid). Focus was not pulled back into the grid. PASS.

## Opener-removed fallback
NOT CHECKED on device: it needs hide-watched reload, i.e. playing an item to the end (account mutation, excluded).
Evidence is the instrumented ShowAllScreenContentFocusTest 5/5 (includes the neighbour fallback and the re-check cases).
