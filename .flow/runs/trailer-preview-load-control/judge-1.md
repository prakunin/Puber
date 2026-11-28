# Gate - trailer-preview-load-control - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** PASS
**Head:** c12e5ea4   **Base:** 8c3117ea

## Done as asked

`git diff 8c3117ea...HEAD`: `app/src/main` changes only `TrailerPreviewPlayer.kt` (params class, `TRAILER_PREVIEW_BUFFER`, `buildTrailerPreviewLoadControl`, `.setLoadControl(...)` inside `remember`), plus the new test and docs. `TrailerOverlay.kt` and `PlaybackController.kt` have no diff; no back buffer, no shared factory, no `app/build.gradle.kts` change.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `TrailerPreviewLoadControlTest.trailerPreviewBuffer_matchesAcceptedValues` | judge check.full at c12e5ea4 exit 0; test XML 2 tests, 0 failures; red on zero stub (plan Implementation record) | PASS |
| AC-2 | `TrailerPreviewLoadControlTest.buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` | same run; red with swapped min/max (plan Implementation record, round 1 fix) | PASS |
| AC-3 | Review checks A, B | diff read: `.setLoadControl(buildTrailerPreviewLoadControl())` between `ExoPlayer.Builder(context)` and `.build()` in `remember`; own `DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)`; check B `--stat` empty | PASS |
| AC-4 | Device manual 1 | evidence `docs/superpowers/evidence/2026-11-28-trailer-preview-load-control-device.md` AC-4 and Round 2: 61.0 s audio vs ~61 s trailer, no player error in pid-filtered log | PASS |
| AC-5 | Device manual 2, 3 | same evidence, AC-5: one AudioTrack at a time, none after Home, no resume on return | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-2 P0 test never seen red | review-tests-1 F1 (major) | fixed | 19187855, plan Implementation record red run; review-tests-2 clean |
| Tests do not check builder slot mapping or wiring | review-blind-1 F1 (minor) | accepted | JVM `DefaultLoadControl` needs a real timeline (plan Deviations); wiring covered by AC-3 review and AC-4/AC-5 on the box |
| AC-4 evidence cannot tell end from player error | review-codex-1 (minor) | fixed | c12e5ea4, evidence Round 2 |
| Dead `assertNotNull` | review-tests-1 F2 (nit) | fixed | 19187855 |
| Wrong commit hash in record | review-tests-1 F3 (nit) | fixed | 19187855 |
| 12 MiB cap may stall a >10 Mbit/s trailer | edge-1, blind-1, codex-1 (unproven) | rejected, accepted | spec Risks 1, approved at gate 1; AC-4 showed no stall |
| Hold shipment for network-interruption comparison | codex-1 adversarial | rejected, accepted | beyond the ACs; AGENTS.md bars probing |
| Red-run line cited as :22, assertion on :21 | review-edge-2 (unproven) | accepted | doc-only, no reproduction impact |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One load control and allocator per player, built inside `remember`; buffer cap lowers preview memory; `bufferForPlaybackMs` 1_000 keeps first-frame timing |
| Reliability | Release and stop paths unchanged (AC-5 on box); durations validated by AC-2 |
| Maintainability | Helper next to `surfaceNeedsRedraw`, `@OptIn` scoped, JUnit 5 test in matching package; check.full (detekt, lint) green |
| Security / privacy | No secrets or account data; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` -> passed |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
