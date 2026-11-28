# Gate - subtitle-switch-text-disable - round 1

**Decision:** PASS
**Head:** bd057686   **Base:** 7a9fd580

## Done as asked

Option B (state.md Decisions, Q1): tests and docs only. `git diff --stat 7a9fd580...HEAD -- app/src/main` is empty. The diff covers plan tasks 1-4 (`PlaybackControllerRoutes.kt` helpers, two instrumented tests, two unit tests, `docs/testing/player-playback.md` section) plus `textTrackLanguages()` in `PlaybackControllerReads.kt`, the `@Suppress("LargeClass")` and the evidence file. The diff adds no fixture binary and does not change `SHA256SUMS`. Nothing it adds falls outside the brief.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PlaybackControllerNetworkTracksTest.qualitySwitch_missingSubtitleLanguage_staysOffAtReusedCoordinates` | `itest-fix1-1.xml`, `itest-fix1-2.xml` at bd057686: 16/0 with the testcase present; the negatives are held over a 1 s window; the positional-selector mutation fails it (implementation.md) | PASS |
| AC-2 | `PlaybackControllerNetworkTracksTest.qualitySwitch_sameManifestSubtitle_staysSelected` | same XMLs; the test makes no second `selectSubtitle` call before the English track is selected | PASS |
| AC-3 | `PlayerVMTrackSelectionTest.selectQuality_withSubtitleRowMissingFromNewReport_sendsOffRow` | judge's `check.full` run at bd057686, exit 0 | PASS |
| AC-4 | `PlayerVMTrackSelectionTest.selectQuality_withIdenticalRows_keepsSubtitleWithoutResending` | same run | PASS |
| AC-5 | itest under lease + `check.full` + docs section | fix-round XMLs; judge's `check.full` exit 0 (BUILD SUCCESSFUL, both tooling scripts PASS); `player-playback.md` names all four final test names | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| No production change | review-blind-1 F1 (major) | rejected, accepted | Q1 option B in state.md; the blind lens had no brief |
| Unit test checks a mocked call | review-blind-1 F2 | rejected, accepted | AC-1 instrumented test covers the controller side |
| Negatives asserted with no settle window | review-blind-1 F3, review-tests-1 | fixed | bd057686 (`NEGATIVE_SETTLE_MS` loop) |
| AC-2 test does not isolate pending-row reselection | review-codex-1, review-tests-1 | fixed by rename (accepted minor) | bd057686: test renamed, comment and docs narrowed |
| Helper placement, LargeClass suppress | review-blind-1 F4 (nit) | rejected, accepted | repo precedent: other test classes carry the same suppress |
| `test-design.md` still names the AC-2 test `reselectsByIdentity` | judge | accepted minor | run file only; the code, docs and evidence carry the final name |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | N/A: test and docs only |
| Reliability | Instrumented tests wait with `awaitCondition` and a bounded poll; 5 emulator runs green (`itest-*.xml`) |
| Maintainability | Helpers reuse `commonHlsRoutes(extraRoutes)`; JUnit 5 conventions kept; no dead code |
| Security / privacy | Synthetic fixtures only; evidence audit passed (qa-report.md); no live API |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
