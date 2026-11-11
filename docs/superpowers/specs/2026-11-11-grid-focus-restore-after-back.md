# Grid focus restore after Back (Collections, CollectionDetail, Show all live fallback)

Status: approved
Date: 2026-11-11
Slug: grid-focus-restore-after-back
Kind: bug
Size: M
Tracks: none

## Problem / Why

After a root push (Details, CollectionDetail) and Back, grid screens other than Show all may focus the first visible card instead of the card that opened the pushed screen. Log additions: Show all fallback C on the live path, and the CollectionDetail rail close.

### Root cause (CollectionDetail symptom confirmed on the device, `qa-report.md#baseline`; mechanisms by code trace, research.md: 3, `design-architect.md#delta-after-baseline`)

1. A root push disposes the tab composition (`FlowComponent.kt:745-765`). After Back only saveable state returns. No code path re-requests tab focus (`RestoreTabContentFocusEffect` `FlowComponent.kt:649-664` fires on a tab-stack pop only; anchors cover `LazyListState` only, `:436-475`). `rememberFocusRequesterOnLaunch` restores as already fired (`FocusOnLaunchRequester.kt:51-65`). Focus then lands by default enter on the first visible card. That landing was seen on a device for Show all before its fix (tab-stack-lost-on-root-push qa-report AC-2).
2. Corrected after the baseline: Collections keeps a saveable index and puts the `focusRestorer` fallback requester on that card (`CollectionsScreenContent.kt:58,69,75-88`). After a root Back the host moves focus into the tab content (the issuing call is not identified, design R8), the restorer's `onEnter` falls back to that requester, and the opener card is focused (device baseline, 4 of 4). Collections is not affected.
3. CollectionDetail (`CollectionDetailScreenContent.kt:63-78`) has no focus memory, requester or restorer at all.
4. Bookmarks (`BookmarksScreenContent.kt:73-79`, the shared `rememberReconciledItemFocus`) and History (VM `focusKey`, `HistoryCard.kt:51-56`) do request the saved card on a fresh composition. Not affected (device baseline).
5. Show all live path: `ShowAllScreenContent.kt:226` runs the grid fallback only `if (pendingEntry && …)`. When the focused card leaves the list and all 3 attempts fail, focus can be left nowhere.
6. CollectionDetail is a root screen outside the drawer (`MainScreenComponent.kt:101-141`), so the rail-close case from the Log cannot occur there.

### Plan outline

1. Device baseline on e9184b79 (qa-device, lease): AC-1, AC-2 and the AC-8 sequence. It confirms which screens fail before code changes.
2. CollectionDetail: adopt `rememberReconciledItemFocus` (`FocusTargetState.kt:40`; `items` are already `VideoItemUIState`) with a requester on the target card and `focusRestorer(itemFocus.focusRequester)`, as in `BookmarksScreenContent.kt:73-79,118-147`.
3. Collections: replace the index memory (`CollectionsScreenContent.kt:58,75-88`) with an id-keyed memory plus an entry request, keeping one mechanism, not a request next to the existing `focusRestorer` (Q3).
4. Show all: relax the gate at `ShowAllScreenContent.kt:226` for the live path, and add the AC-7 case in `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`.
5. Instrumented tests (AC-9), then device verification on the worktree APK (install-preserve).

### Risks (analyst)

- Code reading only: Bookmarks and History may still fail on the device (for example a resume refresh replacing items in the first frames). The baseline (AC-8) is the guard, and a failure there changes scope (Q1).
- Double restore: an entry request beside the Collections `focusRestorer` is the race class the Show all runs removed. Keep one mechanism per grid.
- Same-file overlap in `ShowAllScreenContent.kt` `reconcile()` with `showall-first-entry-rail-focus` and `showall-entry-restore-foreign-landing`. Sequence those runs after this one.
- Collections calls `onLoadMore()` from composition (`CollectionsScreenContent.kt:72-74`). A restore that scrolls to a far card can trigger page loads during the restore.

## Acceptance criteria

