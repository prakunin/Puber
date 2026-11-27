LENS: blind  ROUND: 1  BASE: 0bcd89c7

### F1 [minor] A localized "unknown language" string is saved as the stored audio label
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:22-33`, consumed in `PlayerVM.saveTrackPreferences` (`PlayerVM.kt:1966-1982`)
- Problem: The label now embeds a resource string, "Unknown language" or "Язык не указан". A row with no language but with an author or type title has `isFallbackLabel = false`. `saveTrackPreferences` therefore saves `label` as `audioLabel`, and that label contains the locale-dependent text. Before this change the literal `"Unknown"` was saved.
- Failure scenario:
  1. The user picks an audio track with `lang = null` and `author = "X"`. The stored label is "Язык не указан · X".
  2. The app locale later changes, or the stored label was written by an older build as "Unknown · X". The stored label no longer equals the freshly built one.
  3. If the restore logic matches on the label, the saved audio choice is not restored. I did not read the restore code, so whether it matches by label is unconfirmed.
- Fix: Match or save by language and author, not by the localized label. Alternatively, keep a stable, non-localized key for the stored label.

### Unproven
- `PlayerUIMapper.kt:33` `isFallbackLabel` is true when `language == null && title == null`. The label is then "Unknown language", not a "Track N" placeholder, and `saveTrackPreferences` keeps the stored audio preference for such rows. That looks intended, but the comment on `keepStoredAudio` still says "Track N". Nothing in the diff shows it is wrong.
