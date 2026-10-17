# Harden the Show all focus restore

Status: approved
Date: 2026-10-17
Slug: showall-focus-restore-hardening
Kind: improvement
Size: M
Tracks: none

## Problem / Why

### Request
Harden the Show all grid focus restore (`GridFocusMemory`) and its tests. (1) A reload that drops the opener card inside the ~3-frame entry restore window leaves no card focused. (2) Tests: the "removed before re-entry" case runs the live-grid path, so the entry fallback has no failing test; nothing fails if the per-attempt `contentFocusActive` re-check is reverted; the deep-card guard does not prove an off-viewport card. Also: check whether `resolveFocusedItemId` / `rememberReconciledItemFocus` can serve the grid.

### Code found
All in `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt` (verified against the code; matches research.md: Current state):
- `:45` `RESTORE_FOCUS_ATTEMPTS = 3`. `:80` `rememberFocusRequesterOnLaunch()` (saveable "already requested" flag, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:51-53`, so it never refocuses on re-entry).
- `:84` `rememberGridFocusMemory(state.items)`; `:107-108` grid `focusRequester(mainContentFocus)` + `onFocusChanged -> onGridFocusChanged`; `:119-125` the restore requester is attached only to the card whose id equals `restoreId`; every card reports `onCardFocused`.
- `:144-161` `rememberGridFocusMemory`: `LaunchedEffect(Unit) { restoreOnEntry() }` (`:158`) and `LaunchedEffect(items) { restoreWhenFocusedCardLeft() }` (`:159`).
- `:170-175` `restoreId` resolved once at construction. `:178` `inGrid` false until grid focus. `:194-196` `restoreOnEntry` never re-resolves. `:198-204` `restoreWhenFocusedCardLeft` is gated on `inGrid` (`:200`).
- `:206-212` `requestRestoreFocus`: 3 attempts, per-attempt `contentFocusActive()` check (`:209`), `runCatching { requestFocus() }.getOrDefault(false)` (`:210`).
- `:216-220` `resolveRestoreId` is private, pure, untested.
- Tests: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:34-57` (opener restored), `:60-83` (deep card, guard `index >= DeepRows` at `:75`), `:86-111` (removed before re-entry; `shown` is `mutableStateOf`, changed at `:102` while composed), `:114-135` (live neighbour), `:138-161` (inactive content).
- Reload triggers (`showall/vm/ShowAllVM.kt:56,64-65,110-115,164-165`); `onReturnedContentChanges` fires as the user returns, the most plausible source of the race (research.md: Current state).
- Shared helpers: `core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-24` `resolveFocusedItemId` needs the previous list, which does not exist on re-entry; `FocusTargetState.kt:40-126` `rememberReconciledItemFocus` is row-bound (rowKey, root anchor, tab handoff). Neither is reusable as is. Only its `ReconcilePublishedItemsEffect` shape (`:192-220`) is borrowed.

### Root cause (confirmed by code trace)
Sequence: grid re-entered with the opener present, so `restoreId` is fixed to it (`:170-175`). The entry loop (`:194-196`) starts. A reload drops the opener before any attempt focuses it. The target card is gone, so `restoreFocus` is detached and `requestFocus` fails (`:210`). `LaunchedEffect(items)` does fire (`:159`) but `inGrid` is false (`:178,200`), so nothing re-resolves. `rememberFocusRequesterOnLaunch` does not refocus on re-entry (`:80`). Result: no focus anywhere until the user opens the rail.
Unverified: whether the continuation after `withFrameNanos` runs before or after that frame's recomposition (decides how often the old loop is hit; the chosen design does not depend on it).

### Chosen approach
- B: replace the two effects with one `LaunchedEffect(items)`. A `pendingEntry` flag is true at construction when a saved id exists, and is cleared by any `onGridFocusChanged(true)`. The effect runs when `pendingEntry || (inGrid && opener gone)`: re-resolve `restoreId`, then run the 3-attempt loop. Each list change restarts the loop with a fresh budget against the right card. The per-attempt `contentFocusActive()` check stays.
- C: if all targeted attempts fail while `contentFocusActive()` is still true and the entry is still pending, request the grid's own `mainContentFocus` as a last resort (never in parallel with the targeted attempts).
- Keep it ShowAll-local; no shared helper (grid-focus-restore-after-back does the extraction). `resolveRestoreId` becomes `internal` (or moves next to `resolveFocusedItemId`) for JVM tests. `GridFocusMemory` becomes `internal` with the request and grid fallback injected as `() -> Boolean`, so a JVM test drives it with `runTest` and a fake `MonotonicFrameClock`.

### Device verification
Required (yes): TV focus and D-pad restore, screen wiring. Instrumented tests and the M1/M2 regression need a leased emulator via qa-device.

### Risks (analyst)
- Size is M, not S: it is one file plus tests, but TV focus and D-pad navigation is a risk area named in `.flow/roles/analyst.md`.
- Stealing focus: `pendingEntry` must clear on the first grid focus and every attempt stays gated by `contentFocusActive()` (AC-4, AC-6; re-run device M2).
- C could land on the "first visible card" the parent run avoided; only as a last resort, after every targeted attempt failed (AC-5).
- The race cannot be made red in the instrumented test (attempt 1 normally attaches the opener first); the JVM seam is the deterministic guard. If the planner cannot build the seam cleanly, the plan must record why the androidTest cannot be red.
- Frame ordering after `withFrameNanos` is unverified; B restarts per list change so it is insensitive.
- An append while entry is pending re-requests the same opener: harmless.
- Same-file overlap with showall-first-entry-rail-focus: run sequentially. Possible later overlap with focus-request-helper at `:210` (no conflict).
- `ShowAllScreenContentFocusTest` has only run on a device for its green half so far.

## Acceptance criteria

- AC-1. Given a saved opener id present at construction, When the list is replaced by one without the opener after the entry loop started and before any attempt has focused it, Then the loop restarts against the card at the opener's position (clamped) and focuses it. (JVM test on the injected seam; red on current code.)
- AC-2. Given a saved opener id absent from the list at construction, When the grid is entered, Then the card at the saved index (clamped) is focused. (androidTest: `shown` is a plain var read in `setContent`, changed after focus moved and before `emulateSavedInstanceStateRestore()`, so only the restored composition sees the new list; plus JVM cases.)
- AC-3. Given `resolveRestoreId` inputs, When the opener is present, gone mid-list, was last, the index exceeds a shorter list, the list is empty, or the id is null, Then it returns the opener, the same-index card, the new last card, the last card, null, null. (JVM test.)
- AC-4. Given a restore loop in progress, When `contentFocusActive` flips to false between attempt 1 and attempt 2, Then no further request is made. (JVM test on the seam with a controlled clock; red if the check is reverted to a value read once.)
- AC-5. Given every targeted attempt fails and content focus is still active, When the loop ends, Then the grid-level requester is invoked exactly once, after the last targeted attempt; given content focus is inactive, Then it is not invoked. (JVM test.)
- AC-6. Given focus already landed in the grid (pending cleared), When items change without removing the focused card (append, remap), Then no focus request is made. (JVM test; protects against stealing focus back from the rail.)
- AC-7. Given the deep-card test, When it runs, Then it navigates to an index of at least `DeepRows * GRID_COLUMNS` (15) and asserts card 1 is not displayed before the restore, and the opener is focused after it. (androidTest.)
- AC-8. Given the existing opener, live-neighbour and inactive-content cases, When the change lands, Then they stay green (androidTest, `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`); the AC-2 and AC-7 rewrites have each been seen red once against a reverted fallback or guard.
- AC-9. Given the Show all grid on a device, When the user opens Details from a card and presses Back, Then the same card is focused and the rail is not stolen when the rail was open (parent run M1/M2 regression). The opener-removed path counts as verified on device only if qa finds a trigger without playback mutations; otherwise JVM and itest coverage stand in and the report says so.
- AC-10. Given the change, When `check.full` runs, Then unit tests, detekt and lint pass.

## Out of scope

- Extending the last-resort grid focus (fallback C) to the live path (recorded in grid-focus-restore-after-back).
- Other grid screens (History, CollectionDetail: grid-focus-restore-after-back).
- First-entry rail focus on Show all (showall-first-entry-rail-focus).
- A shared focus-request helper (focus-request-helper).

## Design

Base 35bfbbec. All `path:line` citations below were read in the worktree
`/home/user1/orca/workspaces/Puber/showall-focus-restore-hardening` at that base.
Unprefixed `:NN` refers to
`app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`.

#### Decisions

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

#### Changes by layer

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

#### Plan notes (for the planner)

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

#### Alternatives rejected

- A (re-resolve `restoreId` before each attempt): keeps the fixed budget, so a reload on attempt 3 can still leave no focus (research.md: 6).
- Gate the live path on "pending or in grid" without a pending flag: there is no signal that tells a re-entry apart from a user on the rail.
- Reuse `rememberReconciledItemFocus`: it is row-bound (D8).
- Extract a shared grid focus helper now: owned by grid-focus-restore-after-back (state.md Q2).
- Move `resolveRestoreId` into `FocusTargetResolver.kt`: allowed but not needed. It would sit next to a resolver with different inputs (D7).
- Paused-clock androidTest for AC-4: it cannot make attempt 1 fail deterministically on a device (research.md: 7). The JVM seam is deterministic.
- Keep `FocusRequester` inside `GridFocusMemory` and construct it in the JVM test: that ties the unit test to a Compose UI type for no benefit.
- Re-arm `pendingEntry` when `contentFocusActive` flips back to true: not asked for, and it risks taking focus away from the host's own rail-close handoff.

#### Risks (highest first)

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

#### Open questions

1. Should C (D5) also cover the live path (`inGrid` true, focused card gone, all
   3 attempts failed)? Recommended: no in this run. The brief and AC-5 scope C
   to the pending entry, and the live path has had no reported failure. Record
   it for grid-focus-restore-after-back. Blocks: no.

## Test design

Base 35bfbbec. Production citations are `ShowAllScreenContent.kt:NN` at base. Levels (role addendum): `unit` (JUnit 5, JVM, no Robolectric), `instrumented` (only `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`, run by qa-device), `device` (manual, D-pad, evidence file).

#### Risk profile

P = probability 1-3, I = impact 1-3. Score >= 6 needs a test at the level where it fails.

| # | Risk | P | I | Score | Covered by |
|---|------|---|---|-------|------------|
| R1 | `pendingEntry` outlives the first grid focus, or a loop ignores `contentFocusActive()`: a reload or append steals focus from the open rail | 2 | 3 | 6 | U-AC4, U-AC6a/b/c at unit (decision logic); D-M2 at device |
| R2 | Race itself: reload drops the opener inside the entry window and nothing is focused (the defect) | 3 | 3 | 9 | U-AC1a/b/c (red first); not reproducible in instrumented (attempt 1 normally attaches first) |
| R3 | Fallback C masks a broken targeted path, or fires early / twice / while content focus is inactive (lands on first card) | 2 | 3 | 6 | U-AC5a/b/c; I-AC2 asserts the exact neighbour, so a C landing on another card is red |
| R4 | Seam refactor (D6) changes behaviour or recomposition (`restoreId`/`pendingEntry` read observed, so focus moves recompose the grid) | 2 | 2 | 4 | I-AC8 (existing opener / live / inactive cases stay green); review of `Snapshot.withoutReadObservation` |
| R5 | Fake clock does not model recomposition, so JVM tests prove logic, not attach timing (architect risk 3) | 3 | 2 | 6 | Accepted at unit level by design; attach timing covered by I-AC2, I-AC8, D-M1, D-M2. Stated in the test KDoc |
| R6 | AC-7 viewport flake: row 0 still displayed on a tall emulator, so the "deep" case is not deep | 2 | 2 | 4 | Assert card 1 absent before restore; fall back to 8 Down presses (24 items, 3 columns) if it is still shown |
| R7 | Re-entry while rail is open: loops stop at the first check but `pendingEntry` stays true (one frame per reload) | 1 | 2 | 2 | Accepted; U-AC4 pins the stop. Noted, no extra test |
| R8 | Body leaves composition (Loading/Error) and returns: fresh memory restores again | 1 | 2 | 2 | Accepted; same behaviour as base (`LaunchedEffect(Unit)`) |
| R9 | Test passes for the wrong reason: AC-2 rewrite still runs the live path, or AC-7 guard stays `>= DeepRows` (checked by M-AC7: 2 Down presses, index 6, red under the new `>= 15` guard) | 2 | 2 | 4 | Each rewritten androidTest is seen red once against a reverted fallback/guard (AC-8), recorded in the evidence |

#### Unit seam (JVM) shared setup

`GridFocusMemoryTest` in `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`. No `MainDispatcherExtension`, no MockK for the class under test, no `FakeResourceProvider` (no strings). `VideoItemUIState` is a plain data class: `VideoItemUIState(id = n, title = "Card n", imageUrl = "", bigImageUrl = "")`.

- Real `GridFocusMemory`, constructed with `lastFocusedId = mutableStateOf<Int?>(saved)`, `lastFocusedIndex = mutableStateOf(idx)`, `items = { itemsVar }`, `contentFocusActive = { activeVar }`, and two counting lambdas: `requestRestore` and `requestGrid`.
- `requestRestore` models "the requester is attached only to the target card": `{ restoreCalls++; itemsVar.any { it.id == memory.restoreId } && memory.restoreId == target }`. Where the test names the card it expects to be focused, the lambda records `memory.restoreId` per call in a list, so the assertion is on the card that got the request, not on a call count alone.
- Fake `MonotonicFrameClock`: `withFrameNanos(block)` runs `block(frame * 16_000_000L)`, increments a frame counter, then calls an optional `onFrame(n)` hook and `currentCoroutineContext().ensureActive()` (so a cancel issued in the hook is observed at once). Tests run `runTest { withContext(clock) { ... } }`; no sleeps, no real time.
- Items change the way `LaunchedEffect(items)` does it: the hook mutates `itemsVar`, cancels the running `reconcile()` job, and a new `launch { memory.reconcile() }` starts. A helper `changeItems(newList)` does cancel + relaunch.
- Test names follow the repo form `subject_behaviour`.

#### Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | unit | P0 | `GridFocusMemoryTest.reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour` (U-AC1a): saved id 5, index 4, items 1..10; `onFrame(1)` replaces items with 5 removed and restarts the loop. Assert `requestRestore` succeeded on card 6 (index 4), `restoreId == 6`, `requestGrid` never called | The restart re-resolves the target and focuses the neighbour before any attempt had succeeded |
| AC-1 | unit | P0 | `...reloadOnTheLastAttemptStillGetsAFreshBudget` (U-AC1b): same, change fired in `onFrame(3)`. Assert focus lands on card 6 within a fresh 3-attempt budget (frames 4..6 or fewer) | The budget is per list change, not shared; closes the "reload on attempt 3" hole option A left |
| AC-1 | unit | P0 | `...openerDroppedAsLastItemRetargetsToTheNewLastCard` (U-AC1c): saved id 10 at index 9 of 10, a reload leaves 1..9 | Clamped position under the race, end-of-list edge |
| AC-2 | instrumented | P0 | `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry` (rewrite of `:86-111`): `var shown = items` (plain var, read inside `setContent`); after the Down presses, `shown = items.filterIndexed { i, _ -> i != index }`, then `emulateSavedInstanceStateRestore()`; assert `shown[index]` is focused. Also assert the removed card's title does not exist | Only the restored composition sees the new list, so the construction-time resolver (entry fallback) runs, not the live-grid path. Red-once mutation M-AC2 (resolver `else` -> `null`): nothing is focused, so the test is red by timeout waiting for `shown[index]` to be focused. ~~Earlier text "C would focus the first card" is superseded (readiness-1 must-fix 1): with `else` -> null no fallback card is focused~~ |
| AC-2 | unit | P1 | `GridFocusMemoryTest.openerAbsentAtConstructionFocusesTheClampedIndexCard` (U-AC2a): saved id 5 index 4, list built without 5. Assert `restoreId == items[4].id` before `reconcile()` and the request lands on it; second case index 40 on a 10-card list, lands on the last card | JVM mirror of the above, deterministic, includes the clamp |
| AC-3 | unit | P0 | `GridFocusMemoryTest.resolveRestoreId_*` (one `@ParameterizedTest` or seven tests; `junit-jupiter-params` is already used in `app/src/test`): present -> the opener; gone mid-list (id 5 gone, index 4) -> the card now at index 4; gone and was last (id 10 gone, index 9, 9 cards) -> new last; index beyond a shorter list (index 40) -> last; empty list -> null; null id -> null; null id with non-zero index -> null | The pure resolver, all branches including both clamp ends |
| AC-4 | unit | P0 | `GridFocusMemoryTest.contentFocusFlippingOffBetweenAttemptsStopsFurtherRequests` (U-AC4): `requestRestore` always fails; `onFrame(1)` leaves `active = true`, `onFrame(2)` sets `active = false`. Assert `restoreCalls == 1` and `requestGrid` not called. Companion `contentFocusInactiveFromTheStartMakesNoRequest`: `restoreCalls == 0`, `requestGrid == 0` | The per-attempt re-check. Red if it is reverted to a value captured once before the loop (verified once, recorded) |
| AC-5 | unit | P0 | `...everyTargetedAttemptFailingWhileActiveCallsTheGridRequesterOnce` (U-AC5a): `requestRestore` always false, active; run to completion. Assert `restoreCalls == 3`, `gridCalls == 1`, and call order (grid call recorded after the third restore call, via a shared event list) | C exists, runs once, only after the last targeted attempt |
| AC-5 | unit | P0 | `...gridRequesterIsNotCalledWhenContentFocusIsInactive` (U-AC5b): active false from attempt 1 or flipped at attempt 3. Assert `gridCalls == 0` | C respects the rail |
| AC-5 | unit | P1 | `...gridRequesterIsNotCalledWhileTargetedAttemptsAreStillPendingOrAfterASuccess` (U-AC5c): success on attempt 2 -> `gridCalls == 0`; list change cancels the loop on attempt 2 -> `gridCalls == 0` for the cancelled loop; empty list (restoreId null) -> `restoreCalls == 0`, `gridCalls == 0` | No parallel fallback, no C after success, no C for the cancelled coroutine, no C on an empty list |
| AC-6 | unit | P0 | `...appendWhileFocusIsInTheGridMakesNoRequest` (U-AC6a): call `onGridFocusChanged(true)` then `onCardFocused(3, 2)`, change items by appending cards; run `reconcile()`. Assert `restoreCalls == 0 && gridCalls == 0` | Focus already landed: pending cleared, no steal |
| AC-6 | unit | P0 | `...remapKeepingTheFocusedCardMakesNoRequest` (U-AC6b): same, items replaced by copies of the same ids with changed fields | Remap (the `remapLoadedItems` path) does not re-request |
| AC-6 | unit | P1 | `...focusLeavingTheGridToTheRailThenReloadMakesNoRequest` (U-AC6c): construct with a saved id, `onGridFocusChanged(true)` (clears pending), `onGridFocusChanged(false)` (focused card still in list so `inGrid` false), reload that drops the card, `reconcile()`. Assert no request | Pending never re-arms; live path stays gated on `inGrid` (parent M2) |
| AC-6 | unit | P1 | `...firstLaunchWithNoSavedIdMakesNoRequest` (U-AC6d): saved id null; `reconcile()` after items arrive. Assert `restoreCalls == 0`, `gridCalls == 0` | `rememberFocusRequesterOnLaunch` keeps ownership of the first launch |
| AC-6 | unit | P1 | `...liveGridFocusedCardRemovedRetargetsToTheNeighbour` (U-AC6e): `onGridFocusChanged(true)`, `onCardFocused(5, 4)`, list drops 5, `reconcile()`. Assert request lands on the card at index 4 | Existing live path (`:198-204`) preserved by the merge into one `reconcile()` |
| AC-7 | instrumented | P0 | `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry` (rewrite of `:60-83`): local `GridColumns = 3`, `DeepRows = 5`; guard `index >= DeepRows * GridColumns` (15). Before `emulateSavedInstanceStateRestore()`, assert no displayed node with exact text "Card 1" (`onAllNodesWithText("Card 1")` is empty or none `isDisplayed`; exact match, not substring, so "Card 10" does not count). After it, the opener is focused | The opener was really off the viewport; the 3-frame loop has to attach a card that is not composed at restore start. If "Card 1" is still displayed on the qa emulator, raise to 8 Down presses (all 24 items = 8 rows) and use `DeepRows * GridColumns` accordingly |
| AC-8 | instrumented | P0 | `ShowAllScreenContentFocusTest` full class via `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`: opener restored, live neighbour, inactive content unchanged and green; plus the rewrites under AC-2 and AC-7 | No regression from the seam refactor and `reconcile()` merge. Red-once evidence: M-AC2 (resolver `else` -> `null`; AC-2 test red by timeout, nothing focused) and M-AC7 (AC-7 test with 2 Down presses instead of `DeepRows`: index 6 passes the old `>= 5` guard but fails the new `>= 15` guard, so the guard assertion is red). ~~Superseded: "old `>= DeepRows` guard on a tall viewport"~~. Both recorded then reverted |
| AC-9 | device | P0 | D-M1, D-M2 below | Back from Details restores the same card; rail not stolen |
| AC-10 | unit/static | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Unit tests, detekt, lint |

Count by priority (test rows): P0 = 12 (U-AC1a/b/c, I-AC2, AC-3, AC-4, U-AC5a/b, U-AC6a/b, I-AC7, I-AC8 class run), P1 = 5 (U-AC2a, U-AC5c, U-AC6c/d/e), P2 = 0. AC-9 (device) and AC-10 (check.full) are gates counted under MANUAL.

#### Failing-first (bugs)

Defect: the entry restore does not re-resolve `restoreId`, and the live path is gated on `inGrid` (`:178,194-204`). Old code, read with `git -C <wt> show 35bfbbec:app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`: `restoreOnEntry` calls `requestRestoreFocus()` against the constructor-time `restoreId`; `restoreWhenFocusedCardLeft` returns when `inGrid` is false.

- The failing-first test is `GridFocusMemoryTest.reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour` (U-AC1a), with U-AC1b alongside it. It cannot compile on the base commit (the seam is `private`, no lambdas), so it is red in this order, matching design-architect "Plan notes":
  1. Step 1: seam refactor that keeps behaviour (D6: `internal`, injected lambdas, `restoreOnEntry` and `restoreWhenFocusedCardLeft` still separate; the AC-3 tests go green here). Add U-AC1a/b/c written against `reconcile()`-shaped calls via a small test adapter that calls `restoreOnEntry()` then `restoreWhenFocusedCardLeft()`. They fail: the entry loop keeps asking for card 5, the live path no-ops with `inGrid == false`, no card is focused, `restoreCalls` all false.
  2. Step 2: D1-D5. U-AC1 goes green; U-AC4, U-AC5, U-AC6 go green at the same time (they exercise `reconcile()`).
  The tester observes the red locally and records it in the evidence file; no commit contains a red test.
- U-AC4 is shown red once by capturing `contentFocusActive()` into a local before the loop, then reverted.
- U-AC5a is shown red on step-1 code (there is no grid fallback: `gridCalls == 0`).
- There is no failing-first instrumented test: the race cannot be made red on a device (attempt 1 normally attaches the opener first). The planner records this in the plan, as the brief requires.

#### UNTESTABLE

- The frame ordering after `withFrameNanos` (does the continuation run before or after that frame's recomposition): not testable on the JVM seam (the fake clock has no recomposition) and not deterministic on a device. Not an AC; the design is insensitive to it. Covered only indirectly by I-AC2/I-AC8 and D-M1.
- AC-1 on a real device: the real race (reload inside the ~3 frame window) cannot be forced reliably. Stand-in: U-AC1a/b/c plus D-M3 (best effort). The report must say which was achieved.

#### Manual device checks

Run by qa-device with a lease, `adb -s <serial>`, the worktree's own APK installed with `tools/android-apk-install-preserve`, D-pad keyevents only (`input keyevent KEYCODE_DPAD_DOWN` etc.), no taps. Evidence has no tokens or raw logcat.

**D-M1. Back from Details restores the same card (AC-9, M1 regression)**
1. Open a Show all grid (from a section's "Show all").
2. Wait for the first card to be focused. Press DOWN 4 times and RIGHT once so the focused card is below the first viewport.
3. Press CENTER on the card to open Details. Wait until Details is displayed.
4. Press BACK.
- Expected: Show all returns with the same card focused (the focus ring on the same title); no key press needed.
- Must not happen: the first card focused, nothing focused (the next D-pad key is needed to see a focus ring), the rail opened, crash.
5. Press DOWN once. Expected: focus moves to the card below the restored one (the grid is live).

**D-M2. Rail not stolen (AC-9, M2 regression, R1)**
1. From the state in D-M1 step 5, press LEFT until the rail opens (the grid loses focus).
2. Open Details from the rail's destination only if it does not change the Show all stack; otherwise skip to step 3: with the rail open, trigger a Show all list reload (if one of the D-M3 triggers is available).
3. Observe 3 seconds with no input.
- Expected: the rail keeps focus; the grid does not take it back.
- Must not happen: focus jumps into the grid, the rail collapses without input.
4. Open the rail's entry, return to the Show all tab (the tab-stack case from the parent run) and confirm the grid shows a focused card (parent run behaviour) and the rail is not re-opened.

**D-M3. Opener-removed path on a device without playback (AC-9, best effort)**
There is no way to reach the opener-removed path with Back from Details alone. The ShowAllVM reload triggers are `displaySettingsChanges`, `watchStateChanges` (re-pages when hide-watched is on), the bookmark toggle on a card, and `onReturnedContentChanges` (the player returns a `ContentChangeSet`, so it needs playback and is not allowed here). Candidate triggers without playback:
1. Display setting: with a Show all grid in the back stack and the opener card a title that the setting would remove (for example hide-watched with the opener being a watched title), change the setting while Show all is not the visible screen, then return to Show all. This works only if the Settings screen can be reached with the Show all route still on the stack (qa checks the tab stack). The setting is a local preference; qa records its original value and puts it back at the end. If the setting is persisted account-side or the owner has not authorized the toggle, skip and say so.
2. A card bookmark toggle from the grid is an account mutation: not allowed without explicit authorization. Do not use it.
- Expected when step 1 is reachable: after return, the card now at the opener's position is focused; never "nothing focused".
- Must not happen: no focus on return, the first card focused when the opener's neighbour exists, crash.
- If neither trigger is reachable, the report says: "opener-removed path verified by JVM U-AC1/U-AC2 and instrumented I-AC2 only". That meets AC-9 as the brief wrote it.

**D-M4. Instrumented class (AC-8)**
Run `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest` on the leased emulator (never `connected*AndroidTest`). Expected: all five cases pass. Record the two red-once observations (M-AC2: resolver `else` -> `null`, red by timeout; M-AC7: 2 Down presses instead of `DeepRows`, index 6 passes old `>= 5` and fails new `>= 15`; ~~old wording "AC-7 with the old guard on a tall viewport" superseded~~) in the evidence file; both changes are reverted before the commit.

#### Perf track

Not on (tracks none).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Fix shape B + C versus A only? | no | answered | B + C (recommended) |
| Q2 | Keep the helper ShowAll-local; only the pure resolver may move? | no | answered | Yes (recommended) |
| Q3 | Mid-retry rail check guarded by a JVM seam test or a paused-clock androidTest? | no | answered | JVM seam test; androidTest only if shown red (recommended) |
| Q4 | Extend fallback C to the live path? | no | answered | No; recorded for grid-focus-restore-after-back (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-17 | Q1-Q4 answered with the recommended options | owner (standing instruction: recommended) |
| 2026-10-17 | Gate 1: approved as written (M, tracks none, AC-1..AC-10) | owner (standing instruction: recommended) |
| 2026-10-17 | Readiness-1 must-fix: red-once mutations M-AC2/M-AC7; design Risk 2 and test-design rows corrected by their owners | conductor |
