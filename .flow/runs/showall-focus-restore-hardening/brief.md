# Brief: showall-focus-restore-hardening

kind: improvement | size: M | tracks: none | base: 35bfbbec
Research: `research.md` (read first; cited below as research.md: section).

## Request
Harden the Show all grid focus restore (`GridFocusMemory`) and its tests. (1) A reload that drops the opener card inside the ~3-frame entry restore window leaves no card focused. (2) Tests: the "removed before re-entry" case runs the live-grid path, so the entry fallback has no failing test; nothing fails if the per-attempt `contentFocusActive` re-check is reverted; the deep-card guard does not prove an off-viewport card. Also: check whether `resolveFocusedItemId` / `rememberReconciledItemFocus` can serve the grid.

## Code found
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

## Root cause (confirmed by code trace; no device repro, timing needs a hook)
Sequence: grid re-entered with the opener present, so `restoreId` is fixed to it (`:170-175`). The entry loop (`:194-196`) starts. A reload drops the opener before any attempt focuses it. The target card is gone, so `restoreFocus` is detached and `requestFocus` fails (`:210`). `LaunchedEffect(items)` does fire (`:159`) but `inGrid` is false (`:178,200`), so nothing re-resolves. `rememberFocusRequesterOnLaunch` does not refocus on re-entry (`:80`). Result: no focus anywhere until the user opens the rail.
Unverified: whether the continuation after `withFrameNanos` runs before or after that frame's recomposition (decides how often the old loop is hit; the chosen design does not depend on it).

## Chosen approach (research.md: option B + C, Q1-Q3 recommended answers)
- B: replace the two effects with one `LaunchedEffect(items)`. A `pendingEntry` flag is true at construction when a saved id exists, and is cleared by any `onGridFocusChanged(true)`. The effect runs when `pendingEntry || (inGrid && opener gone)`: re-resolve `restoreId`, then run the 3-attempt loop. Each list change restarts the loop with a fresh budget against the right card. The per-attempt `contentFocusActive()` check stays.
- C: if all targeted attempts fail while `contentFocusActive()` is still true and the entry is still pending, request the grid's own `mainContentFocus` as a last resort (never in parallel with the targeted attempts).
- Keep it ShowAll-local; no shared helper (grid-focus-restore-after-back does the extraction). `resolveRestoreId` becomes `internal` (or moves next to `resolveFocusedItemId`) for JVM tests. `GridFocusMemory` becomes `internal` with the request and grid fallback injected as `() -> Boolean`, so a JVM test drives it with `runTest` and a fake `MonotonicFrameClock`.

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

## Device verification
Required (yes): TV focus and D-pad restore, screen wiring. Instrumented tests and the M1/M2 regression need a leased emulator via qa-device.

## Risks
- Size is M, not S: it is one file plus tests, but TV focus and D-pad navigation is a risk area named in `.flow/roles/analyst.md`.
- Stealing focus: `pendingEntry` must clear on the first grid focus and every attempt stays gated by `contentFocusActive()` (AC-4, AC-6; re-run device M2).
- C could land on the "first visible card" the parent run avoided; only as a last resort, after every targeted attempt failed (AC-5).
- The race cannot be made red in the instrumented test (attempt 1 normally attaches the opener first); the JVM seam is the deterministic guard. If the planner cannot build the seam cleanly, the plan must record why the androidTest cannot be red.
- Frame ordering after `withFrameNanos` is unverified; B restarts per list change so it is insensitive.
- An append while entry is pending re-requests the same opener: harmless.
- Same-file overlap with showall-first-entry-rail-focus: run sequentially. Possible later overlap with focus-request-helper at `:210` (no conflict).
- `ShowAllScreenContentFocusTest` has only run on a device for its green half so far.

## Open questions
1. Fix shape B + C versus A only? Recommended: B + C. Blocks: no.
2. Keep the helper ShowAll-local (only the pure resolver may move next to `resolveFocusedItemId`)? Recommended: yes; extraction belongs to grid-focus-restore-after-back. Blocks: no.
3. The mid-retry `contentFocusActive` guard: JVM seam test (recommended) versus paused-clock androidTest, which is optional and only if it can be shown red. Blocks: no.

## Plan
Not provided: size is M (focus risk area); the planner takes it from here.
