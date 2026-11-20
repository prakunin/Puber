# Directional detail panel transition - plan

**Spec:** `docs/superpowers/specs/2026-11-20-detail-poster-directional-change.md`
**Goal:** On a card change the detail panel's poster and text shift and crossfade from the side the carousel moves toward, with the text held through `Loading`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run both from the worktree root `/home/user1/orca/workspaces/Puber/detail-poster-directional-change`; never `./gradlew`.
- Touched unit tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`. Instrumented sources compile with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`; running them is qa-device's job (`make DEVICE=<serial> itest TESTS=<fqcn>` under a lease), never `connected*AndroidTest`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence with no prefix, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths below are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. `VIGD` = `core/ui/uikit/component/details/VideoItemGridDetails.kt`. Line numbers are at base 01d1d305.
- Motion numbers, curves and the text start-edge clip are in the spec, "UX designer" -> "Motion numbers" and "Edges and clipping"; use them as written, through the constants of Task 3.
- Red-first (spec "Test design" section 2): a red test targets new API, so on base it does not compile. Add the signature with the stub body named in the task, run the tests, record the failing assertions in the Implementation record "Red run", then implement.
- Out of scope (spec): no change to `CommonAction.ItemFocused`, `ContentListVM`, `FavoriteVM`, the details hero (`ui/feature/details/component/DetailsScreenContent.kt:189`), Home `HeroCarousel`, no Compose UI test harness for the panel, no baseline profile regeneration.
- Detekt: `VIGD` has 16 functions at base, `TooManyFunctions` allows 25 per file. If it is exceeded, move the poster transition code of Task 4 into a new file `core/ui/uikit/component/details/PosterTransition.kt` in the same package and note it under "Deviations".

---

## Tasks

### Task 1: Pure direction rule and tracker

**Covers:** AC-4, AC-5 (rule)
**Files:**
- Create: `core/ui/uikit/component/details/PanelDirection.kt`
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/PanelDirectionTest.kt` (new)

**Consumes:** `VideoDetailsUIState` (`core/ui/uikit/component/details/VideoDetailsUIState.kt:9`, `id: Int` at `:10`, `isLoading` at `:21`, `Loading` at `:24`); `androidx.compose.ui.unit.LayoutDirection`.
**Produces** (all `internal`, package `com.kino.puber.core.ui.uikit.component.details`):

```kotlin
enum class PanelDirection { None, Start, End }
@Immutable data class PanelPosition(val rowKey: String, val index: Int, val itemId: Int)
fun panelDirection(from: PanelPosition?, to: PanelPosition?): PanelDirection
fun enterSign(direction: PanelDirection, layoutDirection: LayoutDirection): Int
class PanelDirectionTracker {
    fun onCardFocused(position: PanelPosition)
    fun directionTo(details: VideoDetailsUIState): PanelDirection
    fun markShown(details: VideoDetailsUIState)
}
@Composable fun rememberPanelDirectionTracker(): PanelDirectionTracker // remember { PanelDirectionTracker() }
```

Rules (spec "Architect" -> "Tracker rules"): `panelDirection`: `from` or `to` null -> None; same `itemId` -> None; different `rowKey` -> None; higher index -> End; lower -> Start; equal -> None. `directionTo`: `details.isLoading` -> None, else `panelDirection(shown, focused?.takeIf { it.itemId == details.id })`. `markShown`: returns on `isLoading`; `shown = focused` if its `itemId == details.id`, else keep `shown` if its id matches, else `null`. `enterSign`: End +1, Start -1, None 0, negated under `LayoutDirection.Rtl`. `shown` and `focused` are plain private `var`s, not snapshot state (spec D4).

- [x] Add the file with stub bodies (`PanelDirection.None`, `0`, empty `markShown`/`onCardFocused`)
- [x] Write `PanelDirectionTest` (JUnit 5, no Compose), local helper `details(id: Int, isLoading: Boolean = false) = VideoDetailsUIState.Loading.copy(id = id, isLoading = isLoading)`, with the tests below; run, record the red assertions
- [x] Implement the rules; run the test class and the fast gate; commit

| Test | Set-up -> expected |
|---|---|
| `panelDirection_isEnd_whenSameRowHigherIndex` | ("r", 0, 1) -> ("r", 1, 2): End |
| `panelDirection_isStart_whenSameRowLowerIndex` | ("r", 1, 2) -> ("r", 0, 1): Start |
| `panelDirection_isNone_whenDifferentRow` | ("a", 0, 1) -> ("b", 1, 2): None |
| `panelDirection_isNone_whenFromUnknown` | null -> ("r", 1, 2): None |
| `panelDirection_isNone_whenToUnknown` | ("r", 0, 1) -> null: None |
| `panelDirection_isNone_whenSameItemId` | ("r", 0, 7) -> ("r", 3, 7): None |
| `panelDirection_isNone_whenSameIndex` | ("r", 2, 1) -> ("r", 2, 9): None |
| `directionTo_usesShownAndFinalCard_whenDebouncedJumpAcrossCards` | focus idx 0, `markShown(details(id0))`; focus idx 1, 2, 3; `directionTo(details(id3))`: End |
| `directionTo_isStart_afterReverseJump` | shown idx 3 via `markShown`; focus idx 2, 1; `directionTo(details(id1))`: Start |
| `enterSign_followsDirection_inLtr` | End +1, Start -1, None 0 |
| `enterSign_isNegated_inRtl` | End -1, Start +1, None 0 |
| `directionTo_isNone_whenNothingShownYet` | focus idx 1, no `markShown`; `directionTo(details(id1))`: None |
| `directionTo_isNone_whenRefreshRepublishesSameId` | focus idx 1, `markShown(details(id1))`; `directionTo(details(id1))`: None |
| `directionTo_isNone_whenDetailsLoading` | shown idx 0, focus idx 1; `directionTo(details(0, isLoading = true))`: None |
| `directionTo_isNone_whenFocusedIdDiffersFromDetails` | shown idx 0, focus idx 2; `directionTo(details(id1))`: None |
| `directionTo_isNone_afterFocusMovesToAnotherRow` | shown ("a", 0), focus ("b", 1); `directionTo` of that id: None |
| `markShown_ignoresLoadingDetails` | shown idx 0, focus idx 1, `markShown(Loading)`, then `directionTo(details(id1))`: End |
| `markShown_clearsShown_whenNoPositionMatchesDetails` | shown idx 0, focus idx 1, `markShown(details(99))`; focus idx 2; `directionTo(details(id2))`: None |

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.PanelDirectionTest'` passes and the fast gate is green.

