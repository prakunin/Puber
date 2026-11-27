I found nothing that earns a severity. The change maps "und" (undetermined) and blank languages to "unknown" in the audio rows, the subtitle rows, the player text tracks, the subtitle labels and `sameSubtitleLanguage`. Downstream code already handled an empty language. I checked each new test against the base code, and each one would fail there.

```
LENS: blind  ROUND: 1  BASE: cb7fcd45
No findings.

### Unproven
- app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1966 upgrade from old stored data - builds before this change saved audioLang="und" / audioLabel="und" for unlabelled tracks. Rows now read "Track N" with language "", so `findAudioTrackIndex` returns NO_MATCH, and `keepStoredAudio` keeps that dead "und" pair for good. Real only if restore picked a non-default track before (several "und" tracks where the default is not first). Evidence would be a stored pref in that state.
- app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackSelector.kt:55 and AudioTrackPreferenceResolver.kt:109,169 - an "und" subtitle row now has a blank language, and a blank language counts as a wildcard in the compatibility filters. Before, "und" matched only "und". A saved "rus" subtitle preference can now restore by label match to an undetermined row. Real if the player reports text tracks tagged "und" (an MP4 with embedded subtitles). The new tests build such rows, but nothing shows KinoPub serves them.
- app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:905 - choosing an "und" subtitle row now passes "" to `setPreferredTextLanguage` instead of "und". If `SubtitleTrackSelector.select` finds no target (several "und" tracks with no id, url or group match), no text track gets chosen where "und" used to pick one. Evidence would be a stream with several untagged embedded text tracks that have no format ids.
- app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:14 - `knownLanguageOrNull` returns its input untrimmed (" ru " passes through with its spaces), while its own test treats " und " as blank. This matters only if Media3 or the API ever hands over padded codes.
- app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt:182 - the test reads the first `PlaybackEvent.Tracks` event and calls `.audio.first()`. If the controller ever sends a Tracks event before audio groups exist, this throws `NoSuchElementException` instead of failing with a message. Evidence would be the device test seeing an early empty Tracks event.
```

The production files involved are in `/home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/`: `model/SubtitleLanguage.kt`, `model/SubtitleLabeler.kt`, `vm/PlayerAudioTracks.kt`, `vm/PlayerTextTracks.kt`, `vm/SubtitleTrackSelector.kt`, `vm/AudioTrackPreferenceResolver.kt`, `vm/PlayerVM.kt` and `vm/PlaybackController.kt`.