- AC-1. Given the Collections tab scrolled down with a card in a lower visible row focused, When OK opens CollectionDetail and Back returns, Then the same collection card is focused, as on base (regression guard; device, 3 repeats).
- AC-2. Given CollectionDetail opened from Home and, separately, from Collections, with a card in a lower visible row focused, When OK opens Details and Back returns, Then the same card is focused (device, 3 repeats each).
- AC-3. Given CollectionDetail opened for the first time, When its content loads, Then the first card is focused, and Back from CollectionDetail still restores Home's or Collections' opener (`HomeCollectionBackFocusTest` green).
- AC-4. Dropped: the Collections list is append-only (`CollectionsVM.kt:68-72`), so the saved index names the same collection after appends and the "collection gone" case has no producer (design D12).
- AC-5. Given CollectionDetail with its context menu dialog open, When its items change, Then focus stays in the dialog.
- AC-6. Given the Collections tab with a card focused, When LEFT opens the rail and RIGHT closes it, Then the same card is focused (no regression from base).
- AC-7. Given Show all with focus in the grid, When the focused card leaves the list and all 3 restore attempts fail, Then the grid takes focus while content focus is active. A `GridFocusMemoryTest` case shows this red on base and green after the change; the existing cases stay green.
- AC-8. Given base e9184b79, When the AC-1/AC-2 sequence runs on Bookmarks and History (card → Details → Back, and rail close), Then the result is recorded in the evidence, and after the change those screens and Show all behave as on base (existing `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest`, `HistoryScreenContentTest` green).
- AC-9a. Instrumented tests for CollectionDetail simulate the disposal and recomposition (as `EpisodeSchedulePushBackFocusTest` does). They are red on base and green after the change.
- AC-9b. A Collections instrumented test simulates the same recomposition followed by a focus entry from an ancestor (`requestFocus()` on a parent), and is green on base and after the change.

## Out of scope

In: CollectionDetail restores the opener card after Back. Show all runs fallback C on the live path too. Collections gets an instrumented guard only, no production change (design D10, D11). The device baseline on e9184b79 (done, `qa-report.md#baseline`).
Out: Search (see Deferred ideas). A shared grid helper or a `FlowComponent` grid anchor (research.md: 6, options B and C). The Show all first-entry and foreign-landing ideas (`showall-first-entry-rail-focus`, `showall-entry-restore-foreign-landing`).

Deferred ideas:
- Search clears its query and results on every new composition, including after Details → Back (`SearchScreen.kt:53-57`, `SearchVM.kt:46,51-56`; by design since 8e2a6007). This is product behaviour, not focus: record it as a separate idea.

## Design

Architect, 2026-11-11, base e9184b79. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

- Shared `GridFocusMemory` for all grids (option B): Out of scope per brief.
- Grid anchors in `FlowComponent` (option C): Out of scope per brief.
- Widening `rememberReconciledItemFocus` to any id type: touches every row and VideoGrid caller (state.md Q3).
- Collections entry request without a latched target: the default landing rewrites the memory before the first attempt.
- Removing the Collections `focusRestorer` and answering the handoff in the memory (as Show all `:234-241`): replaces a rail-close path that works on base (AC-6).

