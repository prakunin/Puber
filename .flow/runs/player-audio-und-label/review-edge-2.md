LENS: edge  ROUND: 2  BASE: cb7fcd45

No findings.

Triage items #1 and #2 are fixed, and I found no new boundary that is both unhandled and reachable:
- **Item #1 (manifest row against an untagged candidate):** the language filter now lets a candidate with no language through to the identity checks. It still has to match by format id, source identity or group id plus index. The new test `select_resolvesManifestRowTakingRenditionLanguage_toItsUntaggedCandidate` covers this case.
- **Item #2 ("und" API row):** a row with language "und" now gets past the language filter (`SubtitleTrackSelector.kt:58`) and reaches its side-loaded track by file identity. `setPreferredTextLanguage` now gets an empty string for it, not "und" (`PlaybackController.kt:906`). A blank row already sent an empty string before this change.
- **Language-only match:** `matchByLanguage` still requires a known row language and a tagged candidate. An untagged candidate can never be picked on language alone, and `select_doesNotPickUntaggedCandidateByLanguage_forTaggedRow` covers this.
- **Rows left over from the previous video:** `prepare` clears `pendingSubtitleTrack` (`PlaybackController.kt:413`). So a row from the previous video cannot match the new video's untagged candidates by group id, even though the language filter is now looser.
- **Side-loaded tracks:** they take their id and language from the API (`PlaybackMediaItemFactory.kt:65-70`), so the identity match for an "und" API row works with the data the app actually has.

### Unproven
- `/home/dev/orca/workspaces/Puber/player-audio-und-label/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:906` An "und" row whose identity match fails while several "und" side-loads are present. Before this change the preferred language "und" picked one of them, possibly the wrong one. Now nothing is picked and no subtitles show. This becomes real only if an API subtitle can reach the player without its `sub.id`/url, which no current caller shows.
