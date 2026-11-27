Round 1

CHECK: check.full -> pass (testDevDebugUnitTest, player-test-fixtures, detektAll, lintDevDebug, profile unit tests, instrumentation detekt/lint, both tooling scripts all PASS; exit 0)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage only); instrumented result taken from the implementer's emulator run (itest-2.xml), not re-run.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | itest-2.xml: PlaybackControllerNetworkTracksTest 14 tests, 0 failures, 0 errors, includes unlabelledAudio_usesInjectedResourceProviderForFallbackLabel; itest-1.xml shows the red run (expected injected-fallback-label but was Track 1) before the controller change. Not re-run by QA. |
| AC-2 | PASS | rg "context\.getString" PlaybackController.kt returns nothing; check.full compiles; diff base..HEAD touches PlaybackController.kt (10 lines) and PlayerScreen.kt (fifth get()). |
| AC-3 | PASS | the two fallback tests (unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback, progressiveUndeterminedAudio_reportsLocalizedFallbackRow) are in the 14 passing tests of itest-2.xml. |
| AC-4 | PASS | Box run: worktree dev APK (sha256 7f25f0f9...3b3ff, matched on device) opened "Квазимодо, душа Парижа", resume prompt shown, playback ran (30:22 -> 31:31), same process and MainActivity resumed throughout; logcat counts NoBeanDefFoundException 0, InstanceCreationException 0, FATAL EXCEPTION 0. Audio list showed four named rows, nothing selected. |
TESTS ADDED: none (the P1 error-marker test was omitted by the implementer: the three player_error_playback reads rest on AC-2 and compilation; no runtime test reaches them).
EVIDENCE: /home/dev/orca/workspaces/Puber/player-track-label-resource-provider/docs/superpowers/evidence/2026-11-27-player-track-label-resource-provider-device.md (audit passed, 438 files scanned; not committed per instruction)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Residual: the real post-language-switch re-wrap is not exercised anywhere (brief Q2); the device Audio rows came from API titles, so the "Track N" fallback is covered only by the instrumented test.

Housekeeping: master dev APK restored on the box (on-device sha256 4354d24d...c128 equals the main-checkout build); auth-save taken before the install (.auth/com.kino.puber.stage.tar); lease acquired and released; no language switch; no scripted API requests (one ordinary search with 4 typed characters).
