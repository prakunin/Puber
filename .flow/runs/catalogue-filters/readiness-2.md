# Readiness 2: catalogue-filters

Verdict: **READY**

Inputs: spec and plan at `/home/dev/orca/workspaces/Puber/catalogue-filters` HEAD bd05ca22, `test-design.md` (mtime 2026-11-24 17:09), `state.md`. Checks that passed in [readiness-1.md](readiness-1.md) are not repeated; bd05ca22 touches only the spec and the plan.

## Round-1 must-fix

| # | Status | Evidence |
|---|---|---|
| 1 | Fixed | Every non-heading line of `test-design.md` appears verbatim in the spec's `## Test design`; spec rows 356 (`resetPressed_focusesFiltersThenFirstCardWhenListLands`) and Manual 4 (row 396) match; plan Task 6 table line 266 and Coverage AC-6 line 315 name the new test |
| 2 | Fixed | `AC-5 \| device \| P1 \| Manual 3` and `AC-11 \| device \| P1 \| Manual 5` (test-design 63, 87); AC-11 P0 carried by `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry`, now `P0 red` with its defect (test-design 86), in plan Task 6 (line 268) and Coverage AC-11 (line 320) |

## Round-1 notes

| Note | Status |
|---|---|
| Spec said `LazyColumn` for panel pages | Fixed: spec line 127 says `Column` + `verticalScroll` with the `PlayerSidePanel.kt:125` reason; no `LazyColumn` left in the spec |
| Merge conflict with player-overlays-port on `PlayerSidePanel.kt` / `PlayerGlass` | Open risk for whichever lands second; not blocking |

## New citation sampled

| Citation | Claimed | Found |
|---|---|---|
| `PlayerSidePanel.kt:125` | `IntrinsicSize.Max` width | yes |
| `PlayerSidePanel.kt:237-315` | panel item to lift to `core/ui/uikit` | yes: `PlayerPanelItem` starts at 238 and ends at 315 |
