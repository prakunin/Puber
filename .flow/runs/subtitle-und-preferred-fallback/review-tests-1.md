LENS: tests  ROUND: 1  BASE: 874b049f

### F1 [minor] The new tests pin a helper, not the wiring or the behaviour the change is about
- Where: /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback/app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentityTest.kt:92-105
- Problem: The bug is that an unmatched "und" row shows no subtitles. The two tests only check the helper's return value for a language string. They cannot fail if `PlaybackController.kt:907` goes back to `track.language` or to an inline expression, because the helper would still pass. The test design records this as R3 and AC-3 ("UNTESTABLE", covered by code read). The implementation is correct today. This is a regression-guard gap that was accepted knowingly, so it is minor.
- Failure scenario: A later edit, for example the `playback-controller-split` run touching the same file, restores `setPreferredTextLanguage(track.language)`. All unit tests stay green and an "und" row falls back to an arbitrary "und" track again.
- Fix: Optional. Pull the `TrackSelectionParameters` build into a pure function that takes `track.language` and the selector target, and assert its `preferredTextLanguages` in a unit test. Otherwise keep the recorded acceptance of R3.

### F2 [minor] The device check never reaches the changed branch
- Where: /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback/docs/superpowers/evidence/2026-11-27-subtitle-und-preferred-fallback-device.md (the "Not exercised" paragraph)
- Problem: AC-4 is marked PASS, but the evidence says no "und" row exists in the tested title. The branch where an "und" row finds no selector match was not exercised. The M1 step "pick a language row again" was also skipped. The device run therefore shows no regression on the neighbouring path and nothing about the fix itself. Because the new tests only check the value passed to Media3 (R4), the claim "an unmatched und row shows nothing" has no test. It rests on the bytecode reading in research.md.
- Failure scenario: Media3 treats "" differently from the bytecode reading, for example by picking a default or forced text track. Neither a test nor the device run would show it.
- Fix: Report AC-4 as partial in the qa report, or run M1 once on a title that has an "und" row if one is known. State the Media3 outcome as unverified.

Checks that came out fine:
- Both new tests would fail on base, by compile failure. The implementation record has the red-run entry for each, and the test design marks them as pins.
- `check.full` is recorded as green in the qa report and the implementation record. I did not re-run it.
- Neither test is skipped, and neither depends on time or order.
- The assertions are meaningful. They loop over the inputs and include the input in the failure message.
- No assertions were removed from existing tests.
- The only `setPreferredTextLanguage` in `app/src/main` is at `PlaybackController.kt:907` and uses the helper.

### Unproven
- /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:907 Media3 behaviour for a preferred language of "" with an "und" track present - a device or instrumented run on a title with an "und" row would make it real.
