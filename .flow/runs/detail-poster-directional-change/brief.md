# Brief: detail-poster-directional-change

Base 01d1d305. Analyst, 2026-11-20. Builds on `research.md` (cited as research.md: section); its code references were re-read at base and hold. No brief template exists in the plugin; this follows the analyst job layout.

kind: feature. size: M. tracks: ui. Device verification: **yes** (TV UI, focus-driven motion, trailer SurfaceView interplay; ACs show only at runtime).

Size reason: one goal, but it crosses the shared panel component and two screens' focus wiring, carries a design decision (motion numbers), and its regressions live in the 1.12.1/1.12.2 risk paths (held poster, SurfaceView trailer).

## Request
Owner decisions in `state.md` (2026-11-19, 2026-11-20): on a card change the detail panel's poster and its text (title, ratings, facts, duration, plot) shift 40-60 dp and crossfade over about 250 ms; the new content enters from the side the carousel moves toward, the old leaves the opposite way and fades. No full-width slide, no parallax.

## Current behaviour (confirmed by code trace)
| Part | Today | Where |
|---|---|---|
| Text | old text, then hard cut to skeleton (`placeholder(visible = isLoading)`, plot replaced by bars), then hard cut to new text | `VideoItemGridDetails.kt:357-437`; `Loading` published at `ContentListVM.kt:74` (after 150 ms debounce) and `FavoriteVM.kt:109-111` (at once) |
| Poster | held through `Loading` (3 s cap), in-place Coil crossfade 200 ms, no direction | `VideoItemGridDetails.kt:464`, `:585-594`, `:681-708` |
| Direction | not known to the panel: rows report `onItemFocused(item)` without index or row | `SectionRow.kt:151-161`, `VideoGridUIState.kt:443-458` |
| Trailer | cleared the moment focus moves, republished at earliest 2 s later | `ContentListVM.kt:64`, `:68`, `:89-93` |

Screens with the panel: catalogue (full-bleed, trailer) and Favourites (side-by-side, no trailer). The details hero shows one item per screen and is out of scope (research.md: 3, "Which screens have the panel").

## Scope
In: research.md option A (research.md: 6). UI-only direction tracker in `ContentListScreenContent` and `FavoriteScreenContent` keyed on (row key, index) from the focus event; pure `panelDirection(from, to)`; text held through `Loading` and animated on `id`; poster gets an outgoing layer plus a directional enter on the single still.
Out: VM or `CommonAction.ItemFocused` contract changes (option B); details hero; Home `HeroCarousel`; Compose UI test harness for the panel.

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

## Plan sketch
1. Tracker: report index with the focus in `SectionRow.kt:151-161` and `VideoGridUIState.kt:443-458` (UI callback only, the action stays `ItemFocused(item)`); hold `(rowKey, index)` for the shown and focused id in a small remembered state in `ContentListScreenContent.kt:166` and `FavoriteScreenContent.kt:99`; new pure `panelDirection` next to it.
2. `VideoItemGridDetails` `:228`: new `direction` parameter, default `None`.
3. Text: hold rule for `VideoDetailsDescription` input (pure function, same cap as `POSTER_HOLD_MS` `:74`), then `AnimatedContent` keyed on `id` with `slideInHorizontally + fadeIn togetherWith slideOutHorizontally + fadeOut`, `SizeTransform(clip = false)`, offsets read in `graphicsLayer`.
4. Poster: keep the single `PosterStill` (`:501`); on a card change draw a transient outgoing copy of the previous URL (plain `AsyncImage`, memory cache, no skeleton) that slides and fades out, then leaves composition; enter offset + alpha on the still's `graphicsLayer`, started on the new image's success; Coil crossfade off for card changes (`posterRequest` `:585-594`). Draw order stays: player, then images, then scrims (`:482-565`).
5. ux-designer sets the shift numbers (poster 40-60 dp, text at the lower end) and easing before the spec is frozen.

## Risks
| Risk | Guard |
|---|---|
| A layer over a rendered trailer (`trailer-video-hidden-regression`) | outgoing layer leaves composition when its transition ends; `posterStillAlpha` keeps precedence; AC-8 |
| 1.12.1 skeleton flash returns: a re-created `SkeletonAsyncImage` resets `hasShownImage` (`SkeletonAsyncImage.kt:30`) | still never wrapped in `AnimatedContent` or keyed per card; outgoing copy is not a `SkeletonAsyncImage`; AC-7 |
| Previous card's text visible while the new card is focused (up to 3 s) | same trade-off as the poster hold (source run R7); AC-6 cap |
| Direction ambiguity (same film in two rows, focus restore) | key on the focus event's row key, not an id lookup; unknown -> None; AC-4, AC-5 |
| Two full-width bitmaps and two text columns for 250 ms on TV GPU | animated values read only in `graphicsLayer` lambdas; owner check AC-10 |
| Text and poster start apart on a cold image | accepted by Q3 default below; they coincide on prefetched cards |

## Overlap with `detail-poster-fallback-empty-frame`
That idea run (bug: failed primary URL leaves an empty frame until the fallback loads, `SkeletonAsyncImage.kt:42-62`, `PosterStill`) edits the same still and `onError`/`urlIndex` path (`VideoItemGridDetails.kt:465-466`, `:508-511`) that step 4 reworks. Recommendation: sequence, this run first, fallback run triaged against the new code afterwards. Fold would add a separate decision (keep previous picture vs skeleton) and a bug fix to a UI feature run. This run should only not make it worse: the outgoing layer stays until the incoming still succeeds or the hold cap ends, which also covers a failed primary URL on a card change; the fallback run then keeps the first-load and same-card cases.

## Open questions
1. On a row change (Up/Down), shift vertically or only crossfade? Recommended crossfade only (research.md: 8 Q1; vertical shift bares the top edge of top-aligned media). Blocks: no (AC-4, AC-5 written for it).
2. While the new details load, keep the old text up to 3 s instead of the skeleton? Recommended yes (research.md: 8 Q2). Blocks: yes (AC-6 changes if no).
3. Fold `detail-poster-fallback-empty-frame` into this run? Recommended no, sequence after this run (see Overlap). Blocks: no.

Defaults taken without a question: text does not wait for the new image (research.md: 8 Q3); shift numbers come from ux-designer (research.md: 8 Q4).
