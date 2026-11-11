# Design: grid-focus-restore-after-back

Architect, 2026-11-11, base e9184b79. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | CollectionDetail mechanism | `rememberReconciledItemFocus` (`core/ui/uikit/component/moviesList/FocusTargetState.kt:40`), wired exactly as Bookmarks (`ui/feature/bookmarks/component/BookmarksScreenContent.kt:73-79,118-124,136-147`); `rowKey = "collection_detail"`, `isTargetRow = true`, `requestAfterFrame = true`, `onRowEmpty = {}` | Items are already `VideoItemUIState`. The helper requests the saved id on a fresh composition (`FocusTargetState.kt:234-239`), redirects a default landing to it (`:142-170`), and starts on the first card when nothing is saved (`:49`), which gives AC-2 and AC-3. A constant key is enough: each root screen has its own saveable registry |
| D2 | CollectionDetail dialog focus restorer (`TvDialogFocusRestorer`, as Bookmarks `:80-98`) | Not added | No AC asks for it; dialog close behaviour stays as on base |
| D3 | Collections memory shape (Q3: local, id-keyed) | New `CollectionsFocusMemory` in `ui/feature/collections/component/CollectionsFocusMemory.kt`: saveable `lastFocusedId: Int?` + `lastFocusedIndex: Int`, a `restoreId` latched once at construction, `pendingEntry = lastFocusedId != null`, and `suspend fun restoreEntry()` with up to 3 frame-spaced attempts. Plain class with injected lambdas, like `GridFocusMemory` (`ui/feature/showall/content/ShowAllScreenContent.kt:181-251`) | A latched target survives the default landing that writes a wrong id first (the race `ContentFocusHandoff.kt:36-45` describes). A plain class is JVM-testable |
| D4 | Collections: one mechanism with the grid `focusRestorer` (brief risk "double restore") | Keep `focusRestorer(restoreFocus)` on the grid as the only rail-close path; the entry request runs only while `pendingEntry` and ends for good after success or 3 attempts. `restoreId` is never moved by focus changes | Rail close keeps base behaviour (AC-6): `ContentFocusHandoff.kt:123` restores the grid's saved child. The entry request cannot fire again on later `LocalContentFocusActive` flips |
| D5 | Collections restore target | `resolveCollectionRestoreId(id, index, ids)`: id if present, else the card at `index` clamped, else null; `restoreId = resolved ?: first id` so the requester is always attached when the list is non-empty (base attaches index 0, `CollectionsScreenContent.kt:58,75`) | AC-4. Reusing `resolveRestoreId` (`ShowAllScreenContent.kt:254`) would mean retyping a Show all helper (Out of scope: shared helper) |
| D6 | When the Collections entry request may fire | `LaunchedEffect(contentFocusActive) { memory.restoreEntry() }`; each attempt waits a frame, returns if `LocalContentFocusActive` is false or `pendingEntry` was cleared; `onCardFocused(id == restoreId)` clears `pendingEntry` | AC-5: the rail keeps `LocalContentFocusActive` false while open (`ui/feature/main/component/MainScreenComponent.kt:138`); a pending entry resumes when the rail closes |
| D7 | Show all live-path fallback C | `ShowAllScreenContent.kt:226` becomes `if ((pendingEntry \|\| focusedCardLeft) && contentFocusActive()) requestGrid()` | AC-7; `focusedCardLeft` is already computed at `:221` |
| D8 | Bookmarks and History | No code change unless the AC-8 baseline shows the symptom (state.md Q1); then a design addendum before the plan | Q1 |
| D9 | Instrumented seam (AC-9) | Tests compose `CollectionsScreenContent` / `CollectionDetailScreenContent` under a `SaveableStateHolder`, dispose and recompose them; cards are found by title text. No production test tags | Mirrors `EpisodeSchedulePushBackFocusTest`; both composables are `internal` |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A |
| ViewModel state and actions | N/A |
| Screen and DI module | N/A |
| Compose content | See below |
| Resources | N/A |

Compose content:

