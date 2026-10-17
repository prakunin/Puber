# Harden the Show all focus restore - plan

**Spec:** `docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md`
**Goal:** A reload that drops the opener card during the Show all re-entry restore re-targets the restore instead of leaving nothing focused, and the tests guard that path, the per-attempt rail check and the deep-card case.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate:
  `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- androidTest compile: `./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
  One unit class: `./tools/agentw :app:testDevDebugUnitTest --tests <fqcn>`.
- All commands run in the worktree
  `/home/user1/orca/workspaces/Puber/showall-focus-restore-hardening` through
  `./tools/agentw`, never `./gradlew` (the guard hook refuses it). Set
  `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- The implementer never touches a device and never runs `connected*AndroidTest`.
  Instrumented runs belong to qa-device only:
  `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`.
- One task = green fast gate + green tests of the touched code + one commit,
  message per commit_style: one imperative English sentence, no prefixes, never
  a `Co-Authored-By` line.
- No commit carries a red test. Red runs required by the test design are made
  locally, recorded in the Implementation record ("For the reviewers"), and
  never committed.
- The plan is self-contained: the implementer reads this file, the spec, and the
  files cited below, nothing else.
- Scope: one production file
  (`app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`),
  one new JVM test file, one androidTest file. No VM, DI, screen, resource or
  shared-helper changes (`resolveFocusedItemId` / `rememberReconciledItemFocus`
  are not used, spec D8). `ShowAllScreen.kt:59` keeps calling
  `ShowAllScreenContent(state, onAction)` unchanged.
- Detekt over production and unit-test sources: `ReturnCount` max 3 per function
  (`config/detekt/detekt.yml:756-759`), `UnusedPrivateProperty` is active
  (`:829-832`), so never land a constructor `private val` that is not read yet.
  `MagicNumber` and `FunctionNaming` exclude `**/test/**`.

Unprefixed `:NN` below means `ShowAllScreenContent.kt` at base 35bfbbec.
Existing symbols relied on (all verified at base):

- `:43` `GRID_COLUMNS = 3` (private), `:45` `RESTORE_FOCUS_ATTEMPTS = 3`.
- `:80` `val mainContentFocus = rememberFocusRequesterOnLaunch()`, attached to
  the grid at `:107`; `:108` `onFocusChanged { focusMemory.onGridFocusChanged(it.hasFocus) }`.
- `:84` `val focusMemory = rememberGridFocusMemory(state.items)`.
- `:119-120` card modifier: `if (item.id == focusMemory.restoreId) Modifier.focusRequester(focusMemory.restoreFocus)`.
- `:125` `onFocusChanged { if (it.hasFocus) focusMemory.onCardFocused(item.id, index) }`.
- `:139-143` KDoc of `rememberGridFocusMemory`; `:144-161` the function:
  saveable `lastFocusedId`/`lastFocusedIndex` (`:146-147`), `rememberUpdatedState`
  wrappers (`:148-149`), `remember { GridFocusMemory(...) }` (`:150-157`),
  `LaunchedEffect(Unit) { memory.restoreOnEntry() }` (`:158`),
  `LaunchedEffect(items) { memory.restoreWhenFocusedCardLeft() }` (`:159`).
- `:163-213` `private class GridFocusMemory`: `restoreFocus` field (`:169`),
  `restoreId` with an unobserved construction-time initialiser (`:170-175`),
  `inGrid` (`:178`), `onCardFocused` (`:180-183`), `onGridFocusChanged`
  (`:185-192`), `restoreOnEntry` (`:194-196`), `restoreWhenFocusedCardLeft`
  (`:198-204`), `requestRestoreFocus` (`:206-212`, per-attempt
  `contentFocusActive()` at `:209`, `runCatching { restoreFocus.requestFocus() }.getOrDefault(false)` at `:210`).
- `:215-220` `private fun resolveRestoreId(id: Int?, index: Int, items: List<VideoItemUIState>): Int?`.
- `FocusRequester.requestFocus()` returns `Boolean` in this Compose BOM:
  `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:298`.
- `VideoItemUIState(id, title, imageUrl, bigImageUrl, ...)` is a plain data
  class: `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemUIState.kt:49-53`.
- JUnit 5 (`libs.junit5` = `junit-jupiter`, includes params) and
  `libs.coroutines.test` are unit-test dependencies (`app/build.gradle.kts:414-416`);
  `@ParameterizedTest` + `@CsvSource` precedent:
  `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/SubtitleTrackMergerTest.kt:11-12,26`;
  `runTest` precedent: `app/src/test/kotlin/com/kino/puber/domain/interactor/update/AppUpdateInteractorTest.kt:18,38`.
  Compose `MutableState` in JVM tests: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/state/SessionSaveableStateTest.kt:3,28-30`.
- androidTest: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`
  (cases at `:33-57`, `:59-83`, `:85-111`, `:113-135`, `:137-161`; companion
  `ItemCount = 24`, `DeepRows = 5` at `:184-189`).

Why there is no failing-first instrumented test (required by the spec, Risks):
the real race (a reload inside the ~3-frame entry window) cannot be forced on a
device, because attempt 1 normally attaches the opener first. The deterministic
red guard is the JVM seam in Task 2; the androidTests (Task 3) are seen red once
by qa-device against a temporary mutation (AC-8), not as failing-first. The two
mutations, defined in Task 3 under "Red-once mutations", are each one a weak
version of the test would let through and the rewritten test catches:

- AC-2: the resolver's `else` branch returns `null`. With no target, `reconcile()`
  returns before the grid fallback, so the neighbour is never focused (the wait
  times out). Nothing is focused; it is not "C focuses the first card".
- AC-7: the deep-card test presses Down 2 times instead of `DeepRows` (5). Focus
  lands on index 6: the old guard `>= DeepRows` accepts it (6 >= 5), the new
  guard `>= DeepRows * GridColumns` (15) rejects it. Simply restoring the old
  guard, as the spec's D-M4 and AC-8 test-design row word it, cannot go red:
  the old guard is weaker and the off-viewport assertion stays in place.

---

## Tasks

### Task 1: Open a JVM seam on GridFocusMemory without changing behaviour, and test the resolver

**Covers:** AC-3 (and the behaviour-preserving half of D6/D7; AC-8 keeps holding)
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`
  - `ShowAllContentBody` (`:75-137`): add `val restoreFocus = remember { FocusRequester() }`
    before `:84`; `:84` becomes `rememberGridFocusMemory(state.items, restoreFocus)`;
    `:120` becomes `Modifier.focusRequester(restoreFocus)`.
  - `rememberGridFocusMemory` (`:144-161`): new parameter `restoreFocus: FocusRequester`;
    pass `requestRestore = { restoreFocus.tryRequestFocus() }` to the constructor
    (`:151-156`). Keep both effects at `:158-159` exactly as they are.
  - `GridFocusMemory` (`:163`): `private class` -> `internal class`; add constructor
    parameter `private val requestRestore: () -> Boolean` (after `contentFocusActive`);
    delete the `restoreFocus` field (`:169`); `:210` becomes `if (requestRestore()) return`.
    Do not add `requestGrid` yet (it would be an unused private property, Task 2 adds it).
  - `resolveRestoreId` (`:216`): `private fun` -> `internal fun`; body unchanged.
  - Add at file end: `private fun FocusRequester.tryRequestFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)`.
- Test (new): `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`
  (package `com.kino.puber.ui.feature.showall.content`; the directory
  `app/src/test/kotlin/com/kino/puber/ui/feature/showall/` exists with `vm/`).

**Consumes:** `resolveRestoreId` (`:215-220`), `VideoItemUIState` (`VideoItemUIState.kt:49-53`).
**Produces (relied on by Task 2):**
- `internal class GridFocusMemory(lastFocusedId: MutableState<Int?>, lastFocusedIndex: MutableState<Int>, items: () -> List<VideoItemUIState>, contentFocusActive: () -> Boolean, requestRestore: () -> Boolean)`
  with public `restoreId: Int?` (private set), `onCardFocused(id, index)`,
  `onGridFocusChanged(hasFocus)`, `suspend restoreOnEntry()`,
  `suspend restoreWhenFocusedCardLeft()`.
- `internal fun resolveRestoreId(id: Int?, index: Int, items: List<VideoItemUIState>): Int?`.
- `private fun FocusRequester.tryRequestFocus(): Boolean`.

- [x] Make the production edits above. Behaviour is identical: the same card gets
  the requester, the same 3-attempt loop runs, the same `runCatching` guards the
  request. Imports stay as they are (`remember`, `FocusRequester` are already
  imported at `:18,:26`).
- [x] Write `GridFocusMemoryTest` with the AC-3 cases, as one `@ParameterizedTest`
  or separate tests named `resolveRestoreId_<case>`. Items: `card(n) = VideoItemUIState(id = n, title = "Card $n", imageUrl = "", bigImageUrl = "")`,
  `cards(range) = range.map(::card)`. Cases (input -> expected):
  - present: id 5, index 4, cards 1..10 -> 5
  - gone mid-list: id 5, index 4, cards 1..10 without 5 -> 6 (the card now at index 4)
  - gone and was last: id 10, index 9, cards 1..9 -> 9
  - index beyond a shorter list: id 99, index 40, cards 1..10 -> 10
  - empty list: id 5, index 4, empty -> null
  - null id: null, index 0, cards 1..10 -> null
  - null id with a non-zero index: null, index 4, cards 1..10 -> null
  JUnit 5 only (`org.junit.jupiter.api.*`), no `MainDispatcherExtension`, no MockK,
  no `FakeResourceProvider`.
- [x] Run `./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.ui.feature.showall.content.GridFocusMemoryTest`
  (green), the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`
  (the androidTest still compiles against `internal ShowAllScreenContent`) and
  `./tools/agentw :app:detektAll` (so no detekt finding is committed in Task 1); commit,
  for example "Inject the Show all restore request into GridFocusMemory and test the restore resolver".

**Done when:** the fast gate, the androidTest compile and `:app:detektAll` pass, the
`GridFocusMemoryTest` run shows the seven resolver cases green, and
`git show --stat HEAD` lists only `ShowAllScreenContent.kt` and `GridFocusMemoryTest.kt`.
`grep -n "LaunchedEffect" ShowAllScreenContent.kt` still shows `:95`, the
`LaunchedEffect(Unit)` and the `LaunchedEffect(items)` lines.

---

### Task 2: Re-target the entry restore on every list change, with a last-resort grid focus

**Covers:** AC-1, AC-2 (JVM half), AC-4, AC-5, AC-6; spec D1-D6
**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`
  (line numbers are base lines; Task 1 shifted them by a few, find the symbols by name)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`

**Consumes:** Task 1's `GridFocusMemory` constructor, `restoreId`, `onCardFocused`,
`onGridFocusChanged`, `restoreOnEntry`, `restoreWhenFocusedCardLeft`,
`tryRequestFocus`; `mainContentFocus` (`:80`, grid requester at `:107`).
**Produces:** `GridFocusMemory(..., requestRestore: () -> Boolean, requestGrid: () -> Boolean)`
with `suspend fun reconcile()` replacing `restoreOnEntry` and
`restoreWhenFocusedCardLeft`; `rememberGridFocusMemory(items, restoreFocus, gridFocus)`.

Order inside this task (red first, locally only):

- [x] **Step A, wiring that keeps behaviour.** Add constructor parameter
  `private val requestGrid: () -> Boolean` after `requestRestore`.
  `rememberGridFocusMemory` gains `gridFocus: FocusRequester` and passes
  `requestGrid = { gridFocus.tryRequestFocus() }`; `ShowAllContentBody` passes
  `mainContentFocus` (`rememberGridFocusMemory(state.items, restoreFocus, mainContentFocus)`).
  Do not call `requestGrid` yet. Do not commit this state (detekt would flag the
  unused property; the commit happens after Step D).
- [x] **Step B, write the tests.** Add to `GridFocusMemoryTest`:
  - Harness (in the test file):
    - `FakeFrameClock(onFrame: (Int) -> Unit) : MonotonicFrameClock`:
      `withFrameNanos(block)` increments `frames`, runs `block(frames * 16_000_000L)`,
      calls `onFrame(frames)`, then `currentCoroutineContext().ensureActive()`
      (so a cancel issued in the hook is observed at once), returns the block's result.
    - Mutable `itemsVar`, `activeVar`; a shared `events: MutableList<String>`;
      `requestRestore = { val id = memory.restoreId; val ok = restoreSucceeds(id); events += "restore:$id:$ok"; ok }`
      where the default `restoreSucceeds(id)` is `id != null && itemsVar.any { it.id == id }`
      (models "the requester is attached only to the card whose id equals
      `restoreId`", `:119-120`); `requestGrid = { events += "grid"; true }`.
      Tests that need a failing target override `restoreSucceeds`.
    - Construct the real `GridFocusMemory(mutableStateOf<Int?>(saved), mutableStateOf(index), { itemsVar }, { activeVar }, requestRestore, requestGrid)`.
    - A list change mirrors `LaunchedEffect(items)`: `changeItems(new)` sets
      `itemsVar = new`, cancels the current `reconcile` job and launches a new
      one with `launch(clock) { memory.reconcile() }`. Tests run
      `runTest { ... launch(clock) { memory.reconcile() }; advanceUntilIdle() }`;
      no sleeps, no real time.
    - Until Step C exists, a private test-file adapter
      `private suspend fun GridFocusMemory.reconcile() { restoreOnEntry(); restoreWhenFocusedCardLeft() }`
      lets the tests compile against the Task 1 shape.
    - Class KDoc states that the fake clock does not model recomposition, so these
      tests prove the decision logic, not when the requester attaches (attach
      timing is covered by the androidTests and the device checks; spec R5).
  - Tests, named in the spec's `subject_behaviour` form (subject `reconcile`, or
    the member the test drives); the U-ids go into a one-line comment above each:
    - U-AC1a `reconcile_reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour`:
      saved 5, index 4, cards 1..10; `onFrame(1)` calls `changeItems(cards 1..10 without 5)`.
      Assert the last `restore:` event is `restore:6:true`, `memory.restoreId == 6`, no `grid` event.
    - U-AC1b `reconcile_reloadOnTheLastAttemptStillGetsAFreshBudget`: as U-AC1a with
      `restoreSucceeds` false while `restoreId == 5` (opener not yet attached) and
      the change fired in `onFrame(3)`. Assert focus lands on 6 within frames 4..6.
    - U-AC1c `reconcile_openerDroppedAsLastItemRetargetsToTheNewLastCard`: saved 10, index 9,
      cards 1..10; reload leaves 1..9 inside the window. Assert `restore:9:true`.
    - U-AC2a `reconcile_openerAbsentAtConstructionFocusesTheClampedIndexCard`: saved 5,
      index 4, list built without 5; assert `restoreId == 6` before `reconcile()`
      and the request lands on 6. Second case: index 40 on cards 1..10 -> 10.
    - U-AC4 `reconcile_contentFocusFlippingOffBetweenAttemptsStopsFurtherRequests`:
      `restoreSucceeds` always false; `onFrame(2)` sets `activeVar = false`.
      Assert exactly one `restore:` event, no `grid`. Companion
      `reconcile_contentFocusInactiveFromTheStartMakesNoRequest`: `activeVar = false`; no events.
    - U-AC5a `reconcile_everyTargetedAttemptFailingWhileActiveCallsTheGridRequesterOnce`:
      `restoreSucceeds` always false, active. Assert `events` is three `restore:`
      events followed by exactly one `grid` (order, not only counts).
    - U-AC5b `reconcile_gridRequesterIsNotCalledWhenContentFocusIsInactive`: active false
      from the start; and a second case flipping to false in `onFrame(3)`. No `grid`.
    - U-AC5c `reconcile_gridRequesterIsNotCalledWhileTargetedAttemptsArePendingOrAfterASuccess`:
      success on attempt 2 -> no `grid`; a list change in `onFrame(2)` whose new
      loop succeeds -> no `grid` from the cancelled loop; empty list at
      construction (`restoreId == null`) -> no events at all.
    - U-AC6a `reconcile_appendWhileFocusIsInTheGridMakesNoRequest`: saved 5 index 4;
      `onGridFocusChanged(true)`, `onCardFocused(3, 2)`; append cards 11..13;
      `reconcile()`. No events.
    - U-AC6b `reconcile_remapKeepingTheFocusedCardMakesNoRequest`: same, items replaced by
      copies with the same ids and changed titles. No events.
    - U-AC6c `reconcile_focusLeavingTheGridToTheRailThenReloadMakesNoRequest`: saved 5;
      `onGridFocusChanged(true)`, `onGridFocusChanged(false)` (card 5 still in the
      list, so `inGrid` becomes false), then a reload dropping 5, `reconcile()`. No events.
    - U-AC6d `reconcile_firstLaunchWithNoSavedIdMakesNoRequest`: saved null; items arrive;
      `reconcile()`. No events.
    - U-AC6e `reconcile_liveGridFocusedCardRemovedRetargetsToTheNeighbour`: saved null
      (no entry pending), `onGridFocusChanged(true)`, `onCardFocused(5, 4)`, list
      drops 5, `reconcile()`. Assert `restore:6:true`.
- [x] **Run red, record, do not commit.** Run
  `./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.ui.feature.showall.content.GridFocusMemoryTest`.
  Expected red on the Step A code: U-AC1a/b/c (the entry loop keeps asking for
  card 5 and the live path no-ops with `inGrid == false`) and U-AC5a (no `grid`
  event). Others may also be red (for example U-AC6a, because `restoreOnEntry`
  ignores grid focus); only the expected ones must be. Paste the failing test
  names and their assertion messages into the Implementation record.
- [x] **Step C, the fix (spec D1-D5).** In `GridFocusMemory`:
  - add `private var pendingEntry = Snapshot.withoutReadObservation { lastFocusedId.value != null }`
    with a one-line comment (true from a re-entry with a saved card until focus
    first lands in the grid). The read must stay unobserved (spec risk 5).
  - `onGridFocusChanged`: in the `hasFocus` branch also set `pendingEntry = false`.
    Nothing else ever sets it to true.
  - `requestRestoreFocus()` returns `Boolean`: `repeat(RESTORE_FOCUS_ATTEMPTS) { withFrameNanos { }; if (!contentFocusActive()) return false; if (requestRestore()) return true }; return false`.
    `contentFocusActive()` stays read on every attempt (AC-4).
  - replace `restoreOnEntry` and `restoreWhenFocusedCardLeft` with:
    ```kotlin
    suspend fun reconcile() {
        val focusedId = lastFocusedId.value
        val focusedCardLeft = inGrid && focusedId != null && items().none { it.id == focusedId }
        if (!pendingEntry && !focusedCardLeft) return
        restoreId = resolveRestoreId(focusedId, lastFocusedIndex.value, items())
        if (restoreId == null || requestRestoreFocus()) return
        // Last resort for a pending entry only: never leave the re-entered grid without focus.
        if (pendingEntry && contentFocusActive()) requestGrid()
    }
    ```
    (at most 3 returns per function, detekt `ReturnCount`).
  - `rememberGridFocusMemory`: replace the two effects (`:158-159`) with one
    `LaunchedEffect(items) { memory.reconcile() }`.
  - KDoc `:139-143`: add one sentence, e.g. "A reload that lands before the
    entry focus does re-targets the restore, and if no card takes focus the grid
    itself is focused."
  - Delete the test-file adapter from Step B (the member now exists).
- [x] **Run green.** The `GridFocusMemoryTest` run is all green.
- [x] **AC-4 red once, locally.** Temporarily capture `val active = contentFocusActive()`
  before the `repeat` in `requestRestoreFocus` and use `active` inside the loop;
  run the class; U-AC4 must fail. Record the failure in the Implementation
  record, revert, re-run green.
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin`,
  and `./tools/agentw :app:detektAll`; commit, for example
  "Re-target the Show all entry focus restore when a reload drops the opener".

**Done when:** `GridFocusMemoryTest` is green under the single-class command; the
Implementation record lists the Step B red names (at least U-AC1a, U-AC1b,
U-AC1c, U-AC5a) and the U-AC4 red-once result; `grep -n "restoreOnEntry\|restoreWhenFocusedCardLeft\|LaunchedEffect(Unit)" ShowAllScreenContent.kt`
prints nothing; `grep -n "withoutReadObservation" ShowAllScreenContent.kt` shows
two lines (`restoreId` and `pendingEntry`); the fast gate and detekt pass.

---

### Task 3: Make the re-entry androidTests exercise the entry fallback and a truly deep card

**Covers:** AC-2 (instrumented), AC-7, AC-8 (compile here; device run by qa-device), AC-10
**Files:**
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`

**Consumes:** the unchanged `internal fun ShowAllScreenContent(state, onAction)` (`:47-51`);
production behaviour from Task 2.
**Produces:** nothing consumed later.

- [x] AC-2, rewrite `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry`
  (`:85-111`): `var shown = items` becomes a plain `var` (no `mutableStateOf`), still
  read inside `restoration.setContent { ... ShowAllViewState.Content(shown) ... }`.
  Keep the `DeepRows` Down presses; then `val index = ...` and
  `shown = items.filterIndexed { i, _ -> i != index }` before
  `restoration.emulateSavedInstanceStateRestore()`. Only the restored composition
  sees the new list, so the construction-time resolver runs, not the live path.
  After the restore wait for and assert `shown[index].title` focused, and add
  `composeRule.onNodeWithText(items[index].title).assertDoesNotExist()`.
  Drop the now unused `mutableStateOf`/`getValue`/`setValue` imports only if no
  other case uses them (`:113-161` still do, so they stay).
- [x] AC-7, rewrite `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry`
  (`:59-83`): add companion `const val GridColumns = 3` (mirrors the private
  `GRID_COLUMNS` at `:43`). Guard becomes
  `assertTrue("focus moved below the first viewport", items.indexOfFirst { it.title == title } >= DeepRows * GridColumns)` (15);
  keep `repeat(DeepRows)` for the Down presses (the AC-7 mutation edits exactly that line).
  Before `emulateSavedInstanceStateRestore()`, assert the first card is not
  displayed with an exact text match (so "Card 10" does not count):
  `val first = composeRule.onAllNodesWithText(items.first().title)` and
  `assertTrue("card 1 is off the viewport", (0 until first.fetchSemanticsNodes().size).none { first[it].isDisplayed() })`
  (imports `androidx.compose.ui.test.onAllNodesWithText`, `androidx.compose.ui.test.isDisplayed`).
  After the restore the opener is focused, as now.
- [x] Leave `:33-57`, `:113-135`, `:137-161` unchanged (AC-8).
- [x] Under "Deviations from the plan" in the Implementation record, note: "AC-7
  red-once uses the 2-Down-press mutation (Task 3, Red-once mutations) instead of
  the spec's 'old `>= DeepRows` guard', which cannot go red; conductor's correction
  of D-M4 and the AC-8 test-design row." The implementer does not run either
  mutation (no device); qa-device does.
- [x] Run `./tools/agentw :app:compileDevDebugAndroidTestKotlin`, the fast gate,
  and the full gate `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`
  (AC-10); commit, for example "Make the Show all re-entry tests cover the entry fallback and an off-viewport card".

**Red-once mutations (qa-device, on the committed Task 3 head, never committed):**

- M-AC7: in `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry` change
  `repeat(DeepRows) {` to `repeat(2) {` (that test only). Run the class. Expected:
  that case fails at `focus moved below the first viewport` (focused index 6 <
  15), or, if the guard line were absent, at `card 1 is off the viewport`. The
  other four cases stay green. Record the failure message. For the record: under
  the pre-change guard `>= DeepRows` the same mutation passes the guard (6 >= 5)
  and the test goes green, which is the gap AC-7 closes.
- M-AC2: in `ShowAllScreenContent.kt` change the resolver's
  `else -> items[index.coerceIn(0, items.lastIndex)].id` to `else -> null`.
  Rebuild, install-preserve, run the class. Expected:
  `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry`
  fails with the `waitUntil` timeout for the neighbour (no target, so
  `reconcile()` returns before the grid fallback and nothing is focused).
- After each: revert, `git -C <wt> diff --exit-code` prints nothing, and the
  class is green again on the reverted build before the next step.

**Done when:** the androidTest compile, the fast gate and the full gate pass.
The device half is handed to qa-device and is not run by the implementer:
the class run via
`make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`
shows all five cases green (AC-8); the evidence file records M-AC7 red (with the
guard's failure message) and M-AC2 red (neighbour wait timeout), both reverted
with a clean `git diff`; and the device checks D-M1..D-M3 of the spec (AC-9). If qa finds "Card 1" still displayed after 5
rows on its emulator, the AC-7 test moves to 8 Down presses (24 items, 3
columns) with the guard `>= 7 * GridColumns`; qa reports it and the implementer
lands that change as a follow-up commit under "Deviations from the plan".

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: `Inject the Show all restore request into GridFocusMemory and test the restore resolver`
- Task 2: `Re-target the Show all entry focus restore when a reload drops the opener`
- Task 3: `Make the Show all re-entry tests cover the entry fallback and an off-viewport card`

(shas: `git log 35bfbbec..HEAD`.)

**Deviations from the plan:**

- AC-7 red-once uses the 2-Down-press mutation (Task 3, Red-once mutations) instead of the spec's "old `>= DeepRows` guard", which cannot go red; conductor's correction of D-M4 and the AC-8 test-design row. Neither mutation was run by the implementer (no device); qa-device runs them.
- Readiness-2 note 2 for the R6 fallback: if qa finds "Card 1" still displayed, use 7 Down presses with the guard `>= 7 * GridColumns` (21), not 8. Not needed so far.
- Test harness: `Harness` takes `restoreOverride` (nullable) and exposes a `restoreSucceeds` var, because a constructor default referencing `itemsVar` does not compile before initialisation. Behaviour as planned.

**Files touched outside the plan:** none.

**For the reviewers:**

Local red runs (never committed), `GridFocusMemoryTest` on the Step A code (seam, requestGrid wired but unused, old `restoreOnEntry` + `restoreWhenFocusedCardLeft` through a test adapter): 21 tests, 8 failed.

- U-AC1a `reconcile_reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour`: expected `restore:6:true` but was `restore:5:false`.
- U-AC1b `reconcile_reloadOnTheLastAttemptStillGetsAFreshBudget`: expected `restore:6:true` but was `restore:5:false`.
- U-AC1c `reconcile_openerDroppedAsLastItemRetargetsToTheNewLastCard`: expected `restore:9:true` but was `restore:10:false`.
- U-AC5a `reconcile_everyTargetedAttemptFailingWhileActiveCallsTheGridRequesterOnce`: expected three `restore:5:false` then `grid`, no `grid` event.
- U-AC5c `reconcile_gridRequesterIsNotCalledWhileTargetedAttemptsArePendingOrAfterASuccess`: its "list change in onFrame(2)" part, expected `restore:6:true` but was `restore:5:false` (same cause as U-AC1).
- U-AC6a/b/c (append, remap, rail then reload) also red on the old code: `restoreOnEntry` ignored grid focus, so it kept requesting. Plan allowed this.

After Step C (`reconcile()`, `pendingEntry`, `requestGrid`): all 21 green.

AC-4 red-once: with `val active = contentFocusActive()` captured before the `repeat` in `requestRestoreFocus`, `reconcile_contentFocusFlippingOffBetweenAttemptsStopsFurtherRequests` fails (`expected: <1> but was: <3>`). Reverted, class green again.

Gates: `check.full` (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) plus `:app:compileDevDebugKotlin` and `:app:compileDevDebugAndroidTestKotlin` all BUILD SUCCESSFUL at the Task 3 head.

Look at: `pendingEntry` read under `Snapshot.withoutReadObservation` (no recomposition on focus moves); `reconcile()` last-resort `requestGrid()` only for a pending entry with content focus active; the androidTest cannot be run here, qa-device owns the device half (class run, M-AC7, M-AC2, D-M1..D-M3).

**Review round 1 fixes:**

- Triage #1 (edge F1): `LaunchedEffect(items, contentFocusActive)` so a pending entry restore is retried when content focus turns active. JVM case `reconcile_entryWithContentInactiveRestoresWhenReconcileRerunsAfterItTurnsActive` pins that reconcile acts once active. It cannot go red without the key: the harness calls `reconcile()` itself, and the effect key is Compose wiring, so no local red run exists for it.
- Triage #2 (tests F1): `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid` (three restore events, no `grid`). Red once locally by dropping `pendingEntry &&` from the fallback gate (1 of 23 failed), reverted, not committed.
- Triage #3 (tests F2): the Compose wiring of `requestGrid` to the grid requester (`gridFocus.tryRequestFocus()`), and of the effect key, is checked by review only; it is not deterministically testable (forcing the fallback on device is not practical).

**Blocked:** none.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

### Readiness round 1

Verdict: **NOT READY** (one plan fix; everything else holds)

Read: spec `docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md`
(cdc5a87c), plan `docs/superpowers/plans/2026-10-17-showall-focus-restore-hardening.md`
(767872e4), worktree at base 35bfbbec plus those two doc commits (`git diff 35bfbbec --stat`
shows only the two docs).

## AC coverage

| AC | Plan task | Test design row | Note |
|----|-----------|-----------------|------|
| AC-1 | Task 2 (U-AC1a/b/c, red on Step A) | yes | Traced by hand against the Step A adapter: old job cancelled in `onFrame(1)`, new job asks for card 5 three times, live path no-ops on `inGrid == false` -> red; against Step C `reconcile()` -> `restore:6:true` |
| AC-2 | Task 2 (U-AC2a) + Task 3 (androidTest rewrite) | yes | |
| AC-3 | Task 1 (7 resolver cases) | yes | |
| AC-4 | Task 2 (U-AC4 + red-once with a captured value) | yes | Captured-once revert gives 3 restore events instead of 1 -> red, as claimed |
| AC-5 | Task 2 (U-AC5a/b/c) | yes | |
| AC-6 | Task 2 (U-AC6a-e) | yes | |
| AC-7 | Task 3 | yes | red-once mutation is not workable, see Fix 1 |
| AC-8 | Task 3 (compile) + qa-device class run | yes | depends on Fix 1 |
| AC-9 | handed to qa-device (Task 3 Done when, D-M1..D-M3) | yes | |
| AC-10 | Task 3 full gate | yes | |

## Citations sampled (all resolve at base)

- `ShowAllScreenContent.kt:43,45` `GRID_COLUMNS`, `RESTORE_FOCUS_ATTEMPTS`; `:80` `rememberFocusRequesterOnLaunch()`; `:84` `rememberGridFocusMemory(state.items)`; `:107-108`, `:119-120`, `:125` grid/card modifiers; `:158-159` the two effects; `:163-213` `private class GridFocusMemory` with `restoreFocus` at `:169`, `inGrid` at `:178`, per-attempt check at `:209`, `runCatching` at `:210`; `:216-220` `private fun resolveRestoreId` - all match.
- `FocusTargetState.kt:298` `if (requestFocus()) return` - matches (Boolean return).
- `VideoItemUIState.kt:49-53` data class `VideoItemUIState(id, title, imageUrl, bigImageUrl, ...)` - matches.
- `app/build.gradle.kts:414-416` `junit5`, `junit5.launcher`, `coroutines.test` - matches.
- `SubtitleTrackMergerTest.kt:11-12,26` `ParameterizedTest`/`CsvSource` - matches. `AppUpdateInteractorTest.kt:18,38` `runTest` - matches.
- `config/detekt/detekt.yml:756-759` `ReturnCount max: 3`; `:829-832` `UnusedPrivateProperty active` - matches.
- `ShowAllScreen.kt:59` calls `ShowAllScreenContent(state, onAction)` - matches.
- `FocusTargetResolver.kt:10-24` `resolveFocusedItemId(previousItems, updatedItems, focusedItemId)` - matches (needs the previous list, as D7/D8 say).
- `ShowAllScreenContentFocusTest.kt` cases at `:33-57/:59-83/:85-111/:113-135/:137-161`, companion `ItemCount = 24`, `DeepRows = 5` at `:184-189` - matches.
- Weak: `SessionSaveableStateTest.kt:3,28-30` is cited (plan line 70, spec D6) as proof that `mutableStateOf`/`Snapshot` work in JVM tests. It only imports `MutableState` and gets `null` back from a saver; no JVM test in `app/src/test` creates `mutableStateOf` or calls `withFrameNanos`. I checked feasibility independently: `runtime-android-1.12.0` `classes.jar` (the BOM 2026.08.00 runtime) references no `android/*` class, so snapshot state and `withFrameNanos` with an injected `MonotonicFrameClock` run on the plain JVM. Not blocking.
- `androidx.compose.ui.test.isDisplayed(SemanticsNodeInteraction): Boolean` exists in `ui-test-android-1.12.0` (`AssertionsKt`), so the AC-7 assertion compiles.

## Build and order

- Task 1 is a behaviour-preserving seam plus pure resolver tests; it compiles and commits green. Task 2 keeps the unused `requestGrid` uncommitted until Step C uses it (detekt `UnusedPrivateProperty`), and the red runs stay local. Task 3 only touches the androidTest. Dependency order 1 -> 2 -> 3 is correct; no task leaves the build red.
- `reconcile()` has 2 explicit returns, `requestRestoreFocus()` 3: within `ReturnCount max 3`.
- Scope: one production file, one new JVM test, one androidTest. Nothing from Out of scope (live-path C, other grids, first-entry rail, shared helper) is planned. Q1-Q4 are answered; none blocks.

## Must fix

1. **The AC-7 red-once mutation cannot be red** (plan lines 78-79 and 322-324, also spec Test design AC-8 row and D-M4). The plan has qa run the rewritten deep-card test "with the old `>= DeepRows` guard" and expects red. The old guard is weaker than the new one (`>= 5` instead of `>= 15`), and the new "card 1 not displayed" assertion stays in place, so this mutation can only stay green. qa then cannot produce the evidence AC-8 requires ("the AC-2 and AC-7 rewrites have each been seen red once"). Define a mutation that shows the new guard catches what the old one let through. For example, temporarily replace the 5 Down presses with 2: focus lands on index 6. The old guard accepts that (`6 >= 5`), but the new guard (`>= 15`) and/or the off-viewport assertion fail. Record it in Task 3 "Done when" and in the hand-off to qa-device, and note it under Deviations as the conductor's correction of the spec's D-M4 wording.

## Non-blocking notes (for the implementer and the reviewers)

- Spec risk 2 and the I-AC2 row say that with the resolver's `else` branch reverted, "C would focus the first card". It would not: `restoreId` would be null, and `reconcile()` returns before C. Nothing gets focused, so the test still goes red (timeout). The red-once for AC-2 is valid; only the explanation is wrong.
- Task 1's checks leave out detekt. It is first run in Task 2. A detekt finding introduced in Task 1 would sit in a committed task until then. Consider adding `:app:detektAll` to Task 1's checks.
- The plan's test names (`reloadDropping...`) do not follow the `subject_behaviour` form that the spec's test design asks for. Cosmetic; detekt `FunctionNaming` excludes tests.

### Readiness round 2

Verdict: **READY**

Read: spec `docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md`
(cdc5a87c, still the committed wording), plan
`docs/superpowers/plans/2026-10-17-showall-focus-restore-hardening.md` (767872e4 plus
the uncommitted revision, `git diff` +60/-25, plan file only). Worktree head
767872e4 on base 35bfbbec, no production changes yet.

## Round 1 must-fix 1: AC-7 red-once mutation, resolved

The plan now defines M-AC7 (Task 3, "Red-once mutations", plan lines 336-344):
`repeat(DeepRows) {` becomes `repeat(2) {` in the deep-card test only. I traced it
against the androidTest at base: `awaitAnyFocus()` lands on card 1 (index 0; the
first case at `:49` relies on the same start). Two Down presses in a 3-column grid
reach index 6. The new guard `>= DeepRows * GridColumns` (15) fails with
"focus moved below the first viewport". The old guard `>= DeepRows` (`:75`)
accepts 6, so the old test would have stayed green. The mutation therefore shows
exactly the gap AC-7 closes. Task 3 keeps `repeat(DeepRows)` as the single line
to mutate (line 319). Done-when (lines 358-360) has qa record the failure message.
The deviation note is added for the Implementation record (lines 327-331).

M-AC2 re-checked against the planned `reconcile()` (lines 260-268) and the code
at base. With `else -> null`, the restored composition has saved id 16 absent, so
`restoreId` is null. `pendingEntry` is true, `reconcile()` returns at line 265
before `requestGrid()`, and `rememberFocusRequesterOnLaunch` does not refire
because `isFocusRequested` is `rememberSaveable`
(`FocusOnLaunchRequester.kt:51-61`). Nothing is focused, so the neighbour wait
times out and the test goes red. The plan's explanation (lines 83-85, 349-350) is
now correct. Round 1's note on Risk 2 wording is addressed in the plan.

Both mutations end with a revert, a clean `git diff --exit-code` and a green
re-run (lines 351-352). Neither is committed.

## Other round 1 notes, addressed

- Task 1 now runs `:app:detektAll` and lists it in Done when (lines 143-148).
- The JVM test names now use the `subject_behaviour` form `reconcile_...`, with U-ids
  in comments (lines 204-242).

## AC coverage (unchanged from round 1, re-checked)

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 2 U-AC1a/b/c | yes |
| AC-2 | Task 2 U-AC2a + Task 3 rewrite + M-AC2 | yes |
| AC-3 | Task 1, 7 resolver cases | yes |
| AC-4 | Task 2 U-AC4 + captured-value red-once | yes |
| AC-5 | Task 2 U-AC5a/b/c | yes |
| AC-6 | Task 2 U-AC6a-e | yes |
| AC-7 | Task 3 rewrite + M-AC7 | yes (row wording being corrected) |
| AC-8 | Task 3 compile + qa-device class run with both red-once | yes (row wording being corrected) |
| AC-9 | qa-device D-M1..D-M3 | yes |
| AC-10 | Task 3 full gate | yes |

## Citations sampled this round (all resolve at 767872e4)

- `ShowAllScreenContent.kt:18` `remember` import, `:26` `FocusRequester` import,
  `:43` `GRID_COLUMNS = 3`, `:45` `RESTORE_FOCUS_ATTEMPTS = 3`, `:80`
  `rememberFocusRequesterOnLaunch()`, `:84` `rememberGridFocusMemory(state.items)`,
  `:107-108` grid requester and `onGridFocusChanged`, `:120`
  `focusRequester(focusMemory.restoreFocus)`, `:158-159` the two effects, `:169`
  `restoreFocus`, `:178` `inGrid`, `:209-210` per-attempt check and `runCatching`,
  `:216` `resolveRestoreId`. All match.
- `FocusTargetState.kt:298` `if (requestFocus()) return`. Matches.
- `app/build.gradle.kts:414-416` junit5, launcher, coroutines.test. Matches.
- `config/detekt/detekt.yml:756-759` `ReturnCount max: 3`; `:829-832`
  `UnusedPrivateProperty active`. Match.
- androidTest `:59-83`, `:75` old guard, `:85-111`, `:184-189` companion. Match.

## Build, order, scope

- Order 1 -> 2 -> 3 holds. Each task ends green with one commit. Step A of Task 2
  stays uncommitted until Step C uses `requestGrid`. `reconcile()` has 2 returns
  and `requestRestoreFocus()` has 3, which is within `ReturnCount`.
- Scope is still one production file, one new JVM test and one androidTest.
  Nothing from Out of scope is planned. No blocking open question.

## Must fix

None.

## Conditions and notes (not blocking)

1. Spec AC-8 (line 58), the AC-2/AC-8 Test design rows (lines 263, 276), R9
   (line 244) and D-M4 (line 329) still have the old wording. They say "old
   `>= DeepRows` guard" and "C would focus the first card". The conductor must
   copy the corrected wording into the spec before qa-device runs, because qa
   reads D-M4. Until then the plan's M-AC7/M-AC2 take precedence, and the plan
   records this as a deviation.
2. The R6 fallback in Task 3 (lines 360-363) uses "8 Down presses" with the guard
   `>= 7 * GridColumns`. With 24 items there are 8 rows, so 7 presses reach the
   last row. The 8th press, made from the last row, may move focus out of the grid
   or do nothing. If the fallback is needed, use 7 presses (guard `>= 21`). This
   applies only if qa reports that card 1 is still displayed.
3. Readiness 1's note on `SessionSaveableStateTest.kt` as a weak precedent for
   JVM snapshot state still stands. JVM snapshot state is feasible, as verified in
   round 1.
