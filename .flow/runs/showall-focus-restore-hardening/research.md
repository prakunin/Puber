# Research: showall-focus-restore-hardening

Base: 35bfbbec. Researcher, 2026-10-17.

## 1. Request

> harden the Show all grid focus restore in app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt (GridFocusMemory / rememberGridFocusMemory) and its tests in app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:
> 1. Race: a reload without the opener card that lands within the ~3-frame entry restore window leaves no card focused (restoreId fixed at composition; the live fallback is gated on inGrid, which is false until entry focus succeeds). Suggested fixes: re-resolve restoreId via resolveRestoreId before each attempt, or reconcile while the entry restore is pending.
> 2. Tests: the "removed before re-entry" case actually runs the live-grid path (shown is snapshot state changed while composed) so the entry fallback has no failing test; no test fails if the per-attempt contentFocusActive re-check is reverted (needs a paused clock); the deep-card guard `index >= DeepRows` does not prove an off-viewport card (use rows*columns and assert the first card is not displayed).
>
> (improvement, deferred from tab-stack-lost-on-root-push review round 2; also check whether FocusTargetState.kt `resolveFocusedItemId` / `rememberReconciledItemFocus` could serve the grid, and overlap with grid-focus-restore-after-back.)

## 2. Understanding

When the user comes back to a Show all grid (Back from Details, or after a root push), a small focus memory puts focus on the card that opened Details. If that card is already gone when the grid is shown again, the memory picks the card now at the same position. If the card disappears while the grid already has focus, a second path moves focus to the neighbour. One timing case falls between the two. The grid is shown again with the opener still in the list. The entry restore starts its 3-frame retry, and a reload removes the opener before any attempt has focused it. Nothing then takes focus. The entry loop keeps aiming at a card that is gone, the live path is switched off because the grid never got focus, and the launch-focus helper does not fire again. The D-pad is dead until the user opens the rail. The fix belongs in this one file and should stay small. The deferred test findings ask for three changes. The entry fallback needs a test that can fail. The mid-retry rail check needs a test that fails if it is reverted. The "deep card" case should prove that the card really is off-screen.

## 3. Current state in code