- `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:63-78`: inside the `Content` branch call `rememberReconciledItemFocus` (D1). Grid modifier gains `.focusRestorer(itemFocus.focusRequester).onFocusChanged { itemFocus.rowHasFocusRef[0] = it.hasFocus }`. The card at `:72` gets `focusRequester(itemFocus.focusRequester)` when `item.id == itemFocus.targetItemId`, and `onFocusChanged { if (it.isFocused) itemFocus.onItemFocused(item.id) }`.
- New `ui/feature/collections/component/CollectionsFocusMemory.kt`: `internal class CollectionsFocusMemory(lastFocusedId: MutableState<Int?>, lastFocusedIndex: MutableState<Int>, ids: () -> List<Int>, contentFocusActive: () -> Boolean, requestRestore: () -> Boolean)` with `restoreId` (read once without observation, as `ShowAllScreenContent.kt:189-193`), `onCardFocused(id, index)`, `suspend fun restoreEntry()`; `internal fun resolveCollectionRestoreId(id: Int?, index: Int, ids: List<Int>): Int?`; a private `@Composable rememberCollectionsFocusMemory(collections, restoreFocus)`.
- `ui/feature/collections/component/CollectionsScreenContent.kt:57-90`: `focusedItemIndex` (`:58`) and `savedItemFocusRequester` (`:59`) are replaced by the memory and a `restoreFocus` requester. `focusRestorer(restoreFocus)` at `:69`; requester on the card where `collection.id == memory.restoreId` (`:75-87`); `onFocusChanged` calls `memory.onCardFocused(collection.id, index)` (`:88`). `onLoadMore` at `:72-74` is untouched.
- `ui/feature/showall/content/ShowAllScreenContent.kt:225-226`: D7, comment updated to cover the live path.
- `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt:241-249`: see Open question 1.

## Alternatives rejected

- Shared `GridFocusMemory` for all grids (option B): Out of scope per brief.
- Grid anchors in `FlowComponent` (option C): Out of scope per brief.
- Widening `rememberReconciledItemFocus` to any id type: touches every row and VideoGrid caller (state.md Q3).
- Collections entry request without a latched target: the default landing rewrites the memory before the first attempt.
- Removing the Collections `focusRestorer` and answering the handoff in the memory (as Show all `:234-241`): replaces a rail-close path that works on base (AC-6).

## Risks

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | A default landing after Back reaches the grid before the restore. Collections relies on the latched `restoreId` and up to 3 attempts; CollectionDetail on the first-focus redirect | `CollectionsFocusMemory.kt` (new); `FocusTargetState.kt:142-170` |
| R2 | Focus / lifecycle | CollectionDetail reloads through `Loading` when Details returns content changes (`ui/feature/collections/detail/vm/CollectionDetailVM.kt:69-70,98-102`). The `Content` branch is disposed with its `rememberSaveable` memory and grid scroll, so focus lands on the first card. Not covered by any AC | `CollectionDetailVM.kt:98-102`, `CollectionDetailScreenContent.kt:46` |
| R3 | Focus / D-pad | Double restore in Collections: entry request, grid `focusRestorer` and the tab handoff (`ContentFocusHandoff.kt:116-131`) all aim at a card. D4 bounds the entry request to one pending window; all three target the same card | `CollectionsFocusMemory.kt`, `CollectionsScreenContent.kt:69` |
| R4 | Focus / data shape | After process death the saved id may not be on page 1; the clamped index can be outside the composed viewport, the 3 attempts fail and focus stays where the default enter put it (Collections has no fallback C) | `resolveCollectionRestoreId` |
| R5 | Focus | Live fallback C focuses the grid while content focus is active even if the user's focus left by other means in the same frames; `focusedCardLeft` needs `inGrid` (`ShowAllScreenContent.kt:209-217`) | `ShowAllScreenContent.kt:221-226` |
| R6 | Recomposition | CollectionDetail reads `focusedItemId` in composition (`FocusTargetState.kt:76-80`), so each card focus recomposes the grid content, as Bookmarks does today. Collections reads `restoreId`, which changes only at construction | `CollectionDetailScreenContent.kt` |
| R7 | Rollout | Same-file overlap in `ShowAllScreenContent.kt` `reconcile()` with `showall-first-entry-rail-focus` and `showall-entry-restore-foreign-landing` | `ShowAllScreenContent.kt:219-227` |

## Open questions

1. AC-7 says the existing `GridFocusMemoryTest` cases stay green, but `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid` (`GridFocusMemoryTest.kt:241-249`) asserts the opposite of AC-7 and turns red with D7. Recommended: amend AC-7 so that this case is replaced by the AC-7 case (renamed, expecting `restore:6:false` x3 then `grid`), all other cases unchanged. Blocks: yes.
2. R2: refresh CollectionDetail in place when content is already shown (no `Loading` on `onReturnedContentChanges`) so focus survives a Details change? Recommended: no in this run; record a deferred idea, since no AC covers it. Blocks: no.

