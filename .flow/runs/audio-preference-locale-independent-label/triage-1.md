# Review triage - round 1

Run: audio-preference-locale-independent-label   Base: 6ccf2ebf   Reviewed head: aa440706

Sources: review-blind-1.md (F1 minor, F2, F3 nit), review-tests-1.md (F1 minor), review-codex-1.md (ran: review; clean), review-state-path-1.md (no findings). Antigravity: skipped, quota exhausted (owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | minor | `AudioTrackPreferenceResolver.kt:205-208` | a null stored language with a known-language label ("Русский · X") would be title-matched to an empty-language row | `PlayerVM.saveTrackPreferences` stores `audioLang = row.language.takeIf { isNotEmpty }` (state-path-1 trace), so a null language is only ever saved from an empty-language row; a known-language row saves its language and skips the matcher (AC-4) | reject | unreachable from app-written preferences |
| 2 | blind F2 | nit | `AudioTrackPreferenceResolver.kt:246`, `PlayerUIMapper.kt:27` | the " · " separator is duplicated; a mapper format change would silently break the matcher | read both files | fix | share one constant |
| 3 | blind F3, codex | nit | `PlayerAudioTracks.kt:10-17` | a manifest label containing " · " could be title-matched | no real manifest label with " · " seen; worst case is a pick among tracks that already have no language | reject | unproven |
| 4 | tests F1 | minor | `AudioTrackPreferenceResolverTest.kt` exactLabel_wins | the guard does not isolate exactLabelMatch (normalized also returns 1) | read the fixture | fix | rows [01. X, 02. X], stored "02. X" -> 1 |

## Rejected codex claims

- Title matcher picks an unrelated Media3 track with the same " · " suffix - unproven (see #3).

## Next

- fix round 1: #2, #4 (small refactor + test; check.fast and the touched tests); size S, no blocker or major, no second review round; unproven lines: 1 (tests 1)
