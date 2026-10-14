LENS: tests  ROUND: 3  BASE: a97ee2b1  RANGE: 7ca85b93..f4b311e0
### F1 [minor] No test covers the subtitle-restore reset on a same-media rebuild, and the skipped P1 test would not have caught it either
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:936 (`selectBufferPreset`) and :948 (`toggleFastDns`), both `subtitleRestoredForCurrentMedia = false`
- Problem: No test sends `PlayerAction.ToggleFastDns` or `PlayerAction.SelectBufferPreset`, so the new flag reset on both rebuild paths is never exercised. The P1 test was skipped "because the fixture has one buffer preset", but `ToggleFastDns` needs no fixture change. The P1 test as designed would also miss the regression that matters: the rebuild keeps `intendedSubtitleRow`, so an audio pick saves `rus` either way.
- Failure scenario: someone drops `subtitleRestoredForCurrentMedia = false` from `toggleFastDns`. Viewer on Russian toggles Fast DNS; `prepare` clears the controller mirror and disables text. On the first report with text, `onTracksUpdated` skips the restore and goes to the re-send branch, where `row == previousRow` (rows rebuild equal), so `selectSubtitle` is never called: picker says Russian and nothing renders. All 1590 tests stay green.
- Fix: add `rebuild_fastDns_reappliesSubtitleOnFirstReportWithText`: `startedVM()`, `report(rows)`, pick `rus.vtt`, `onAction(ToggleFastDns)`, `clearMocks(playbackController)`, `report(emptyList())`, `report(rows)`. Assert no `selectSubtitle` after the text-less report, exactly one `selectSubtitle(match { it.playerTrackId == "rus.vtt" })` after `report(rows)`, zero `saveTrackPreferences`. Optionally an audio pick between the two reports asserting `rus` is saved.

Checked, nothing to report:
- tests-2 F1 closed: `currentItemHasSideLoads = spec.hasSideLoads` at PlaybackController.kt:440 and :511; `hasSideLoads_*` tests fail on regression.
- `subtitleReportKind_*` tests pin every branch; the call site in `notifyTracksUpdated` needs ExoPlayer (accepted: normally unreachable).
- Replaced/extended tests fail on 7ca85b93 (collapse to Off, intended row re-applied, row disappears, deferred restore on next media).
- Tests green on old code still guard the new design (audio pick persists intent, Off pick forgets intent, next-media audio pick keeps saved subtitle, refresh sends refreshed row only).
- No assertions weakened; no mocks of the class under test, no sleeps, no skips.
- Suite at f4b311e0: BUILD SUCCESSFUL, 1590 tests, 0 failures.