### Risks

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | A default landing after Back reaches the grid before the restore. Collections relies on the latched `restoreId` and up to 3 attempts; CollectionDetail on the first-focus redirect | `CollectionsFocusMemory.kt` (new); `FocusTargetState.kt:142-170` |
| R2 | Focus / lifecycle | CollectionDetail reloads through `Loading` when Details returns content changes (`ui/feature/collections/detail/vm/CollectionDetailVM.kt:69-70,98-102`). The `Content` branch is disposed with its `rememberSaveable` memory and grid scroll, so focus lands on the first card. Not covered by any AC | `CollectionDetailVM.kt:98-102`, `CollectionDetailScreenContent.kt:46` |
| R3 | Focus / D-pad | Double restore in Collections: entry request, grid `focusRestorer` and the tab handoff (`ContentFocusHandoff.kt:116-131`) all aim at a card. D4 bounds the entry request to one pending window; all three target the same card | `CollectionsFocusMemory.kt`, `CollectionsScreenContent.kt:69` |
| R4 | Focus / data shape | After process death the saved id may not be on page 1; the clamped index can be outside the composed viewport, the 3 attempts fail and focus stays where the default enter put it (Collections has no fallback C) | `resolveCollectionRestoreId` |
| R5 | Focus | Live fallback C focuses the grid while content focus is active even if the user's focus left by other means in the same frames; `focusedCardLeft` needs `inGrid` (`ShowAllScreenContent.kt:209-217`) | `ShowAllScreenContent.kt:221-226` |
| R6 | Recomposition | CollectionDetail reads `focusedItemId` in composition (`FocusTargetState.kt:76-80`), so each card focus recomposes the grid content, as Bookmarks does today. Collections reads `restoreId`, which changes only at construction | `CollectionDetailScreenContent.kt` |
| R7 | Rollout | Same-file overlap in `ShowAllScreenContent.kt` `reconcile()` with `showall-first-entry-rail-focus` and `showall-entry-restore-foreign-landing` | `ShowAllScreenContent.kt:219-227` |

### Open questions

1. AC-7 says the existing `GridFocusMemoryTest` cases stay green, but `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid` (`GridFocusMemoryTest.kt:241-249`) asserts the opposite of AC-7 and turns red with D7. Recommended: amend AC-7 so that this case is replaced by the AC-7 case (renamed, expecting `restore:6:false` x3 then `grid`), all other cases unchanged. Blocks: yes.
2. R2: refresh CollectionDetail in place when content is already shown (no `Loading` on `onReturnedContentChanges`) so focus survives a Details change? Recommended: no in this run; record a deferred idea, since no AC covers it. Blocks: no.

### Delta after baseline

Input: device baseline `qa-report.md#baseline` (evidence `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md`). Collections restores the opener and keeps it on rail close on base; CollectionDetail has no focus after Back from either origin; Bookmarks and History are not affected.

#### Why Collections restores on base

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

#### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D10 | Collections production change (D3-D6) | Withdrawn: no `CollectionsFocusMemory`, `CollectionsScreenContent.kt` unchanged | The base chain meets AC-1 and AC-6 on the device. A second entry request is the double-restore class (R3) with no AC left to pay for it |
| D11 | Collections coverage | One instrumented guard that models the host entry (an ancestor `requestFocus()` after the recomposition), green on base and after; AC-1 and AC-6 stay device checks | Locks the chain the brief misread. A direct request on the first card does not pass through the grid's `onEnter`, so the earlier "default landing" variant would be red on base for a reason the device never shows |
| D12 | AC-4 | Proposed drop (AC delta below) | Append-only list: the saved index names the same collection after appends; the "collection gone" branch has no producer |
| D13 | CollectionDetail (D1) and Show all live fallback C (D7) | Unchanged | Baseline reproduces the CollectionDetail symptom from both origins; AC-7 is independent of the baseline |

#### AC delta (proposal for the analyst-owned AC section)

| AC | Change | Proposed wording |
|---|---|---|
| AC-1 | Becomes a regression guard | Given the Collections tab scrolled down with a card in a lower visible row focused, When OK opens CollectionDetail and Back returns, Then the same collection card is focused, as on base (device, 3 repeats). |
| AC-4 | Dropped | N/A |
| AC-5 | Collections rail clause dropped (Collections gets no pending request) | Given CollectionDetail with its context menu dialog open, When its items change, Then focus stays in the dialog. |
| AC-6 | Unchanged (already "no regression from base") | N/A |
| AC-9 | Split: CollectionDetail red, Collections guard | Instrumented tests for CollectionDetail simulate the disposal and recomposition (as `EpisodeSchedulePushBackFocusTest` does); they are red on base and green after the change. A Collections instrumented test simulates the same recomposition followed by a focus entry from an ancestor, and is green on base and after the change. |
| Root cause #2 (brief and spec Problem) | Corrected | Collections keeps a saveable index and puts the `focusRestorer` fallback requester on that card (`CollectionsScreenContent.kt:58,69,75-88`). After a root Back the host moves focus into the tab content, the restorer's `onEnter` falls back to that requester, and the opener card is focused (device baseline, 4 of 4). Collections is not affected. |

