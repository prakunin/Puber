# Research: showall-rail-close-focus-shift

Base: 6b087697. Researcher, 2026-10-18. Kind: bug.

## 1. Request

> Show all grid: closing the side rail (left navigation rail) with RIGHT puts focus one card row above the card that was focused when the rail opened. Reproduces on base 35bfbbec too (pre-existing). The History grid keeps the same card correctly.

## 2. Understanding

In the Show all grid, the user presses LEFT on a left-column card, the rail opens, and RIGHT closes it. Focus should come back to the same card. It lands one row higher. Pressing RIGHT in the rail does not move focus on its own. It starts a "handoff": the rail asks the content to take focus back and gives it 5 frames to do so before forcing focus in. History, and every row-based screen, answer that request by focusing the remembered card. Show all does not. Its focus memory runs again when the rail closes, but it returns early because the case is neither a re-entry nor a removed card. Compose's own restore cannot reach the card either, because nothing inside the Show all grid saved it. After the grace frames the handoff calls a plain `requestFocus()` on the tab content box. Compose then enters that box by its default rule: from the box's top-left corner, the nearest card to the right. That is the left-column card of the topmost row the grid has laid out. The user's card is usually in the lowest visible row, because the grid scrolls only far enough to show it, and about two rows fit. So the topmost row is the one above. The fix belongs in `ShowAllScreenContent.kt`. The grid should answer the rail handoff with its remembered card, the way rows already do.

## 3. Current state in code

How RIGHT closes the rail:
- `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:398-407`: the rail's `onPreviewKeyEvent` swallows DPAD_RIGHT. When the rail is `Open` it calls `drawerState.beginHandoff(expectsNewContent = false)`, and it also swallows the key during `HandingOff`. RIGHT never reaches the 2D focus search. The comment at `:387-397` explains why: a plain search "lands on whatever happens to sit nearest the rail".
- `ModalNavigationDrawer.kt:264-281`: the state goes `HandingOff` and gets a new `pendingHandoffId`. `:416-417`: if focus bounces back into the rail during the handoff, `redirectFocusToContent()` sends it on.
- `ui/feature/main/component/MainScreenComponent.kt:137-138`: `LocalContentFocusActive` is `drawerState.currentValue != Open`, so it turns `true` as soon as the handoff starts. `:101-104` provides `LocalContentFocusHandoff`.
- `core/navigation/component/FlowComponent.kt:609-617`: the tab content box is `focusRequester(contentFocusRequester)`, `onFocusChanged`, `focusRestorer()`, `focusGroup()`. `:640-645` runs `ContentFocusHandoffEffect` against that box.
- `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:116-131`: every frame it tries `contentFocusRequester.restoreFocusedChild()`. Once `attempt > HandoffContentGraceFrames` (5, `:45`), it also calls `contentFocusRequester.requestFocus()`. It settles when the box has focus. The KDoc at `:36-44` warns that this blind request "takes whatever the focus search finds first" and overwrites the remembered position.

Show all grid (`ui/feature/showall/content/ShowAllScreenContent.kt`):
- `:103-109`: `LazyVerticalGrid` with `focusRequester(mainContentFocus)` and `onFocusChanged`. It has **no `focusRestorer()`**. `:114-126`: each card reports `onCardFocused(id, index)`. Only the card whose id equals `restoreId` carries the `restoreFocus` requester.
- `:166`: `LaunchedEffect(items, contentFocusActive) { memory.reconcile() }`. It does re-run when the rail goes Open -> HandingOff, but:
- `:206-209`: `reconcile()` returns early unless `pendingEntry` (true only on re-entry, until the grid first gets focus, `:186,200`) or `focusedCardLeft` (needs `inGrid`, which `onGridFocusChanged(false)` cleared when the rail took focus, `:201-203`). Neither holds on a rail close, so nothing requests the remembered card. **This is the gap.**
- `:178-183,210`: `restoreId` is only re-resolved inside `reconcile()`. After entry it still points at the entry target, not at the card focused most recently. `lastFocusedId`/`lastFocusedIndex` (`:152-153`, saveable) do hold the right card.

