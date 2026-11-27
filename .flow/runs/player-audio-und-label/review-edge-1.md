LENS: edge  ROUND: 1  BASE: cb7fcd45

No findings.

### Unproven
- /home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/SubtitleTrackMerger.kt:105 API subtitle rows tagged "und": the change leaves `orderingLanguage` alone (Q3), so such a row would land in a language group "und". A manifest row with the same subtitle now has language "" and lands in the unknown-language group, so the two would no longer be deduplicated. This becomes real only if KinoPub API subtitles actually carry language "und". The API mapper path is outside this run (Q2).
- /home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerTextTracks.kt:68-71 An HLS subtitle format and its rendition that are both "und", carry the same label and have no matching id are no longer linked through the label+language matcher, because `sameSubtitleLanguage` now returns false for "und". This needs a manifest where the player's text format id differs from the rendition's format id while both declare LANGUAGE="und". None is known.
- /home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolver.kt:201,210 A stored audio preference of ("und","und") written by earlier builds no longer matches any row, so playback falls back to the default track. The spec accepts this risk. It matters only for a multi-audio item whose default track is not the one the viewer picked.