## Delta after baseline

Input: device baseline `qa-report.md#baseline` (evidence `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md`). Collections restores the opener and keeps it on rail close on base; CollectionDetail has no focus after Back from either origin; Bookmarks and History are not affected.

### Why Collections restores on base

Root cause #2 of the brief holds that nothing calls `requestFocus()` after the fresh composition. True for the screen, wrong for the chain:

| Step | Code |
|---|---|
| The saved index survives the root push and comes back with the tab subtree | `ui/feature/collections/component/CollectionsScreenContent.kt:58` (`rememberSaveable`), tab holder `core/ui/navigation/component/TabComponent.kt:146-153` |
| The fresh composition attaches the grid's fallback requester to the card at that index | `CollectionsScreenContent.kt:59,75,82-83` |
| After a root Back the host moves focus into the tab content from above (not a card request). Device fact: before its fix Show all landed by default enter inside the grid (tab-stack-lost-on-root-push `qa-report.md` AC-2); here, 4 of 4 runs focused a card without a key press | Host boxes `ui/feature/main/component/MainScreenComponent.kt:296-302`, `core/ui/navigation/component/FlowComponent.kt:613-620`. Which call issues the entry is not identified (Open question 3) |
| That entry reaches the grid's `focusRestorer`. `restoreFocusedChild()` has nothing after a fresh composition, so `onEnter` falls back to the requester, which sits on the saved card | `CollectionsScreenContent.kt:69` |
| Rail close goes through the same restorer: the handoff calls `restoreFocusedChild()`/`requestFocus()` on the tab box | `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:123-124` |

Show all lacked the fallback requester then, hence the default-enter landing. CollectionDetail differs on both counts: it is a root screen whose host gets no entry after Back (the baseline shows no focus at all), and it has no restorer or requester (`ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:63-78`). D1 does not depend on a host entry: the helper requests the saved card itself (`core/ui/uikit/component/moviesList/FocusTargetState.kt:234-239`).

Collections data never shifts the index: the VM only appends pages (`ui/feature/collections/vm/CollectionsVM.kt:68-72`) and has no reload or removal path.

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D10 | Collections production change (D3-D6) | Withdrawn: no `CollectionsFocusMemory`, `CollectionsScreenContent.kt` unchanged | The base chain meets AC-1 and AC-6 on the device. A second entry request is the double-restore class (R3) with no AC left to pay for it |
| D11 | Collections coverage | One instrumented guard that models the host entry (an ancestor `requestFocus()` after the recomposition), green on base and after; AC-1 and AC-6 stay device checks | Locks the chain the brief misread. A direct request on the first card does not pass through the grid's `onEnter`, so the earlier "default landing" variant would be red on base for a reason the device never shows |
| D12 | AC-4 | Proposed drop (AC delta below) | Append-only list: the saved index names the same collection after appends; the "collection gone" branch has no producer |
| D13 | CollectionDetail (D1) and Show all live fallback C (D7) | Unchanged | Baseline reproduces the CollectionDetail symptom from both origins; AC-7 is independent of the baseline |

### AC delta (proposal for the analyst-owned AC section)

| AC | Change | Proposed wording |
|---|---|---|
| AC-1 | Becomes a regression guard | Given the Collections tab scrolled down with a card in a lower visible row focused, When OK opens CollectionDetail and Back returns, Then the same collection card is focused, as on base (device, 3 repeats). |
| AC-4 | Dropped | N/A |
| AC-5 | Collections rail clause dropped (Collections gets no pending request) | Given CollectionDetail with its context menu dialog open, When its items change, Then focus stays in the dialog. |
| AC-6 | Unchanged (already "no regression from base") | N/A |
| AC-9 | Split: CollectionDetail red, Collections guard | Instrumented tests for CollectionDetail simulate the disposal and recomposition (as `EpisodeSchedulePushBackFocusTest` does); they are red on base and green after the change. A Collections instrumented test simulates the same recomposition followed by a focus entry from an ancestor, and is green on base and after the change. |
| Root cause #2 (brief and spec Problem) | Corrected | Collections keeps a saveable index and puts the `focusRestorer` fallback requester on that card (`CollectionsScreenContent.kt:58,69,75-88`). After a root Back the host moves focus into the tab content, the restorer's `onEnter` falls back to that requester, and the opener card is focused (device baseline, 4 of 4). Collections is not affected. |

