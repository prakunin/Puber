# Detail panel poster and text change with a directional shift and crossfade

Status: approved
Date: 2026-11-20
Slug: detail-poster-directional-change
Kind: feature
Size: M
Tracks: ui

## Problem / Why

Owner decisions in `state.md` (2026-11-19, 2026-11-20): on a card change the detail panel's poster and its text (title, ratings, facts, duration, plot) shift 40-60 dp and crossfade over about 250 ms; the new content enters from the side the carousel moves toward, the old leaves the opposite way and fades. No full-width slide, no parallax.

| Part | Today | Where |
|---|---|---|
| Text | old text, then hard cut to skeleton (`placeholder(visible = isLoading)`, plot replaced by bars), then hard cut to new text | `VideoItemGridDetails.kt:357-437`; `Loading` published at `ContentListVM.kt:74` (after 150 ms debounce) and `FavoriteVM.kt:109-111` (at once) |
| Poster | held through `Loading` (3 s cap), in-place Coil crossfade 200 ms, no direction | `VideoItemGridDetails.kt:464`, `:585-594`, `:681-708` |
| Direction | not known to the panel: rows report `onItemFocused(item)` without index or row | `SectionRow.kt:151-161`, `VideoGridUIState.kt:443-458` |
| Trailer | cleared the moment focus moves, republished at earliest 2 s later | `ContentListVM.kt:64`, `:68`, `:89-93` |

Screens with the panel: catalogue (full-bleed, trailer) and Favourites (side-by-side, no trailer). The details hero shows one item per screen and is out of scope (research.md: 3, "Which screens have the panel").

## Acceptance criteria

- AC-1. Given the catalogue panel shows the card at index i of a row and the card at i+1 is warm, When the user presses Right, Then the new poster and the new text enter shifted from the end side and fade in, the old poster and text leave toward the start side and fade out, over about 250 ms (screencap burst on the emulator).
- AC-2. Given the same, When the user presses Left, Then AC-1 holds mirrored.
- AC-3. Given AC-1 and AC-2 set-ups on Favourites (side-by-side layout), Then both hold there.
- AC-4. Given `panelDirection(from, to)`, Then: same row, higher index -> End; same row, lower index -> Start; different row, unknown `from` or unknown `to` -> None; same id -> no transition (unit test, pure function). A debounced jump across several cards uses the shown card and the final card.
- AC-5. Given a row change, first focus on the screen, Back from Details, or tab re-entry, When the panel changes card, Then it crossfades with no shift; a refresh that republishes the same `id` (`ContentListVM.kt:161-169`) runs no transition (device observation; AC-4 covers the rule).
- AC-6. Given the panel shows loaded details, When focus moves and the new details are `Loading`, Then the previous text stays (no skeleton) until the new details land or 3 s pass; the skeleton shows only before the first loaded details and after the cap (unit test on the hold rule beside `heldPosterUrls`; device observation with a cold card).
- AC-7. Given any card change, Then no skeleton flashes over the poster and the previous poster is held through `Loading` as in 1.12.1 (`VideoDetailsPosterStateTest`, `SkeletonAsyncImageVisibilityTest` green, deliberate edits only to `posterRequest`/`PosterCrossfade` cases; device observation).
- AC-8. Given a card change has finished its transition, When the 2 s trailer gate opens, Then the trailer video is visible (not black, not hidden) and the outgoing poster layer is no longer in composition (device observation, 3 repeats, trailer-enabled card).
- AC-9. Given the details screen hero, Then it looks and behaves as at base (default `direction = None` path; device observation).
- AC-10. The motion reads as smooth and directional on the emulator at 1080p, with no edge strip visible in the full-bleed layout (owner judges).
- AC-11. The full check passes in the worktree.

## Out of scope

In: research.md option A (research.md: 6). UI-only direction tracker in `ContentListScreenContent` and `FavoriteScreenContent` keyed on (row key, index) from the focus event; pure `panelDirection(from, to)`; text held through `Loading` and animated on `id`; poster gets an outgoing layer plus a directional enter on the single still.
Out: VM or `CommonAction.ItemFocused` contract changes (option B); details hero; Home `HeroCarousel`; Compose UI test harness for the panel.

## Design

### Architect

Base 01d1d305, worktree read at that commit. Paths relative to `app/src/main/java/com/kino/puber/`; `VIGD` = `core/ui/uikit/component/details/VideoItemGridDetails.kt`. Motion numbers, curves, draw order and the text start-edge clip come from `design-ux-designer.md` ("Motion numbers", "Edges and clipping") and are not restated here.

#### Decisions

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

#### Components and data flow

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

#### Changes by layer

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

#### Alternatives rejected

