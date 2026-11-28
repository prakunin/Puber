LENS: blind  ROUND: 1  BASE: 7a9fd580

### F1 [major] The diff has no production change, so nothing here implements a fix
- Where: whole diff 7a9fd580..4e4369e2 (`git diff --stat -- app` lists 4 test or test-helper files, 176 insertions, nothing under `app/src/main`)
- Problem: the run slug is `subtitle-switch-text-disable`, which suggests a behavioural fix when the subtitle language is missing after a quality switch. The branch only adds tests and helpers. Either the behaviour already worked and these are guard tests, or the production fix is missing or sits in an earlier commit.
- Failure scenario: if a bug existed, such as the text track staying enabled on reused coordinates and rendering the wrong language, it is still present, and the new tests would have been red on base. If no bug existed, the tests cannot fail on base and prove nothing about a change.
- Fix: confirm the new tests were recorded red on base. If they are green on base, mark them as guards and drop the claim that this branch fixes anything. If red, find the missing production hunk.

### F2 [minor] The unit test asserts a mocked call, not the real behaviour
- Where: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt`, `selectQuality_withSubtitleRowMissingFromNewReport_sendsOffRow`
- Problem: the test checks `verify(exactly = 1) { playbackController.selectSubtitle(match { it.isOff }) }` on a mocked `playbackController`. It shows the ViewModel sends an off row. It does not show the player actually disables the text track.
- Failure scenario: the controller ignores or mishandles an off row, and this test stays green.
- Fix: this is acceptable only if the instrumented test `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` runs in the suite. It is an `androidTestInstrumentation` test, so confirm it is registered and actually run.

### F3 [minor] Weak negative assertions in the instrumented test
- Where: `PlaybackControllerNetworkTracksTest.kt`, `qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates`
- Problem: `assertFalse(selectedTextTrack())` is read right after the "es exposed and READY" condition, and `requestJournal.entries.none { it.path == "/media/hls/sub_es.vtt" }` is read at the same moment. A late selection or fetch would be missed. The negative checks are time-sensitive and have no settle wait.
- Failure scenario: a regression that selects the Spanish track a few hundred milliseconds after READY still passes.
- Fix: add a short bounded wait, or poll for a stable state, before asserting the negatives.

### F4 [nit] Helper placed between test methods
- Where: `PlaybackControllerNetworkTracksTest.kt`, `manifestEnglishRow()`
- Problem: it is declared mid-class, before the next `@Test`. `@Suppress("LargeClass")` was added to `PlayerVMTrackSelectionTest`, which hides a growing file rather than splitting it.
- Fix: optional.

### Unproven
- `PlaybackControllerReads.kt` `textTrackLanguages` uses `getTrackFormat(0)` - it would throw on an empty track group, and there is no evidence that happens.
