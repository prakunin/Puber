# Test design: subtitle-switch-text-disable

Base 7a9fd580. Option B: tests pin the fork's current switch behaviour; production sources unchanged. Every new test is therefore `guard` (passes on base); there is no failing-first test. Each Proves column names the mutation to apply by hand once to show the test can fail.

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Old text override lands on a different-language rendition at reused coordinates after a switch (wrong subtitle shown) | 2 | 3 | 6 | AC-1 instrumented |
| Selector regression drops identity re-selection, subtitle off after every quality switch | 2 | 3 | 6 | AC-2 instrumented, AC-4 unit |
| VM stops sending Off when the intended row vanishes after a switch | 2 | 2 | 4 | AC-3 unit |
| VM re-sends on identical rows (double select, flicker) | 2 | 2 | 4 | AC-4 unit |
| Instrumented test flaky (async track report, immediate asserts) | 2 | 2 | 4 | `awaitCondition`, no immediate asserts |
| Media3 default selection (DEFAULT=YES) mistaken for a switch defect | 1 | 1 | 1 | accepted (Q2): not switch-specific; AC-1 uses DEFAULT=NO |
| Docs drift from the pinned semantics | 1 | 1 | 1 | accepted; checked in review (AC-5) |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 guard | `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` in `PlaybackControllerNetworkTracksTest` | fails if the selector applies the old override or a positional match to the Spanish rendition (selected text track appears, or the journal shows a Spanish VTT request) |
| AC-2 | instrumented | P0 guard | `qualitySwitch_sameManifestSubtitle_reselectsByIdentity` in `PlaybackControllerNetworkTracksTest` | fails if the pending row is cleared on switch without a VM re-send (English not selected) or if the test needs an extra `selectSubtitle` call |
| AC-3 | unit | P0 guard | `selectQuality with subtitle row missing from new report sends Off row` in `PlayerVMTrackSelectionTest` | fails if the VM keeps the stale intended row or skips the Off re-send after a switch |
| AC-4 | unit | P0 guard | `selectQuality with identical rows keeps subtitle without resending` in `PlayerVMTrackSelectionTest` | fails if the VM re-sends on identical rows or drops the selected row; `verify(exactly = 1)` on the initial send only |
| AC-5 | instrumented + check.full | P1 | the two instrumented tests above via `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest`; `check.full` | fails if either does not pass or the docs lack the switch semantics and test names |

Observable assertions: AC-1/AC-2 read `selectedTextTrack()` and `textTracksDisabled()` (`PlaybackControllerReads.kt`) and the request journal after the new source is READY; AC-3/AC-4 read the VM's content state (selected subtitle row) and the `playbackController.selectSubtitle` calls recorded by the existing fake, driving `callbackSlot.captured.onTracksUpdated` before and after `PlayerAction.SelectQuality(1)`. Unit test names follow the file's existing style; adjust the backtick names to what the implementer writes and keep this table in sync.

Reuse: `commonHlsRoutes(extraRoutes = ...)` and the new `subtitleVariantMaster` helper in `PlaybackControllerRoutes.kt`; `MainDispatcherExtension` and `FakeResourceProvider` in the unit test.

## Bugs

None. No failing-first test; the implementer need not run a red check.

## Manual checks

None; the brief rules out device verification (production sources unchanged). Do not drive the box or the live API.

## perf track

Off.

## UNTESTABLE

None. AC-5 doc content is checked by reading `docs/testing/player-playback.md`, not by a test.
