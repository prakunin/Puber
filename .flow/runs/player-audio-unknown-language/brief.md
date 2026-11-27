# Brief: player-audio-unknown-language

Kind: bug   Size: S   Tracks: none   Base: 0bcd89c7
Research: `research.md` (option A).

## Problem

An audio row built from the API `Audio` list with no `lang` starts with the literal English "Unknown", so a Russian UI shows "Unknown · <author>". Every other fallback in the mapper resolves through `ResourceProvider`.

## Root cause (confirmed, by reading the code)

`app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:24`: `append(audio.lang ?: "Unknown")`. Introduced in 88cdf071 and left by 0bf26ee5 (research.md: Related work). A blank or "und" `lang` is shown as-is (line 24) and stored as the language (line 31), unlike the Media3 path, which uses `knownLanguageOrNull` (`vm/PlayerAudioTracks.kt:10`).

These rows are what the picker shows until the player reports its own audio groups, or when it reports none (`vm/PlayerVM.kt:576`).

## Acceptance criteria

Unit tests use `PlayerUIMapper(FakeResourceProvider())`; "UNKNOWN" below means `resources.getString(R.string.player_audio_language_unknown)`.

| ID | Given | When | Then |
|----|-------|------|------|
| AC-1 | `Audio(lang = null, author = AudioAuthor(title = "X"))` | `mapAudioTracks` | label = "UNKNOWN · X", language = "", `isFallbackLabel` = false |
| AC-2 | `lang` = "" or "und" (with `author` "X") | `mapAudioTracks` | the same row as AC-1 |
| AC-3 | `lang = null`, no `author`, `type = AudioType(title = "Дубляж")` | `mapAudioTracks` | label = "UNKNOWN · Дубляж", `isFallbackLabel` = false |
| AC-4 | `lang = null`, no `author`, no `type` (or both titles null) | `mapAudioTracks` | label = "UNKNOWN", `isFallbackLabel` = true |
| AC-5 | `lang = "rus"`, `author` "X" | `mapAudioTracks` | label = "rus · X", language = "rus", `isFallbackLabel` = false (unchanged) |
| AC-6 | the source tree | `grep -n '"Unknown"' PlayerUIMapper.kt` | no match; `player_audio_language_unknown` exists in `res/values/player.xml` ("Язык не указан") and `res/values-en/strings.xml` ("Unknown language"); check.full passes |
| AC-7 | the dev APK on the box 192.168.1.110:5555, RU UI, an ordinary item with several dubbings | open the player and the audio picker with the D-pad | rows read as before, playback and track switching work, no English "Unknown" (regression check; a row with no API language is not expected to be reachable without probing) |

## Plan

1. Strings: add `player_audio_language_unknown` next to `player_audio_track_fallback` in `app/src/main/res/values/player.xml:48` ("Язык не указан") and `app/src/main/res/values-en/strings.xml:302` ("Unknown language").
2. Mapper: in `PlayerUIMapper.mapAudioTracks` (`PlayerUIMapper.kt:21-34`) compute `val language = knownLanguageOrNull(audio.lang)` (`model/SubtitleLanguage.kt:14`) and `val title = audio.author?.title ?: audio.type?.title`; label = `language ?: resources.getString(R.string.player_audio_language_unknown)` plus `" · $title"` when `title != null`; `language = language.orEmpty()`; `isFallbackLabel = language == null && title == null`.
3. Tests: add `mapAudioTracks` cases for AC-1..AC-5 to `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt`.
4. Device: AC-7 on the box with the worktree's own dev APK (install-preserve, `com.kino.puber.stage` only).

Device verification: yes, AC-7 only (player UI strings feed ViewModel state).

## Risks

- Saved preferences: a no-language API row was stored with label "Unknown · X" and an empty lang (`PlayerVM.kt:1955-1982`). After the change it no longer matches in `AudioTrackPreferenceResolver.kt:176-191`, so such a title falls back to the player's default track once. Narrow, because API rows only last until Media3 reports audio.
- The stored label now depends on the UI language at save time for no-language rows; AC-4 keeps a row with nothing known out of the stored preference.

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Wording: localized prefix "Язык не указан · X" (A) or drop the prefix and use "Track N" when nothing is known (B)? | A: ru "Язык не указан", en "Unknown language" | no |
| Q2 | Also route `vm/PlaybackController.kt:873-875` through `ResourceProvider`? | No: separate idea run, it needs DI and a device language-switch check | no |
| Q3 | Mark a row with neither language nor author/type as `isFallbackLabel` (AC-4)? | Yes, the same rule as `buildAudioRows` | yes |