Why Compose's restore does not reach the card (by code reading, consistent with the device result):
- The tab box's `focusRestorer()` saves only its **immediate** focus-target child when focus leaves it (`FocusRestorer.kt` `saveFocusedChild`, see External). In Show all that child is the grid's internal scroll focus group. `Scrollable.kt` delegates a `FocusTargetModifierNode(focusability = Never)`. No ShowAll focus target sits between the tab box and the grid (`ShowAllScreen.kt:55-59` is `DIScope` plus content, and `ShowAllScreenContent.kt:52,102` are plain `Box`es). **unverified** whether Voyager's `CurrentScreen` adds one. If it does, the argument is the same one level deeper.
- With the default flag (`isFocusRestorationEnabled = false`), `restoreFocusedChild()` recurses: `child.restoreFocusedChild() || (child.canFocus && child.requestFocus())`. The grid group never saved a child, because it has no restorer. It cannot take focus itself either. So the call returns `false` in every frame.
- After frame 5, `requestFocus()` on the tab box. The box is not focusable itself, so Compose goes to `findChildCorrespondingToFocusEnter(Enter)`. That gathers every focusable descendant, treats Enter as Right, starts from the box's top-left corner and picks the best candidate. In practice that is the left-column card of the topmost laid-out row, including a row partly cut off at the top. Then `onCardFocused` writes that card into the memory (`:126,191-194`), so the original position is lost.
- That explains "exactly one row up": rail-open is only possible from the left column, and with default bring-into-view (no `LocalBringIntoViewSpec` in ShowAll; only `PositionFocusedItemInLazyLayout.kt` sets one, for other screens) the focused card sits in the lowest visible row while roughly two rows fit. A testable prediction (**unverified**): if the card is already in the topmost visible row (for example after UP), the rail close lands on the same card. If more rows fit and the card is two rows down, it lands two rows up.

Why History keeps the card:
- `ui/feature/history/component/HistoryCard.kt:49-56`: each card runs `LaunchedEffect(requestFocus, contentFocusActive)`. When `contentFocusActive` turns `true` (Open -> HandingOff) and this is the VM's focus key (updated on every card focus via `CommonAction.ItemFocused`, `HistoryScreenContent.kt:325-335`), it waits one frame and requests focus. That lands well inside the 5-frame grace.
- `HistoryScreenContent.kt:278-283`: the History grid also has `focusRestorer()`. That gives the tab box's `restoreFocusedChild()` a chain down to the card. Two mechanisms, either one enough. Which one wins on the device is **unverified**.

The established pattern for rows and `VideoGrid`:
- `core/ui/uikit/component/moviesList/FocusTargetState.kt:258-290` `RestoreItemFocusOnTabReentryEffect`: it latches `LocalContentFocusHandoff.current?.pendingRequestId` (`:71`), and on a handoff with `contentFocusActive` and the row not holding focus it calls `requestAfterAnchorRestore()` (3-frame loop, `:292-297`) on the remembered card. Spec `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md:190-221` designed exactly this trigger and said grids "are fixed incidentally" through `rememberReconciledItemFocus`. That holds only for `VideoGrid` screens. Show all (`LazyVerticalGrid` with its own `GridFocusMemory`) was not covered.

Tests around it:
- `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt` (317 lines, 23 cases per the qa report): a JVM seam with injected `requestRestore` / `requestGrid` / `contentFocusActive`. A new rail-close case fits here.
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt` (5 tests): re-entry, deep card, opener removed, live neighbour, inactive content. No rail or handoff case.
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:185-250`: a harness with a real `DrawerState`, `ContentFocusHandoff`, `LocalContentFocusHandoff` and `ContentFocusHandoffEffect`. It can be reused to host `ShowAllScreenContent` and press LEFT/RIGHT.

Other grids with the same shape (not in scope, **unverified** on device):
- `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:63` `LazyVerticalGrid` has no `focusRestorer` and no handoff response. It probably shifts the same way.

## 4. Related work