AC-2, AC-3, AC-7 and AC-8 are unchanged.

### Test-design rows

| Row | Becomes |
|---|---|
| `collectionsFocusReturnsToTheFocusedCardAfterReentry` (P0 red) | Replaced by `collectionsHostEntryAfterReentryFocusesTheFocusedCard` (AC-1, AC-9; instrumented; P0 guard) |
| `collectionsFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | Dropped (D11) |
| `restoreEntry_*` (7 unit rows) | Dropped (no class) |
| `resolveCollectionRestoreId_*` (4 unit rows) | Dropped (AC-4) |
| `collectionsFocusReturnsToTheSameCollectionAfterPagesAppendBeforeReentry`, `collectionsFocusMovesToTheCardAtTheSameIndexWhenTheCollectionLeftBeforeReentry` | Dropped (AC-4) |
| `collectionsPendingRestoreDoesNotTakeFocusFromTheRail` | Dropped (AC-5 clause) |
| `collectionsRailCloseKeepsFocusOnTheLowerRowCard` | Dropped; AC-6 stays Manual 6 on the worktree APK (no Collections code changes) |
| Risk profile rows R1 (Collections half), R3, brief `onLoadMore` row | Withdrawn with D10 |
| Manual 2 | After-half is a guard: same card as base |
| Manual 6 | AC-6 only (the AC-5 rail clause is gone) |
| CollectionDetail, Show all, AC-3 and AC-8 rows | Unchanged |

### Risks (delta)

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | Narrowed to CollectionDetail | `FocusTargetState.kt:142-170` |
| R3, R4 | | Withdrawn with D10; R4 is base behaviour | |
| R8 | Focus / lifecycle | AC-1 rests on an implicit chain: an unidentified host entry plus the grid's restorer fallback. A change to Main's focus entry after a root Back would break Collections silently; the guard covers only the part below the host | `MainScreenComponent.kt:296-302`, `CollectionsScreenContent.kt:69,75-88` |
| R9 | Test seam | Whether an ancestor `requestFocus()` runs the nested grid's `onEnter` in ui 1.12.0 is unverified (research.md §5). A guard red on base is a seam finding for the conductor, not a production defect; AC-1 on the device governs | `CollectionsScreenContentFocusTest` (new) |

### Open questions (delta)

3. Which call moves focus into the Main tab content after a root Back (R8)? Recommended: not in this run; the device result is what AC-1 needs. Record as a deferred idea if the owner wants the chain made explicit. Blocks: no.
4. Accept the AC delta above (AC-1, AC-4, AC-5, AC-9 and root cause #2)? Recommended: yes. Blocks: yes (Task 2 of the plan and the qa red checkpoint follow it).

## Addendum after verify round 1

Input: `qa-report.md#round-1` defects D1 and D2, worktree HEAD 5ea2899e. Lines in this section are at HEAD 5ea2899e; `CollectionDetailScreenContent.kt` is `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`, the test is `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`.

### D1: the default-landing test models a producer the device does not have

| Step in the test | Code |
|---|---|
| The fresh composition launches the entry request, which waits one frame (`requestAfterFrame = true`) | `core/ui/uikit/component/moviesList/FocusTargetState.kt:234-239,302-307` |
| Before that frame the test calls `requestFocus()` on `Card 1` directly; it does not pass through the grid's `focusRestorer` | test `:59-64` |
| `onItemFocused(1)` tries the first-focus redirect from inside that focus-change callback. Line 64 passes, so the redirect did not take; the request returned false | `FocusTargetState.kt:156-159` |
| A failed redirect records the landing: `focusedItemId = 1`, so `targetItemId` and the requester move to `Card 1`, and the pending entry request focuses `Card 1` | `FocusTargetState.kt:76-80,161`; `CollectionDetailScreenContent.kt:91-92` |

No production path produces a direct request on a non-target card in CollectionDetail: the only requester in the screen sits on the target card (`CollectionDetailScreenContent.kt:91-92`), and an entry from any ancestor reaches the grid's `focusRestorer`, whose `onEnter` falls back to that same requester (`:81`). The device baseline shows no entry at all after Back (`qa-report.md#baseline`), and Manual 3 restores the card. This is the seam fault D11 already named for the dropped Collections variant (`test-design.md:55`).

