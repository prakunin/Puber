LENS: tests  ROUND: 1  BASE: cb7fcd45
### F1 [minor] AC-7's subtitle clause was never checked on the device, and the evidence file does not say how that is covered
- Where: /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md (step 3)
- Problem: AC-7 promises that "subtitle rows keep their language names". Step 3 of the evidence is marked NOT CHECKED because the movie had no subtitle tracks. The audio half passed. Several unit tests do cover this: `SubtitleTrackIdentityTest.knownLanguageOrNull_keepsRealLanguagesVerbatim`, the existing `SubtitleLabelerTest` tests for unresolvable codes and `SubtitleTrackSelectorTest.select_matchesByLanguage_onlyWhenUnambiguous`. The evidence file names none of them and never calls the device check partial.
- Failure scenario: A real HLS item has named subtitle renditions and `knownLanguageOrNull` or the labeler drops a real code. Then the device shows "Субтитры N" instead of "Русский". Gate 2 reads AC-7 as passed even though nobody checked that path on the box.
- Fix: Either repeat manual check step 3 on an item that has subtitles (at human pace), or mark AC-7's subtitle clause as covered only by the unit guards named above, in the evidence file and the plan's Implementation record, so gate 2 sees the gap.

### F2 [nit] AC-4 still says "rus", but the test asserts "ru"
- Where: /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/specs/2026-11-27-player-audio-und-label.md (AC-4); app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTextTracksTest.kt:135
- Problem: `Format.Builder` normalises "rus" to "ru", so the test is right. The readiness check (Notes, item 1) asked for the AC text to be changed before the gate, and it has not been.
- Failure scenario: Someone checking traceability at gate 2 sees the AC and its test disagree.
- Fix: Change AC-4's second clause to "the row's language is the rendition's language (normalised, e.g. "ru")".

Suite state: I ran `check.full` at HEAD 3fab644b and it exited 0. Gradle took the unit, detekt and lint tasks from its up-to-date cache, and their recorded results come from a run at this HEAD. The 7 suites I checked report 0 failures and 0 skipped, and all 14 new or related test cases are registered, including all 8 new P0 tests. The baseline-profile tooling script passed every case.

The red run is recorded for every P0 `red` row and for both P1 `red` rows, including the instrumented one (itest-1.xml). The `knownLanguageOrNull_*` tests are rightly marked `guard` or P1, since they cannot compile on base. Each P0 red test would fail on base, judged against the base code:
- `buildAudioRows` labels the row with the raw "und".
- `PlayerVM` then saves ("und", "und") over the stored preference.
- `sameSubtitleLanguage` treats "und" and "und" as the same language.
- The labeler shows "UND".

### Unproven
- app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTrackSelectionTest.kt:146-161: the `TextUtils` static stub is copied from `PlayerAudioTracksTest.kt:23-32` instead of shared. It would only matter if a third test class needed the same stub.