- **showall-focus-restore-hardening** (done, merged 55bb5bfd). It added `pendingEntry`, the `contentFocusActive` key and fallback C (`a50c4b2f`). Its `qa-report.md` has two relevant sections. "Review round 1 fixes": the open observation, 5/5 one row up on HEAD cc66ec96, History keeps the card, and by code reading `reconcile()` returns early. "Base comparison: rail close focus": 3/3 one row up on base 35bfbbec. Evidence: `docs/superpowers/evidence/2026-10-17-showall-focus-restore-hardening-device-round1-fixes.md`. That run did not cause this bug, but it supplies the seam (`GridFocusMemory` internal, `GridFocusMemoryTest`) the fix needs.
- **tab-stack-lost-on-root-push** (done): origin of `GridFocusMemory` (`b0b1740b`).
- **side-rail-focus-handoff** (done, `04915c81`, `016dde35`; plan `docs/superpowers/plans/2026-08-16-side-rail-focus-handoff.md`, spec `...-design.md`): made RIGHT a handoff and added `RestoreItemFocusOnTabReentryEffect`. It assumed grids were covered. This bug is the hole in that assumption for Show all.
- **showall-first-entry-rail-focus** (idea, sibling). On first entry focus sits on the rail, and RIGHT focuses the first card. Same handoff path, but there is no memory yet (`lastFocusedId == null`), so landing on the first card there is the default-enter result and arguably correct. Same file, so run the two one after the other. A handoff response with a null memory must leave that behaviour as it is, or that run must decide otherwise.
- **showall-entry-restore-foreign-landing** (idea): a handoff or D-pad landing plus a list change inside the entry window clears `pendingEntry`. Same file and the same `reconcile()` gate. Overlaps in code, not in symptom. A handoff-aware request could make it smaller, but it does not fix it.
- **grid-focus-restore-after-back** (idea): History and CollectionDetail restore after Back. CollectionDetail's rail-close behaviour likely belongs there or in a follow-up.
- **rail-content-mismatch-rapid-back** (idea): rail state versus content after rapid Back. A different symptom, not related.
- Duplicate: **none**.

## 5. External findings

`.flow/config.yaml` has no `research` key. I treated web research as on, as the previous run did. Sources are the AndroidX source mirror on GitHub, branch `androidx-main`, fetched 2026-10-18. The project uses Compose BOM `2026.08.00` (`gradle/libs.versions.toml:7`). Its pom maps `androidx.compose.ui:ui` and `foundation` to **1.12.0**. No sources jar was in the Gradle cache, so these files are from `main`, not the exact 1.12.0 tag. That the code is identical in 1.12.0 is **unverified**.
- `FocusRestorer.kt`: `saveFocusedChild()` stores the compositeKeyHash of the immediate focus-target child that has focus. With `isFocusRestorationEnabled == false`, `restoreFocusedChild()` finds that child and returns `child.restoreFocusedChild() || (child.canFocus && child.requestFocus())`. The restorer's `onExit` saves only when that flag is false, and `onEnter` restores or uses the fallback. https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRestorer.kt
- `ComposeUiFlags.kt`: `isFocusRestorationEnabled` defaults to `false` on main. It "depends on `isInitialFocusOnFocusableAvailable`", also `false`. The app does not set either (`grep` on `app/src` found only baseline-profile entries). https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/ComposeUiFlags.kt
- `FocusTargetNode.kt` `requestFocus(direction)`: a target that cannot take focus itself goes to `findChildCorrespondingToFocusEnter(direction)`. https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusTargetNode.kt
- `TwoDimensionalFocusSearch.kt:120-156`: Enter is treated as Right, the search starts from `focusRect().topLeft()`, and the result is `findBestCandidate`. `collectAccessibleChildren` (`:219-232`) flattens through groups that cannot take focus, so every card in the grid is a candidate. https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/TwoDimensionalFocusSearch.kt
- `Scrollable.kt:299`: the scrollable node delegates `FocusTargetModifierNode(focusability = Focusability.Never)`, so a lazy grid is a focus group of its own. https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/Scrollable.kt
- Also read for context: `FocusRequester.kt`, `FocusTransactions.kt`, `LazyGrid.kt` (same path prefix).
- Known issues already cited in code, not re-fetched: https://issuetracker.google.com/issues/296551299 (`focusRestorer` saves only on D-pad exit) and https://issuetracker.google.com/issues/290645002 (save/restore unreliable with `LazyColumn`), from `ModalNavigationDrawer.kt:77-81`.

