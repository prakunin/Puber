# Readiness 1: detail-poster-directional-change

Verdict: **READY**

Inputs: spec and plan in the worktree (spec committed `a0c42d8f`, plan untracked), `test-design.md`. Code read at base 01d1d305; the worktree diff against base touches only the spec.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | T2, T3, T4 | M1; `SectionRowFocusTraversalTest.dpadRightReportsFocusedPositionWithRowKeyAndIndex` (P1) |
| AC-2 | T3, T4 | M2 |
| AC-3 | T2, T3, T4 | M3; `VideoGridInitialFocusTest.dpadRightReportsFocusedPositionWithRowKeyAndIndex` (P2) |
| AC-4 | T1 | `PanelDirectionTest` `panelDirection_*`, `directionTo_usesShownAndFinalCard_*` |
| AC-5 | T1, T3 | `PanelDirectionTest` `directionTo_isNone_*`, `markShown_*`; M4, M5 |
| AC-6 | T3 | `heldDescription_*`; M6 |
| AC-7 | T4 | `posterRequest_hasNoTransition_whenCrossfadeOff` plus guards; M1, M6 |
| AC-8 | T4 | `cardEnterAlpha_*`, `TrailerSurfaceRedrawTest`; M7 |
| AC-9 | T4 (default `None`) | M8 |
| AC-10 | T3 (constants), owner | M9 |
| AC-11 | T5 | full check |

## Citations sampled

| Citation | Resolves to | Result |
|---|---|---|
| `VIGD:74` | `POSTER_HOLD_MS = 3_000L` | ok |
| `VIGD:228`, `:284`, `:357`, `:441`, `:597` | `VideoItemGridDetails`, `VideoDetailsMedia`, `VideoDetailsDescription`, `VideoDetailsPoster`, `PosterStill` | ok |
| `VIGD:464`, `:467`, `:506` | `rememberHeldUrls(...)`, `rememberPosterRequest(currentUrl)`, still `graphicsLayer { alpha = posterStillAlpha(...) }` | ok |
| `VIGD:579`, `:585`, `:667`, `:681`, `:690`, `:695`, `:718` | `rememberPosterRequest`, `posterRequest`, `heldPosterUrls`, `PosterCrossfade`, `PosterUrlHolder`, `rememberHeldUrls`, `posterStillAlpha` | ok |
| `VIGD:241/251/260/265/295/312`, `:620/:635` | the six inner calls; the two preview calls | ok |
| `SectionRow.kt:47`, `:53`, `:107`, `:112`, `:151`, `:159-161` | `SectionRowContent`, `onItemFocused`, `ContentSectionCards`, `rowKey`, `itemsIndexed { index, item }`, guard | ok |
| `VideoGridUIState.kt:76`, `:105`, `:328`, `:399`, `:454-458`, `:478`, `:481` | `VideoGrid`, `VideoGridContent`, `VideoGridSection`, `VideoGridItems`, guard, `remember(itemIndex, item.id)`, `onItemFocused(itemIndex, item)`; `Items.rowKey` at `:62` | ok |
| `ContentListScreenContent.kt:127/166/203/311/324/356/387`, `FavoriteScreenContent.kt:66/99/107` | the named composables and calls | ok |
| `SkeletonAsyncImage.kt:20`, `:30`, `:47` | `SkeletonAsyncImage`, `hasShownImage`, `onSuccess` | ok |
| `VideoDetailsPosterStateTest.kt:78`; five `heldPosterUrls_*`, six `posterStillAlpha_*`/`posterFadeTarget_*`, three `posterCrossfade_*` | as claimed | ok |
| `SectionRowFocusTraversalTest.kt:76`, `item(row, col)` id `row*10+col`; `VideoGridInitialFocusTest.kt:300` `performDirection` | as the T2 expectations need (`PanelPosition("row_0", 1, 1)`) | ok |
| coil 3.6.3: `Image.asPainter(Context)`, `Transition.Factory.NONE` | present in `coil-compose-core-android` and `coil-core-android` jars | ok |
| spec `ContentListVM.kt:74` ("`Loading` published") | blank line; `Loading` is published at `:71` | wrong line |
| spec `VIGD:72` (`POSTER_CROSSFADE_MS`) | blank line; constant at `:71` | wrong line |

## Build order and scope

Tasks follow dependency order: T1 (new file) -> T2 (defaulted callback, no caller yet) -> T3 (text, screens, `direction` on `VideoItemGridDetails`/`VideoDetailsDescription`) -> T4 (poster, `direction` on `VideoDetailsMedia`/`VideoDetailsPoster`) -> T5. Each step compiles alone: new parameters are defaulted and the one-argument `onItemFocused` stays. No task touches the VMs, `CommonAction.ItemFocused`, the hero or `HeroCarousel`. Detekt `TooManyFunctions` is 25 per file (`config/detekt/detekt.yml:160`); the plan carries the `PosterTransition.kt` fallback. Blocking question Q2 is answered.

## Test design

Every automated P0 row names its defect and is `red` or `guard` with a backticked test name. P0 device rows (M1-M7) and the AC-11 check are manual or command rows, not code tests.

## Non-blocking corrections

1. Spec "Problem" table: change `ContentListVM.kt:74` to `:71`; spec ux "Shared constants": change `VIGD:72` to `:71`.
2. Spec AC-10 and M9 say the owner judges on the emulator; the 2026-11-20 decision says on the TV after release. The gate will carry AC-10 as a manual-by-nature `NOT CHECKED` item unless the spec is aligned.
3. D13 assumes the hero's held URLs never change between two shown images. If a hero re-map changes the URL list, a `None` record runs (fade, Coil crossfade off). M8 must compare against a base screenshot, as written.
