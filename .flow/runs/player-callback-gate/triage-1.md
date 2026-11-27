# Review triage - round 1

Run: player-callback-gate   Base: cec5f7fc   Reviewed head: 2d748aaf

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (ran, clean), review-state-path-1.md (no findings).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests | minor | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:303` | No test checks that the controller feeds the new stream's load start into the gate (`dataSpec.uri` vs `stream.url`); a broken wiring would give every post-switch real error one silent extra prepare | read PlaybackController.kt:297-304 and the device tests: every switchStream call in androidTestInstrumentation ends on a healthy stream, no case fails B after a switch | fix | instrumented case: healthy A, switchStream to a failing B, one StreamFailure with B's URL, one request to B |
| 2 | codex | minor | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:83-84` | `switchStreamAfterQueuedFailure_...` relies on a fixed `Thread.sleep(PARSE_WAIT_MS)` to have A's error queued; on a slow device the precondition fails | read the test; the sleep is in the test body AC-1 requires unchanged | defer | appended as item 4 to existing idea run `player-device-tests-hardening` (same scope: device tests that can miss their scenario) |
| 3 | qa lead; edge, tests, codex, antigravity, state-path | - | `PlaybackController.kt:873` | Audio row shows "Track 1" after a quality switch on the box | five lenses traced it: label comes from `format.label ?: format.language` in code the diff does not touch; the gate only decides delivery; the manual-quality path tries `hls` before `hls4` (`PlayerInteractor.kt:236,247`), likely an unlabelled playlist; master not compared | reject (for this diff) + defer | not caused by this diff; new idea run `player-audio-track-label-fallback` to compare master on the box |

## Rejected codex claims

- Old load-start for URL A marks a replacement session for the same URL A as loading - needs a switch to the identical URL while its error is queued; spec risk R12 accepts it, behaviour equals base, not a regression.
- Single source-session identity that tells repeated URLs apart - design preference, no concrete failure shown.

## Next

- fix round 1: #1 (tests only) by the implementer, then incremental review: edge, tests, codex, antigravity on `git diff 2d748aaf`; the new device case run 3x on the emulator; unproven lines: 6 (blind 3, edge 2, tests 1)