Production (`app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`):
- `:45` `RESTORE_FOCUS_ATTEMPTS = 3`.
- `:80` `rememberFocusRequesterOnLaunch()` gives the first-launch focus. Its "already requested" flag is `rememberSaveable` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51-53`). It comes back `true` on re-entry, so it never refocuses. That is why a failed entry restore leaves focus nowhere.
- `:84` `rememberGridFocusMemory(state.items)`. `:108` grid `onFocusChanged` feeds `onGridFocusChanged(hasFocus)`. `:119-125` the restore `FocusRequester` is attached only to the card whose id equals `restoreId`, and every card reports `onCardFocused(id, index)`.
- `:144-161` `rememberGridFocusMemory`: saveable `lastFocusedId`/`lastFocusedIndex` (`:146-147`), `rememberUpdatedState` for items and `LocalContentFocusActive` (`:148-149`), `LaunchedEffect(Unit) { restoreOnEntry() }` (`:158`), `LaunchedEffect(items) { restoreWhenFocusedCardLeft() }` (`:159`).
- `:170-175` `restoreId` is computed once, when the memory is constructed, from the saved id/index and the items at that moment (`Snapshot.withoutReadObservation`). This is the entry fallback the tests F1 finding refers to.
- `:178` `inGrid` is a plain var. It is false on a fresh composition and set only by grid focus (`:185-192`).
- `:194-196` `restoreOnEntry` calls `requestRestoreFocus()` with the fixed `restoreId`. It never re-resolves. This is the race (edge F1 / codex).
- `:198-204` `restoreWhenFocusedCardLeft` re-resolves and requests, but only when `inGrid` is true (`:200`). It is skipped during the entry window.
- `:206-212` `requestRestoreFocus`: for each of 3 attempts it waits a frame, returns if `!contentFocusActive()` (`:209`, the "fix #1" per-attempt check), and tries `runCatching { requestFocus() }.getOrDefault(false)` (`:210`). A requester that is not attached fails silently.
- `:216-220` `resolveRestoreId` (private, pure) is the opener if present, else `items[index.coerceIn(0, lastIndex)]`. **No unit test covers it.**
- `:53-71` `ShowAllContentBody` is composed only for `ShowAllViewState.Content`. `refreshFirstPage` keeps content on screen (`ContentListPagingVM.kt:104-108`, per review-edge-2), so the body stays composed across a reload.

Reload triggers (`app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt`):
- `:56` display-settings changes, `:64-65` watch-state changes with hide-watched on, `:110-115` `onReturnedContentChanges` (a Details/Player result that is not empty: cache invalidated plus `refreshFirstPage`), `:164-165` a save mutation. `onReturnedContentChanges` fires as the user returns, so it is the most plausible source of a reload that lands just after re-entry.

Tests (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`):
- `:34-57` opener restored after `StateRestorationTester` re-entry.
- `:60-83` "below the first viewport". The guard at `:75` is `index >= DeepRows` (5). Five Down presses actually land on index 15, but the guard would pass at index 5 (row 1). Nothing asserts that card 1 is off-screen (tests F3).
- `:86-111` "left the list before re-entry". `shown` is `mutableStateOf` (`:87`) and changes at `:102` while the grid is composed, so `LaunchedEffect(items)` moves focus to the neighbour, and `onCardFocused` saves the neighbour, before the state is saved. On re-entry the saved id is present, so the `resolveRestoreId` fallback is never exercised (tests F1).
- `:114-135` live-grid neighbour case. This case can fail.
- `:138-161` inactive content. `active=false` is set before restore, so this case also passes with a value captured once (tests F2).
- No test uses `mainClock.autoAdvance = false` in this class. The only androidTest with frame-level control is `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:221` (`repeat(3) { withFrameNanos { } }`).

Row focus helpers (the request asks whether they can serve the grid):
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-24` `resolveFocusedItemId(previousItems, updatedItems, focusedItemId)` is pure and unit-tested (`app/src/test/kotlin/.../moviesList/FocusTargetResolverTest.kt`). It locates the removed card by its index **in the previous list**. On re-entry there is no previous list, only the saved index, so it cannot replace `resolveRestoreId` without saving the whole id list. Semantics are the same for a single removal: same index, else the previous index, else the first card. `resolveRestoreId` clamps the index, which gives the same result for a removed last card.
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:40-126` `rememberReconciledItemFocus` is tied to rows. It needs `rowKey` and `isTargetRow`, uses root-anchor restore completion (`:72-73,248-256`) and the tab handoff (`:74,273-293`), and reports to the prefetch surface (`:124,167`). Its useful pattern is `ReconcilePublishedItemsEffect` (`:192-220`): on each `items` change it re-resolves and posts a pending request. That pattern is not gated on "row already has focus". It is not reusable as is for a `LazyVerticalGrid`, but it shows the shape the race fix needs. It re-resolves on every list change, and the pending request survives until it can fire.
- `FocusTargetState.kt:295-300` `requestAfterAnchorRestore` is the 3-frame loop that the ShowAll loop copies (plan Task 5 told the implementer to copy it, not import it). Unlike ShowAll it has no `runCatching`.

Other grids (overlap with grid-focus-restore-after-back):
- Only `ShowAllScreenContent.kt`, `history/component/HistoryScreenContent.kt:278-283` (`focusRestorer()`) and `collections/detail/component/CollectionDetailScreenContent.kt:63` use `LazyVerticalGrid`. Bookmarks, Favorites, Search, Collections, EpisodeSchedule and the player's EpisodesPanel use `VideoGrid`, which already goes through `rememberVideoGridFocusState` / `rememberReconciledItemFocus` (`FocusTargetState.kt:364-388`).