Bookmarks runs the same helper with the same wiring (`ui/feature/bookmarks/component/BookmarksScreenContent.kt:73-79,118-124,135-147`) and has no default-landing instrumented test; its device restore holds (`qa-report.md#baseline`, AC-8).

### D2: the parked reconcile request fires while the menu holds focus

| Step | Code |
|---|---|
| The context menu is an overlay in the same composition, a `focusGroup` box; focus moves to its first action | `core/ui/uikit/component/TvContextMenu.kt:194,233`, `core/ui/uikit/component/TvDialogOverlay.kt:46-54` |
| Focus leaving the grid clears `rowHasFocusRef`, so `canRestoreInactiveContent` is false | `CollectionDetailScreenContent.kt:82-84`, `FocusTargetState.kt:81-85` |
| Removing the focused card publishes the neighbour as a pending request | `FocusTargetState.kt:202-214` |
| The pending effect fires whenever `contentFocusActive` is true, which it is with the rail closed or on a root screen; the neighbour card takes focus from the menu | `FocusTargetState.kt:240-247`; default `true` at `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30` |

Bookmarks has the same exposure: its `TvDialogFocusRestorer` (`BookmarksScreenContent.kt:80-98`) acts only on open and close, not on a publish while open. Show all is not exposed: `GridFocusMemory` requests only while the grid holds focus or an entry is pending (`ui/feature/showall/content/ShowAllScreenContent.kt:219-227`).

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D14 | D1: production or seam | Production unchanged. Replace `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` with `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard`: the content inside a host `Box(Modifier.focusRequester(host).focusRestorer().focusGroup())` as the Collections guard (`CollectionsScreenContentFocusTest.kt:47-49`); after the restore, with the clock held, `host.requestFocus()` once `Card 1` is composed and before the entry request's frame; expect the saved card | Keeps R1's ordering (an entry before the first restore attempt) with the producer the device has. Red on base: no grid restorer, the entry lands on `Card 1` and nothing moves it. Green after: the grid's `onEnter` falls back to the target requester |
| D15 | D2 fix | In the `Content` branch, read `LocalContentFocusActive` and provide `outer && contextMenuItem == null` around the helper call and the `Box` (`CollectionDetailScreenContent.kt:52-115`). No helper change | The helper already parks requests while content focus is inactive (`FocusTargetState.kt:69-71,240-247`), the way the rail parks them (`ui/feature/main/component/MainScreenComponent.kt:138`). `FocusTargetState.kt` stays out of scope |
| D16 | D2 (no `TvDialogFocusRestorer`) | Still no restorer. Revised side effect: when the menu closes, the flip back to active re-runs the entry request (`FocusTargetState.kt:234-239`) and the parked request, so the target card (the opener, or its neighbour if it left) takes focus one frame after close; HEAD issues no request on close | Comes with D15 at no extra code; no AC covers close |
| D17 | Bookmarks | Unchanged | No AC; its exposure to D2 is recorded as Open question 5 |

AC impact: none. AC-2, AC-5 and AC-9 keep their wording; the test-design rows change as below (owner: test-designer).

### Test-design rows

| Row | Becomes |
|---|---|
| `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` (AC-2, AC-9, P0 red; risk R1 row) | Replaced by `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` (AC-2, AC-9, instrumented, P0 red). Fails if the grid's `focusRestorer` fallback or the helper's entry request does not reach the saved card |
| `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Unchanged; red at HEAD 5ea2899e, green after D15 |
| Manual 7 | Adds an observation: after BACK closes the menu, the opener card is focused (D16, not an AC) |

### Risks (delta)

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | Residual for every helper caller: a direct request on a non-target card in the first frame is recorded as the position, because the redirect from inside `onFocusChanged` can fail. No producer in CollectionDetail or Bookmarks today | `FocusTargetState.kt:156-161` |
| R10 | Focus / lifecycle | Menu close now refocuses the target card (D16). A menu action that leaves the screen dismisses first; the request is a frame later and is cancelled when the composition is disposed | `CollectionDetailScreenContent.kt:110-114`, `FocusTargetState.kt:234-247` |
| R11 | Recomposition | `LocalContentFocusActive` is static (`FocusOnLaunchRequester.kt:30`); each menu open and close recomposes the `Content` subtree once | `CollectionDetailScreenContent.kt:52-115` |

### Open questions (delta)

5. Bookmarks parks nothing while its menu is open (D2 exposure, `BookmarksScreenContent.kt:73-98`). Recommended: record a deferred idea; no AC covers Bookmarks here. Blocks: no.
