# Review triage - round 1

Run: manual-quality-hls4   Base: 0823528b   Reviewed head: 173b3da9

Sources: review-blind-1.md (no findings), review-edge-1.md (no findings), review-tests-1.md (F1-F3), review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (ran, clean), review-state-path-1.md (no findings).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:428`; `PlaybackControllerNetworkTracksTest.kt:117,139` | No test fails if `prepare` stops applying the video cap: the prepare-step check `selectedVideoHeight()==360` also holds with no cap (ABR starts on 360) | read the tests and the plan's Implementation record ("green with no cap at all (ABR starts at 360)") | fix | assert the player's parameters (max video size, force-highest) after prepare in both tests |
| 2 | tests F2 | minor | `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt:295` | Auto guard's VideoFile has no w/h, so a cap leaking into Auto would still be null | read the test | fix | add w/h to that file |
| 3 | tests F3 | minor | `PlaybackController.kt:872` | no test ties real unlabelled Media3 tracks to `isFallbackLabel` rows with the localized label | read PlayerAudioTracksTest and PlayerVMTrackSelectionTest: inputs are handmade | fix | instrumented case on a fixture whose audio has no label/language, capturing onTracksUpdated |
| 4 | codex | minor | `app/src/main/java/com/kino/puber/ui/feature/player/vm/VideoCapParameters.kt:11-13` | the cap leaves Media3's default physical-display viewport constraint on, so on a shared multi-variant hls4 a pick above the display size (2160p on a 1080p TV) can play lower while the picker shows the pick | read applyVideoCap (only setMaxVideoSize/forceHighest) and PlaybackController.kt:424 (`DefaultTrackSelector(context, ...)` starts from context defaults, which set the viewport to the physical display) | fix | clear the viewport constraint when a cap is applied, restore the context default on Auto; unit and instrumented assertion |

## Rejected codex claims

- hls4 401/403 strands a playable quality because hls/http are never tried - unproven: Auto already plays hls4 first on base, and `PlayerVM.onStreamFailure` advances candidates on NEXT_URL errors.
- Unequal TrackGroups across masters revert the dubbing - unproven: needs per-quality masters to differ; design risk 3, AC-4/AC-9 cover the row and the guard.
- Master without RESOLUTION pins the highest variant - unproven: no such master seen; playback still succeeds.
- Fallback label via ResourceProvider instead of Context.getString - spec design decision (lambda with Context), the pure builder is tested with FakeResourceProvider.

## Next

- fix round 1: #1-#4 by the implementer; then edge, tests, codex, antigravity on the incremental diff; qa-device on AC-2/AC-3/AC-5 (runtime code changes in #4); unproven lines: 8 (blind 3, edge 3, tests 2)