AC-2, AC-3, AC-7 and AC-8 are unchanged.

#### Test-design rows

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

#### Risks (delta)

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | Narrowed to CollectionDetail | `FocusTargetState.kt:142-170` |
| R3, R4 | | Withdrawn with D10; R4 is base behaviour | |
| R8 | Focus / lifecycle | AC-1 rests on an implicit chain: an unidentified host entry plus the grid's restorer fallback. A change to Main's focus entry after a root Back would break Collections silently; the guard covers only the part below the host | `MainScreenComponent.kt:296-302`, `CollectionsScreenContent.kt:69,75-88` |
| R9 | Test seam | Whether an ancestor `requestFocus()` runs the nested grid's `onEnter` in ui 1.12.0 is unverified (research.md §5). A guard red on base is a seam finding for the conductor, not a production defect; AC-1 on the device governs | `CollectionsScreenContentFocusTest` (new) |

#### Open questions (delta)

3. Which call moves focus into the Main tab content after a root Back (R8)? Recommended: not in this run; the device result is what AC-1 needs. Record as a deferred idea if the owner wants the chain made explicit. Blocks: no.
4. Accept the AC delta above (AC-1, AC-4, AC-5, AC-9 and root cause #2)? Recommended: yes. Blocks: yes (Task 2 of the plan and the qa red checkpoint follow it).

### Addendum after verify round 1

Input: `qa-report.md#round-1` defects D1 and D2, worktree HEAD 5ea2899e. Lines in this section are at HEAD 5ea2899e; `CollectionDetailScreenContent.kt` is `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`, the test is `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`.

#### D1: the default-landing test models a producer the device does not have

| Step in the test | Code |
|---|---|
| The fresh composition launches the entry request, which waits one frame (`requestAfterFrame = true`) | `core/ui/uikit/component/moviesList/FocusTargetState.kt:234-239,302-307` |
| Before that frame the test calls `requestFocus()` on `Card 1` directly; it does not pass through the grid's `focusRestorer` | test `:59-64` |
| `onItemFocused(1)` tries the first-focus redirect from inside that focus-change callback. Line 64 passes, so the redirect did not take; the request returned false | `FocusTargetState.kt:156-159` |
| A failed redirect records the landing: `focusedItemId = 1`, so `targetItemId` and the requester move to `Card 1`, and the pending entry request focuses `Card 1` | `FocusTargetState.kt:76-80,161`; `CollectionDetailScreenContent.kt:91-92` |

No production path produces a direct request on a non-target card in CollectionDetail: the only requester in the screen sits on the target card (`CollectionDetailScreenContent.kt:91-92`), and an entry from any ancestor reaches the grid's `focusRestorer`, whose `onEnter` falls back to that same requester (`:81`). The device baseline shows no entry at all after Back (`qa-report.md#baseline`), and Manual 3 restores the card. This is the seam fault D11 already named for the dropped Collections variant (`test-design.md:55`).

Bookmarks runs the same helper with the same wiring (`ui/feature/bookmarks/component/BookmarksScreenContent.kt:73-79,118-124,135-147`) and has no default-landing instrumented test; its device restore holds (`qa-report.md#baseline`, AC-8).

#### D2: the parked reconcile request fires while the menu holds focus

| Step | Code |
|---|---|
| The context menu is an overlay in the same composition, a `focusGroup` box; focus moves to its first action | `core/ui/uikit/component/TvContextMenu.kt:194,233`, `core/ui/uikit/component/TvDialogOverlay.kt:46-54` |
| Focus leaving the grid clears `rowHasFocusRef`, so `canRestoreInactiveContent` is false | `CollectionDetailScreenContent.kt:82-84`, `FocusTargetState.kt:81-85` |
| Removing the focused card publishes the neighbour as a pending request | `FocusTargetState.kt:202-214` |
| The pending effect fires whenever `contentFocusActive` is true, which it is with the rail closed or on a root screen; the neighbour card takes focus from the menu | `FocusTargetState.kt:240-247`; default `true` at `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30` |

Bookmarks has the same exposure: its `TvDialogFocusRestorer` (`BookmarksScreenContent.kt:80-98`) acts only on open and close, not on a publish while open. Show all is not exposed: `GridFocusMemory` requests only while the grid holds focus or an entry is pending (`ui/feature/showall/content/ShowAllScreenContent.kt:219-227`).

#### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D14 | D1: production or seam | Production unchanged. Replace `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` with `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard`: the content inside a host `Box(Modifier.focusRequester(host).focusRestorer().focusGroup())` as the Collections guard (`CollectionsScreenContentFocusTest.kt:47-49`); after the restore, with the clock held, `host.requestFocus()` once `Card 1` is composed and before the entry request's frame; expect the saved card | Keeps R1's ordering (an entry before the first restore attempt) with the producer the device has. Red on base: no grid restorer, the entry lands on `Card 1` and nothing moves it. Green after: the grid's `onEnter` falls back to the target requester |
| D15 | D2 fix | In the `Content` branch, read `LocalContentFocusActive` and provide `outer && contextMenuItem == null` around the helper call and the `Box` (`CollectionDetailScreenContent.kt:52-115`). No helper change | The helper already parks requests while content focus is inactive (`FocusTargetState.kt:69-71,240-247`), the way the rail parks them (`ui/feature/main/component/MainScreenComponent.kt:138`). `FocusTargetState.kt` stays out of scope |
| D16 | D2 (no `TvDialogFocusRestorer`) | Still no restorer. Revised side effect: when the menu closes, the flip back to active re-runs the entry request (`FocusTargetState.kt:234-239`) and the parked request, so the target card (the opener, or its neighbour if it left) takes focus one frame after close; HEAD issues no request on close | Comes with D15 at no extra code; no AC covers close |
| D17 | Bookmarks | Unchanged | No AC; its exposure to D2 is recorded as Open question 5 |

AC impact: none. AC-2, AC-5 and AC-9 keep their wording; the test-design rows change as below (owner: test-designer).

#### Test-design rows

| Row | Becomes |
|---|---|
| `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` (AC-2, AC-9, P0 red; risk R1 row) | Replaced by `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` (AC-2, AC-9, instrumented, P0 red). Fails if the grid's `focusRestorer` fallback or the helper's entry request does not reach the saved card |
| `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Unchanged; red at HEAD 5ea2899e, green after D15 |
| Manual 7 | Adds an observation: after BACK closes the menu, the opener card is focused (D16, not an AC) |

#### Risks (delta)

| # | Area | Risk | Lives in |
|---|---|---|---|
| R1 | Focus / D-pad | Residual for every helper caller: a direct request on a non-target card in the first frame is recorded as the position, because the redirect from inside `onFocusChanged` can fail. No producer in CollectionDetail or Bookmarks today | `FocusTargetState.kt:156-161` |
| R10 | Focus / lifecycle | Menu close now refocuses the target card (D16). A menu action that leaves the screen dismisses first; the request is a frame later and is cancelled when the composition is disposed | `CollectionDetailScreenContent.kt:110-114`, `FocusTargetState.kt:234-247` |
| R11 | Recomposition | `LocalContentFocusActive` is static (`FocusOnLaunchRequester.kt:30`); each menu open and close recomposes the `Content` subtree once | `CollectionDetailScreenContent.kt:52-115` |

#### Open questions (delta)

5. Bookmarks parks nothing while its menu is open (D2 exposure, `BookmarksScreenContent.kt:73-98`). Recommended: record a deferred idea; no AC covers Bookmarks here. Blocks: no.

## Test design

Test designer, 2026-11-11, base e9184b79, updated after the device baseline (`qa-report.md#baseline`) after verify round 1 (`qa-report.md#round-1` D1, D2; `design-architect.md#addendum-after-verify-round-1` D14-D16) and after verify round 2 (`qa-report.md#round-2` D3; host-entry test dropped, state.md Decisions, verify cap). ACs: `brief.md#acceptance-criteria` with AC-7 amended (state.md Decisions, Design Q1) and the AC delta accepted (state.md Decisions, Design delta Q4; wording in `design-architect.md#ac-delta-proposal-for-the-analyst-owned-ac-section`). Risks: `design-architect.md#risks` and `#risks-delta`. Levels: `.flow/roles/test-designer.md`. Test names and steps: plan Tasks 1-4.

### Harnesses

| File (new unless noted) | Pattern reused |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt` (existing) | `Harness`, `FakeFrameClock`; AC-7 case replaces `:241-249` |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/component/CollectionsScreenContentFocusTest.kt` | `ShowAllScreenContentFocusTest`: `StateRestorationTester.emulateSavedInstanceStateRestore()` (D9 seam); host box modelled on `MainScreenComponent.kt:296-302`, entered with `host.requestFocus()` after the restore |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt` | Same harness; context menu by long OK as `TvContextMenuLongPressTest` |

Reentry tests start with `onNodeWithText("<first title>").requestFocus()`, then DOWN and RIGHT to a lower visible row. CollectionDetail "no landing" requests nothing after the restore (the baseline symptom: no focus).

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Parked reconcile request takes focus from the open context menu when `items` change (D2, seen in round 1) | 3 | 2 | 6 | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`, Manual 7 |
| R1 an entry from an ancestor reaches the CollectionDetail grid before the first restore attempt | 2 | 3 | 6 | Covered by Manual 3 (device restores 6/6) and `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` only; the early-entry instrumented test is dropped (below) and deferred to idea run `collection-detail-early-entry-test`. Residual accepted by the owner (state.md Decisions, verify cap) |
| R1 residual: a direct request on a non-target card in the first frame is recorded as the position (`FocusTargetState.kt:156-161`) | 1 | 3 | 3 | Accepted: no producer in CollectionDetail or Bookmarks (D1); out of scope with `FocusTargetState.kt` |
| R2 CollectionDetail reloads through `Loading` after a Details change | 2 | 2 | 4 | Accepted: out of scope (state.md Design Q2); Manual 3 uses Details without changes |
| R8 Collections AC-1 rests on an unidentified host entry plus the grid restorer fallback | 1 | 3 | 3 | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` (below the host), Manual 2 (whole chain); host call deferred (state.md Design delta Q3) |
| R9 an ancestor `requestFocus()` may not run the nested grid's `onEnter` in the test seam | 2 | 1 | 2 | Accepted: a red guard on base is a seam finding; Manual 2 governs AC-1 |
| R5 live fallback C takes focus the user moved away | 1 | 2 | 2 | Existing `reconcile_focusLeavingTheGridToTheRailThenReloadMakesNoRequest`; `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid` |
| Bookmarks and History regress (baseline: not affected) | 1 | 2 | 2 | Manual 5; `HistoryScreenContentTest` |
| R10 menu close refocuses the target card one frame later (D16); a menu action that leaves the screen must not be overtaken | 1 | 2 | 2 | Manual 7 observation; the request is cancelled when the composition is disposed |
| R7 same-file overlap with the two Show all runs | 2 | 1 | 2 | Accepted: sequencing, not a test |
| R6 recomposition per card focus in CollectionDetail | 1 | 1 | 1 | Accepted: same cost as Bookmarks today |
| R11 one `Content` recomposition per menu open and close (`LocalContentFocusActive` is static) | 1 | 1 | 1 | Accepted |

R3, R4 and the brief's `onLoadMore` risk are withdrawn with D10 (no Collections production change).

### Traceability

| AC | Level | Priority | Mark | Test | Proves |
|---|---|---|---|---|---|
| AC-1, AC-9 | instrumented | P0 | guard | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` | Fails if the saveable index, its fallback requester or the grid `focusRestorer` (`CollectionsScreenContent.kt:58,59,69,75-88`) is removed or detached from the saved card |
| AC-2, AC-9 | instrumented | P0 | red | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | Fails if CollectionDetail has no saveable focus memory or no requester on the target card (base: no focus) |
| AC-3 | instrumented | P0 | red | `collectionDetailFocusesTheFirstCardOnFirstLoad` | Fails if `rememberReconciledItemFocus` is not given the items or the requester sits on another card |
| AC-3 | instrumented | P0 | guard | `HomeCollectionBackFocusTest` (existing, both cases) | Fails if the CollectionDetail change breaks Home's opener restore |
| AC-5 | instrumented | P0 | guard | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Fails if the open menu does not park the card focus requests (`LocalContentFocusActive` provided as `outer && contextMenuItem == null`, D15); red at HEAD 5ea2899e |
| AC-7 | unit | P0 | red | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` | Expects `restore:6:false` x3 then `grid`; fails if the `pendingEntry` gate at `ShowAllScreenContent.kt:226` stays |
| AC-7 | unit | P0 | guard | Every other `GridFocusMemoryTest` case (existing) | Fails if D7 also fires the grid for a pending-free, non-removed state |
| AC-7 | unit | P1 | guard | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid` | Fails if the `contentFocusActive()` part of the gate is dropped (R5) |
| AC-8 | instrumented | P0 | guard | `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest`, `HistoryScreenContentTest` (existing) | Fails on any regression in Show all or History focus |
| AC-8 | manual | P0 | guard | Manual 1 (done) and 5 | Bookmarks and History behave as on base |
| AC-1, AC-6 | manual | P0 | guard | Manual 2 and 6 | Collections opener restore and rail close behave as on base |

AC-4: N/A, dropped (state.md Decisions, Design delta Q4).

#### Dropped

| Test | Reason |
|---|---|
| `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` | Dropped at the verify cap (qa-report.md Round 2 D3): red on base and on HEAD with the same timeout while the device restores 6/6 (Manual 3), so the seam does not reproduce the device and cannot tell R1 from a seam fault. R1 instrumented coverage deferred to idea run `collection-detail-early-entry-test` |
| `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | After verify round 1: a direct request on `Card 1` before the entry frame is a producer the device lacks; the failed redirect records `Card 1` as the position, so it stays red for a seam reason (D1, D14). The host-entry test that replaced it is dropped too (above) |
| `collectionsFocusReturnsToTheFocusedCardAfterReentry` | Replaced by the Collections guard: Collections restores on base (baseline, D10, D11) |
| `collectionsFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | A direct request on the first card skips the grid's `onEnter`; red on base for a reason the device never shows (D11) |
| `restoreEntry_*` (7 unit cases) | No `CollectionsFocusMemory` class (D10) |
| `resolveCollectionRestoreId_*` (4 unit cases), `collectionsFocusReturnsToTheSameCollectionAfterPagesAppendBeforeReentry`, `collectionsFocusMovesToTheCardAtTheSameIndexWhenTheCollectionLeftBeforeReentry` | AC-4 dropped (D12) |
| `collectionsPendingRestoreDoesNotTakeFocusFromTheRail` | AC-5 Collections rail clause dropped |
| `collectionsRailCloseKeepsFocusOnTheLowerRowCard` | No Collections code change; AC-6 stays Manual 6 |

### Failing-first

`collectionDetailFocusReturnsToTheFocusedCardAfterReentry`, written at the plan's red checkpoint on unchanged code and run by qa-device through `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`. Base trace: `git show e9184b79:app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt` lines 63-78: the grid has no `focusRestorer`, no requester and no saveable focus, so after the restore nothing is focused and `waitUntil` times out (device baseline: no focus after Back, 3/3 from both origins). The first red in plan order is `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` (Task 1, unit, base gate at `ShowAllScreenContent.kt:226`).

Every `red` row runs on base first and must fail there; every `guard` row must pass there. A red `collectionsHostEntryAfterReentryFocusesTheFocusedCard` on base is a seam finding (R9) for the conductor, not a test to adjust.

### Manual checks

Emulator under a lease, `adb -s <serial> shell input keyevent`: DOWN 20, RIGHT 22, LEFT 21, OK 23, BACK 4. Settle 1 s between keys. Evidence per step: a screenshot showing the focused card. Each sequence 3 times. "LEFT (rail)" starts from a column-1 card (from column 2 LEFT only moves one column). Bookmarks and Collections tabs are enabled in Settings > Navigation for the run and disabled after.

| # | Build | Steps | Expected | Must not happen |
|---|---|---|---|---|
| 1 | base e9184b79 | Done in the baseline (`docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md`): Bookmarks and History, card, OK, BACK; column-1 card, LEFT (rail), RIGHT | Same card focused on both screens | N/A |
| 2 | worktree APK | Collections tab: DOWN x3 (scrolls), RIGHT, OK (CollectionDetail), BACK | Same collection card focused, viewport unchanged, as on base (AC-1 guard) | First visible card focused; no focus |
| 3 | worktree APK | Open CollectionDetail from Home, DOWN x2, RIGHT, OK (Details, no watch/bookmark change), BACK. Repeat opening from Collections | Same card focused, both origins (AC-2); base had no focus | First card focused; no focus; focus inside the title text |
| 4 | worktree APK | Fresh CollectionDetail (from Home and from Collections) | First card focused on load; BACK returns focus to the Home or Collections opener (AC-3) | Focus stays on the rail or Home first row |
| 5 | worktree APK | Repeat 1 on Bookmarks and History; Show all: card, OK, BACK, and LEFT (rail)/RIGHT | Same as Manual 1; Show all keeps the opener (AC-8) | Any difference from base |
| 6 | worktree APK | Collections: column-1 card in a lower row, LEFT (rail), wait 2 s, RIGHT | Same card focused, as on base (AC-6) | Row shift; no focus |
| 7 | worktree APK | CollectionDetail after Manual 3's BACK: long OK on a card (context menu), wait 2 s, BACK | Menu keeps focus (AC-5); after BACK closes it, the opener card is focused (observation, D16, R10, not an AC) | Focus jumps behind the dialog; after close, no focus or another card focused |

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 (analyst) | Scope | yes | answered | Collections + CollectionDetail + Show all live-path fallback C; Bookmarks and History in the baseline only |
| Q2 (analyst) | Fix shape | no | answered | A, per-screen existing helpers |
| Q3 (analyst) | Collections memory | no | answered | small local id-keyed memory |
| D-Q1 (architect) | AC-7 conflicts with an existing GridFocusMemoryTest case | yes | answered | amend AC-7 (see Decisions) |
| D-Q2 (architect) | CollectionDetail in-place refresh | no | answered | out of scope, idea run `collection-detail-inplace-refresh` |
| D-Q3 (architect, delta) | Find the call that moves focus into the tab content after a root Back | no | answered | not in this run (R8) |
| D-Q4 (architect, delta) | Accept the AC delta after the baseline | yes | answered | yes |
| D-Q5 (architect, addendum) | Bookmarks has the same context-menu exposure (D2) | no | answered | deferred: idea run `bookmarks-context-menu-focus-loss` |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-11 | Size M, tracks none, kind bug | owner (standing instruction: recommended) |
| 2026-11-11 | Q1-Q3 as in Open questions | owner (standing instruction: recommended) |
| 2026-11-11 | AC-7 amended: "the existing cases stay green" excludes `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid`, which is replaced by the AC-7 case `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` (three failed restores, then grid) | owner (standing instruction: recommended) |
| 2026-11-11 | Search reset after Details deferred as idea run `search-reset-after-details` | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approve | owner (standing instruction: recommended) |
| 2026-11-11 | Device baseline (evidence 3fe58218): CollectionDetail loses focus after Back; Collections, Bookmarks, History restore on base | qa-device |
| 2026-11-11 | Gate 1 delta: approve the AC delta (AC-1 guard, AC-4 dropped, AC-5 CollectionDetail only, AC-9 split into 9a/9b); Collections production change dropped (D10-D13) | owner (standing instruction: recommended) |
| 2026-11-11 | Addendum after verify round 1 (D14-D17): D1 test-seam fault, D2 fixed by holding content focus inactive while the context menu is open; no AC change | owner (standing instruction: recommended) |
| 2026-11-11 | Verify cap reached with D3: P0 test `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` dropped (red on base and HEAD, device restores 6/6); AC-2 rests on the reentry test and Manual 3; deferred as idea run `collection-detail-early-entry-test` | owner (standing instruction: recommended) |