## 4. Related work

- **tab-stack-lost-on-root-push** (done, merged db16ba9d) is the origin. Commit `b0b1740b` "Restore focus on the Show all grid after Back" added `GridFocusMemory` (+106 lines). `e55af20f` added the focus test. The review findings are in `review-edge-2.md` F1, `review-codex-2.md` (CONFIRMED minor, re-graded from medium) and `review-tests-2.md` F1-F3. `triage-2.md` rows #1-#4 are all deferred here because the review cap was reached. In `qa-report.md` "Review round 1 fixes", the opener-removed fallback is NOT CHECKED on device (it needs a hide-watched reload, which means playing to the end), and the red half of the scrolled case was never run. Spec D9 (`docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md:78`) set a budget of about 30 lines, ShowAll-local, with no change to `FlowComponent` or `RootAnchorCaptureRegistry`. Plan Task 5 is in `docs/superpowers/plans/2026-10-17-tab-stack-lost-on-root-push.md:249-290`.
- **grid-focus-restore-after-back** (idea) covers other grid screens that may focus the first visible card after Back, with ShowAll's fix as the reference. It overlaps with this run, but this run is not a duplicate of it. Hardening `GridFocusMemory` here improves the reference that run would copy or extract. The real candidates there are History and CollectionDetail (`LazyVerticalGrid`), not the `VideoGrid` screens. If the memory is extracted into a shared helper here, that run gets smaller. I recommend not extracting it in this run (see Q2).
- **showall-first-entry-rail-focus** (idea, bug): focus stays on the rail on the *first* entry into Show all. Same file, different path (`rememberFocusRequesterOnLaunch`, `:80`, and the drawer gate at `FocusOnLaunchRequester.kt:46,55`). No overlap in logic, but the two runs edit the same file, so run them one after the other.
- **focus-request-helper** (idea, M): one `tryRequestFocus(): Boolean` for about 28 copies of `runCatching { requestFocus() }` (audit BP-3, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:120`). ShowAll's `.getOrDefault(false)` already reads the result correctly. If that run lands first, `:210` becomes one more call site. No conflict.
- **tab-refresh-while-covered-flash** (idea): a tab content refresh while the host is covered. It is related to "reload while away", but it is about the tab stack, not grid focus.
- Duplicate: none.

## 5. External findings

`research` has no key in `.flow/config.yaml`, so I treated web research as on. I made one fetch.
- Compose test clock (Compose BOM `2026.08.00`, `gradle/libs.versions.toml:7`). With `mainClock.autoAdvance = false`, the test drives time with `advanceTimeByFrame()` / `advanceTimeBy()` / `advanceTimeUntil {}`. `waitForIdle()` then does not advance the clock, but it still waits for pending layout and draw. `MainTestClock` drives recomposition, animations and gestures, not Android measure and draw. Source: https://developer.android.com/develop/ui/compose/testing/synchronization (fetched 2026-10-17, page not versioned). Consequence: a paused-clock test can hold the restore loop between frames, but it cannot directly order "the request runs before or after the recomposition in the same frame". See Risks.
- `FocusRequester.requestFocus()` returning `Boolean`, and throwing when not attached: the reference page was too large to confirm. **unverified** from docs. The code compiles with `.getOrDefault(false)` on a `runCatching` (check.full green in the parent run), so a `Boolean` overload exists in this BOM.

## 6. Options

**A. Re-resolve the target on each attempt (minimal).** In `requestRestoreFocus`, at the top of each iteration *before* `withFrameNanos`, set `restoreId = resolveRestoreId(lastFocusedId.value, lastFocusedIndex.value, items())`. The card modifier then moves during that frame's recomposition.
- Pros: about 3 lines, and it follows the reviewers' suggestion. It also makes the `:170-175` initializer less load-bearing.
- Cons: it keeps the fixed 3-attempt budget. If the reload lands on attempt 3, the new target may get only that attempt or none, because the request may run before the frame's recomposition attaches the requester (ordering **unverified**). The window gets smaller but does not close. Two loops (entry and live) still exist.
- Size: S.

**B. One reconciling effect with a pending-entry flag (recommended).** Replace `LaunchedEffect(Unit)` plus the `inGrid`-gated `LaunchedEffect(items)` with a single `LaunchedEffect(items)`. It runs when `pendingEntry || (inGrid && opener gone)`. It re-resolves `restoreId` and requests with the 3-attempt loop. `pendingEntry` is true at construction when a saved id exists, and `onGridFocusChanged(true)` clears it. This is the shape of `ReconcilePublishedItemsEffect` + `pendingFocus` in `FocusTargetState.kt:192-247`.
- Pros: a reload at any time before focus lands restarts the loop with a fresh budget against the right card, so the window closes instead of shrinking. There is one code path instead of two, and the rail gate (`contentFocusActive`) still applies per attempt.
- Cons: about 10-15 changed lines. An append (load-more) while the entry is still pending re-requests the same opener, which is harmless because it is the intended target. `pendingEntry` must not outlive a user move: it clears on any grid focus.
- Size: S (one production file plus tests; no data or contract change).

**C. Safety net on top of A or B.** If every attempt fails while `contentFocusActive()` is still true, request the grid's own `mainContentFocus` (`:80,107`) so that some visible card gets focus.
- Pros: whatever the cause (requester not attached, a deep card not composed yet, ordering surprises), focus is never left nowhere. This matches the spec's "Must not happen: no focus ring anywhere" (spec M1).
- Cons: it can land on the "first visible card" that the parent run worked to avoid, but only as a last resort. It adds a second requester call per failed entry.
- Size: S (add-on).

Testing (same for A, B and C):
- (i) Make `resolveRestoreId` `internal` (or move it next to `resolveFocusedItemId` in `FocusTargetResolver.kt`) and add JVM cases: opener present, opener gone mid-list, opener was last, index beyond a shorter reloaded list, empty list, null id.
- (ii) Fix tests F1 in androidTest: `shown` becomes a plain `var` read inside `setContent`, changed after the focus moves and before `emulateSavedInstanceStateRestore()`, so only the restored composition sees the new list.
- (iii) Fix tests F3: guard `index >= DeepRows * GRID_COLUMNS` (15), and assert that card 1 is not displayed (`assertIsNotDisplayed()` / `assertDoesNotExist()`) before the restore.
- (iv) Tests F2 and the race: a paused clock (`autoAdvance = false`) can hold the loop between frames, but making attempt 1 *fail* deterministically on a device is hard (see Risks). The deterministic route is to make `GridFocusMemory` `internal` with the request injected as `() -> Boolean`, and drive it in a JVM test with `runTest` and a fake `MonotonicFrameClock` (compose runtime is already on the unit-test classpath, e.g. `app/src/test/kotlin/.../SessionSaveableStateTest.kt`). That covers "the flag flips between attempt 1 and 2", which is red if the check is reverted to read once, and "items change after attempt 1", which is red on the current code. The androidTest paused-clock case is optional, best effort.

**Recommendation: B + C, with tests (i)-(iv).** The whole change stays S and ShowAll-local, and it adds no shared helper (see Q2).

## 7. Risks

- **Frame ordering (unverified).** It is not established whether the continuation after `withFrameNanos` runs before or after that frame's recomposition and lazy measure. It decides whether a target retargeted in frame k can take focus in frame k or only in k+1. B is insensitive to this because each list change restarts the loop. A is not.
- **Making the race red on a device.** In the instrumented test, the opener card is normally attached by the first attempt, so the request succeeds before the reload. The race then degrades to the live path and the test stays green on the current code. A deterministic red needs the JVM seam in (iv). Otherwise the plan must record why the androidTest cannot be red (as review-tests-2 F2 allows).
- **Stealing focus.** B's `pendingEntry` must clear on the first grid focus and stay gated by `contentFocusActive()` on every attempt. Otherwise an append or a remap could pull focus back from the rail (K13 / M2 of the parent run). Re-run device M2.
- **C landing on the wrong card.** The net must fire only after every targeted attempt has failed, never in parallel with them.
- **Device verification is needed** (AGENTS.md: TV focus, ViewModel-driven UI). The opener-removed path on a device needs a hide-watched reload. qa could not trigger it before ("playing to the end"). A display-settings toggle or a save toggle (`ShowAllVM.kt:56,164-165`) may be an easier trigger. **unverified** whether either drops the opener.
- Same-file conflict with showall-first-entry-rail-focus if both run in parallel.
- `ShowAllScreenContentFocusTest` has run on a device only for its green half. The F1 and F3 rewrites should be run red against a reverted fallback/guard once.

## 8. Open questions

1. **Fix shape: A (re-resolve per attempt), B (single reconciling effect with a pending-entry flag), with or without C (grid-level safety net)?** Recommended: **B + C**. B closes the window instead of shrinking it, and C guarantees focus is never lost, at S size.
2. **Extract a shared grid focus helper now (for History and CollectionDetail), or keep it ShowAll-local?** Recommended: **keep it ShowAll-local.** Moving only the pure resolver next to `resolveFocusedItemId` is fine. Leave extraction to grid-focus-restore-after-back, which will have the hardened reference to copy or lift.
3. **The mid-retry `contentFocusActive` check and the race: a JVM test through an internal `GridFocusMemory` seam, or a paused-clock androidTest?** Recommended: **a JVM test as the failing guard**, plus the androidTest F1/F3 fixes. Attempt a paused-clock androidTest only if it can be shown red. Otherwise record the reason in the plan.
4. **Device verification of the opener-removed fallback: required, or itest only?** Recommended: **required for M1/M2 regression** (exact opener focus, rail not stolen). The opener-removed path counts as verified on device only if qa finds a trigger without playback mutations. Otherwise the JVM and itest coverage stand in, and that is noted as such.

## 9. Sources

Files:
- `/mnt/data/projects/PetProjets/Puber/AGENTS.md`, `/mnt/data/projects/PetProjets/Puber/.flow/config.yaml`
- `/mnt/data/projects/PetProjets/Puber/docs/architecture/ui-and-compose.md` (Focus), `/mnt/data/projects/PetProjets/Puber/docs/architecture/testing.md`
- `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`
- `app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt`, `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt`
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt`, `FocusTargetResolver.kt`, `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetResolverTest.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`
- `app/src/main/java/com/kino/puber/ui/feature/history/component/HistoryScreenContent.kt`, `app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt`
- `gradle/libs.versions.toml`
- `docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md`, `docs/superpowers/plans/2026-10-17-tab-stack-lost-on-root-push.md`
- `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` (BP-3)

Commits: `b0b1740b`, `e55af20f`, `db16ba9d`, `35bfbbec`.

Runs: `.flow/runs/tab-stack-lost-on-root-push/` (state.md, review-edge-2.md, review-codex-2.md, review-tests-2.md, triage-2.md, qa-report.md), `.flow/runs/grid-focus-restore-after-back/state.md`, `.flow/runs/showall-first-entry-rail-focus/state.md`, `.flow/runs/focus-request-helper/state.md`, `.flow/runs/tab-refresh-while-covered-flash/state.md`, `.flow/runs/showall-focus-restore-hardening/state.md`.

URLs:
- https://developer.android.com/develop/ui/compose/testing/synchronization (fetched 2026-10-17)
- https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/FocusRequester (fetched; the relevant section was not reached, so nothing from it is used as fact)
