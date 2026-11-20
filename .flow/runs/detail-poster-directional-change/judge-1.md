# Gate - detail-poster-directional-change - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** a6c6d77f   **Base:** 01d1d305

## Done as asked

The diff (`git diff 01d1d305...HEAD`) does what the spec asks with option A. It adds `PanelDirection.kt` (the pure rule and a tracker that uses plain vars). The rows report `PanelPosition` inside the existing focus guards (`SectionRow.kt`, `VideoGridUIState.kt`). The screens call `directionTo`/`markShown` (`ContentListScreenContent.kt`, `FavoriteScreenContent.kt`). In `VideoItemGridDetails.kt` the text is held through `Loading` and runs as `AnimatedContent` keyed on `id` with a start-edge clip. The poster gets an outgoing `asPainter` layer and a directional still enter, and Coil crossfade is off for a card change. The stand gets a Motion section (`docs/design/catalogue/README.md`). Nothing excluded is touched: no VM, no `CommonAction`, no details hero (`DetailsScreenContent.kt:189` passes no direction), no `HeroCarousel`, no Compose UI harness.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | M1, R2-1, R3-1, R3-3, R4-1, R4-3 | device evidence rounds 1-4 | PASS |
| AC-2 | M2, R2-2, R3-2, R4-2, R4-3 | device evidence; seam note R2-3 | PASS |
| AC-3 | R2-4..R2-6, R3-4, R3-5, R4-4..R4-6 (real side-by-side Favourites); I1, I2 instrumented | device evidence | PASS |
| AC-4 | `PanelDirectionTest` (18 tests); mutants listed in the plan Implementation record | check.full green (2134 tests, 0 failures, re-read from test-results) | PASS |
| AC-5 (rule, row change, first focus, Back, tab) | `PanelDirectionTest` `directionTo_*`/`markShown_*`; M4, M5, R4-9 | device evidence | PASS |
| AC-5 (refresh republishes same id) | unit `directionTo_isNone_whenRefreshRepublishesSameId`; device needs an account change | qa: NOT CHECKED | NOT CHECKED (manual, section 6) |
| AC-6 | `heldDescription_*` (4); M6 | unit green; device M6 | PASS |
| AC-7 | `VideoDetailsPosterStateTest`, `SkeletonAsyncImageVisibilityTest` unchanged guards, `posterRequest_hasNoTransition_whenCrossfadeOff`; M1, M6, R2-7, R3-6, R4-7 | unit green; device | PASS |
| AC-8 | `cardEnterAlpha_*`, `TrailerSurfaceRedrawTest`; M7, R2-8, R3-7, R4-8 | 3 of 3 or more screenshots per round show video | PASS |
| AC-9 | M8; no direction passed by the hero | M8 PASS (no base pixel comparison) | PASS |
| AC-10 | M9: owner on the TV (state.md decision) | qa: NOT CHECKED | NOT CHECKED (manual, section 6) |
| AC-11 | check.full, run by the judge at a6c6d77f | BUILD SUCCESSFUL; test-results 2134 tests, 0 failures | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Right-edge strip on Left press | ux F1, codex-1 major | fixed (option b) | ba4ee226; seam of the old poster remains, see Concerns |
| Failed last URL holds the old poster 3 s; stale `shownImage` | blind F1, edge-1 F1 | fixed | ba4ee226 (`onError` else branch settles and clears) |
| Favourites side-by-side not measured | ux F2 | fixed (re-run) | evidence R2-4..R2-6 |
| Interrupted layer vanishes in one frame | blind F2 | fixed | ba4ee226 (`PosterTransitionHolder.active`) |
| `outgoingImage` kept after the motion | codex-1 minor | fixed | ba4ee226 (nullable, cleared in `run` finally) |
| Stand Motion section reads as agreed | ux F3 | fixed | 4d9725b9 |
| P0 "red" tests green on stubs | tests-1 F1 | fixed | plan record "Fix round 1": guards marked, mutant runs |
| 3 s text-cap timer wiring untested | tests-1 F2 | accepted minor | same wiring shape as `rememberHeldUrls` |
| Seeded see-through outgoing layer after an interrupt | edge-2 F1, codex-2 | fixed | b27800f6, then 484e5adf |
| Failed-last-URL branch untested | tests-2 F1 | accepted minor | spot-checked: the two-line branch is present in `onError` |
| Interruption filter and draw order untested | tests-2 F2 | accepted minor, partly fixed | `seedFrom_*` tests in 484e5adf |
| Self-comparing assertion | tests-2 F3, tests-3 F3 | fixed | 484e5adf |
| Warm-cache settle cancels the alpha rest | edge-3 F1, tests-3 F1 | fixed | 484e5adf; `run_keepsRestingTheAlpha_whenSettledBeforeRun` |
| Seeding `when` untested | tests-3 F2 | fixed | 484e5adf; `seedFrom_*` (3) |
| "Device evidence predates the fix" | codex-3 | rejected, accepted by the judge | evidence Round 3 built at b27800f6 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Animated values are read only in `graphicsLayer`. Tracker state is not snapshot state. The seed is read under `Snapshot.withoutReadObservation`. `layers` allocates a small list per poster recomposition. The baseline profile is not regenerated (R11, accepted) |
| Reliability | Empty URLs, a failed last URL, the 3 s cap, an interrupt while waiting, an interrupt in motion and a rendered trailer all end or replace the record. Jobs run in `rememberCoroutineScope` and are cancelled on dispose. Unproven: a chain of non-ready records restarts the rest from zero velocity (triage-4) |
| Maintainability | Follows the AGENTS.md conventions: pure content composables and optional callbacks with defaults. `PanelMotion` keeps the motion values in one place. `PosterCrossfade` still serves the first image and fallback, and no dead code is left. `VideoItemGridDetails.kt` grew by about 450 lines, and the poster transition could move to its own file later |
| Security / privacy | No secrets or account data in the code or docs. No frames were committed. `tools/mobile-evidence-audit.sh` on the evidence file: `passed` |

## Manual checks for the owner

1. AC-10 on the TV, with the release build installed:
   1. In the Movies catalogue, rest on a card for 3 s. Press Right once, then Left once, at normal pace. Then hold Right for 1 s.
   2. Judge whether the poster and the text move in from the side of the press within about 250 ms and read as one smooth motion.
   3. On each Left press, watch the right screen edge for a strip of the old poster or of the background. If you still see it, ask for option c (overscan), as the stand describes.
   4. Repeat steps 1-3 on Favourites (heart in the rail, Enter).
2. AC-5 refresh path:
   1. Focus a card in the catalogue and open Details (Center).
   2. Change its bookmark or watchlist state, then press Back.
   3. The panel must re-render in place: no slide and no fade.

## Must fix

none

## Concerns for the owner

- On a Left press, a strip of the old poster at full strength, about 70 px wide, stays at the right screen edge for 1-3 frames (evidence R2-3, R2-6). AC-10 asks for no visible edge strip. This is an accepted risk (option b; option c only if the owner sees it on the TV), so AC-10 stays open until the owner judges it.
