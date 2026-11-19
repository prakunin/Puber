# Gate - trailer-video-hidden-regression - round 1

**Decision:** CONCERNS
**Head:** 2578a702   **Base:** 3ec85287

## Done as asked

Fix T as designed (spec `## Design`): `TrailerPreviewPlayer.kt` adds `surfaceNeedsRedraw`, one stateless `surfaceRedrawOnSizeChange` listener attached to `videoSurfaceView` in `factory` and removed in a new `onRelease`, `post { invalidate() }`, and the KDoc sentence. New `TrailerSurfaceRedrawTest.kt` (5 tests). No other production file; `VideoItemGridDetails.kt`, `SkeletonAsyncImage.kt`, option B and the version bump are untouched. `git diff 4c3248cc --stat` after qa: plan and spec only.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | D1 | evidence file D1: 4 trailers after card changes, layer, growing frames, video in screencap | PASS |
| AC-2 | D2 | evidence file D2 (reopened catalogue) | PASS |
| AC-3 | T1 | none, owner's TV | NOT CHECKED |
| AC-4 | D3, T2 | hero series and movie PASS on emulator (D3); favourites N/A per spec Decision 2026-11-19 (`FavoriteScreenContent.kt` has 0 `trailer` matches, re-checked); TV part T2 | NOT CHECKED (TV) |
| AC-5 | D4 (M1-M3, M5), T3, guard unit tests | M1-M3 PASS on emulator; M5 NOT CHECKED (no fitted trailer found); guard: `git diff` touches no existing test | NOT CHECKED (M5, TV) |
| AC-6 | `TrailerSurfaceRedrawTest` | `TEST-...TrailerSurfaceRedrawTest.xml`: tests=5, failures=0; red run recorded in plan Implementation record | PASS |
| AC-7 | check.full | re-run by the judge at 2578a702: BUILD SUCCESSFUL, exit 0 (tests and detekt up to date, lint executed) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Listener wiring has no automated test | review-blind-1 F1, review-tests-1 note | rejected, accepted | No Robolectric (`grep -ci robolectric` = 0 in `libs.versions.toml`, `app/build.gradle.kts`); `connected*AndroidTest` denied by `guard-bash.sh:55`; wiring proven by D1 against diagnosis.md Evidence row 2 |
| AC-4 names favourites, which plays no trailer | review-tests-1 F1, codex adversarial | fixed (record) | 2578a702, spec Decisions row |
| Red run recorded for one of four P0 red tests | review-tests-1 F2 | fixed | e8eb1b3e, plan Implementation record |
| AC-3 / M5 / M3 baseline untested | codex adversarial | rejected as code finding, accepted | carried to Manual checks and Concerns below |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One posted message and one draw per non-zero size change; predicate excludes unchanged and zero sizes, so no invalidate loop. No recomposition impact: top-level val, non-capturing `onRelease`. |
| Reliability | Null `videoSurfaceView` skips the fix silently; only possible with a non-default surface type, none in `app/src/main`. Posted runnable on a detached view is a no-op. |
| Maintainability | Follows AGENTS.md; KDoc explains why the listener exists; no dead code or duplicated helper. |
| Security / privacy | No secrets or URLs in diff or evidence; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` -> `evidence_audit_status=passed`. |
| TV | No focusable view or modifier added; `useController = false` kept. Focus and BACK on the hero pass in D3. |

## Manual checks for the owner

Install the worktree's dev APK built by the conductor on the TV.

| # | Steps | Pass |
|---|-------|------|
| T1 (AC-3) | Catalogue: let a trailer play, then move to another card with a trailer and wait; repeat at least three times. | Every trailer shows video; the poster fades out over it in about 300 ms. Fail: audio with the poster fully visible -> fallback B (spec Q2). |
| T2 (AC-4) | Open a details screen with a trailer (series and movie). Skip favourites. | Same as T1. |
| T3 (AC-5) | In the catalogue watch a trailer start, let one play to its end, and change cards fast. | No skeleton or black flash, poster back at once at the end, smooth crossfades; note whether one distorted video frame shows just before the poster returns. |
| M5 (AC-5) | If any trailer shows letterboxed (fitted) in the panel, watch it. | No poster pixels around the fitted video. |

## Must fix

none

## Concerns for the owner

1. The fix is proven on the emulator only; the vendor-HWUI risk on the TV (spec Risks: rollout) stays open until T1.
2. Two emulator observations have no base comparison and could have been compared here: the skewed video frame at trailer end (evidence D4 M3) and card 1 playing no trailer on the first open after install (evidence D1). Neither touches the changed code path (the listener only invalidates), but both are unexplained.
