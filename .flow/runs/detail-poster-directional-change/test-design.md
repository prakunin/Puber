# Test design: detail-poster-directional-change

Base 01d1d305. Inputs: `brief.md` (AC-1..AC-11), `design-architect.md` (D1-D13, R1-R11), `design-ux-designer.md` (motion numbers). Paths relative to `app/src/`. Levels from `.flow/roles/test-designer.md`: `unit`, `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>` under a qa-device lease), `device` (emulator, D-pad by `adb -s <serial> shell input keyevent`).

Test files:

| Short name | Path | State |
|---|---|---|
| `PanelDirectionTest` | `test/kotlin/com/kino/puber/core/ui/uikit/component/details/PanelDirectionTest.kt` | new |
| `VideoDetailsPosterStateTest` | `test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` | additions; one deliberate edit allowed (AC-7) |
| `SkeletonAsyncImageVisibilityTest` | `test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt` | unchanged |
| `TrailerSurfaceRedrawTest` | `test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt` | unchanged |
| `SectionRowFocusTraversalTest` | `androidTest/kotlin/com/kino/puber/ui/feature/contentlist/content/SectionRowFocusTraversalTest.kt` | one addition |
| `VideoGridInitialFocusTest` | `androidTest/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridInitialFocusTest.kt` | one addition (P2) |

`PanelDirectionTest` builds `PanelPosition(rowKey, index, itemId)` and `VideoDetailsUIState` with a local `details(id, isLoading = false)` helper (copy of `VideoDetailsUIState.Loading` with `id`/`isLoading` set). `PanelDirectionTracker` is a plain class (D4), tested without Compose.

## 1. Risk profile

P = probability, I = impact, S = P x I.

| Risk | Source | P | I | S | Test that would fail |
|---|---|---|---|---|---|
| Outgoing layer or still drawn over a rendered trailer | R1, brief | 2 | 3 | 6 | `cardEnterAlpha_isOne_whenTrailerRendered` (alpha rule); M7 (composition, 3 repeats) |
| 1.12.1 skeleton flash returns over the poster | R3, brief | 2 | 3 | 6 | guards in AC-7 row; M1, M6 (keying per card only shows at runtime) |
| Wrong direction after debounce, focus restore, stale details, same-id refresh | R7, brief | 2 | 3 | 6 | `PanelDirectionTest` tracker rows (AC-4, AC-5) |
| Skeleton text flash on a card change instead of holding | brief, Q2 | 3 | 2 | 6 | `heldDescription_*` (AC-6); M6 |
| Warm new poster drawn one frame at full alpha before the enter | R2 | 2 | 2 | 4 | M1 screencap burst |
| Bounced focus records a wrong position | R4 | 2 | 2 | 4 | `dpadRightReportsFocusedPositionWithRowKeyAndIndex` |
| Direction drops to `None` mid-transition (trailer publish recomposes) | D5 | 2 | 2 | 4 | M1 with a trailer card |
| Text slides into the closed rail (missing start-edge clip) | ux "Edges" | 2 | 2 | 4 | M2 |
| Two bitmaps and text columns janks on TV GPU | R6, brief | 2 | 2 | 4 | M9 (owner, AC-10) |
| Details hero changes look | AC-9, D13 | 1 | 3 | 3 | M8 |
| Old text visible beside a new focused card up to 3 s | brief | 3 | 1 | 3 | `heldDescription_returnsCurrent_whenHoldExpired` |
| Coil crossfade still runs on a card change (double fade) | D10 | 2 | 1 | 2 | `posterRequest_hasNoTransition_whenCrossfadeOff`; accepted beyond that |
| Stale non-stable callback in `VideoGridRowItem` | R5 | 1 | 2 | 2 | accepted: stable `tracker::onCardFocused`; M3 would show no direction |
| Outgoing `coil3.Image` kept past exit | R8 | 1 | 2 | 2 | accepted: one record alive by construction |
| Cold or failed image keeps outgoing picture up to 3 s | R9 | 2 | 1 | 2 | accepted: same cap as poster hold; owned by `detail-poster-fallback-empty-frame` |
| Equal URL lists for two ids: text moves, poster does not | R10 | 1 | 1 | 1 | accepted |
| New code paths outside the baseline profile | R11 | 1 | 1 | 1 | accepted: no regeneration this run |

## 2. Traceability

