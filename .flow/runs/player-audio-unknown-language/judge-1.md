# Gate - player-audio-unknown-language - round 1

**Decision:** CONCERNS
**Head:** d914810b   **Base:** 0bcd89c7

## Done as asked

The diff matches brief.md Plan 1-3: `PlayerUIMapper.mapAudioTracks` (PlayerUIMapper.kt:21-36) uses `knownLanguageOrNull`, the localized `player_audio_language_unknown`, an author-then-type title and `isFallbackLabel = language == null && title == null`. The string is added to `values/player.xml:49` and `values-en/strings.xml:303`, there are six `mapAudioTracks_*` tests, and the device evidence file is added. `PlaybackController.kt` is untouched, as Q2 requires. Nothing else changes.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `mapAudioTracks_nullLanguageUsesLocalizedUnknownWithAuthor` | green at d914810b (TEST-...PlayerUIMapperTest.xml, 13/13, 16:11:53); red run in implementation.md | PASS |
| AC-2 | `mapAudioTracks_blankOrUndLanguageIsTreatedAsUnknown` ("", " ", "und", "UND", "und-Latn") | same run | PASS |
| AC-3 | `mapAudioTracks_nullLanguageFallsBackToTypeTitle` | same run | PASS |
| AC-4 | `mapAudioTracks_noLanguageAndNoTitlesIsFallbackLabel` | same run | PASS |
| AC-5 | `mapAudioTracks_keepsKnownLanguageAndAuthor`, `mapAudioTracks_knownLanguageWithoutTitleIsNotFallbackLabel` | same run | PASS |
| AC-6 | grep, resources, check.full | grep shows no `"Unknown"` in the mapper; both strings are present; check.full rerun by the judge at d914810b: BUILD SUCCESSFUL, all tooling tests PASS | PASS |
| AC-7 | Manual check 1 (box) | evidence/2026-11-27-player-audio-unknown-language-device.md: rows, focus, playback and no "Unknown" are shown. No track was selected ("No selection was made"), so test-design step 3 and the AC's "track switching work" have no evidence | NOT VERIFIED (switching part) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| The stored audio label is localized, so old saves and saves under the other locale miss once | blind F1, codex (minor) | deferred | run `.flow/runs/audio-preference-locale-independent-label` (stage idea, source set); also listed in brief Risks |
| No test keeps `isFallbackLabel` false for a known language without a title | tests F1 (minor) | fixed | d914810b `mapAudioTracks_knownLanguageWithoutTitleIsNotFallbackLabel` |
| The "und" variants are not exercised | tests F2 (nit) | fixed | d914810b, AC-2 list |
| A blank author or type title leaves a dangling " · " | codex (rejected) | rejected, accepted | the base mapper (`?.let` on the title) behaved the same; no blank titles are known from the API |
| The `keepStoredAudio` comment still says only "Track N" | blind (unproven) | accepted minor | PlayerVM.kt:1965: a stale comment now that an "Unknown language" row is also a fallback; no behaviour impact |

Antigravity was skipped (quota exhausted), as recorded in triage-1.md.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One string lookup per audio row on a short list; no concern |
| Reliability | Null, blank, "und" and title-less rows are all covered by tests; empty list path unchanged |
| Maintainability | Follows the project conventions (`ResourceProvider`, the shared `knownLanguageOrNull`, `FakeResourceProvider` tests); no helper is duplicated. The only leftover is the stale comment at PlayerVM.kt:1965 |
| Security / privacy | No secrets or account data; evidence audit passed (qa-report.md) |

## Manual checks for the owner

1. AC-7 switching: on box 192.168.1.110:5555 with the worktree dev APK (`com.kino.puber.stage`), open an item with several dubbings, start playback, then open gear -> Audio and press OK on another row. Expected: the audio switches, playback continues and the picker closes; no English "Unknown" appears.

## Must fix

none

## Concerns for the owner

- AC-7 says "track switching work", but the device run never selected a track; qa marked it PASS anyway. The change only touches API-built rows, not the Media3 rows used on the box, so the regression risk is low. The step is listed above.
- An accepted risk: a stored no-language audio choice misses once after the update. The follow-up is deferred to `audio-preference-locale-independent-label`.
