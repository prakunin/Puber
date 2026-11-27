# Review triage - round 1

Run: player-audio-unknown-language   Base: 0bcd89c7   Reviewed head: fba1d336

Sources: review-blind-1.md (F1 minor), review-tests-1.md (F1 minor, F2 nit), review-codex-1.md (ran: review; 1 minor), review-state-path-1.md (no findings). Antigravity: skipped, quota exhausted (resets in about 136h; owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, codex | minor | `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:22-33`; `PlayerVM.kt:1966-1982` | the stored audio label is now a localized string; old saves ("Unknown · X", "und · X") and saves under the other locale stop matching, so the default track plays once | state-path-1 traced saveTrackPreferences and the resolver: audioLang null, label-only match; the brief lists the one-time miss under Risks | defer | new idea run `audio-preference-locale-independent-label` (locale-independent identity for API audio rows); the one-time miss stays an accepted brief risk here |
| 2 | tests F1 | minor | `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt` | no test keeps `isFallbackLabel` false for a known language with no title; a mutated rule `title == null` passes every test | read the tests: only the AC-4 case has both null, the guard has a title | fix | add `Audio(id = 1, lang = "rus")` expecting label "rus", language "rus", isFallbackLabel false |
| 3 | tests F2 | nit | same file | "und" variants (" ", "UND", "und-Latn") not exercised at mapper level | read the test | fix | cheap: add " " and "und-Latn" to the list |

## Rejected codex claims

- Blank author/type title leaves a dangling " · " and a wrong isFallbackLabel - unproven, no blank titles seen from the API, same as base.

## Next

- fix round 1: #2, #3 (tests only: those tests and check.fast); size S, no blocker or major, no second review round; unproven lines: 2 (blind 1, tests 1)
