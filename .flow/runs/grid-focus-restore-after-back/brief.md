# Brief: grid-focus-restore-after-back

Base e9184b79. Analyst, 2026-11-11. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; the layout follows `showall-rail-close-focus-shift/brief.md`.

kind: bug. size: M. tracks: none. Device verification: **yes** (TV focus, D-pad, navigation back from a root push).

## Request
After a root push (Details, CollectionDetail) and Back, grid screens other than Show all may focus the first visible card instead of the card that opened the pushed screen. Log additions: Show all fallback C on the live path, and the CollectionDetail rail close.

## Root cause (CollectionDetail symptom confirmed on the device, `qa-report.md#baseline`; mechanisms by code trace, research.md: 3, `design-architect.md#delta-after-baseline`)
1. A root push disposes the tab composition (`FlowComponent.kt:745-765`). After Back only saveable state returns. No code path re-requests tab focus (`RestoreTabContentFocusEffect` `FlowComponent.kt:649-664` fires on a tab-stack pop only; anchors cover `LazyListState` only, `:436-475`). `rememberFocusRequesterOnLaunch` restores as already fired (`FocusOnLaunchRequester.kt:51-65`). Focus then lands by default enter on the first visible card. That landing was seen on a device for Show all before its fix (tab-stack-lost-on-root-push qa-report AC-2).
2. Corrected after the baseline: Collections keeps a saveable index and puts the `focusRestorer` fallback requester on that card (`CollectionsScreenContent.kt:58,69,75-88`). After a root Back the host moves focus into the tab content (the issuing call is not identified, design R8), the restorer's `onEnter` falls back to that requester, and the opener card is focused (device baseline, 4 of 4). Collections is not affected.
3. CollectionDetail (`CollectionDetailScreenContent.kt:63-78`) has no focus memory, requester or restorer at all.
4. Bookmarks (`BookmarksScreenContent.kt:73-79`, the shared `rememberReconciledItemFocus`) and History (VM `focusKey`, `HistoryCard.kt:51-56`) do request the saved card on a fresh composition. Not affected (device baseline).
5. Show all live path: `ShowAllScreenContent.kt:226` runs the grid fallback only `if (pendingEntry && …)`. When the focused card leaves the list and all 3 attempts fail, focus can be left nowhere.
6. CollectionDetail is a root screen outside the drawer (`MainScreenComponent.kt:101-141`), so the rail-close case from the Log cannot occur there.

## Scope
In: CollectionDetail restores the opener card after Back. Show all runs fallback C on the live path too. Collections gets an instrumented guard only, no production change (design D10, D11). The device baseline on e9184b79 (done, `qa-report.md#baseline`).
Out: Search (see Deferred ideas). A shared grid helper or a `FlowComponent` grid anchor (research.md: 6, options B and C). The Show all first-entry and foreign-landing ideas (`showall-first-entry-rail-focus`, `showall-entry-restore-foreign-landing`).

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

## Plan outline (M: the design step owns the detailed plan)
1. Device baseline on e9184b79 (qa-device, lease): AC-1, AC-2 and the AC-8 sequence. It confirms which screens fail before code changes.
2. CollectionDetail: adopt `rememberReconciledItemFocus` (`FocusTargetState.kt:40`; `items` are already `VideoItemUIState`) with a requester on the target card and `focusRestorer(itemFocus.focusRequester)`, as in `BookmarksScreenContent.kt:73-79,118-147`.
3. Collections: replace the index memory (`CollectionsScreenContent.kt:58,75-88`) with an id-keyed memory plus an entry request, keeping one mechanism, not a request next to the existing `focusRestorer` (Q3).
4. Show all: relax the gate at `ShowAllScreenContent.kt:226` for the live path, and add the AC-7 case in `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`.
5. Instrumented tests (AC-9), then device verification on the worktree APK (install-preserve).

## Risks
- Code reading only: Bookmarks and History may still fail on the device (for example a resume refresh replacing items in the first frames). The baseline (AC-8) is the guard, and a failure there changes scope (Q1).
- Double restore: an entry request beside the Collections `focusRestorer` is the race class the Show all runs removed. Keep one mechanism per grid.
- Same-file overlap in `ShowAllScreenContent.kt` `reconcile()` with `showall-first-entry-rail-focus` and `showall-entry-restore-foreign-landing`. Sequence those runs after this one.
- Collections calls `onLoadMore()` from composition (`CollectionsScreenContent.kt:72-74`). A restore that scrolls to a far card can trigger page loads during the restore.

## Open questions
1. Scope: Collections, CollectionDetail and the Show all live-path fallback C, with Bookmarks and History in a baseline only and fixed only if the baseline shows the symptom? Recommended: yes. If the baseline fails on Bookmarks or History, fix them in this run with their existing mechanism. Blocks: yes.
2. Fix shape: A (per-screen existing helpers), B (promote `GridFocusMemory` to a shared helper) or C (grid anchors in `FlowComponent`)? Recommended: A. Blocks: no.
3. Collections memory: a small local id-keyed memory, or widen `rememberReconciledItemFocus` beyond `VideoItemUIState`? Recommended: a local memory, because widening the shared helper touches every row and VideoGrid caller. Blocks: no.

## Deferred ideas
- Search clears its query and results on every new composition, including after Details → Back (`SearchScreen.kt:53-57`, `SearchVM.kt:46,51-56`; by design since 8e2a6007). This is product behaviour, not focus: record it as a separate idea.