Red = fails on base. All red tests below target new API, so on base they do not compile; the implementer first adds the signature with a stub body (`PanelDirection.None`, `current`, `PosterCrossfade`, `enter`), runs the test and sees the assertion fail, then implements.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isEnd_whenSameRowHigherIndex` | fails if the index comparison is dropped or inverted |
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isStart_whenSameRowLowerIndex` | fails if Left maps to End or None |
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isNone_whenDifferentRow` | fails if the row-key check is removed (Up/Down would shift, Q1) |
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isNone_whenFromUnknown` | fails if a null `from` yields a direction |
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isNone_whenToUnknown` | fails if a null `to` yields a direction |
| AC-4 | unit | P0 red | `PanelDirectionTest.panelDirection_isNone_whenSameItemId` | fails if a same-id pair at different indices shifts |
| AC-4 | unit | P2 | `PanelDirectionTest.panelDirection_isNone_whenSameIndex` | fails if equal index returns Start/End |
| AC-4 | unit | P0 red | `PanelDirectionTest.directionTo_usesShownAndFinalCard_whenDebouncedJumpAcrossCards` | shown idx 0, focus 1, 2, 3, details id of idx 3 -> End; fails if the tracker compares with the previous focus or a middle card |
| AC-4 | unit | P1 | `PanelDirectionTest.directionTo_isStart_afterReverseJump` | shown idx 3, focus 2, 1, details idx 1 -> Start; fails if `shown` is not advanced by `markShown` |
| AC-4 | unit | P1 | `PanelDirectionTest.enterSign_followsDirection_inLtr` | End +1, Start -1, None 0; fails on a swapped sign |
| AC-4 | unit | P1 | `PanelDirectionTest.enterSign_isNegated_inRtl` | fails if RTL is ignored |
| AC-5 | unit | P0 red | `PanelDirectionTest.directionTo_isNone_whenNothingShownYet` | first focus on the screen; fails if an unknown `shown` defaults to index 0 |
| AC-5 | unit | P0 red | `PanelDirectionTest.directionTo_isNone_whenRefreshRepublishesSameId` | after `markShown(id 1)`, `directionTo(id 1)` -> None; fails if a refresh (`ContentListVM.kt:161-169`) re-runs a shift |
| AC-5 | unit | P0 red | `PanelDirectionTest.directionTo_isNone_whenDetailsLoading` | fails if `Loading` (id 0) gets a direction |
| AC-5 | unit | P0 red | `PanelDirectionTest.directionTo_isNone_whenFocusedIdDiffersFromDetails` | stale details for an earlier card; fails if the id match on `focused` is removed |
| AC-5 | unit | P1 | `PanelDirectionTest.directionTo_isNone_afterFocusMovesToAnotherRow` | tracker path of the row rule; fails if `onCardFocused` drops the row key |
| AC-5 | unit | P1 | `PanelDirectionTest.markShown_ignoresLoadingDetails` | fails if `Loading` overwrites `shown` (next Right would read None) |
| AC-5 | unit | P1 | `PanelDirectionTest.markShown_clearsShown_whenNoPositionMatchesDetails` | Back from Details / tab re-entry with an unknown position -> next change None; fails if a stale `shown` survives |
| AC-5 | device | P0 | M4 | row change, first focus, Back, tab re-entry crossfade with zero shift |
| AC-6 | unit | P0 red | `VideoDetailsPosterStateTest.heldDescription_returnsPrevious_whenLoadingAfterLoaded` | fails if the skeleton replaces loaded text on `Loading` (base behaviour) |
| AC-6 | unit | P0 red | `VideoDetailsPosterStateTest.heldDescription_returnsCurrent_whenNewDetailsLoaded` | fails if the hold sticks after the new card lands |
| AC-6 | unit | P0 red | `VideoDetailsPosterStateTest.heldDescription_returnsCurrent_whenNothingHeldYet` | fails if the first load shows no skeleton |
| AC-6 | unit | P0 red | `VideoDetailsPosterStateTest.heldDescription_returnsCurrent_whenHoldExpired` | fails if the 3 s cap is ignored |
| AC-6 | device | P0 | M6 | held text on a cold card, skeleton only after the cap |
| AC-7 | unit | P0 red | `VideoDetailsPosterStateTest.posterRequest_hasNoTransition_whenCrossfadeOff` | `transitionFactory` is `Transition.Factory.NONE`; fails if the card-change request still uses `PosterCrossfade` |
| AC-7 | unit | P0 guard | `VideoDetailsPosterStateTest.posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent` | default `crossfade = true` keeps first-image/fallback crossfade; may only gain the explicit default |
| AC-7 | unit | P0 guard | `VideoDetailsPosterStateTest.heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` and the other four `heldPosterUrls_*` | poster hold through `Loading` unchanged |
| AC-7 | unit | P0 guard | `VideoDetailsPosterStateTest.posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage` | fails if the still drops the opt-in |
| AC-7 | unit | P0 guard | `SkeletonAsyncImageVisibilityTest` (all three) | skeleton-once rule unchanged |
| AC-7 | device | P0 | M1, M6 | no skeleton over the poster on any change |
| AC-8 | unit | P0 red | `VideoDetailsPosterStateTest.cardEnterAlpha_isOne_whenTrailerRendered` | fails if the enter alpha can dim the still over a rendered trailer |
| AC-8 | unit | P0 red | `VideoDetailsPosterStateTest.cardEnterAlpha_followsEnter_whenNoTrailer` | fails if the enter fade is lost on cards without a trailer |
| AC-8 | unit | P1 | `VideoDetailsPosterStateTest.cardEnterAlpha_followsEnter_whenTrailerNotRendered` | fails if a pending trailer suppresses the enter |
| AC-8 | unit | P0 guard | `VideoDetailsPosterStateTest.posterStillAlpha_*`, `posterFadeTarget_*` (six) | trailer fade precedence unchanged (D11) |
| AC-8 | unit | P0 guard | `TrailerSurfaceRedrawTest` | 1.12.2 trailer redraw fix intact |
| AC-8 | device | P0 | M7 | trailer visible, outgoing layer gone, 3 repeats |
| AC-1 | device | P0 | M1 | catalogue Right |
| AC-2 | device | P0 | M2 | catalogue Left |
| AC-3 | device | P0 | M3 | Favourites Right and Left |
| AC-1, AC-3 | instrumented | P1 red | `SectionRowFocusTraversalTest.dpadRightReportsFocusedPositionWithRowKeyAndIndex` | Right from idx 0 reports `PanelPosition("row_0", 1, id)` once; fails if the callback sits outside the focus guard (R4) or reports the wrong index |
| AC-3 | instrumented | P2 | `VideoGridInitialFocusTest.dpadRightReportsFocusedPositionWithRowKeyAndIndex` | same for `VideoGrid` (Favourites path) |
| AC-1..AC-3 | instrumented | P1 guard | `SectionRowFocusTraversalTest`, `VideoGridInitialFocusTest` (whole classes) | one-argument `onItemFocused` callers unchanged (D2) |
| AC-9 | device | P1 | M8 | details hero as at base |
| AC-10 | device | P1 | M9 | owner judges smoothness and edge strip |
| AC-11 | check | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | full check green in the worktree |

## 3. Bugs

N/A: kind feature. The first test to write is `heldDescription_returnsPrevious_whenLoadingAfterLoaded` (the only behaviour that changes against base, `VideoItemGridDetails.kt:357-437` shows the skeleton on `Loading`).

## 4. Device checks

qa-device, emulator only, 1080p, worktree dev APK installed with `tools/android-apk-install-preserve`. Motion evidence: screencap burst (`adb -s <serial> exec-out screencap -p` in a loop, at least 8 frames over 300 ms) or `screenrecord`, frames kept under `docs/superpowers/` evidence and audited with `tools/mobile-evidence-audit.sh`. Warm card = focused once before, details and poster cached. Keys: Right 22, Left 21, Up 19, Down 20, Back 4, Center 23.

| # | AC | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 | AC-1, AC-7 | Catalogue, focus card i in a row, wait 3 s; focus i+1 and back to i (warms); then key 22 and capture | new poster and text enter from the right (end) and fade in; old leave to the left and fade out; done in about 250 ms; same direction on a trailer card while the trailer gate opens | skeleton over poster or text; a frame with the new poster at full alpha and zero offset before the motion; motion snapping to no shift mid-way |
| M2 | AC-2 | Same set-up, key 21 | mirrored: enters from the left, old leaves to the right | text drawn into the 60 dp rail left of the panel; skeleton |
| M3 | AC-3 | Favourites tab, repeat M1 and M2 on its grid; then hold key 22 for 1 s | both directions as in M1/M2 in the side-by-side layout; held key chains retargets with at most two posters and two text columns | wrong or missing direction; focus leaving the grid; crash |
| M4 | AC-5 | Catalogue: key 20 to the next row; launch the screen fresh and land on the first card; key 23 into Details, key 4 back; switch tab away and back | each card change crossfades with zero horizontal shift | any horizontal shift; focus loss after Back |
| M5 | AC-5 | Focus a card, wait 5 s with no input | the panel stays still (no transition on repeated state) | any re-run of the transition |
| M6 | AC-6, AC-7 | Catalogue, key 22 onto a cold card (not visited this session) | previous text and poster stay at full alpha until the new details land, then the transition runs; first screen open shows the skeleton once | skeleton text or skeleton poster between cards; previous text beyond 3 s |
| M7 | AC-8 | Trailer-enabled card, auto-trailer on: key 22 onto it, wait 4 s; repeat 3 times (also once on a cold card) | trailer video visible after the 2 s gate; capture shows video, not the poster or black | black or hidden video; outgoing poster over the video |
| M8 | AC-9 | Open Details of a card (key 23), wait for the hero and its trailer | hero looks and behaves as at base (compare with base screenshot) | any shift or extra fade in the hero |
| M9 | AC-10 | Owner on the emulator at 1080p: M1-M3 at normal pace and held keys | owner calls the motion smooth and directional; no strip at the right screen edge (ux D2) | visible jank, bare strip, two readable titles at once |

M5 does not cover the refresh path (`ContentListVM.kt:161-169`): it needs an account mutation in Details, which needs explicit owner authorization; `directionTo_isNone_whenRefreshRepublishesSameId` covers the rule.

## 5. Perf track

N/A: perf track off (tracks: ui); AC-10 is the owner's judgement, M9.

## UNTESTABLE

none