---

### Task 2: Rows report the focused card's position

**Covers:** AC-1, AC-3 (position source; device proof is qa's M1, M3)
**Files:**
- Modify: `ui/feature/contentlist/content/SectionRow.kt` - add `onItemPositionFocused: (PanelPosition) -> Unit = {}` to `SectionRowContent` (`:47`, after `onItemFocused` at `:53`), pass it in the `ContentSectionCards` call (`:87-101`), add it to `ContentSectionCards` (`:107`); inside the existing guard (`:159-161`) call `onItemPositionFocused(PanelPosition(rowKey, index, item.id))` before `onItemFocused(item)`, `index` from `itemsIndexed` (`:151`), `rowKey` the parameter at `:112`
- Modify: `core/ui/uikit/component/moviesList/VideoGridUIState.kt` - add the same defaulted parameter to `VideoGrid` (`:76`); thread it through `VideoGridContent` (`:105`, call at `:89-100`), `VideoGridSection` (`:328`, call at `:190-204`) and `VideoGridItems` (`:399`, calls at `:223-243` and `:353-372`); in `VideoGridItems` replace the `{ _, focusedItem -> ... }` callback (`:454-458`) with `{ index, focusedItem -> if (itemFocus.onItemFocused(focusedItem.id)) { onItemPositionFocused(PanelPosition(items.rowKey, index, focusedItem.id)); onItemFocused(focusedItem) } }`. `VideoGridRowItem` (`:478`) remembers its focus modifier per `(itemIndex, item.id)`, so the callback it captures must stay stable (Task 3 passes a bound reference of a remembered tracker)
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/contentlist/content/SectionRowFocusTraversalTest.kt`
- Test: `app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridInitialFocusTest.kt`

**Consumes:** `PanelPosition` (Task 1).
**Produces:** `onItemPositionFocused: (PanelPosition) -> Unit = {}` on `SectionRowContent` and `VideoGrid`. The one-argument `onItemFocused` stays; callers `ui/feature/player/component/EpisodesPanel.kt:113` and `ui/feature/episodeschedule/component/EpisodeScheduleScreenContent.kt:72` are not edited.

- [x] Add `dpadRightReportsFocusedPositionWithRowKeyAndIndex` to `SectionRowFocusTraversalTest`, modelled on `dpadFocusOnNonFallbackItemInNonTargetRowRecordsActualIdentity` (`:76`): items `item(0, 0..2)`, `SectionConfig(id = "row_0", ...)`, collect positions into a list, `requestFocus(itemTitle(0, 0))`, clear the list, `pressCurrent(Key.DirectionRight)`; assert the list equals `listOf(PanelPosition("row_0", 1, 1))`
- [x] Add `dpadRightReportsFocusedPositionWithRowKeyAndIndex` to `VideoGridInitialFocusTest`: one `VideoGridItemUIState.Items` of `videoItem(10..12)` with `rowKey = "grid_row"`, `initialFocusedItemId = 10`, collect positions, clear after `onNodeWithText("Item 10").assertIsFocused()`, `performDirection(Key.DirectionRight)` (`:300`); assert `listOf(PanelPosition("grid_row", 1, 11))`
- [x] Implement the threading; run the fast gate and `:app:compileDevDebugAndroidTestKotlin`; commit

**Done when:** fast gate and `:app:compileDevDebugAndroidTestKotlin` are green; `git diff` shows the new callback only inside the two guards. The red/green run of the two instrumented tests is recorded by qa-device.

---

### Task 3: Text held through Loading and animated on id; screens pass the direction

**Covers:** AC-1, AC-2, AC-3, AC-5, AC-6 (text half), AC-10 (motion constants in one place)
**Files:**
- Modify: `VIGD`
  - Motion constants beside `POSTER_HOLD_MS` (`:74`), one group, named once (spec ux "Shared constants"): duration 250 ms, poster shift 48 dp, text shift 40 dp, poster fade-in 150 ms, text fade-out 90 ms, text fade-in delay 90 ms and length 160 ms
  - `direction: PanelDirection = PanelDirection.None` on `VideoItemGridDetails` (`:228`) and `VideoDetailsDescription` (`:357`); pass it at `:251` and `:260`
  - `internal fun heldDescription(previous: VideoDetailsUIState?, current: VideoDetailsUIState, holdExpired: Boolean = false): VideoDetailsUIState` next to `heldPosterUrls` (`:667`): returns `previous` when `current.isLoading && previous != null && !previous.isLoading && !holdExpired`, else `current`
  - `rememberHeldDescription(state)` shaped like `rememberHeldUrls` (`:695-708`): holder class like `PosterUrlHolder` (`:690`), `holding = state.isLoading`, `LaunchedEffect(holding)` sets `holdExpired` after `POSTER_HOLD_MS`, `SideEffect { holder.last = held }`
  - Move the `Column` of `VideoDetailsDescription` (`:368-435`) into a private `VideoDetailsDescriptionBody(state, descriptionMaxLines, layout, factsStyle)`; the outer `Box` (`:365-367`) holds `AnimatedContent(targetState = held, contentKey = { it.id }, transitionSpec = ...)`, `label` set
  - Direction latched per change: `val latched = remember(held.id) { direction }`. In `transitionSpec`, `sign = if (initialState.isLoading || targetState.isLoading) 0 else enterSign(latched, LocalLayoutDirection)` (read the layout direction outside the lambda); enter = `slideInHorizontally { sign * textShiftPx }` + delayed `fadeIn`, exit = `slideOutHorizontally { -sign * textShiftPx }` + short `fadeOut`, `using SizeTransform(clip = false)`, curves per the spec's motion table
  - Clip the animated content at the start edge only (spec ux "Edges and clipping"): a `drawWithContent { clipRect(left = startEdge, top = -h, right = w + far, bottom = h + far) { this@drawWithContent.drawContent() } }` on the `AnimatedContent` modifier, with the start edge at 0 in LTR and at `size.width` in RTL; no bottom clip
- Modify: `ui/feature/contentlist/content/ContentListScreenContent.kt` - in `ContentListLayout` (`:127`) `val tracker = rememberPanelDirectionTracker()`; `direction = tracker.directionTo(state.selectedItem)` into `VideoItemGridDetails` (`:166-175`); `SideEffect { tracker.markShown(state.selectedItem) }`; add `onCardFocused: (PanelPosition) -> Unit` to `sectionItems` (`:311`, call at `:203`) and `sectionItem` (`:356`, call at `:324`), pass `tracker::onCardFocused`, and set `onItemPositionFocused = onCardFocused` in the `SectionRowContent` call (`:387-405`)
- Modify: `ui/feature/favorites/content/FavoriteScreenContent.kt` - in `FavoriteScreenContentBody` (`:66`) the same tracker, `direction` into `VideoItemGridDetails` (`:99-104`), `SideEffect { tracker.markShown(state.selectedItem) }`, `onItemPositionFocused = tracker::onCardFocused` on `VideoGrid` (`:107-114`)
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` (additions)

**Consumes:** Task 1 API; Task 2 `onItemPositionFocused`.
**Produces:** `direction` parameter on `VideoItemGridDetails`; motion constants used by Task 4.

- [x] Add `heldDescription` with stub body `current`; write the four tests below; run, record the red assertion
- [x] Implement `heldDescription`, `rememberHeldDescription`, the `AnimatedContent`, the clip and both screens
- [x] Run `VideoDetailsPosterStateTest` and the fast gate; commit

| Test | Set-up -> expected (`assertSame`) |
|---|---|
| `heldDescription_returnsPrevious_whenLoadingAfterLoaded` | previous loaded id 1, current `Loading`: previous |
| `heldDescription_returnsCurrent_whenNewDetailsLoaded` | previous loaded id 1, current loaded id 2: current |
| `heldDescription_returnsCurrent_whenNothingHeldYet` | previous null, current `Loading` copy: current |
| `heldDescription_returnsCurrent_whenHoldExpired` | previous loaded id 1, current `Loading` copy, `holdExpired = true`: current |

**Done when:** `VideoDetailsPosterStateTest` passes with every base test unchanged, the fast gate is green, and `VideoItemGridDetails` callers other than the two screens (`VIGD:620`, `VIGD:635` previews) compile unchanged.

---

### Task 4: Poster outgoing layer and directional enter

**Covers:** AC-1, AC-2, AC-3, AC-7, AC-8, AC-9
**Files:**
- Modify: `core/ui/uikit/component/SkeletonAsyncImage.kt` - add `onSuccess: (SuccessResult) -> Unit = {}` to `SkeletonAsyncImage` (`:20`), call `onSuccess(it.result)` at the end of the `AsyncImage` `onSuccess` block (`:47-51`); the skeleton state (`:29-31`) is untouched
- Modify: `VIGD`
  - `direction: PanelDirection = PanelDirection.None` on `VideoDetailsMedia` (`:284`) and `VideoDetailsPoster` (`:441`); pass it at `:241`, `:265`, `:295`, `:312`. The details hero (`ui/feature/details/component/DetailsScreenContent.kt:189`) passes nothing
  - `posterRequest(context, imageUrl, crossfade: Boolean = true)` (`:585`): `transitionFactory(if (crossfade) PosterCrossfade else Transition.Factory.NONE)`; `rememberPosterRequest(imageUrl, crossfade)` (`:579`) keys `remember` on both
  - `internal fun cardEnterAlpha(hasTrailer: Boolean, trailerRendered: Boolean, enter: Float): Float = if (hasTrailer && trailerRendered) 1F else enter`, beside `posterStillAlpha` (`:718`, unchanged)
  - `PosterStill` (`:597`) takes `onSuccess: (SuccessResult) -> Unit` and passes it to `SkeletonAsyncImage`
  - Last shown image: a holder like `PosterUrlHolder` (`:690`) with `var image: coil3.Image?`, set from the still's `onSuccess` (`result.image`), set to `null` in a `SideEffect` when `heldUrls` is empty
  - Transition record (private class), created in composition as `remember(heldUrls) { ... }` right after `rememberHeldUrls` (`:464`), only when the holder has an image and `heldUrls` is non-empty, else `null` (spec D9). Fields: latched `sign = enterSign(direction, layoutDirection)`; `outgoing: MutableState<OutgoingPoster?>` with the holder's image and `Animatable` offset/alpha seeded from the previous record's still values when one is mid-flight, else 0 px / 1; still `Animatable` offset `sign * posterShiftPx` and alpha 0; `ready: MutableState<Boolean>`. Keep the previous record in a holder to seed from
  - `crossfade = !(record != null && urlIndex == 0)` into `rememberPosterRequest` (`:467`): no Coil crossfade on a card change, first image and fallback keep `PosterCrossfade` (spec D10)
  - `LaunchedEffect(record)`: `withTimeoutOrNull(POSTER_HOLD_MS) { snapshotFlow { record.ready.value }.first { it } }`; on timeout snap the still to 0 px / 1 and set `outgoing = null`; otherwise run in parallel the still offset to 0 and alpha to 1, the outgoing offset to `-sign * posterShiftPx` (its current value when `sign == 0`) and alpha to 0, specs per the motion table, then `outgoing = null`
  - The still's `onSuccess` sets `record?.ready = true` and the image holder
  - Draw order (`VIGD:482-513`): player, outgoing `Image(painter = remember(image) { image.asPainter(context) }, contentScale = ContentScale.FillWidth, alignment = <same as still>, modifier = fillMaxSize + graphicsLayer { translationX; alpha })` composed only while `outgoing != null && !(hasTrailer && trailerRendered)`, then `PosterStill`. Still `graphicsLayer` at `:506`: `alpha = posterStillAlpha(hasTrailer, trailerRendered, fade.value) * cardEnterAlpha(hasTrailer, trailerRendered, record?.stillAlpha?.value ?: 1F)`, `translationX = record?.stillOffset?.value ?: 0F`. Animated values are read only inside `graphicsLayer { }`
  - Update the KDoc of `rememberPosterRequest` (`:569-576`), which says every card change crossfades
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` (additions; `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent` at `:78` may only gain an explicit `crossfade = true`)

**Consumes:** Task 1 `enterSign`, `PanelDirection`; Task 3 constants and `direction` on `VideoItemGridDetails`.
**Produces:** nothing later tasks rely on.

- [x] Add `cardEnterAlpha` with stub body `enter` and the `crossfade` parameter ignored; write the tests below; run, record the red assertions
- [x] Implement the rest of the task
- [x] Run `VideoDetailsPosterStateTest`, `SkeletonAsyncImageVisibilityTest`, `TrailerSurfaceRedrawTest` and the fast gate; commit

| Test | Expected |
|---|---|
| `posterRequest_hasNoTransition_whenCrossfadeOff` | `posterRequest(context, url, crossfade = false)?.transitionFactory` is `Transition.Factory.NONE` (`assertSame`) |
| `cardEnterAlpha_isOne_whenTrailerRendered` | `cardEnterAlpha(true, true, 0.3F) == 1F` |
| `cardEnterAlpha_followsEnter_whenNoTrailer` | `cardEnterAlpha(false, false, 0.3F) == 0.3F` |
| `cardEnterAlpha_followsEnter_whenTrailerNotRendered` | `cardEnterAlpha(true, false, 0.3F) == 0.3F` |

Guards that stay green unchanged: the five `heldPosterUrls_*`, `posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage`, the six `posterStillAlpha_*`/`posterFadeTarget_*`, the three `posterCrossfade_*` in `VideoDetailsPosterStateTest`; all of `SkeletonAsyncImageVisibilityTest` and `TrailerSurfaceRedrawTest`.

**Done when:** the three test classes pass, the fast gate is green, and `git diff app/src/test` shows only additions plus at most the explicit default on `:78`.

---

### Task 5: Record the motion in the catalogue stand and run the full check

**Covers:** AC-11; ux "Governing stand" (motion section)
**Files:**
- Modify: `docs/design/catalogue/README.md` - add a short `## Motion` section after `## Agreed sizes` with the shipped values (the motion table of the spec, as the constants of Task 3 hold them) and a pointer to the constants in `VIGD`

- [x] Write the section; run the full gate; commit

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` is green in the worktree.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** one per task (T1 `bb800826`, T2 `8b146c77`, T3 `ba338727`, T4 `1170b759`, T5 the README commit); `git log base..HEAD` lists them.

**Red run** (P0 `red` tests on the unchanged code, against the stubs of the plan):

- red: `PanelDirectionTest.panelDirection_isEnd_whenSameRowHigherIndex`, `panelDirection_isStart_whenSameRowLowerIndex`, `directionTo_usesShownAndFinalCard_whenDebouncedJumpAcrossCards`, `directionTo_isStart_afterReverseJump`, `markShown_ignoresLoadingDetails`, `enterSign_followsDirection_inLtr`, `enterSign_isNegated_inRtl` - expected End/Start/sign, stub returned `None`/0 (7 failed of 18).
- green on base: the eleven `*_isNone_*`, `markShown_clearsShown_*` tests of `PanelDirectionTest` - the stub already returns `None`, so they only become discriminating once the rule exists; they guard against a rule that shifts where it must not.
- red: `VideoDetailsPosterStateTest.heldDescription_returnsPrevious_whenLoadingAfterLoaded` - stub returned `current`, not `previous`.
- green on base: `heldDescription_returnsCurrent_whenNewDetailsLoaded`, `_whenNothingHeldYet`, `_whenHoldExpired` - the stub returns `current`; they guard the hold against sticking.
- red: `VideoDetailsPosterStateTest.cardEnterAlpha_isOne_whenTrailerRendered`, `posterRequest_hasNoTransition_whenCrossfadeOff` - stub returned `enter` / still used `PosterCrossfade`.
- green on base: `cardEnterAlpha_followsEnter_whenNoTrailer`, `cardEnterAlpha_followsEnter_whenTrailerNotRendered` (P1; the stub returns `enter`).
- Instrumented `dpadRightReportsFocusedPositionWithRowKeyAndIndex` (two classes): compiled only, not run.

**Deviations from the plan:**

- `PanelPosition` and `PanelDirection` are public, not `internal`: they appear in the signatures of the public `VideoGrid` and `VideoItemGridDetails`, which Kotlin refuses to expose with internal types. The tracker, `panelDirection`, `enterSign` and `rememberPanelDirectionTracker` stay `internal`.
- The record's `outgoing: MutableState<OutgoingPoster?>` is a `var outgoingShown` plus a fixed `outgoingImage` on `PosterTransition`; same behaviour, one class fewer.
- Detekt (`ComplexCondition`, `CyclomaticComplexMethod`) made me extract `OutgoingPoster` and `PosterTransitionState.crossfades`; `heldDescription` is written with `takeIf`. `TooManyFunctions` was not hit, so `PosterTransition.kt` was not needed.
- `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent` is unchanged.

**Files touched outside the plan:** none.

**For the reviewers:**

- `VideoDetailsPoster` / `rememberPosterTransition`: the record is built in composition from `remember(heldUrls)`; the seed values are read under `Snapshot.withoutReadObservation` so the composition does not subscribe to the animations. The `shownImage` holder is written from the still's `onSuccess`.
- `VideoDetailsDescription`: `latched = remember(held.id) { direction }` keeps the direction through the `markShown` recomposition; `AnimatedContent` is keyed by `id`, so a same-id republish updates in place.
- The text clip is a `drawWithContent` with `clipRect` open on three sides (16384 px), not a layout clip.
- Detail panel motion is not run on a device (implementer scope); M1-M9 belong to qa-device and the owner.

**Blocked:** none.

### Fix round 1

Triage items 1, 2, 4, 5, 6, 7 (`.flow/runs/detail-poster-directional-change/triage-1.md`).

| Item | Change | Commit |
|---|---|---|
| 1, 4, 5 | The outgoing poster keeps alpha 1 under the still until the motion ends (option b), then `outgoingImage` is cleared (nullable). An interrupted ready record keeps running in its own job and is drawn under the new one (`PosterTransitionHolder.active`); a record still waiting for its picture is replaced and the new one starts from its values. Fade animation of the outgoing layer removed. | `ba4ee226` |
| 2 | `onError` on the last URL calls `settle()` (ends the hold) and clears `shownImage`. | `ba4ee226` |
| 6 | Catalogue README Motion section: values pending AC-10, outgoing alpha row, edge-strip and retarget lines. | next commit |
| 7 | Guard marking and mutant runs below. | next commit |

Red run (fix round): new `PosterTransitionTest` (4 tests, `PosterTransition` and `POSTER_HOLD_MS` made `internal`).

- red: mutant without clearing `outgoingImage` - `run_clearsOutgoingImage_whenMotionEnds`, `_whenHoldExpiresWithoutPicture`, `run_keepsOutgoingImageUnderTheStill_untilMotionEnds`, `run_endsTheHold_whenSettledBeforeTheCap` failed (4 of 4).
- red: mutant with an empty `settle()` (a failed last URL would not end the hold) - `run_endsTheHold_whenSettledBeforeTheCap` failed: ended at 3000 ms.

Item 7: the twelve P0 rows marked `red` that were green on the stubs are guards, not red tests: the eleven `*_isNone_*` and `markShown_clearsShown_*` rows of `PanelDirectionTest`, and the three `heldDescription_returnsCurrent_*` rows plus the two `cardEnterAlpha_followsEnter_*` rows of `VideoDetailsPosterStateTest` as listed under Red run. Targeted mutants on the shipped code, each run against both classes:

| Mutant | Failing guard |
|---|---|
| `directionTo` without the `id == details.id` match | `directionTo_isNone_whenFocusedIdDiffersFromDetails` |
| `panelDirection` without the `rowKey` check | `panelDirection_isNone_whenDifferentRow`, `directionTo_isNone_afterFocusMovesToAnotherRow` |
| `heldDescription` always returns `previous` | `heldDescription_returnsCurrent_whenNewDetailsLoaded`, `_whenHoldExpired` |
| `cardEnterAlpha` always 1 | `cardEnterAlpha_followsEnter_whenNoTrailer`, `_whenTrailerNotRendered` |

Not done: item 3 (qa re-runs M3 on Favourites) and item 8 (accepted) are not implementer work. Not run on a device. For the reviewers: `rememberPosterTransition` effect (cancel of waiting records, `active` list) and the draw order in `VideoDetailsPoster` (`transition.layers` before the still).

## Readiness

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

### Fix round 2

Triage 2 item 1 (`.flow/runs/detail-poster-directional-change/triage-2.md`; edge F1, codex).

| Item | Change |
|---|---|
| 1 | `outgoingAlpha` is an `Animatable`. While a record waits for its picture, the outgoing layer rests: alpha goes to 1 and the offset to 0, over a time scaled to the distance left, so it is opaque and unshifted before the predecessor's own layers end. The animation is cancelled when the picture settles, and `run` then moves the layer as before. |

- red: mutant that skips the rest animation - `run_bringsASeededOutgoingLayerToRest_whileWaitingForThePicture` failed (alpha 0.5 left); the other four stayed green.
- In `run_keepsOutgoingImageUnderTheStill_untilMotionEnds` the line-57 assertion was only changed to read the `Animatable` value; it still compared the default `1F` with itself (dropped in fix round 3); `run_clearsOutgoingImage_whenHoldExpiresWithoutPicture` now runs under the frame clock.
- Catalogue README: retarget rule text unchanged, not touched.
- For the reviewers: `PosterTransition.restOutgoing` and its cancellation in `run`; a rest that is longer than the predecessor's remaining motion would leave a short seam (not run on a device).

### Fix round 3

Triage 3 items 1-3 (`.flow/runs/detail-poster-directional-change/triage-3.md`; edge F1, tests F1-F3).

| Item | Change |
|---|---|
| 1 | The alpha rest (`restOutgoingAlpha`) runs in `run`'s outer scope and is no longer cancelled when the record becomes ready, so the outgoing layer is opaque during the motion. Only the offset rest is cancelled and handed to the main motion, whose outgoing target is `-sign * shiftPx` (0F for sign 0). |
| 2 | The seeding `when` is the internal `seedFrom(previous)`; tested for null, ready mid-motion, waiting. |
| 3 | The echo assertion at line 57 is dropped; the alpha checks live in the new tests, where the seed is below 1. |

- red (against the previous code, `seedFrom` stubbed): `run_keepsRestingTheAlpha_whenSettledBeforeRun` - outgoingAlpha not 1 (PosterTransitionTest.kt:76); `run_keepsRestingTheAlpha_whenSettledMidRest` - same (:89); `run_bringsTheOutgoingOffsetToZero_whenSettledWithoutDirection` - offset kept at 30 (:109); `seedFrom_takesTheStill_whenPredecessorIsReadyMidMotion` and `seedFrom_takesTheOutgoingLayer_whenPredecessorIsWaiting` - fail against the stub (:131, :147). `seedFrom_isASettledLayer_withoutPredecessor` passes on the stub's default (guard).
- For the reviewers: `PosterTransition.run` scopes; the alpha rest (at most 150 ms) always ends before the 250 ms motion.
