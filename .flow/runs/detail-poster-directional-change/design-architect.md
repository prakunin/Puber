# Design (architect): detail-poster-directional-change

Base 01d1d305, worktree read at that commit. Paths relative to `app/src/main/java/com/kino/puber/`; `VIGD` = `core/ui/uikit/component/details/VideoItemGridDetails.kt`. Motion numbers, curves, draw order and the text start-edge clip come from `design-ux-designer.md` ("Motion numbers", "Edges and clipping") and are not restated here.

## Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where does the direction come from? | UI-only `PanelDirectionTracker` per screen, fed `PanelPosition(rowKey, index, itemId)` from the row's focus callback; the panel gets a `direction: PanelDirection` (`None`, `Start`, `End`) parameter, default `None` | Option A (state.md 2026-11-20); no change to `CommonAction.ItemFocused` or the VMs |
| D2 | How do positions leave the rows without breaking callers? | New optional callback `onItemPositionFocused: (PanelPosition) -> Unit = {}` beside `onItemFocused` on `SectionRowContent` and `VideoGrid`; the `(VideoItemUIState) -> Unit` callback stays | `SectionRowFocusTraversalTest` and `VideoGridInitialFocusTest` (androidTest) and the `EpisodeScheduleScreenContent` and `EpisodesPanel` `VideoGrid` calls use the one-argument form |
| D3 | When is the direction read? | In composition: `tracker.directionTo(details)` = `panelDirection(shown, focused)`; `shown` moves to `focused` in a `SideEffect` once loaded details with the focused id are drawn | The direction must exist in the same composition where the panel's held id and held URLs change; a debounced jump compares the shown card to the final card (brief.md#AC-4) |
| D4 | Is tracker state snapshot state? | No: plain `var`s in a remembered class, written from focus callbacks, read only when `selectedItem` changes | Snapshot state would recompose the whole screen content on every D-pad move; the read is only needed in the composition that a new `selectedItem` already triggers |
| D5 | How is the direction kept for the whole transition? | Latched per change: text `remember(held.id) { direction }`, poster inside the transition record built in `remember(heldUrls)` | The parameter drops to `None` at the next screen recomposition (trailer publish), mid-transition |
| D6 | Text hold | Pure `heldDescription(previous, current, holdExpired)` beside `heldPosterUrls`; holds the last loaded details while `current.isLoading`; same `POSTER_HOLD_MS` cap (`VIGD:74`) with its own timer, as `rememberHeldUrls` (`VIGD:695-708`) does | Q2 (state.md); one rule shape, one cap |
| D7 | Text animation | `AnimatedContent(targetState = held, contentKey = { it.id })`, slide + fade both ways, `SizeTransform(clip = false)`; direction forced to `None` when either side `isLoading`; interruptions use `AnimatedContent`'s own retargeting | Same `id` (refresh, focus restore) re-renders in place with no transition (brief.md#AC-5); slide offsets are applied at placement, not recomposition |
| D8 | Poster: what draws the outgoing image? | A plain `Image(painter = image.asPainter(context))` of the `coil3.Image` the still last reported in `onSuccess`, not a second `AsyncImage` | No request, no cache miss, drawn in the first frame; `asPainter` exists in coil-compose-core 3.6.3; not a `SkeletonAsyncImage`, so no `hasShownImage` reset (`core/ui/uikit/component/SkeletonAsyncImage.kt:30`) |
| D9 | Poster: when does a transition start? | A transition record is created when `heldUrls` changes (by equality) while an image is shown and the new list is non-empty; the still gets enter start values in that same composition; motion starts on the new still's `onSuccess` | Creating the record in an effect would draw one frame of a warm new image at alpha 1, offset 0; same-card fallback (`urlIndex++`, `VIGD:508-512`) does not change `heldUrls` and starts nothing |
| D10 | Coil crossfade | `posterRequest(context, url, crossfade: Boolean = true)`; `false` (`Transition.Factory.NONE`) for a card change with a shown image, latched per `heldUrls`; first image and fallback keep `PosterCrossfade` | Brief plan step 4; ux "Shared constants"; the existing `posterRequest` case keeps its default |
| D11 | Trailer precedence | Still alpha = `posterStillAlpha(...)` (`VIGD:718`, unchanged) × `cardEnterAlpha(hasTrailer, trailerRendered, enterAlpha)` (1 over a rendered trailer); outgoing layer composed only while `!(hasTrailer && trailerRendered)` | Nothing extra over a rendered SurfaceView; existing `posterStillAlpha` tests unchanged (brief.md#AC-7) |
| D12 | Outgoing layer lifetime | Leaves composition at the end of its exit, on a newer card change (it is replaced, retarget per ux "Rapid focus changes"), when `heldUrls` becomes empty, over a rendered trailer, or `POSTER_HOLD_MS` after the change if the still never succeeds | brief.md#AC-8; brief "Overlap" (failed primary URL keeps the previous picture up to the cap) |
| D13 | Details hero | Passes nothing, so `None`; its held URLs never change between two shown images (one item per screen, `ui/feature/details/model/DetailsScreenUIMapper.kt:51`, `:259`), so no record is ever created | brief.md#AC-9 |

## Components and data flow

```
row card focus (guarded by itemFocus.onItemFocused)
  -> onItemFocused(item)            -> CommonAction.ItemFocused (unchanged VM path)
  -> onItemPositionFocused(pos)     -> tracker.focused = pos
VM publishes selectedItem (Loading, then details)
  -> screen composition: direction = tracker.directionTo(selectedItem); SideEffect { tracker.markShown(selectedItem) }
  -> VideoItemGridDetails(state, direction)
       -> VideoDetailsDescription: heldDescription -> AnimatedContent keyed on id, direction latched
       -> VideoDetailsPoster: rememberHeldUrls -> remember(heldUrls) { transition record } -> layers
```

Tracker rules (pure, in `PanelDirection.kt`):

| Function | Rule |
|---|---|
| `panelDirection(from, to)` | `from` or `to` null -> `None`; same `itemId` -> `None`; different `rowKey` -> `None`; `to.index > from.index` -> `End`; lower -> `Start`; equal index -> `None` |
| `directionTo(details)` | `details.isLoading` -> `None`; else `panelDirection(shown, focused?.takeIf { it.itemId == details.id })` |
| `markShown(details)` | ignores `isLoading`; `shown = focused` if its id matches, else keep `shown` if its id matches, else `null` |
| `enterSign(direction, layoutDirection)` | `End` -> +1, `Start` -> -1, `None` -> 0, negated under RTL |

Poster record (`VIGD`, private class, one per `heldUrls` change): `outgoing: MutableState<OutgoingPoster?>` (image, `Animatable` offset and alpha, seeded from the previous record's still values so a mid-flight change retargets), still `Animatable` offset and alpha (start: `sign × shift`, 0), `ready: MutableState<Boolean>` set by the still's `onSuccess`, latched `crossfade` flag. A `LaunchedEffect(record)` waits for `ready` with `withTimeoutOrNull(POSTER_HOLD_MS)`, then runs both animations and sets `outgoing = null`; on timeout it snaps the still to offset 0, alpha 1 and drops the outgoing layer. The holder of the last shown `coil3.Image` follows the `PosterUrlHolder` + `SideEffect` pattern (`VIGD:690-708`) and is cleared when `heldUrls` is empty.

Draw order in `VideoDetailsPoster` (`VIGD:482-565`): player, outgoing layer, still, scrims. Offsets and alphas are read only inside `graphicsLayer { }`.

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change |
| ViewModel state and actions | N/A: `ContentListVM.onItemFocused` (`ui/feature/contentlist/vm/ContentListVM.kt:61-95`) and `FavoriteVM.onItemFocused` (`ui/feature/favorites/vm/FavoriteVM.kt:106-118`) unchanged |
| Screen and DI module | No DI change. Screen content only, see the next two rows |
| Compose content: catalogue | `ui/feature/contentlist/content/ContentListScreenContent.kt`: `rememberPanelDirectionTracker()` in `ContentListLayout` (`:127`); `direction = tracker.directionTo(state.selectedItem)` into `VideoItemGridDetails` (`:166-175`); `tracker::onCardFocused` threaded through `sectionItems` (`:203`, `:311`) and `sectionItem` (`:324`, `:356`) into `SectionRowContent` (`:387-405`) |
| Compose content: catalogue row | `ui/feature/contentlist/content/SectionRow.kt`: `onItemPositionFocused` on `SectionRowContent` (`:47`) and `ContentSectionCards` (`:107`); called inside the existing guard at `:159-161` with `PanelPosition(rowKey, index, item.id)` from `itemsIndexed` (`:151`) |
| Compose content: Favourites | `ui/feature/favorites/content/FavoriteScreenContent.kt`: tracker in `FavoriteScreenContentBody` (`:66`), `direction` into `VideoItemGridDetails` (`:99-104`), `onItemPositionFocused = tracker::onCardFocused` on `VideoGrid` (`:107-114`) |
| Compose content: grid | `core/ui/uikit/component/moviesList/VideoGridUIState.kt`: `onItemPositionFocused` on `VideoGrid` (`:76`), `VideoGridContent` (`:105`), `VideoGridSection` (`:328`) and `VideoGridItems` (`:399`); `VideoGridItems` calls it inside the guard at `:454-458` using the index `VideoGridRowItem` already passes (`:481`) |
| Compose content: panel | `VIGD`: `direction` on `VideoItemGridDetails` (`:228`), `VideoDetailsMedia` (`:284`), `VideoDetailsDescription` (`:357`), `VideoDetailsPoster` (`:441`), passed down at `:241`, `:251`, `:260`, `:265`, `:295`, `:312`; current `Column` of `VideoDetailsDescription` (`:368-435`) moves into a private body composable under `AnimatedContent`; `heldDescription` + `rememberHeldDescription`; poster record, outgoing layer between player (`:482-493`) and `PosterStill` (`:501-513`); still alpha at `:506` becomes `posterStillAlpha(...) * cardEnterAlpha(...)` plus `translationX`; `posterRequest` (`:585`) and `rememberPosterRequest` (`:579`) take `crossfade`; motion constants beside `POSTER_HOLD_MS` (`:74`) |
| Compose content: image | `core/ui/uikit/component/SkeletonAsyncImage.kt`: `onSuccess: (SuccessResult) -> Unit = {}` (`:20`), forwarded from the `AsyncImage` callback at `:47`; `PosterStill` (`VIGD:597`) passes it |
| New file | `core/ui/uikit/component/details/PanelDirection.kt`: `PanelDirection`, `@Immutable PanelPosition`, `panelDirection`, `enterSign`, `PanelDirectionTracker`, `rememberPanelDirectionTracker` |
| Resources | N/A: no strings or assets |

Unit tests the design makes possible (the spec's Test design owns names and counts):

| Test file | Covers |
|---|---|
| new `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/PanelDirectionTest.kt` | `panelDirection` table rows, `enterSign`, tracker: debounced jump uses shown and final, same-id refresh, unknown focus, stale details (AC-4, AC-5 rule) |
| `.../details/VideoDetailsPosterStateTest.kt` (additions only) | `heldDescription` cases (AC-6); `posterRequest(crossfade = false)` has no crossfade (AC-7); `cardEnterAlpha` over a rendered trailer (AC-8 rule) |

## Alternatives rejected

- Option B (VM carries index, hold and direction): changes `CommonAction.ItemFocused` and two VMs, duplicates the poster hold.
- Option C (shift the whole still): old and new move together, contradicts the owner decision of 2026-11-19.
- Direction by id lookup in `FocusNeighbourhood`: ambiguous when one film is in two rows.
- `AnimatedContent` around `PosterStill`: re-creates `SkeletonAsyncImage` per card, the 1.12.1 skeleton flash.
- Outgoing copy as a second `AsyncImage` from the memory cache: the key depends on the resolved size, a miss draws nothing for the first frames.
- Snapshot-state tracker: recomposes the screen content on every focus move.
- Changing `onItemFocused` to carry the index: breaks two androidTest suites and two unrelated `VideoGrid` callers.

## Risks

| # | Area | Risk | Lives in | Guard |
|---|---|---|---|---|
| R1 | Lifecycle | Outgoing layer or still drawn over a rendered trailer when cold details land near the 2 s gate (`ContentListVM.kt:64`, `:89-93`) | `VIGD:482-513`, `TrailerPreviewPlayer.kt:89`, `:136` | D11, D12; AC-8 device check, 3 repeats |
| R2 | Correctness | One frame of a warm new poster at full alpha before the enter starts | `remember(heldUrls)` record vs `rememberPosterRequest` (`VIGD:579`) | D9: start values set in the composition that changes the request |
| R3 | Correctness | 1.12.1 skeleton flash returns | `SkeletonAsyncImage.kt:30`, `PosterStill` `VIGD:597-614` | still never keyed or wrapped per card; outgoing is a painter; last image cleared on empty list; AC-7 |
| R4 | Focus and D-pad | A bounced focus (`FocusTargetState` redirect) records a wrong position | `SectionRow.kt:159`, `VideoGridUIState.kt:455` | callback only inside the existing guard |
| R5 | Recomposition | `VideoGridRowItem` remembers its focus modifier per `(itemIndex, item.id)` (`VideoGridUIState.kt:478`), so a non-stable callback goes stale | grid threading | pass the stable `tracker::onCardFocused` of a remembered tracker |
| R6 | Recomposition / main thread | Two full-width bitmaps and up to three text columns for 250 ms | `VIGD` poster and description | animated values only in `graphicsLayer`; text outgoing is at alpha 0 after 90 ms (ux), so a retarget's third column is invisible; AC-10 |
| R7 | Correctness | Wrong direction after focus restore or stale details | tracker `markShown` | unknown -> `None`; AC-4 tests, AC-5 device |
| R8 | Memory | Outgoing `coil3.Image` kept past its exit | poster record | cleared on every exit path in D12; at most one record alive |
| R9 | Correctness | A cold or failed new image keeps the outgoing picture static up to 3 s | `LaunchedEffect(record)` timeout | same cap as the poster hold; failed-URL frame stays with `detail-poster-fallback-empty-frame` |
| R10 | Correctness | Two different ids with equal poster URL lists: text moves, poster does not | `remember(heldUrls)` key | accepted, poster unchanged on screen |
| R11 | Baseline profile | New panel code paths are not in the shipped profile | `:baselineprofile` | no regeneration in this run |

## Open questions

none