## 6. Options

**A. Answer the rail handoff in `GridFocusMemory` (recommended).** Copy the rows' `RestoreItemFocusOnTabReentryEffect` pattern into the ShowAll memory. Read `LocalContentFocusHandoff.current?.pendingRequestId` in `rememberGridFocusMemory`, latch it, and key the effect on it. When a new handoff id arrives, `contentFocusActive` is true, the grid does not hold focus (`!inGrid`) and `lastFocusedId != null`, set `restoreId = resolveRestoreId(lastFocusedId, lastFocusedIndex, items)` and run the existing 3-attempt `requestRestoreFocus()`. The first attempt comes after one frame, well inside the handoff's 5-frame grace. The handoff then sees the box focused and settles.
- Pros: it uses the same trigger the side-rail spec chose for rows, and it reuses the existing loop, resolver and JVM seam. It is safe against dialogs: it fires only on a real handoff, not on any `contentFocusActive` flip or list change, so the context menu (`:132-136`) cannot have focus taken from it. The null-memory first-entry case (sibling run) stays as it is. It also covers Back-closes-rail (`MainScreenComponent.kt:191`) and re-picking the current tab (`:272`), which start the same handoff.
- Cons: about 10-15 lines in one file, plus a fifth `GridFocusMemory` input (`handoffId`), so the JVM tests need it too. The interplay with `pendingEntry` must be defined. A handoff during a pending entry targets the same card, so it is harmless.
- Size: **S** (one production file plus tests; no contract or data change).

