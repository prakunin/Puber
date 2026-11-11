# Brief: collection-detail-inplace-refresh

Base 0e4449e2. Analyst, 2026-11-11. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; this follows the analyst job layout.

kind: bug. size: M. tracks: none. Device verification: **yes** (ViewModel state, TV focus and grid scroll after a return).

Size reason: the code change is one VM plus tests, but it changes ViewModel state publishing and its visible result is TV focus; both are risk areas in `.flow/roles/analyst.md`.

## Request
CollectionDetail: after Details or the player returns any content change, the screen flashes a full-screen spinner and rebuilds the grid; focus lands on the first card at the top instead of the card the user opened.

## Root cause (confirmed by code trace)
1. `CollectionDetailVM.kt:98-102`: `onReturnedContentChanges` calls `loadItems()` for any non-empty change set.
2. `CollectionDetailVM.kt:69-70`: `loadItems()` always publishes `CollectionDetailViewState.Loading` before fetching.
3. `CollectionDetailScreenContent.kt:46-52`: `Loading` is a separate `when` branch, so the `is Content` branch is disposed. With it go `rememberReconciledItemFocus` (`:59-65`, focused id in `rememberSaveable(rowKey)` scoped to that branch, `FocusTargetState.kt:40-101`) and the `LazyVerticalGrid`'s default grid state (`:81`). The new `Content` composes fresh: first card, scroll 0.
4. Secondary: `CollectionDetailVM.kt:71` launches unsequenced, so two quick returns can publish out of order (research.md: Risks).

## Scope
In: a return with `Content` on screen refreshes in place (research.md: Options, option A): fetch, publish a new `Content`, no `Loading`; a single load job; a just-toggled heart is not reverted by the refresh.
Out: `Loading` on first load and Retry (unchanged); typed remap without a request (option B); hoisting UI state in the composable (option C); `CollectionDetailScreenContent.kt` changes.

## Acceptance criteria
- AC-1. Given `Content` is shown, When Details returns a non-empty change set, Then the VM publishes no `Loading` and the next state is a `Content` with the refetched items (unit test on the state sequence; replaces `CollectionDetailVMTest.kt:71-82`, red on base).
- AC-2. Given the grid scrolled so a card in row 3 or lower is focused, When the user opens it, toggles watched in Details and presses Back, Then the same card is focused, the scroll position is kept, no spinner shows, and the card shows the new watched state (device, 3 repeats; same for a bookmark toggle in Details and for Back from the player after playback).
- AC-3. Given the first load or `Error` with Retry, When loading starts, Then `Loading` is published first, as on base (unit test).
- AC-4. Given `Content` is shown, When the in-place refresh fails, Then the grid stays as it was and the error message is shown; the state does not become `Error` (unit test; existing `dispatchError`, `CollectionDetailVM.kt:37-43`).
- AC-5. Given a refresh is in flight, When a second return starts another refresh, Then only the second fetch's result is published (unit test with two `CompletableDeferred` fetches completing in reverse order).
- AC-6. Given a refresh is in flight, When the user toggles a card's heart and the fetch lands afterwards with the old saved value, Then the card keeps the toggled value (unit test; see Q2).
- AC-7. Given the composable shows `Content` with a non-first card focused, When a new `Content` with changed items replaces it (no `Loading` between), Then focus stays on that card, or on its neighbour if the card left the list (instrumented test in `CollectionDetailScreenContentFocusTest.kt`, the review-blind-1 F1 gap).
- AC-8. A null or empty change set triggers no fetch, and Back still forwards the merged `contentChanges` (`CollectionDetailVM.kt:150`); existing VM tests stay green.

## Risks
- Heart revert (AC-6): the fetched payload can predate a toggle made during the fetch. Awaiting `pendingMutations` (`CollectionDetailVM.kt:168-174`) before publishing is not enough on its own: a toggle that starts after the request was sent still gets the old server value. The ids toggled since the load began need their current `isSaved` carried into the new list (the narrow form of `HomeVM.kt:639-655`).
- Overlap with idea run `vm-mutation-queue` on `launchMutation`/`awaitPendingMutations` (`CollectionDetailVM.kt:154-174`): keep this change outside those two functions.
- Device-only: focus and scroll survival show only at runtime (AC-2); needs an emulator lease and install-preserve.

## Open questions
1. Refresh shape: A (silent refetch), B (typed remap plus refetch) or C (hoisted UI state)? Recommended A (research.md: Options). Blocks: no.
2. Heart-revert guard: carry the current `isSaved` for ids toggled during the load (after awaiting pending mutations), or only await pending mutations? Recommended carry plus await: awaiting alone leaves the race in Risks open. Blocks: no (AC-6 holds either way; the answer picks the mechanism).
3. On a failed in-place refresh, keep the stale grid and show the message? Recommended yes (AC-4, current `dispatchError`). Blocks: no.
