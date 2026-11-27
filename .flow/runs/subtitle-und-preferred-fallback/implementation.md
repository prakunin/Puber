# Implementation record: subtitle-und-preferred-fallback

## Commits

- ce8deff2 Pin the preferred subtitle language of an undetermined row to empty through a tested helper

## Red run

- red: preferredTextLanguage_isEmpty_forUndeterminedAndBlank - compile failure on base: "Unresolved reference 'preferredTextLanguage'" (SubtitleTrackIdentityTest.kt:95); the helper does not exist on base, so no stub was needed to show red. Value passed to Media3 was already "" on base: this is a pin.
- red: preferredTextLanguage_keepsKnownLanguage - same compile failure (:102).

## Check

- check.fast: BUILD SUCCESSFUL.
- check.full: BUILD SUCCESSFUL (2m 43s), baseline-profile tooling tests all PASS.

## Deviations

None. Files: model/SubtitleLanguage.kt, vm/PlaybackController.kt (call, import, comment), SubtitleTrackIdentityTest.kt.

## For reviewers

- AC-3: the only setPreferredTextLanguage in app/src/main is PlaybackController.kt:906 and receives preferredTextLanguage(track.language).
- AC-4 (device smoke on the box) not run by the implementer; left to qa.

## Fix round 1

- #1: KDoc of preferredTextLanguage and the comment at the setPreferredTextLanguage call reworded (empty language prefers no language; Media3 default or forced selection may still apply). Comment-only; check.fast run.