**B. Add `.focusRestorer()` to the Show all grid modifier (as History does).** One line at `ShowAllScreenContent.kt:108`. The grid's scroll group then saves the focused card on LEFT exit, and the tab box's `restoreFocusedChild()` recursion reaches it inside the grace frames.
- Pros: smallest change, the same as History, no new state.
- Cons: it relies on the legacy compositeKeyHash restore chain that the codebase itself calls unreliable for lazy layouts (#290645002, `ModalNavigationDrawer.kt:80`). It breaks if an intermediate focus target appears, or if the saved card leaves composition (a reload during the rail). Its behaviour changes if Compose turns on `isFocusRestorationEnabled`. It would also run on the Back-from-Details path (`FlowComponent.kt:651-662` `RestoreTabContentFocusEffect` calls `restoreFocusedChild()`), next to `GridFocusMemory`'s entry restore. Two restore mechanisms on one grid is the kind of race the last two runs spent their effort removing. History is a weak precedent, since its card-level effect probably does the real work there.
- Size: S.

**C. Generic fix at the handoff (all screens).** For example, have `ContentFocusHandoffEffect` or the tab box restore deeper (walk the saved chain, or publish a "restore your remembered item" signal every screen must honour), or make the blind fallback aim at the previously focused rect instead of top-left.
- Pros: it fixes CollectionDetail and any future grid at once.
- Cons: it touches the shared drawer/handoff contract that every tab depends on (risk area: navigation, focus, startup handoff). It needs a new design and device runs across tabs, and it duplicates the per-screen memories the spec deliberately relied on.
- Size: **M**.

**Recommendation: A.** It is local and deterministic, uses the trigger the rail design already set for this job, and can be tested in JVM and instrumented tests. Do not combine it with B (see Risks).

## 7. Risks

- **Stealing focus.** The handoff response must fire only for a new handoff id with `contentFocusActive`, and only while the grid lacks focus. Otherwise a late attempt could pull focus back after the user moved (rows guard with `rowHasFocusRef`, here `inGrid`). Re-run the hardening run's M2 (a rail opened just after Back is not taken from the user) on the device.
- **Handoff timing.** The 3-attempt loop must land before the handoff's blind request (attempt > 5 frames, `ContentFocusHandoff.kt:123`). Attempt 1 after one frame should do that. If the remembered card has been scrolled out of composition while the rail was open (unlikely, the rail does not scroll the grid), the request fails and the blind landing still occurs. Fallback C (`requestGrid`) would not help, because it is the same default enter.
- **Latching semantics.** `pendingRequestId` goes back to null on settle. Read directly, that cancels the effect mid-loop (the reason rows latch it, spec `:203-208`). Copy the latch.
- **Same-file conflicts** with showall-first-entry-rail-focus and showall-entry-restore-foreign-landing. Sequence them.
- **Root-cause confidence.** The chain "restoreFocusedChild fails, then default enter goes top-left" comes from reading `androidx-main` sources, not the 1.12.0 tag. The device facts match it (one row up, always the left column, History fine). The prediction in section 3 (no shift when the card is in the topmost visible row) is a cheap check for qa.
- **Device verification is required** (AGENTS.md: TV focus, D-pad, overlays). The sequence in the hardening qa report ("Base comparison") is the acceptance script. Also check Back-closes-rail and the first-entry (null memory) case for no change.
- **Option B side effects** if chosen: a double restore on Back from Details, and lazy-layout save/restore flakiness.

## 8. Open questions

1. **Fix shape: A (handoff-aware `GridFocusMemory`), B (`focusRestorer()` on the grid), or C (generic handoff change)?** Recommended: **A**, S, local, using the side-rail design's own trigger.
2. **Should the same handoff response also cover Back-closes-rail and re-picking the current tab?** Recommended: **yes**. They go through the same `beginHandoff(expectsNewContent = false)`, and A covers them for free. Name them in the acceptance criteria.
3. **On first entry with no memory (the sibling run's case), should a handoff do anything?** Recommended: **no, keep today's behaviour** (RIGHT focuses the first card). Leave that decision to showall-first-entry-rail-focus.
4. **Fix CollectionDetail's grid in this run as well?** Recommended: **no**. Record it as a likely sibling (unverified on the device) under grid-focus-restore-after-back and keep this run ShowAll-local.
5. **Test guard: a JVM `GridFocusMemoryTest` case only, or also an instrumented drawer case?** Recommended: **both**. A JVM case (a handoff id arrives with the grid unfocused, so the remembered card is requested, and it is red on the current code), plus an instrumented test reusing the `MainSideMenuFocusTraversalTest` drawer harness with ShowAll content (LEFT from a lower-row card, then RIGHT, then the same card focused). The instrumented test must be shown red once on the current code.

## 9. Sources

Files (main checkout, base 6b087697):
- `AGENTS.md`, `.flow/config.yaml`, `.flow/roles/` (no `researcher.md`), `docs/architecture/ui-and-compose.md` (Focus)
- `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`, `.../showall/ShowAllScreen.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`, `.../drawer/ContentFocusHandoff.kt`
- `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt`
- `app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt`
- `app/src/main/java/com/kino/puber/ui/feature/history/component/HistoryScreenContent.kt`, `.../history/component/HistoryCard.kt`
- `app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`, `.../collections/detail/CollectionDetailScreen.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/PositionFocusedItemInLazyLayout.kt`
- `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt`
- `gradle/libs.versions.toml`, Compose BOM 2026.08.00 pom in the Gradle cache
- `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md`, `docs/superpowers/plans/2026-08-16-side-rail-focus-handoff.md`

Commits: `55bb5bfd`, `a50c4b2f`, `b0b1740b`, `016dde35`, `04915c81`, `35bfbbec` (base of the qa comparison).

Runs: `showall-focus-restore-hardening` (state.md, qa-report.md, research.md), `showall-first-entry-rail-focus`, `showall-entry-restore-foreign-landing`, `grid-focus-restore-after-back`, `side-rail-focus-handoff`, `rail-content-mismatch-rapid-back`, `focus-request-helper`, `showall-rail-close-focus-shift/state.md`.

URLs (fetched 2026-10-18, androidx-main):
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRestorer.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/ComposeUiFlags.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusTargetNode.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/TwoDimensionalFocusSearch.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusTransactions.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRequester.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/Scrollable.kt
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/grid/LazyGrid.kt
- Cited from code, not fetched: https://issuetracker.google.com/issues/296551299, https://issuetracker.google.com/issues/290645002
