# Brief: showall-rail-close-focus-shift

Base 6b087697. Analyst, 2026-10-18. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; this follows the analyst job layout.

kind: bug. size: S. tracks: none. Device verification: **yes** (TV focus, D-pad, rail handoff).

## Request
Show all grid: after LEFT opens the side rail from a card, RIGHT closes it and focus lands one card row above the card focused before. History keeps the card. Also on 35bfbbec (pre-existing).

## Root cause (confirmed by code trace; the device observation 5/5 and 3/3 matches; research.md: 3)
1. RIGHT in the open rail is swallowed and starts a handoff, `ModalNavigationDrawer.kt:398-407`. Content must take focus back, and `ContentFocusHandoffEffect` allows 5 frames (`ContentFocusHandoff.kt:45,116-131`) before a blind `contentFocusRequester.requestFocus()` on the tab box (`FlowComponent.kt:609-617,640-645`).
2. Show all does not answer. `ShowAllScreenContent.kt:103-109`: the grid has no `focusRestorer()`, so `restoreFocusedChild()` cannot reach a card. `GridFocusMemory.reconcile()` (`:206-211`) returns early on a rail close: `pendingEntry` is false after first focus (`:186,200`) and `focusedCardLeft` needs `inGrid`, which the rail cleared (`:201-203`). Nothing requests the remembered card. `rememberGridFocusMemory` (`:158-168`) never reads `LocalContentFocusHandoff`.
3. After 5 frames the blind request enters the box by Compose's default Enter rule (top-left, nearest card to the right). That is the left-column card of the topmost laid-out row. Rail-open is only possible from the left column and the grid scrolls the focused card to the lowest visible row, so the result is the row above. The landing card then overwrites the memory via `onCardFocused` (`:126,191-194`).
4. History answers per card: `HistoryCard.kt:49-56` requests focus when `contentFocusActive` flips. Rows and `VideoGrid` use `RestoreItemFocusOnTabReentryEffect` (`FocusTargetState.kt:258-290`, trigger latched from `pendingRequestId`, `:71`). Show all (own `LazyVerticalGrid` and `GridFocusMemory`) was outside both, a hole in `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md:190-221`.

The Compose-source part of the chain (androidx-main, not the 1.12.0 tag) is `plausible`. The bug itself needs none of it: the missing handoff response is read straight from the code.

## Scope
In: `GridFocusMemory` answers a rail handoff by focusing the remembered card (recommended option A, research.md: 6). This covers RIGHT, Back-closes-rail (`MainScreenComponent.kt:191`) and re-picking the current tab (`:272`), which share `beginHandoff(expectsNewContent = false)`.
Out: first entry with no memory (sibling `showall-first-entry-rail-focus`; behaviour stays as today), CollectionDetail grid, changes to the shared drawer/handoff contract, `focusRestorer()` on the grid (option B: double restore with `GridFocusMemory` on Back from Details).

## Acceptance criteria
- AC-1. Given the Show all grid with a card focused in a lower visible row of the left column, When LEFT opens the rail and RIGHT closes it, Then focus is on that same card (checked on the device, 3 repeats; instrumented test with the drawer harness).
- AC-2. Given a card focused in the topmost visible row, or in a different scroll position (after scrolling), When the same LEFT, RIGHT sequence runs, Then the same card is focused each time.
- AC-3. Given the rail is open and the user closes it with Back or re-picks the current tab, When the handoff completes, Then the same card is focused.
- AC-4. Given Show all with no remembered card (first entry, focus on the rail), When RIGHT is pressed, Then behaviour is unchanged from base (the first card gets focus, no new request is made).
- AC-5. Given a handoff response is running, When the user moves focus (the grid holds focus) or a dialog or context menu holds it, Then the response never takes focus back (`!inGrid` guard, only a new handoff id triggers it).
- AC-6. Given the remembered card was dropped by a reload while the rail was open, When the rail closes, Then the neighbour at the same index is focused (`resolveRestoreId`).
- AC-7. Unit: a `GridFocusMemoryTest` case where a new handoff id arrives with the grid unfocused and a remembered card requests that card (red on current code); a case for null memory (no request); a case for `inGrid` true (no request). Existing 23 cases stay green.
- AC-8. The existing re-entry and fallback behaviour (`ShowAllScreenContentFocusTest`, 5 tests) is unchanged. The History grid is untouched.
- AC-9. Instrumented test hosting `ShowAllScreenContent` in the `MainSideMenuFocusTraversalTest.kt:185-250` harness (real `DrawerState`, `ContentFocusHandoffEffect`): shown red once on base, green after the fix.

## Plan (size S: one production file plus tests)
1. `ui/feature/showall/content/ShowAllScreenContent.kt`, `rememberGridFocusMemory` (`:158-168`): read `LocalContentFocusHandoff.current?.pendingRequestId`, latch it in a `remember` (copy `FocusTargetState.kt:258-290`; read directly it goes null on settle and cancels the effect). Pass it to `GridFocusMemory`, or call a new method from a `LaunchedEffect(latchedId, contentFocusActive)`.
2. Same file, `GridFocusMemory` (`:171-235`): add `suspend fun onHandoff()` (name free). Guard: `contentFocusActive() && !inGrid && lastFocusedId.value != null`. Then `restoreId = resolveRestoreId(lastFocusedId.value, lastFocusedIndex.value, items())` and `requestRestoreFocus()`. The 3 attempts after a frame land inside the 5-frame grace. Run it only once per latched id, so a later recomposition does not re-fire. Do not touch `pendingEntry`.
3. `app/src/test/.../showall/content/GridFocusMemoryTest.kt`: add the AC-7 cases using the injected `requestRestore`, `contentFocusActive`.
4. `app/src/androidTest/.../showall/content/ShowAllScreenContentFocusTest.kt` (or a new test beside `MainSideMenuFocusTraversalTest.kt:185-250`): the AC-9 drawer test. Do it in a worktree via `./tools/agentw`.
5. Device (qa-device, emulator lease, install-preserve): the "Base comparison: rail close focus" sequence from `showall-focus-restore-hardening` qa-report.md, plus AC-2 to AC-4, plus the hardening run's M2 (a rail opened right after Back is not taken from the user).

## Risks
- Focus stealing by a late attempt (AC-5): keep the `!inGrid` and single-fire-per-id guards. Re-check M2.
- Timing: if the request misses (card not composed), the blind landing still occurs; fallback `requestGrid` would not help (same default enter).
- Same-file overlap with `showall-first-entry-rail-focus` and `showall-entry-restore-foreign-landing`: sequence them; this change must keep a null memory a no-op.
- Risk area: TV focus and D-pad navigation, so size is not below M by the role rule; I size it S only because it is one file and one local mechanism using an existing pattern, with no change to the shared contract. Conductor may treat it as M if strict.
- CollectionDetail likely shifts the same way (`CollectionDetailScreenContent.kt:63`, unverified): record under `grid-focus-restore-after-back`.

## Open questions
1. Fix shape: A (handoff-aware `GridFocusMemory`) vs B (`focusRestorer()`) vs C (shared handoff). Recommended A. Blocks: no.
2. Cover Back-closes-rail and tab re-pick in the same fix (AC-3)? Recommended yes, they come for free. Blocks: no.
3. Include CollectionDetail's grid? Recommended no, separate run. Blocks: no.
