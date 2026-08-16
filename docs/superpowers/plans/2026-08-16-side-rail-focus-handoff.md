# Side Rail Focus Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the side rail collapse when a tab is picked with the remote, and land focus in the content at its remembered position, the way YouTube and Netflix behave.

**Architecture:** `DrawerState` stops deriving its value from focus and becomes an explicit three-state machine (`Closed`, `Open`, `HandingOff`). A `ContentFocusHandoff` object carries the transient "focus is in flight" step: the rail begins a handoff, the content retries `requestFocus()` across frames until its focusable children exist, and the content's own `onFocusChanged` confirms the landing. Per-tab position is not stored anew — the handoff request id becomes an additional restore trigger for the `rememberSaveable` row/card memory that already survives tab switches.

**Tech Stack:** Kotlin, Jetpack Compose (BOM `2026.08.00`), AndroidX TV Material3, Voyager navigation, Koin, JUnit 6 (Jupiter) for unit tests, JUnit 4 + Compose UI test for instrumented tests.

**Spec:** `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md`

## Global Constraints

- JDK 21 runs the compilers (`Versions.ToolchainJavaVersion`); bytecode target stays 17 (`Versions.JvmTargetVersion`). Android SDK 37.
- Build with `./gradlew` in the main checkout. In a git worktree use `./tools/agentw <task>` instead.
- Scope is `NavigationMode.SideDrawer` only. `NavigationMode.TopTabs` must keep working unchanged and gets no new behaviour.
- Detekt runs against `config/detekt/detekt.yml` with an **empty** baseline (`config/detekt/detekt-baseline.xml`). The baseline must stay empty. A deliberate exception is a `@Suppress` at the declaration with a comment saying why.
- All file content, comments and commit messages in English.
- UI is `androidx.tv.material3`, not mobile Material.
- Never hardcode dependency or SDK versions; they come from `gradle/libs.versions.toml` and `buildSrc/src/main/kotlin/Versions.kt`.
- Unit tests use `org.junit.jupiter.api.Test` / `org.junit.jupiter.api.Assertions`. Instrumented tests use `org.junit.Test` / `org.junit.Rule`.
- Fire TV: preserve-data installs only. No uninstall, no data wipe, no logout — re-pairing risks an account block.
- Handoff retry budget: `HANDOFF_FOCUS_REQUEST_FRAMES = 30`.

---

### Task 1: Prove the current instrumented test is a false green

`MainSideMenuFocusTraversalTest` already asserts "Enter collapses the rail" and passes, because its content is a static focusable present from frame one. This task produces the evidence that the test does not catch the reported defect, and leaves a repaired test that does.

**This task has no commit step.** The repaired test fails against current production code, and master must stay green. It is committed in Task 6, once the implementation makes it pass. Keep the working-tree change; do not stash it.

**Files:**
- Modify: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:44-95`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing consumed by later tasks. Evidence only.

- [ ] **Step 1: Make the test content appear asynchronously**

Replace the `content` lambda of the `ModalNavigationDrawer` call (currently lines 68-77, the static `Box(Modifier.focusRequester(contentFocusRequester).focusable())`) so the focusable child only exists after a couple of frames, modelling a tab being swapped in:

```kotlin
                    content = {
                        // Models a tab swap: TabComponent posts the new tab over a flow, so the
                        // content has no focusable children for the first frames after a click.
                        var contentReady by remember { mutableStateOf(true) }
                        LaunchedEffect(selectedTab) {
                            contentReady = false
                            repeat(3) { withFrameNanos { } }
                            contentReady = true
                        }
                        Box(Modifier.focusRequester(contentFocusRequester).focusGroup()) {
                            if (contentReady) {
                                Box(Modifier.focusable())
                            }
                        }
                    },