- Option B (VM carries index, hold and direction): changes `CommonAction.ItemFocused` and two VMs, duplicates the poster hold.
- Option C (shift the whole still): old and new move together, contradicts the owner decision of 2026-11-19.
- Direction by id lookup in `FocusNeighbourhood`: ambiguous when one film is in two rows.
- `AnimatedContent` around `PosterStill`: re-creates `SkeletonAsyncImage` per card, the 1.12.1 skeleton flash.
- Outgoing copy as a second `AsyncImage` from the memory cache: the key depends on the resolved size, a miss draws nothing for the first frames.
- Snapshot-state tracker: recomposes the screen content on every focus move.
- Changing `onItemFocused` to carry the index: breaks two androidTest suites and two unrelated `VideoGrid` callers.

#### Risks

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

#### Open questions

none

### UX designer

ux-designer, 2026-11-20, worktree at 01d1d305. Code paths are relative to `app/src/main/java/com/kino/puber/`; the panel is `core/ui/uikit/component/details/VideoItemGridDetails.kt` (cited as `VIGD`).

#### Governing stand

- Geometry: `docs/design/catalogue/` governs the catalogue panel. Its "Agreed sizes" table (panel 67 %, description 60 % of the width, 15 dp side padding, scrim edge 10 % at 90 %, 50 dp bottom fade) applies unchanged. This run changes no size, so it does not need a designing-a-screen session.
- Favourites (side-by-side layout, `DescriptionLayout.Default`, 48 dp left gradient `VIGD:532-547`) has no stand. Its geometry stays as at base.
- Motion: no stand states motion (`catalogue/README.md`, `content/README.md`). The numbers below are the agreed numbers once the owner accepts them on the emulator (brief.md#AC-10). Then add a short "Motion" section to `docs/design/catalogue/README.md` with the final values in this same run, so the stand does not drift from the code.

#### Motion numbers

Values are in dp at 1080p (1 dp = 2 px). "Forward" means the direction from brief.md#AC-4: the new content enters from that side.

| Part | Shift | Translation | Alpha | Draw order |
|---|---|---|---|---|
| Poster, incoming (the still) | 48 dp from the forward side to 0 | 250 ms, `FastOutSlowInEasing` | 0 to 1 over 150 ms, `LinearOutSlowInEasing`, no delay | above the outgoing layer |
| Poster, outgoing (transient copy) | 0 to 48 dp toward the opposite side | 250 ms, `FastOutSlowInEasing` | 1 to 0 over 250 ms, `LinearEasing` | below the still, above the player |
| Text, incoming | 40 dp from the forward side to 0 | 250 ms, `FastOutSlowInEasing` | 0 to 1 from 90 to 250 ms (`delayMillis = 90`, 160 ms, `LinearOutSlowInEasing`) | above |
| Text, outgoing | 0 to 40 dp toward the opposite side | 250 ms, `FastOutSlowInEasing` | 1 to 0 over 0-90 ms, `FastOutLinearInEasing` | below |

- The poster crossfades with overlap: two pictures blend cleanly. The text fades through: the old column is gone before the new one shows, so two titles and two plots are never readable on top of each other. Both move on the same 250 ms translation, so they read as one motion when they start together (warm card).
- Poster shift 48 dp sits in the owner's 40-60 dp range and equals the Favourites left gradient width, which hides the bared strip there. Text 40 dp is the floor of the range: text is smaller than the picture and the same distance reads larger on it.
- No stagger between text blocks. Title, ratings, facts, duration and plot move as one layer: the stand pins them in one column ("pinned, not flowed"), and a stagger would stretch the change past 250 ms.
- Direction `None` (row change Up/Down, first focus, Back from Details, tab re-entry; brief.md#AC-5): same durations and alpha curves, shift 0. Same `id`: no transition at all.

#### Edges and clipping

| Edge | What is bared | Treatment |
|---|---|---|
| Catalogue poster, left | up to 48 dp at the media's left edge | lies under the scrim's 0-10 % band (67.5 dp of a 675 dp frame at alpha 1 to 0.9, drawn after the still `VIGD:515-531`). Nothing to add |
| Catalogue poster, right (screen edge) | a strip as wide as the outgoing layer has moved, covered only by the fading-in still | kept small by the still's 150 ms alpha: the still is opaque by the time the outgoing layer has moved about 40 dp. The owner judges it under brief.md#AC-10 |
| Favourites poster, left | up to 48 dp | under the existing 48 dp gradient |
| Text, start side | up to 40 dp past the column start (15 dp padding in the catalogue, 16 dp in Favourites) | clip the animated text to the description's start edge only. Do not clip the bottom: a catalogue plot has no `maxLines` and deliberately runs below the panel (`catalogue/README.md`, "Agreed sizes"). Without the clip the text draws into the 60 dp closed rail (`ui/feature/main/component/MainScreenComponent.kt:291-298`, no clip there) |
| Text, end side | none: in the catalogue it lies over the picture, in Favourites over the poster's gradient | no clip |

The poster box keeps its `clipToBounds()` (`VIGD:457`); the vertical overflow of the 16:9 catalogue frame is unchanged because the shift is horizontal only.

#### Rapid focus changes

| Case | Behaviour |
|---|---|
| A new card lands during a transition | Retarget. The in-flight incoming becomes the outgoing and continues from its current offset and alpha toward the side opposite the new direction; the older outgoing leaves composition at once. Never more than two poster layers and two text columns. |
| Direction reverses mid-flight (Right, then Left) | Same rule: the in-flight content leaves toward the end side, the new one enters from the start side. |
| Held key in the catalogue | No transitions while the key repeats: the 150 ms debounce (`ui/feature/contentlist/vm/ContentListVM.kt:61-95`) publishes only the final card, and the direction is shown card to final card (brief.md#AC-4). |
| Held key in Favourites | No debounce (`FavoriteVM.kt:106-118`); warm details can land faster than 250 ms apart and chain retargets. Accepted under the retarget rule; the owner judges it under AC-10. |
| Text and poster start at different moments (cold image) | Each runs its own 250 ms when its content is ready (brief Q3 default). |

#### Held text and skeleton

- During `Loading` the previous text stays at full alpha, with no dimming, until the new details land or 3 s pass (brief.md#AC-6, same cap as `POSTER_HOLD_MS` `VIGD:74`).
- Text to skeleton after the cap, and skeleton to the first text: direction `None` (fade only, 250 ms).
- First load of the screen: skeleton as at base, then fade only.

#### What stays static

Static: the panel ground, the full-bleed scrim and the bottom fade (`VIGD:515-565`), the Favourites left gradient, the trailer player and the still's trailer fade (`posterStillAlpha`, 300 ms `POSTER_FADE_OUT_MS`, unchanged), the section heading and the rows below.

Moves with the text: everything inside `VideoDetailsDescription`, including the rating badges. The panel has no buttons or labels of its own.

#### Focus

N/A: the change adds no focusable element and does not move focus. The panel is not focusable. Focus stays on the card in the row throughout the transition.

#### Shared constants

| Constant | Rule |
|---|---|
| Motion values above | New private constants in `VIGD` next to `POSTER_HOLD_MS`, grouped and named once (for example `PanelMotion`: `DURATION_MS = 250`, `POSTER_SHIFT = 48.dp`, `TEXT_SHIFT = 40.dp`, `POSTER_FADE_IN_MS = 150`, `TEXT_FADE_OUT_MS = 90`), so the AC-10 tuning is a one-line change. |
| `POSTER_CROSSFADE_MS = 200`, `PosterCrossfade` (`VIGD:72`, `:681-688`) | Not retuned to 250. A card change no longer goes through it (brief plan step 4). It stays for the same-card cases (first image, fallback URL) that the `detail-poster-fallback-empty-frame` run owns. |
| `POSTER_FADE_OUT_MS = 300` | Unchanged: the trailer fade is not a card change. |
| `DescriptionLayout`, `MediaScrim` | Unchanged. Shift distances do not go into them: the motion is the same on both screens. |
| `direction` parameter on `VideoItemGridDetails` / `VideoDetailsMedia` | Default `None`. The details hero (`ui/feature/details/component/DetailsScreenContent.kt:189-231`) passes nothing and keeps its base look (brief.md#AC-9). |

#### Open

| ID | Question | Recommended |
|---|---|---|
| D1 | Poster 48 dp / text 40 dp / 250 ms, or another point in the 40-60 dp range | As above; the owner confirms on the emulator under AC-10 |
| D2 | The right screen-edge strip on the catalogue poster during the first ~150 ms | Accept if the owner sees no strip. Otherwise move the still's alpha to 100 ms rather than adding an overscan |

## Test design

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

#### 1. Risk profile

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

#### 2. Traceability

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

#### 3. Bugs

N/A: kind feature. The first test to write is `heldDescription_returnsPrevious_whenLoadingAfterLoaded` (the only behaviour that changes against base, `VideoItemGridDetails.kt:357-437` shows the skeleton on `Loading`).

#### 4. Device checks

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

#### 5. Perf track

N/A: perf track off (tracks: ui); AC-10 is the owner's judgement, M9.

#### UNTESTABLE

none

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Row change: vertical shift or crossfade only? | no | answered | crossfade only |
| Q2 | Keep the old text up to 3 s while new details load? | yes | answered | yes |
| Q3 | Fold detail-poster-fallback-empty-frame? | no | answered | no, sequence after this run |
| D1 | Poster 48 dp / text 40 dp / 250 ms? | no | answered | yes as designed; the owner judges the feel on the TV (AC-10) |
| D2 | Right-edge strip on the catalogue poster | no | answered | accept unless seen on the emulator; else poster fade-in 100 ms |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Short shift (40-60 dp) plus crossfade, about 250 ms, from the side the carousel moves toward | owner |
| 2026-11-20 | Text (title, description, metadata) changes smoothly with the poster | owner |
| 2026-11-20 | Q1-Q3, D1, D2 as recommended; approach option A | owner (standing instruction: recommended) |
| 2026-11-20 | AC-10 is judged by the owner on the TV after release, not on the emulator; qa records emulator observations | owner (standing instruction: recommended) |
| 2026-11-20 | Gate 1: approved | owner (standing instruction: recommended) |
