# Review triage - round 1

Run: player-audio-und-label   Base: cb7fcd45   Reviewed head: 3fab644b

Sources: review-blind-1.md (no findings), review-edge-1.md (no findings), review-tests-1.md (F1 minor, F2 nit), review-codex-1.md (ran: review, adversarial; 1 major, 1 minor), review-antigravity-1.md (ran, clean), review-state-path-1.md (F1 minor).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex | major | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerTextTracks.kt:47,88`; `SubtitleTrackSelector.kt:55-56` | an HLS subtitle row whose player format is "und" takes the rendition language ("ru"), but its player candidate now has language null, so `isSemanticallyCompatibleWith` drops it before the identity matchers; picking the row selects another track or none. On base both sides were "und" and matched | read PlayerTextTracks.kt:47 (row language from rendition), :88 (candidate `knownLanguageOrNull`), SubtitleTrackSelector.kt:55-56 (non-blank row language requires non-null equal candidate language) | fix | candidate and row must agree, or identity matching must accept a candidate with no language; test: select the generated row, resolves to the original track |
| 2 | state-path F1 | minor | `PlayerTextTracks.kt:88`; `SubtitleTrackSelector.kt:55-56`; `PlaybackController.kt:905` | an API subtitle row with lang "und" (progressive, API_ROWS) no longer reaches its side-loaded track: candidate language null, row "und" non-blank, filter empty; `setPreferredTextLanguage("und")` selects nothing | same reading as #1; PlayerUIMapper.kt:45 keeps `sub.lang` | fix | treat an undetermined row language like a blank one in the selector and in `setPreferredTextLanguage`; selector test with an "und" API row and its side-load candidate |
| 3 | tests F1, codex | minor | `docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md` (step 3) | AC-7 subtitle clause not checked on the box (movie had no subtitles) | read qa-report.md round 1 | fix | qa round 1b on an item with 11 subtitle rows (already dispatched); repeated after the fix |
| 4 | tests F2 | nit | spec AC-4 | AC-4 says "rus", test asserts Media3-normalized "ru" | read the test and readiness-1 note 1 | reject | decision recorded in state.md (conductor, readiness note 1): "rus" is the manifest code, the normalized "ru" satisfies it; AC text belongs to the analyst and does not change scope |

## Rejected codex claims

- none

## Next

- fix round 1: #1, #2 by the implementer; then edge, codex, antigravity, tests, state-path on the incremental diff; qa on AC-4, AC-5, AC-7 (subtitles on the box); unproven lines: 9 (blind 5, edge 3, tests 1)
