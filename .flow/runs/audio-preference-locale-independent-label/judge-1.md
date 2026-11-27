# Gate - audio-preference-locale-independent-label - round 1

**Decision:** PASS
**Head:** 2b5c7b32   **Base:** 6ccf2ebf

## Done as asked

The diff adds the brief's option A: `unknownLanguageTitleMatch`, third in the matcher list (`AudioTrackPreferenceResolver.kt`). It runs only when `knownLanguageOrNull(preferredLang)` is null and compares the title after `AUDIO_LABEL_SEPARATOR` against rows with an empty language. The stored format is unchanged and no other matcher changed. The separator constant moved into `PlayerUIMapper.kt` with no behaviour change. Nothing in the diff is outside the brief.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `findAudioTrackIndex_unknownLanguageLabelStoredUnderEnglish_matchesRussianRow` | red on base (-1), `implementation.md#Red run`; green in check.full at 2b5c7b32 | PASS |
| AC-2 | `findAudioTrackIndex_unknownLanguageLabelStoredUnderRussian_matchesEnglishRow` | red on base, green at 2b5c7b32 | PASS |
| AC-3 | `findAudioTrackIndex_legacyUnknownLabelWithNullLang_matchesRow`, `findAudioTrackIndex_legacyUndLabelWithUndLang_matchesRow` | red on base, green at 2b5c7b32 | PASS |
| AC-4 | `findAudioTrackIndex_knownStoredLanguage_titleMatchDoesNotApply` | guard, green at 2b5c7b32 | PASS |
| AC-5 | `findAudioTrackIndex_noSeparator_noMatch`, `findAudioTrackIndex_emptyTitle_noMatch`, `findAudioTrackIndex_titleAbsentFromEmptyLanguageRows_noMatch` | guards, green at 2b5c7b32 | PASS |
| AC-6 | four existing-matcher guards; check.full; M1 on the box | check.full exit 0 at 2b5c7b32 (judge run); `docs/superpowers/evidence/2026-11-27-audio-preference-locale-independent-label-device.md` (APK at 7905b9d9; 2b5c7b32 is a refactor with no behaviour change) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Null stored language with a known-language label gets title-matched (minor) | blind F1 | rejected, accepted | `PlayerVM.saveTrackPreferences` (PlayerVM.kt:1965-1972) saves `language.takeIf { isNotEmpty() }`, so a null language comes only from an empty-language row or a kept stored value |
| Separator duplicated in mapper and resolver (nit) | blind F2 | fixed | 2b5c7b32 (`AUDIO_LABEL_SEPARATOR`) |
| Manifest label containing " · " could be title-matched (nit) | blind F3, codex P2 | rejected, accepted | the matcher only picks among rows with no language, and only after the exact and normalized matchers fail; before the change those rows fell through to the default track |
| `exactLabel_wins` did not isolate exact matching (minor) | tests F1 | fixed | 2b5c7b32: rows `01. X`, `02. X`, stored `02. X` -> 1 |

Antigravity was skipped because its quota ran out (`triage-1.md`), as the owner's standing rule allows.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One extra linear pass over the audio rows, once per restore |
| Reliability | Null or empty labels and titles return `NO_MATCH`; the matcher is stateless, so a re-run restore gives the same result (`review-state-path-1.md`) |
| Maintainability | Follows the existing matcher pattern; the shared constant removes the duplicate; no dead code left |
| Security / privacy | No secrets; the judge re-ran `tools/mobile-evidence-audit.sh` on the evidence directory: passed |

## Manual checks for the owner

N/A: M1 was run on the box by qa.

## Must fix

none

## Concerns for the owner

none