```

Add the imports this needs:

```kotlin
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
```

- [ ] **Step 2: Run the test and confirm it now fails**

Run: `./gradlew :app:connectedDevDebugAndroidTest --tests "com.kino.puber.ui.feature.main.component.MainSideMenuFocusTraversalTest"`

Expected: FAIL on `assertEquals(DrawerValue.Closed, drawerState.currentValue)` — the rail reopens because `requestFocus()` misses while the content has no focusable child, exactly as reported.

Requires the emulator, and the Fire TV boxes must be off the bus first. AGP runs connected tests on *every* attached device, which would reinstall the app onto the boxes — the one thing that must not happen. The procedure, in order:

```bash
adb disconnect 192.168.1.104:5555
adb disconnect 192.168.1.106:5555
/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd puber_tv_36 \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
```

Reconnect both boxes with `adb connect <ip>:5555` when the instrumented runs are done. Disconnecting is not destructive; reinstalling on them would be.

If it *passes*, stop and report: the reproduction is wrong and the rest of the plan is built on a false premise.

- [ ] **Step 3: Record the failure output in the task notes, do not commit**

Leave the modified test in the working tree. Task 6 commits it.

---

### Task 2: Explicit rail state machine

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:49-109`
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt` (create)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class DrawerValue { Closed, Open, HandingOff }`
  - `DrawerState.currentValue: DrawerValue` (read-only from outside)
  - `DrawerState.pendingHandoffId: Long?` — non-null exactly while `currentValue == HandingOff`
  - `fun DrawerState.reveal()`
  - `fun DrawerState.beginHandoff(): Long?` — returns the new request id, or `null` if not in `Open`
  - `fun DrawerState.focusExited()`
  - `fun DrawerState.settleHandoff(requestId: Long)`
  - `fun DrawerState.failHandoff(requestId: Long)`
  - `DrawerState.Saver` persists `HandingOff` as `Closed`

