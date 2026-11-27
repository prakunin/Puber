No findings.

I traced restore of a stored audio preference with an unknown language through `findAudioTrackIndex` in `AudioTrackPreferenceResolver.kt`, on the worktree at `aa440706`. I read the code and did not run the tests.

**First track report**
1. `onTracksUpdated` sets `audioTracks` from the reported rows, then calls `restoreAudioPreference()` once, guarded by `tracksRestoredForCurrentMedia`.
2. `restoreAudioPreference()` reads the stored label and language and runs the matchers in order: exact, normalized, the new unknown-language title matcher, voice type plus language, language only.
3. When the stored label is `<placeholder> · X` and the stored language is null, the new matcher compares the title after ` · ` against rows with an empty language. It returns the first match, or NO_MATCH.
4. A hit goes to `applyAudioTrackSelection(idx, persist = false)`, which sets `selectedAudioTrackIndex` and calls `playbackController.selectAudioTrack`. Nothing is saved, so the stored label stays in its old locale and re-matches by title on the next launch.
5. A later explicit pick of an audio or subtitle row calls `saveTrackPreferences()`, which rewrites the stored label from the row's current label. That is correct.
6. Rows with no title have no separator. The matcher returns NO_MATCH, and `isFallbackLabel` makes `saveTrackPreferences()` keep the stored value.
7. A stored non-null known language skips the new matcher, so the voice-type and language matchers behave as before.

**After a quality switch**
- `selectQuality` calls `switchStreamUrl`, which calls `playbackController.switchStream`. It does not reset `tracksRestoredForCurrentMedia`, so there is no second restore and no second write.
- The next `onTracksUpdated` takes the player's reported audio index, so the restored choice carries through. The diff changes nothing in this path.
- `selectBufferPreset`, `toggleFastDns` and `resetMediaSession` do reset the flag. Restore then runs again against the same stored values and gives the same result, because the matcher has no state.

**Not defects, outside the diff**
- If the first report arrives with no audio rows, `tracksRestoredForCurrentMedia` is set anyway and restore is never retried. This is existing behaviour.
- Rows from Media3 are labelled by manifest name, not by the localized placeholder, so the title matcher mostly acts on the API-row window. The evidence file already notes that this window is covered only by unit tests.
