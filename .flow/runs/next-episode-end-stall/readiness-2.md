# Readiness 2: next-episode-end-stall

Verdict: **READY**. Spec and plan at worktree HEAD aadb323a. The only change since 071a4bd0 is in the docs. Coverage, citations and order are as in `readiness-1.md` and still hold.

## Round-1 fix list

| # | Status | Evidence |
|---|---|---|
| 1 | Resolved | New P0 guard `aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose` in spec Test design, plan Task 2 and `test-design.md:36`. It catches the named mutation. Base `onPanelClosed()` falls to `checkEarlyNextEpisode()`, which is gated by `!countdownDismissed` (`PlayerVM.kt:1572`), so `release()` stays uncalled. A close path that reads `countdownDismissed` calls `playNextEpisode()` with the next stub. The old row (`test-design.md:37`) now names what the null-next fixture (`PlayerVMTestFixture.kt:117`) can catch. |
| 2 | Resolved | Task 1 and Task 2 red-run steps now say "the `red` rows fail" with no count. |
| 3 | Resolved | The spec Design row and `design-architect.md:13` now fall through to `checkEarlyNextEpisode()` on false, matching plan Task 2 (`onPanelClosed()` step). The `showControlsForEndedPlayback()` fallback stays only on the no-panel ended branch (R7). |

## New citations checked

| Citation | Symbol found |
|---|---|
| `PlayerVM.kt:848-861` | `onPanelClosed()`: the `exitWhenPanelCloses` block, then `checkEarlyNextEpisode()` |
| `PlayerVM.kt:1398-1404` | `cancelCountdown()` sets `countdownDismissed = true` with no prompt up |
| `PlayerAction.kt:27`, `:30`, `:56` | `OpenSettingsPanel`, `ClosePanel`, `CancelNextEpisodeCountdown` |
| `PlayerVMTestFixture.kt:76`, `:190` | `callbackSlot`, `startedVM()` |

## Fix list

None.
