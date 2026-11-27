LENS: blind  ROUND: 1  BASE: 874b049f

### F1 [minor] The "shows no subtitles" claim for an empty preferred language is unproven, and the change is a pure extraction
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:19-24`, `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:907`
- Problem: `preferredTextLanguage` does exactly what the old inline `knownLanguageOrNull(track.language).orEmpty()` did, so runtime behaviour is unchanged. The new KDoc and comment say that `""` makes Media3 prefer no track, so an undetermined row shows nothing. I could not verify that against the Media3 source. `DefaultTrackSelector` normalizes `""` and `"und"` to null before scoring. As I recall, a null preferred language can still give a score of 1 to text tracks whose own language is undetermined or null. If so, an "und" row with no override would still auto-select an und-tagged track. That is the arbitrary "und" track the comment says it avoids.
- Failure scenario: The API row has language "und" and `subtitleTrackSelector.select` returns null, so no override is set. The manifest has a text track tagged "und". Media3 auto-selects it, which contradicts the new comment.
- Fix: Confirm the Media3 behaviour. If I am right, either set the text track type disabled when the row is undetermined and unmatched, or drop the claim from the comments.

### F2 [minor] The tests cover only the helper, not the controller wiring
- Where: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackIdentityTest.kt:92-105`
- Problem: Both new tests call `preferredTextLanguage` directly. Nothing checks that `PlaybackController` passes its result to `setPreferredTextLanguage`, or that an undetermined row ends up with nothing selected. Reverting the call site to a different expression would not fail any test.
- Failure scenario: A future edit to `PlaybackController.kt:907` changes the language handling. The suite stays green.
- Fix: Add a controller-level or fake-player test that asserts the `TrackSelectionParameters` after selecting an "und" row.
