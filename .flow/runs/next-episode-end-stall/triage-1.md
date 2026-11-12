# Review triage - round 1

Run: next-episode-end-stall   Base: 4db9c0d4   Reviewed head: 3bf1209d

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | major (raised from minor) | `PlayerVM.kt:1407` via `PlaybackController.kt:231-234` | The dismissed-end switch calls `switchEpisode()` -> `playbackController.release()` synchronously from ExoPlayer's `onPlaybackStateChanged(STATE_ENDED)` dispatch; the project forbids releasing inside a player callback (`PlaybackController.kt:686`) | read `PlaybackController.kt:229-235` (direct `callback?.onPlaybackEnded()`), `:684-687` (warm-up release is posted), `PlayerVM.kt:1044-1048` (`switchEpisode` releases at once); base ended path never released synchronously | fix | defer the switch out of the callback (post / launch), keeping mark-watched order and the null fallback; test proves the switch does not run inside the callback frame and still happens |
| 2 | tests F1 | minor | `PlayerPositionPublishingTest.kt:113-129` | P1 AC-7 test uses the panel path, not the spec's R7 no-panel setup, so it cannot catch a publish moved into one branch | read the test against the spec row | fix | rewrite to the R7 setup |
| 3 | tests F2 | minor | `PlayerVM.kt:1405` | The `nextEpisodeCountdown != null` branch of the reordered `when` has no test | grep of `onPlaybackEnded()` callers in tests: none ends while the countdown runs with no panel | fix | add a guard test |
| 4 | blind F1 | minor | `PlayerVMPromptDismissalTest.kt:147` | The R7 guard does not assert that the countdown stays down after a cancel, which the deleted test did | read the test | fix | add `assertNull(nextEpisodeCountdown)` |

## Rejected codex claims

- Device coverage incomplete (M1 X button, M3) - process note, not a code defect; carried as NOT CHECKED to the gate.

## Next

- Fix round: implementer (#1-#4), check.fast and the player tests; then lens edge + codex on the incremental diff, lens tests (tests changed), qa-device on AC-1/AC-2/AC-8 (runtime code changed). Unproven lines: 6.
