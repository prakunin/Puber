# Gate - player-audio-und-label - round 1

**Decision:** CONCERNS
**Head:** 070c644c   **Base:** cb7fcd45

## Done as asked

The diff (`git -C /home/dev/orca/workspaces/Puber/player-audio-und-label diff cb7fcd45...HEAD`) does what brief.md#scope option B asks. It adds `knownLanguageOrNull` (`model/SubtitleLanguage.kt:14`) and uses it in `buildAudioRows`, `buildSubtitleRow`, `toPlayerTextTracks`, `SubtitleLabeler` and `sameSubtitleLanguage`. Two changes go beyond the brief's three scope items, and both come from triage-1 #1/#2 (d1ed06ad): the `SubtitleTrackSelector` filter, and `setPreferredTextLanguage` at `vm/PlaybackController.kt:906`. The diff leaves `PlayerUIMapper` "Unknown" (Q2) and `SubtitleTrackMerger.orderingLanguage` (Q3) unchanged.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString`, `SubtitleTrackIdentityTest.knownLanguageOrNull_*` | qa-report.md round 1 and round 2 (check.full at 2d52a21e, exit 0); tests are in the diff | PASS |
| AC-2 | `PlayerAudioTracksTest.buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage` | same | PASS |
| AC-3 | `PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_*`, `selectSubtitle_whileAudioRowIsUndetermined_*` | same | PASS |
| AC-4 | `SubtitleLabelerTest.apply_undeterminedLanguageWithoutLabel_readsAsUnknownOrdinal`, `PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage` (asserts normalized "ru"; triage-1 #4) | same, plus the box run in evidence Round 2 | PASS |
| AC-5 | `SubtitleTrackIdentityTest.sameSubtitleLanguage_rejectsUndetermined`, `SubtitleTrackSelectorTest.select_doesNotMatchByLanguage_whenBothAreUndetermined` | same | PASS |
| AC-6 | `PlaybackControllerNetworkTracksTest.progressiveUndeterminedAudio_reportsLocalizedFallbackRow` | itest-fix1.xml: 13 tests, 0 failures, 0 errors, 0 skipped, at d1ed06ad | PASS |
| AC-7 | manual check, box 192.168.1.110:5555 | `docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md`: round 1 (audio), round 1b (subtitle rows) and round 2 (subtitle pick after the fix) | PASS |
| AC-8 | check.full | qa-report.md round 2 at 2d52a21e. Commit 070c644c changes only the evidence file, so qa-report.md is current and check.full was not run again | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| An HLS row that takes its rendition language no longer selects its untagged candidate (major) | codex-1, triage-1 #1 | fixed | d1ed06ad; `select_resolvesManifestRowTakingRenditionLanguage_toItsUntaggedCandidate`; box Round 2 |
| An API row tagged "und" no longer reaches its side-load (minor) | state-path-1 F1, triage-1 #2 | fixed | d1ed06ad; `select_resolvesUndeterminedApiRow_toItsUntaggedSideLoad` |
| The AC-7 subtitle clause was not checked on the box (minor) | tests-1 F1, codex-1 | fixed | evidence rounds 1b and 2 (2d52a21e, 070c644c) |
| AC-4 text says "rus", the test asserts "ru" (nit) | tests-1 F2, triage-1 #4 | rejected, accepted here | Format.Builder normalizes the code; state.md Decisions, readiness note 1 |
| `setPreferredTextLanguage` change at `PlaybackController.kt:906` has no test (minor) | tests-2 F1, triage-2 #1 | accepted as a minor; the stated reason is wrong | see Concerns 1 |
| Codex-2: a shared identity between a tagged and an untagged side-load makes the match ambiguous | codex-2, triage-2 | rejected, accepted here | side-loads are keyed by distinct file names; edge-2 |
| Codex-2: an untagged candidate bypasses the reused-id guard after a refresh | codex-2, triage-2 | rejected, accepted here | `prepare` clears `pendingSubtitleTrack` (`PlaybackController.kt:413`); a match still needs a unique id, url or group+index |
| Antigravity round 2 | review-antigravity-2.md | skipped (quota) | owner rule: skip, do not wait |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One string trim and lowercase per track row and selector candidate; no hot path. |
| Reliability | A stored ("und","und") audio preference from an older build now falls to the default track (accepted risk in brief.md#risks). The fallback at `PlaybackController.kt:906` is narrower, see Concerns 1. |
| Maintainability | Follows AGENTS.md conventions, and `knownLanguageOrNull` is the one helper. The `TextUtils` static stub is copied in `PlayerVMTrackSelectionTest.kt` (tests-1 Unproven), which is too small to fix. |
| Security / privacy | No secrets or account data in the code or evidence; `mobile-evidence-audit.sh` passed (qa-report.md). The screenshots show only player UI and film frames. |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. `PlaybackController.kt:906` now passes "" instead of "und" to `setPreferredTextLanguage`. Triage-2 accepted this as behaviour-neutral because "a preferred 'und' selects nothing". Media3 1.11.0 contradicts that: `DefaultTrackSelector.getFormatLanguageScore` returns 4 when the preferred language equals `format.language`, and it checks this before it normalizes "und" (javap of `media3-exoplayer-1.11.0.aar`, bytecode offsets 0-19). So for a row tagged "und" whose `SubtitleTrackSelector.select` returns null, the base build fell back to an "und"-tagged track, and the head build shows no subtitles. When the selector does find the track, the exact override applies and this line has no effect. That is the case the tests and the box covered. The remaining case needs two "und" side-loads with the same identity, or a side-load whose id does not match its API url, and no current caller shows either. No test covers the line either way. Options: keep it as is, revert the line to `track.language`, or open a run that adds a test.
2. Antigravity did not review the fix diff d1ed06ad (quota). Codex, edge, state-path and tests did.
