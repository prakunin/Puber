## Design

Base 35bfbbec. All `path:line` citations below were read in the worktree
`/home/user1/orca/workspaces/Puber/showall-focus-restore-hardening` at that base.
Unprefixed `:NN` refers to
`app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Shape of the fix (state.md Q1) | B + C. The two effects `LaunchedEffect(Unit) { memory.restoreOnEntry() }` (`:158`) and `LaunchedEffect(items) { memory.restoreWhenFocusedCardLeft() }` (`:159`) become one `LaunchedEffect(items) { memory.reconcile() }`. `restoreOnEntry` (`:194-196`) and `restoreWhenFocusedCardLeft` (`:198-204`) merge into a single `suspend fun reconcile()`. | Each list change cancels the running loop and starts a new one with a fresh 3-attempt budget against a re-resolved target. That closes the race window. Option A only shrinks it (research.md: 6). The first composition also runs a `LaunchedEffect(items)`, so the entry case needs no separate effect. |
| D2 | When `reconcile()` acts | It reads `focusedId = lastFocusedId.value`. It returns immediately unless `pendingEntry` is true, or `inGrid && focusedId != null && items().none { it.id == focusedId }` (the existing `:200` condition). Otherwise it sets `restoreId = resolveRestoreId(focusedId, lastFocusedIndex.value, items())`, then runs the attempt loop when `restoreId != null`. | The first branch is new and is the race fix. The second keeps the live-grid path (`:198-204`) unchanged. Neither branch writes `restoreId` or requests focus when focus is already in the grid and the focused card is still in the list. That covers AC-6. |
| D3 | `pendingEntry` lifetime | `private var pendingEntry: Boolean` (a plain var, like `inGrid` at `:178`). It is initialised at construction to `Snapshot.withoutReadObservation { lastFocusedId.value != null }`. `onGridFocusChanged(true)` (`:185-188`) clears it, and nothing ever sets it again. | It is true only on a re-entry that has a saved card. On the first launch there is no saved id and `rememberFocusRequesterOnLaunch` (`:80`) still owns focus. Any grid focus clears it: a successful restore, the C fallback, or the user moving in from the rail. After that, a later reload cannot pull focus back from the rail (parent M2). The construction-time read is unobserved for the same reason as `restoreId` (`:170-174`, KDoc `:139-143`): a focus move must not recompose the grid. |
| D4 | The per-attempt rail check | The attempt loop keeps its shape (`:206-212`): `withFrameNanos {}`, then `if (!contentFocusActive()) return`, then try the targeted request. It now returns a `Boolean` (true means focused). `contentFocusActive` stays a `() -> Boolean` that is read on every attempt. | AC-4. The JVM seam (D6) is what lets a test flip the value between attempts. |
| D5 | The C safety net | `reconcile()` ends with: if the targeted loop did not focus a card, `pendingEntry` is still true, and `contentFocusActive()` is true, call `requestGrid()` once. If `restoreId` is null (the list is empty), there is no loop and no C. C never runs while targeted attempts are still pending. A list change cancels the coroutine before C is reached, so C belongs only to the last loop. | AC-5. This is the "never no focus" guarantee of parent spec M1. It is limited to the pending entry, as the brief scopes C. Whether the live path gets the same net is Open question 1. The grid requester is the existing `mainContentFocus` (`:80`, attached at `:107`). Requesting it moves focus into the grid's first focusable child, the same mechanism the first launch already uses. |
| D6 | Test seam (state.md Q3) | `GridFocusMemory` (`:163`) changes from `private` to `internal`. Its constructor gains `requestRestore: () -> Boolean` and `requestGrid: () -> Boolean`. `val restoreFocus = FocusRequester()` (`:169`) leaves the class. `ShowAllContentBody` creates `val restoreFocus = remember { FocusRequester() }` and passes it, together with `mainContentFocus`, to `rememberGridFocusMemory(state.items, restoreFocus, mainContentFocus)`. That function wraps both requesters in `{ requester.tryRequestFocus() }`. The card modifier at `:119-120` uses the local `restoreFocus`. A new `private fun FocusRequester.tryRequestFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)` in the same file replaces the inline `:210` form. | With these changes the class holds no Compose UI type. A JVM test can then drive `reconcile()` under `runTest` + `withContext(fakeFrameClock)`. `withFrameNanos` reads the `MonotonicFrameClock` from the coroutine context. `mutableStateOf`/`Snapshot` already work in JVM tests here (`app/src/test/kotlin/com/kino/puber/core/ui/uikit/state/SessionSaveableStateTest.kt:28-30`), and `VideoItemUIState` is a plain data class (`core/ui/uikit/component/moviesList/VideoItemUIState.kt:49-50`). The test sees `restoreId`, which is already public, so a fake `requestRestore = { memory.restoreId == expectedId }` models "the requester is attached only to the target card" (`:119-120`). `FocusRequester.requestFocus()` returns `Boolean` in this BOM: `FocusTargetState.kt:298` uses `if (requestFocus()) return`. |
| D7 | Where `resolveRestoreId` lives (state.md Q2) | It stays in `ShowAllScreenContent.kt` next to `GridFocusMemory` and changes from `private` to `internal` (`:216-220`). Its body is unchanged. It does not move next to `resolveFocusedItemId`. | It is the smallest change. Decision Q2 allows the move but does not require it. `resolveFocusedItemId` (`core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-24`) needs the previous list, which does not exist on re-entry (research.md: 3). Two resolvers with different inputs in one file would invite the wrong one being reused. grid-focus-restore-after-back owns any extraction. |
| D8 | `rememberReconciledItemFocus` / `resolveFocusedItemId` for the grid | Not used. | `rememberReconciledItemFocus` (`core/ui/uikit/component/moviesList/FocusTargetState.kt:40-126`) is bound to rows: it uses `rowKey`, `isTargetRow`, the root-anchor completion and the tab handoff. Only the shape of `ReconcilePublishedItemsEffect` (`FocusTargetState.kt:192-220`) is borrowed: re-resolve on every `items` change, not gated on "already focused". |
| D9 | Test files | New JVM file `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt` (JUnit 5; the package dir `app/src/test/kotlin/com/kino/puber/ui/feature/showall/` exists). Edits to `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt` at `:60-83` (AC-7) and `:86-111` (AC-2). The test-designer owns the cases. | AC-1 and AC-3 to AC-6 are JVM tests. AC-2, AC-7 and AC-8 are androidTests. No paused-clock androidTest is planned (Q3). |

### Changes by layer

**API / repository**: none.

**ViewModel state and actions**: none. `ShowAllVM` reload triggers
(`ui/feature/showall/vm/ShowAllVM.kt:56,64-65,110-115,164-165`) are unchanged.
They are the inputs the fix tolerates.

**Screen and DI module**: none. `ShowAllScreen.kt:59` still calls
`ShowAllScreenContent(...)` with the same signature.

**Compose content**: one file, `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`.
- `ShowAllContentBody` (`:75-137`): add `val restoreFocus = remember { FocusRequester() }`.
  `:84` becomes `rememberGridFocusMemory(state.items, restoreFocus, mainContentFocus)`.
  `:120` attaches the local `restoreFocus`. The grid modifier at `:107-108` and
  the card callback at `:125` are unchanged.
- `rememberGridFocusMemory` (`:144-161`): it gains the two `FocusRequester`
  parameters. It passes `requestRestore = { restoreFocus.tryRequestFocus() }` and
  `requestGrid = { gridFocus.tryRequestFocus() }`. `:158-159` become the single
  `LaunchedEffect(items) { memory.reconcile() }`. The saveable state (`:146-147`)
  and the `rememberUpdatedState` wrappers (`:148-149`) are unchanged.
- `GridFocusMemory` (`:163-213`): becomes `internal`. It gets the two lambda
  parameters, drops the `restoreFocus` field (`:169`), adds `pendingEntry` (D3),
  clears it in `onGridFocusChanged` (`:187-188`), replaces `restoreOnEntry` and
  `restoreWhenFocusedCardLeft` with `reconcile()` (D2, D5), and has
  `requestRestoreFocus()` return `Boolean` (D4). `restoreId` (`:170-175`) keeps
  its construction-time initialiser, so the target card gets the requester in
  the first composition, one frame before the effect re-resolves it.
- `resolveRestoreId` (`:216-220`): `private` becomes `internal`.
- New `private fun FocusRequester.tryRequestFocus()` (D6).
- KDoc `:139-143` gets one more sentence: a reload before entry focus lands
  re-targets the restore.

Expected size: about 25 to 35 changed production lines. No new imports beyond
what the file already has (`remember`, `FocusRequester`, `withFrameNanos`,
`Snapshot`).

**Resources**: none.

**Tests**:
- `GridFocusMemoryTest.kt` (new, JVM). It uses a fake `MonotonicFrameClock`
  with a per-frame hook, mutable `items`/`active` vars behind the lambdas, and
  counters on `requestRestore`/`requestGrid`. A list change is simulated the way
  `LaunchedEffect(items)` does it: cancel the running `reconcile()` job, then
  launch a new one.
- `ShowAllScreenContentFocusTest.kt`. `:86-111`: `shown` becomes a plain `var`
  read inside `setContent`, changed after the Down presses and before
  `emulateSavedInstanceStateRestore()` (AC-2). `:60-83`: the guard becomes
  `index >= DeepRows * columns` (15), with a local constant because
  `GRID_COLUMNS` (`:43`) is private. It also asserts that "Card 1" is not
  displayed before the restore (AC-7). The other three cases stay as they are
  (AC-8).

**Docs**: none required. `docs/architecture/ui-and-compose.md` "Focus"
(`:48-58`) does not describe `GridFocusMemory`.

### Plan notes (for the planner)

- AC-1 asks for "red on current code" and AC-4 for "red if the check is
  reverted". The seam does not exist at base, so a red run is only possible
  after D6 exists. Suggested order: (1) seam refactor that keeps behaviour, with
  the two old functions still in place, plus the AC-3 resolver tests; (2) the
  AC-1/AC-4/AC-5/AC-6 JVM tests. Observe AC-1 red against the step-1 code
  locally and record it, then apply D1-D5 and see it green. Show AC-4 red by
  temporarily capturing `contentFocusActive()` once before the loop. No commit
  contains a red test.
- check.fast is `./tools/agentw :app:compileDevDebugKotlin`. check.full is
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run
  both in the worktree only, never `./gradlew`.

### Alternatives rejected

- A (re-resolve `restoreId` before each attempt): keeps the fixed budget, so a reload on attempt 3 can still leave no focus (research.md: 6).
- Gate the live path on "pending or in grid" without a pending flag: there is no signal that tells a re-entry apart from a user on the rail.
- Reuse `rememberReconciledItemFocus`: it is row-bound (D8).
- Extract a shared grid focus helper now: owned by grid-focus-restore-after-back (state.md Q2).
- Move `resolveRestoreId` into `FocusTargetResolver.kt`: allowed but not needed. It would sit next to a resolver with different inputs (D7).
- Paused-clock androidTest for AC-4: it cannot make attempt 1 fail deterministically on a device (research.md: 7). The JVM seam is deterministic.
- Keep `FocusRequester` inside `GridFocusMemory` and construct it in the JVM test: that ties the unit test to a Compose UI type for no benefit.
- Re-arm `pendingEntry` when `contentFocusActive` flips back to true: not asked for, and it risks taking focus away from the host's own rail-close handoff.

### Risks (highest first)

1. **Stealing focus from the rail or the user** (focus and D-pad path). It would
   live in `onGridFocusChanged`/`reconcile` (`:185-204` after the edit). If
   `pendingEntry` outlived the first grid focus, or the attempt loop stopped
   re-reading `contentFocusActive()`, an append or a remap would pull focus
   back. Mitigation: D3 clears it on any grid focus, and D4 keeps the
   per-attempt read. Guarded by AC-4, AC-6 and the device M2 re-run (AC-9). A
   remaining case is a re-entry while the rail is open
   (`LocalContentFocusActive` false, `FocusOnLaunchRequester.kt:30`). There
   `pendingEntry` stays true until the user enters the grid, and every list
   change in that time runs a loop that stops at the first check. That is
   correct, but it costs one frame per reload.
2. **C lands on the "first visible card"** (`mainContentFocus`, `:80,107`). It
   would also hide a broken targeted path, in production and in tests. It fires
   only after all targeted attempts failed (D5). A reverted resolver fallback
   still shows red: with the resolver's else branch gone, `restoreId` is null
   and `reconcile()` returns before C runs, so nothing is focused and the AC-2
   androidTest fails by timeout (corrected, readiness-1). AC-5 pins "exactly
   once, after the last attempt".
3. **Seam fidelity** (`GridFocusMemoryTest.kt`). The fake clock does not model
   recomposition, so the JVM tests prove the decision logic, not when the
   requester attaches. Whether the continuation after `withFrameNanos` runs
   before or after that frame's recomposition is still unverified. D1 restarts
   the loop per list change, so the design does not depend on it. AC-2, AC-8 and
   AC-9 cover attach timing on a device.
4. **Lifecycle** (`ShowAllContentBody`, `:67-70`). The memory is remembered
   inside the `Content` branch. If the body leaves composition (Loading or
   Error) and comes back, a fresh memory with the saved id sets
   `pendingEntry = true` and restores. That already happened at base through
   `LaunchedEffect(Unit)`, so it is not new behaviour. `refreshFirstPage` keeps
   content on screen (`ContentListPagingVM.kt:104-108`, per research.md: 3), so
   ordinary reloads do not take this path.
5. **Recomposition** (`:119`). Every visible card reads `restoreId`. `reconcile`
   writes it on every list change while entry is pending. The write is
   structural, so it only invalidates when the target really changes. The new
   `pendingEntry` read at construction must use `Snapshot.withoutReadObservation`
   (D3). Otherwise each `onCardFocused` write (`:181-182`) would recompose the
   body.
6. **Test flakiness: viewport size** (`ShowAllScreenContentFocusTest.kt:60-83`).
   AC-7 asserts that card 1 is off-screen after 5 rows. On a very tall emulator
   viewport row 0 may still be partly displayed. The test-designer should check
   this against the qa emulator, or use more Down presses (24 items give 8 rows,
   `:185`).
7. **Rollout / merge overlap**. showall-first-entry-rail-focus edits the same
   file (`:80`), so the two runs go one after the other. focus-request-helper
   would later replace `tryRequestFocus` with the shared helper; that is no
   conflict. Baseline profile: no change in startup paths.

### Open questions

1. Should C (D5) also cover the live path (`inGrid` true, focused card gone, all
   3 attempts failed)? Recommended: no in this run. The brief and AC-5 scope C
   to the pending entry, and the live path has had no reported failure. Record
   it for grid-focus-restore-after-back. Blocks: no.
