LENS: state-path  ROUND: 1  BASE: 0bcd89c7

No findings.

Trace of the two cases through `saveTrackPreferences` (`PlayerVM.kt:1958-1982`) and the restore in `AudioTrackPreferenceResolver.kt`:

1. **API row with no language and no author or type title** (AC-4).
   - The mapper gives `label = UNKNOWN`, `language = ""` and `isFallbackLabel = true`.
   - `saveTrackPreferences` reads the selected row and sets `keepStoredAudio = true`. It writes `getPreferredAudioLang` and `getPreferredAudioLabel` back unchanged, so the stored dubbing is not overwritten.
   - Only the subtitle part of the save is independent of this row, and it is unchanged.

2. **Localized row "Язык не указан · X"** (AC-1 to AC-3).
   - The mapper gives `isFallbackLabel = false` and `language = ""`.
   - `saveTrackPreferences` stores `audioLang = null`, because `takeIf { isNotEmpty }` drops the empty language, and `audioLabel = "Язык не указан · X"`.
   - On restore, `exactLabelMatch` finds this row again among API rows while the UI language is the same.
   - Against Media3 rows, labels like "01. Дубляж (RUS)" do not match it. `languageMatch` and `voiceTypeAndLanguageMatch` return no match for a null language, so the default track applies.
   - Before the change, "Unknown · X" with an empty language behaved the same way against Media3 rows, so this is not a regression.

The only change in behaviour is the one the brief already lists under Risks: the stored label now depends on the UI language at save time, and an old "Unknown · X" preference falls back to the default track once. This is narrow, and I treat it as accepted.

I found no write that a later call or emission undoes. The `isFallbackLabel` guard follows the same pattern as the existing Media3 "Track N" rows. The mapper change is a pure function, so there is no ordering, double-press or focus concern.