`setValue`, `isOverlayActive` and `contentFocusRequester` are **not** removed in this task — call sites still use them and the module must compile. Task 3 removes them.

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt`:

```kotlin
package com.kino.puber.core.ui.uikit.component.drawer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class DrawerStateTest {

    @Test
    fun `reveal opens a closed rail`() {
        val state = DrawerState(DrawerValue.Closed)

        state.reveal()

        assertEquals(DrawerValue.Open, state.currentValue)
    }

    @Test
    fun `reveal is ignored while handing off`() {
        val state = openState()
        state.beginHandoff()

        state.reveal()

        assertEquals(DrawerValue.HandingOff, state.currentValue)
    }

    @Test
    fun `begin handoff moves an open rail to handing off`() {
        val state = openState()

        val requestId = state.beginHandoff()

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(requestId, state.pendingHandoffId)
    }

    @Test
    fun `begin handoff is refused unless the rail is open`() {
        val state = DrawerState(DrawerValue.Closed)

        assertNull(state.beginHandoff())
        assertEquals(DrawerValue.Closed, state.currentValue)
    }

    @Test
    fun `focus exit closes an open rail without a handoff`() {
        val state = openState()

        state.focusExited()

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `settling the active handoff closes the rail`() {
        val state = openState()
        val requestId = requireNotNull(state.beginHandoff())

        state.settleHandoff(requestId)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `settling a superseded handoff is ignored`() {
        val state = openState()
        val staleId = requireNotNull(state.beginHandoff())
        state.failHandoff(staleId)
        val freshId = requireNotNull(state.beginHandoff())

        state.settleHandoff(staleId)

        assertEquals(DrawerValue.HandingOff, state.currentValue)
        assertEquals(freshId, state.pendingHandoffId)
    }

    @Test
    fun `a failed handoff returns the rail to open`() {
        val state = openState()
        val requestId = requireNotNull(state.beginHandoff())

        state.failHandoff(requestId)

        assertEquals(DrawerValue.Open, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    @Test
    fun `handing off is persisted as closed`() {
        val state = openState()
        state.beginHandoff()

        assertEquals(DrawerValue.Closed, DrawerState.persistedValue(state.currentValue))
    }

    @Test
    fun `a rail restored into handing off starts closed`() {
        val state = DrawerState(DrawerValue.HandingOff)

        assertEquals(DrawerValue.Closed, state.currentValue)
        assertNull(state.pendingHandoffId)
    }

    private fun openState() = DrawerState(DrawerValue.Open)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.core.ui.uikit.component.drawer.DrawerStateTest"`

Expected: compilation failure — `reveal`, `beginHandoff`, `focusExited`, `settleHandoff`, `failHandoff`, `pendingHandoffId`, `persistedValue` and `DrawerValue.HandingOff` are all unresolved.

- [ ] **Step 3: Add the third state and the intent methods**

In `ModalNavigationDrawer.kt`, replace the `DrawerValue` enum (lines 49-53):

```kotlin
/** States that the drawer can exist in. */
enum class DrawerValue {
    Closed,
    Open,

    /**
     * The user has chosen — the rail is logically closed, but focus has not landed in the
     * content yet.
     *
     * It exists to tell apart "focus left the rail because the user decided so" from "focus
     * came back to the rail because there was nowhere to land". Without it the rail's value
     * is a projection of focus, and a `requestFocus()` that misses reads as the user
     * returning to the menu.
     */
    HandingOff,
}
```

Replace `DrawerState`'s body (lines 60-104), keeping `isOverlayActive`, `contentFocusRequester` and `setValue` for now so existing call sites still compile:

```kotlin
class DrawerState(initialValue: DrawerValue = DrawerValue.Closed) {
    var currentValue by mutableStateOf(persistedValue(initialValue))
        private set

    /**
     * Identifies the handoff currently waiting on the content, or `null` when none is.
     *
     * Monotonic on purpose: the user can press again while a handoff is in flight, and a
     * confirmation belonging to the abandoned attempt must not close a rail that a newer
     * intent has just reopened.
     */
    var pendingHandoffId: Long? by mutableStateOf(null)
        private set

    private var lastHandoffId = 0L

    /** Focus entered the rail, or Back was pressed in the content. */
    fun reveal() {
        if (currentValue == DrawerValue.Closed) {
            currentValue = DrawerValue.Open
        }
    }

    /**
     * A rail item was clicked, or Back was pressed while the rail was open — both change the
     * rail without moving focus, so focus has to be handed over explicitly.
     *
     * @return the request id to confirm against, or `null` if the rail was not open.
     */
    fun beginHandoff(): Long? {
        if (currentValue != DrawerValue.Open) return null
        lastHandoffId += 1
        pendingHandoffId = lastHandoffId
        currentValue = DrawerValue.HandingOff
        return lastHandoffId
    }

    /** D-pad right carried focus into the content, which needs no handoff. */
    fun focusExited() {
        if (currentValue == DrawerValue.Open) {
            currentValue = DrawerValue.Closed
        }
    }

    /** The content confirmed it holds focus. */
    fun settleHandoff(requestId: Long) {
        if (pendingHandoffId != requestId) return
        pendingHandoffId = null
        currentValue = DrawerValue.Closed
    }

    /** The content never took focus. Reopening beats stranding focus in nothing. */
    fun failHandoff(requestId: Long) {
        if (pendingHandoffId != requestId) return
        pendingHandoffId = null
        currentValue = DrawerValue.Open
    }

    companion object {
        /** [DrawerValue.HandingOff] is transient and must not survive process death. */
        fun persistedValue(value: DrawerValue): DrawerValue =
            if (value == DrawerValue.HandingOff) DrawerValue.Closed else value

        val Saver =
            Saver<DrawerState, DrawerValue>(
                save = { persistedValue(it.currentValue) },
                restore = { DrawerState(it) },
            )
    }
}
```

Leave `isOverlayActive`, `contentFocusRequester` and `setValue` in place, each unchanged, above the `companion object`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.core.ui.uikit.component.drawer.DrawerStateTest"`

Expected: PASS, 10 tests.

- [ ] **Step 5: Verify the module still compiles**

Run: `./gradlew :app:compileDevDebugKotlin`

Expected: BUILD SUCCESSFUL. Existing `setValue` call sites are untouched.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt \
        app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt
git commit -m "feat: give the side rail an explicit state machine

The rail's value was a projection of focus, so \"closed, but focus is
still in flight\" could not be expressed. Adds HandingOff plus explicit
intents; call sites still use setValue and follow in the next change."
```

---

### Task 3: Focus handoff contract, wired into the rail

**Files:**
- Create: `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ContentFocusHandoff.kt`
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt` — remove `isOverlayActive`, `contentFocusRequester`, `setValue`; rewrite `DrawerSheet`'s `onFocusChanged`; add the `handoff` parameter
- Modify: `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:90-119`, `:143-145`, `:188-206`, `:224-252`

**Interfaces:**
- Consumes: `DrawerState.reveal/beginHandoff/focusExited/settleHandoff/failHandoff/pendingHandoffId` from Task 2.
- Produces:
  - `class ContentFocusHandoff(drawerState: DrawerState, contentFocusRequester: FocusRequester)`
    - `val pendingRequestId: Long?`
    - `fun settle(requestId: Long)`
    - `fun fail(requestId: Long)`
    - `fun settleActive()` — settles whatever request is pending, if any
    - `fun redirectFocusToContent()`
  - `val LocalContentFocusHandoff: ProvidableCompositionLocal<ContentFocusHandoff?>`
  - `@Composable fun ContentFocusHandoffEffect(handoff: ContentFocusHandoff?, restartKey: Any?, contentFocusRequester: FocusRequester)`
  - `ModalNavigationDrawer(..., handoff: ContentFocusHandoff? = null, ...)`

- [ ] **Step 1: Create the handoff contract**

Create `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ContentFocusHandoff.kt`:

```kotlin
package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

val LocalContentFocusHandoff = staticCompositionLocalOf<ContentFocusHandoff?> { null }

/**
 * How many frames the content is given to produce something focusable.
 *
 * Deliberately larger than [androidx.compose.ui.focus.FocusRequester]-based restores elsewhere
 * in the app, which resume an already-composed screen: this one waits on a tab's first page of
 * data arriving from Paging. A heuristic, not a handshake — overshooting costs a longer wait on
 * a genuinely empty tab before the rail comes back, undershooting reopens the rail under a user
 * who was about to get content.
 */
private const val HANDOFF_FOCUS_REQUEST_FRAMES = 30

/**
 * The contract by which the rail hands focus to the content it just revealed.
 *
 * A thin facade over [DrawerState] rather than a second store of state: the rail's value stays
 * the single source of truth, and this only exposes the part the content needs to see.
 */
@Stable
class ContentFocusHandoff(
    private val drawerState: DrawerState,
    private val contentFocusRequester: FocusRequester,
) {
    val pendingRequestId: Long?
        get() = drawerState.pendingHandoffId

    fun settle(requestId: Long) = drawerState.settleHandoff(requestId)

    fun fail(requestId: Long) = drawerState.failHandoff(requestId)

    /** Confirms whichever handoff is in flight; a no-op when none is. */
    fun settleActive() {
        pendingRequestId?.let(::settle)
    }

    /**
     * Sends focus back to the content. Used while a handoff is in flight, when the focus system
     * bounces focus into the rail because the arriving content had nothing focusable yet.
     */
    fun redirectFocusToContent() {
        runCatching { contentFocusRequester.requestFocus() }
    }
}

/**
 * Drives the content side of a handoff: retries focus across frames until the content has
 * something to focus, and gives up rather than leaving the rail stuck.
 *
 * Success is not read from [FocusRequester.requestFocus]'s return value. That reports only that
 * the request was accepted at that instant, whereas what matters is that focus *stayed* out of
 * the rail — the failure mode is an accepted request followed by the focus system bouncing focus
 * back. The loop therefore runs until the caller's `onFocusChanged` settles the request.
 *
 * @param restartKey re-runs the retry when the content instance changes under the same request.
 */
@Composable
fun ContentFocusHandoffEffect(
    handoff: ContentFocusHandoff?,
    restartKey: Any?,
    contentFocusRequester: FocusRequester,
) {
    val requestId = handoff?.pendingRequestId
    LaunchedEffect(requestId, restartKey) {
        if (handoff == null || requestId == null) return@LaunchedEffect
        repeat(HANDOFF_FOCUS_REQUEST_FRAMES) {
            withFrameNanos { }
            if (handoff.pendingRequestId != requestId) return@LaunchedEffect
            if (!contentFocusRequester.restoreFocusedChild()) {
                runCatching { contentFocusRequester.requestFocus() }
            }
        }
        handoff.fail(requestId)
    }
}
```

- [ ] **Step 2: Rewrite `DrawerSheet`'s focus handling and drop the dead flag**

In `ModalNavigationDrawer.kt`, delete `isOverlayActive` (and its long KDoc), `contentFocusRequester`, and `setValue` from `DrawerState`. Delete the `isOverlayActive` mention from the file header comment (lines 5-8) and replace it with:

```kotlin
 * Reason: DrawerSheet derived the drawer's value from focus (onFocusChanged -> setValue), so a
 * focus request that missed read as the user reopening the menu. The fork gives DrawerState an
 * explicit machine and demotes focus to one input among several.
```

Add the `handoff` parameter to `ModalNavigationDrawer` and pass it down:

```kotlin
@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    handoff: ContentFocusHandoff? = null,
    scrimBrush: Brush = SolidColor(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    content: @Composable () -> Unit,
) {
```

and in its body change the `DrawerSheet(` call to also pass `handoff = handoff,`.

Change `DrawerSheet`'s signature to accept `handoff: ContentFocusHandoff? = null`, and replace its `onFocusChanged` block (lines 170-186) with:

```kotlin
            .onFocusChanged {
                focusState = it

                if (!initializationComplete) return@onFocusChanged

                when {
                    // A handoff is in flight: focus arriving here is the bounce we are guarding
                    // against, not the user coming back. Send it where it was headed.
                    drawerState.currentValue == DrawerValue.HandingOff -> {
                        if (it.hasFocus) handoff?.redirectFocusToContent()
                    }

                    it.hasFocus -> drawerState.reveal()
                    else -> drawerState.focusExited()
                }
            }
```

- [ ] **Step 3: Wire the main screen to the contract**

In `MainScreenComponent.kt`, replace `DrawerMainContent` (lines 89-119):

```kotlin
@Composable
private fun DrawerMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val handoff = remember(drawerState, mainContentFocus) {
        ContentFocusHandoff(drawerState, mainContentFocus)
    }

    CompositionLocalProvider(
        LocalDrawerState provides drawerState,
        LocalContentFocusHandoff provides handoff,
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            handoff = handoff,
            scrimBrush = Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.scrim, Color.Transparent
                )
            ),
            drawerContent = {
                MainSideMenuContent(
                    state = state,
                    onAction = onAction,
                    drawerState = drawerState,
                )
            },
            content = { MainScreenContentBody(mainContentFocus, tabRouter, tabAppRouterHolder) },
        )
    }
}
```

Note `mainContentFocus` is no longer threaded into `MainSideMenuContent` — the handoff owns the transfer. Drop the `mainContentFocus` parameter from `MainSideMenuContent` and from `MainSideMenuItem`, and remove the now-unused `SideEffect { drawerState.contentFocusRequester = ... }` line and its `SideEffect` import.

Replace the `BackHandler` (lines 143-145) so Back is handled in both directions:

```kotlin
    // Back is meaningful in every state: it reveals a closed rail, and closes an open one
    // instead of falling through to TabBackHandler and popping the stack behind it.
    BackHandler(enabled = drawerState.currentValue != DrawerValue.HandingOff) {
        when (drawerState.currentValue) {
            DrawerValue.Closed -> drawerState.reveal()
            DrawerValue.Open -> drawerState.beginHandoff()
            DrawerValue.HandingOff -> Unit
        }
    }
```

Replace `MainSideMenuItem`'s `onClick` (lines 191-195):

```kotlin
        onClick = {
            onAction(CommonAction.ItemSelected(tab))
            drawerState.beginHandoff()
        },
```

In `MainScreenContentBody` (lines 224-252), delete the `LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }` and its comment — the handoff retry replaces the arbitrary constant with a confirmation — and drop the now-unused `LaunchedEffect` and `delay` imports.

Keep the private `DrawerState.isOpen` extension (line 173-174) as is: `currentValue == DrawerValue.Open` is still what the background colour and item chrome want.

- [ ] **Step 4: Verify the two places that need no edit**

Both read `== DrawerValue.Open`, which stays correct once a third state exists. Confirm by reading, and change nothing:

- `ModalNavigationDrawer.kt`, the `NavigationDrawerScopeImpl(drawerState.currentValue == DrawerValue.Open)` call — items must render collapsed during `HandingOff`, so the collapse begins on keypress rather than waiting on focus.
- `FocusOnLaunchRequester.kt:36`, `LocalDrawerState.current?.currentValue == DrawerValue.Open` — auto-focus must stay *allowed* during `HandingOff`, or the arriving screen would refuse the focus being handed to it.

If either reads something broader than `== DrawerValue.Open` by the time you get here, stop and report — the plan's assumption has drifted.

- [ ] **Step 5: Compile and run the unit tests**

Run: `./gradlew :app:compileDevDebugKotlin && ./gradlew testDevDebugUnitTest`

Expected: BUILD SUCCESSFUL, all unit tests pass. If `MainSideMenuFocusTraversalTest.kt` fails to compile because it passes `mainContentFocus` to `MainSideMenuContent`, fix that call in the test file too — it is already in the working tree from Task 1.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ \
        app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt
git commit -m "feat: hand focus from the side rail to the content explicitly

Replaces the focus-derived drawer value with the state machine's intents,
adds the handoff contract the content confirms, and drops the dead
isOverlayActive flag, which was read but never set. Back now closes an
open rail instead of popping the stack behind it."
```

---

### Task 4: Drive the handoff from the tab content

The retry and the confirmation have to live where the tab's content actually is, so that a tab arriving over `tabRouter` completes the handoff the rail started.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:526-569`

**Interfaces:**
- Consumes: `ContentFocusHandoff`, `LocalContentFocusHandoff`, `ContentFocusHandoffEffect` from Task 3.
- Produces: nothing new; completes the loop opened in Task 3.

- [ ] **Step 1: Confirm and drive the handoff in `TabFlowNavigator`**

In `TabFlowNavigator`, read the handoff next to the existing `contentFocusRequester` (line 527):

```kotlin
    val contentFocusRequester = remember { FocusRequester() }
    val contentFocusHandoff = LocalContentFocusHandoff.current
```

Add the confirmation to the content `Box` (lines 547-554) — this is the signal that closes the rail:

```kotlin
        Box(
            Modifier
                .focusRequester(contentFocusRequester)
                // The rail closes on this, not on requestFocus() returning true: what matters is
                // that focus settled here rather than bouncing back into the rail.
                .onFocusChanged { if (it.hasFocus) contentFocusHandoff?.settleActive() }
                .focusRestorer()
                .focusGroup()
        ) {
            CurrentScreen("currentTab$scopeName")
        }
```

Add the retry after `RestoreTabContentFocusEffect` (line 565-568):

```kotlin
        ContentFocusHandoffEffect(
            handoff = contentFocusHandoff,
            restartKey = contentInstanceKey,
            contentFocusRequester = contentFocusRequester,
        )
```

Add the imports:

```kotlin
import androidx.compose.ui.focus.onFocusChanged
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoffEffect
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
```

(`onFocusChanged` may already be imported; check before adding.)

- [ ] **Step 2: Compile and run the unit tests**

Run: `./gradlew :app:compileDevDebugKotlin && ./gradlew testDevDebugUnitTest`

Expected: BUILD SUCCESSFUL, unit tests pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt
git commit -m "feat: complete the rail focus handoff from the tab content

The arriving tab retries focus across frames until its children exist and
confirms the landing, which is what closes the rail."
```

---

### Task 5: Restore the tab's remembered row and card

Per-tab position already survives a switch — `PuberCurrentTab` wraps content in `tabNavigator.saveableState`, and both the focused row key and the focused card id are `rememberSaveable`. What is missing is a trigger on re-entry. The handoff request id becomes that trigger; no second mechanism is introduced.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:31-93`, `:176-205`

**Interfaces:**
- Consumes: `LocalContentFocusHandoff` from Task 3.
- Produces: nothing new.

- [ ] **Step 1: Read the pending handoff in `rememberReconciledItemFocus`**

Alongside the existing locals (near line 50, `val onRootFocusRestored = LocalRootAnchorFocusRestored.current`):

```kotlin
    val handoffRequestId = LocalContentFocusHandoff.current?.pendingRequestId
```

Pass it into `RequestReconciledItemFocusEffects` at the call site (line 79-89) by adding:

```kotlin
        handoffRequestId = handoffRequestId,
```

- [ ] **Step 2: Add the handoff as a restore ground**

Add the parameter to `RequestReconciledItemFocusEffects` (signature at lines 172-182):

```kotlin
    handoffRequestId: Long?,
```

and add a fourth effect after the `rootAnchorRestoreCompletion` one (which ends at line 204):

```kotlin
    // Re-entering a tab restores into the row and card the user left, the way returning from a
    // details screen already does. The saved position is the rememberSaveable state the tab kept
    // across the switch; only the trigger was missing.
    LaunchedEffect(handoffRequestId) {
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (handoffRequestId != null && targetCanReceiveFocus) {
            focusRequester.requestAfterAnchorRestore()
        }
    }
```

Add the import:

```kotlin
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
```

- [ ] **Step 3: Compile and run the unit tests**

Run: `./gradlew :app:compileDevDebugKotlin && ./gradlew testDevDebugUnitTest`

Expected: BUILD SUCCESSFUL, unit tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt
git commit -m "feat: restore a tab's remembered row and card on re-entry

The position was already saved across tab switches; an in-flight focus
handoff now triggers the restore, so picking a tab lands where the user
left it."
```

---

### Task 6: Land the instrumented tests

**Files:**
- Modify: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt` (already edited in Task 1, still uncommitted)

**Interfaces:**
- Consumes: everything from Tasks 2-5.
- Produces: nothing.

- [ ] **Step 1: Run the Task 1 test and verify it now passes**

Run: `./gradlew :app:connectedDevDebugAndroidTest --tests "com.kino.puber.ui.feature.main.component.MainSideMenuFocusTraversalTest"`

Expected: PASS. The same test that failed in Task 1 Step 2 now passes — that pairing is the evidence the change fixes the reported defect.

- [ ] **Step 2: Add the handoff-failure test**

Append to `MainSideMenuFocusTraversalTest`:

```kotlin
    @Test
    fun selectingATabWithNoFocusableContent_reopensTheRail() {
        lateinit var drawerState: DrawerState

        composeRule.setContent {
            PuberTheme {
                var selectedTab by remember { mutableStateOf(TabType.Favourites) }
                drawerState = rememberDrawerState(DrawerValue.Open)
                val contentFocusRequester = remember { FocusRequester() }
                val handoff = remember(drawerState, contentFocusRequester) {
                    ContentFocusHandoff(drawerState, contentFocusRequester)
                }

                CompositionLocalProvider(LocalContentFocusHandoff provides handoff) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        handoff = handoff,
                        drawerContent = {
                            MainSideMenuContent(
                                state = MainViewState(
                                    tabs = testTabs(selectedTab),
                                    selectedTab = selectedTab,
                                ),
                                drawerState = drawerState,
                                onAction = { action ->
                                    if (action is CommonAction.ItemSelected<*>) {
                                        selectedTab = (action.item as MainTab).type
                                    }
                                },
                            )
                        },
                        content = {
                            // An empty or errored tab: nothing to focus, ever.
                            Box(
                                Modifier
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { if (it.hasFocus) handoff.settleActive() }
                                    .focusGroup(),
                            )
                            ContentFocusHandoffEffect(
                                handoff = handoff,
                                restartKey = selectedTab,
                                contentFocusRequester = contentFocusRequester,
                            )
                        },
                    )
                }
            }
        }

        focusedItem("Favorites").press(Key.DirectionDown)
        focusedItem("History").press(Key.Enter)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            drawerState.currentValue != DrawerValue.HandingOff
        }
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Open, drawerState.currentValue)
        }
    }
```

Add the imports it needs:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoffEffect
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
```

Also update the first test to wire the same handoff (`ContentFocusHandoff`, `LocalContentFocusHandoff`, `ContentFocusHandoffEffect`) around its async content, so both tests exercise the real contract rather than a hand-rolled stand-in.

- [ ] **Step 3: Run both tests**

Run: `./gradlew :app:connectedDevDebugAndroidTest --tests "com.kino.puber.ui.feature.main.component.MainSideMenuFocusTraversalTest"`

Expected: PASS, 2 tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt
git commit -m "test: make the side rail focus test model a real tab swap

The old test's content was focusable from the first frame, so a focus
request could not miss and the test passed against the broken behaviour.
Adds the handoff-failure case alongside it."
```

---

### Task 7: Full verification and device pass

**Files:**
- Modify: `app/build.gradle.kts:16` — version bump

**Interfaces:**
- Consumes: everything.
- Produces: the verification record.

- [ ] **Step 1: Run Detekt with type resolution and compare against HEAD**

Run: `./gradlew :app:detektAll`

`detektAll` **already fails on untouched master** — the baseline in `config/detekt/detekt-baseline.xml` is empty and has never been filled, the Detekt CI job is disabled (`if: false`), and master carries roughly 108 pre-existing findings. So "BUILD SUCCESSFUL" is not the gate and never was.

The gate is: **no new findings attributable to this change.** Record the finding count and the findings in the files this plan touches, then compare against the same command run at the commit before this work. Fix anything new at the source; if an exception is genuinely warranted, add a `@Suppress` at the declaration with a comment saying why — never extend the baseline.

- [ ] **Step 2: Run the full unit test suite**

Run: `./gradlew testDevDebugUnitTest`

Expected: PASS.

- [ ] **Step 3: Run the full instrumented suite and compare against the baseline**

Run: `./gradlew :app:connectedDevDebugAndroidTest`

Expected: 73/76 plus the two tests added here. The three pre-existing failures on the `puber_tv_36` AVD are an environment artefact, named explicitly:

- `TabFlowLifecycleTest.refreshThenPlayerAndDetailsBackUsesTheRefreshedHistoryLifecycle`
- `SectionRowFocusTraversalTest.downToEndRightThenUpRestoresGenericRowTargetsInsideViewport`
- `HomeFocusTraversalTest.downToEndRightThenUpRestoresHomeRowTargetsInsideViewport`

Exactly these three failing is a clean run. **The last two are focus-restoration tests, which is precisely the area this change touches** — so if either changes shape, do not wave it through as the known artefact. Re-run it at the commit before this work against the same AVD before concluding anything.

- [ ] **Step 4: Bump the version**

In `app/build.gradle.kts:16`:

```kotlin
val currentVersion = "1.7.5"
```

Without this the settings screen cannot tell which build is actually on the TV.

- [ ] **Step 5: Build and install on the TV**

Run: `./gradlew :app:assembleDevDebug`

Then follow `AGENTS.md:55-57` and `.kent/commands/smoke-test.md`: acquire the emulator lease, address the exact serial, and install the fresh APK through the preserve-data adapter. No uninstall, no data wipe, no logout.

- [ ] **Step 6: Walk the manual scenarios**

Compose tests do not exercise the real Android TV focus engine, which is where the defect lives, so these are the checks that actually settle it:

1. Open the rail from content, pick a different tab → rail collapses, focus is in the content.
2. Return to a previously visited tab → focus lands on the row and card left behind.
3. Press Back with the rail open → the rail closes; the content stack is **not** popped.
4. From a rail item press right → the rail collapses and focus moves into the content.
5. Pick a tab that is empty or errored → the rail comes back open rather than stranding focus.
6. Push a details screen and press Back → focus returns to the card it was opened from. This one is a regression watch: `MainScreenContentBody`'s `delay(100)` focus request was removed in Task 3, and this is the path it used to cover.
7. Switch to `TopTabs` in device settings and confirm nothing changed there.

- [ ] **Step 7: Commit the version bump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.7.5"
```

- [ ] **Step 8: Report**

Report the Detekt result, both suite results with the baseline comparison, and the outcome of each of the seven manual scenarios. Say plainly which ones were not run and why, if any.
