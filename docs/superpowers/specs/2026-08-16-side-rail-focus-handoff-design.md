# Side rail: explicit state and focus handoff

Status: approved design, pending implementation
Date: 2026-08-16
Scope: `NavigationMode.SideDrawer` only. `NavigationMode.TopTabs` is out of scope.

## Problem

With the side rail enabled, selecting a tab with the remote leaves the rail expanded.
The user expects the YouTube/Netflix behaviour: picking an entry collapses the rail and
drops focus into the content.

The cause is not the collapse animation. It is that the rail has no state of its own.
`DrawerSheet` derives it from focus:

```kotlin
// ModalNavigationDrawer.kt:184
drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
```

`MainSideMenuItem.onClick` (`MainScreenComponent.kt:191-195`) does ask for the right
outcome — select the tab, close the rail, move focus to the content — but the close is
immediately overwritten. `tabRouter.openTab()` only posts a command onto a flow, so at
the instant `mainContentFocus.requestFocus()` runs, the new tab is not composed and has
no focusable children. The request misses, the focus system falls back to the first
focusable ancestor group — the rail — and `onFocusChanged(hasFocus = true)` reopens it.

Because state is a projection of focus, "closed, but focus is still in flight" is
inexpressible. Every bug in this family follows from that.

Two further defects share the same root:

- **`isOverlayActive` is dead code.** `ModalNavigationDrawer.kt:85` is read at `:178`
  and never written anywhere in `app/src/main`. The bottom-sheet workaround it documents
  is not in force.
- **Back is mishandled while the rail is open.** `BackHandler` in
  `MainScreenComponent.kt:143` is enabled only while the rail is *closed*. With the rail
  open, Back falls through to `TabBackHandler` (`FlowComponent.kt:639`) and pops the
  content stack behind the open rail instead of closing it.

The existing instrumented test does not catch any of this. `MainSideMenuFocusTraversalTest`
asserts exactly "Enter collapses the rail" and passes, because its `content` is a static
`Box().focusable()` present from the first frame (`MainSideMenuFocusTraversalTest.kt:70-77`).
A focus request cannot miss against that. The test is a false green.

## Design

### 1. Rail state machine

`DrawerState` stops projecting focus and owns the state. Focus is demoted to one input
among several.

States: `Closed`, `Open`, `HandingOff`.

`HandingOff` is transient: the user has chosen, the rail is logically closed, but focus
has not landed in the content yet. It exists to distinguish "focus left the rail because
the user decided so" from "focus returned to the rail because there was nowhere to land".

`setValue` is removed. Transitions happen only through explicit intents:

| Intent | From | To | Raised by |
|---|---|---|---|
| `Reveal` | `Closed` | `Open` | focus entered the rail; Back from content |
| `SelectTab` | `Open` | `HandingOff` | rail item clicked |
| `Dismiss` | `Open` | `HandingOff` | Back while the rail is open |
| `FocusExited` | `Open` | `Closed` | D-pad right carried focus into the content |
| `HandoffSettled` | `HandingOff` | `Closed` | content confirmed it holds focus (§2) |
| `HandoffFailed` | `HandingOff` | `Open` | retries exhausted |

`SelectTab` and `Dismiss` differ in one respect that the content side needs: `SelectTab` for a
*different* tab is waiting on content that does not exist yet, while `Dismiss` — and re-picking the
already-selected tab — is waiting on the content already on screen. The state carries this as
`handoffExpectsNewContent`; §2 explains what depends on it.

`FocusExited` needs no handoff: D-pad right moves focus by itself, so by the time the
intent is raised the content already holds it. Only intents that change state *without*
moving focus — a click that swaps the tab, a Back press — have to hand focus over.

The governing rule: **while `HandingOff`, focus entering the rail does not reveal it** —
it is redirected to the content. This is precisely what `isOverlayActive` pretended to
do. That flag is deleted; `HandingOff` takes over its role.

Consequences elsewhere:

- `DrawerState.Saver` (`ModalNavigationDrawer.kt:98`) persists only `Closed`/`Open`.
  `HandingOff` collapses to `Closed` on restore — a transient state must not survive
  process death.
- `NavigationDrawerScopeImpl(currentValue == Open)` (`:190`) keeps its `== Open` test,
  so items already render collapsed during `HandingOff`. The collapse begins on
  keypress rather than waiting on focus.
- `FocusOnLaunchRequester.kt:36` must block auto-focus on `Open` only — blocking during
  `HandingOff` would make the arriving screen refuse the focus being handed to it. It
  already reads `currentValue == DrawerValue.Open`, so adding the third state leaves it
  correct as written. No change; verified, not edited.
- Back needs **two** handlers, not one, and `enabled` alone cannot express it.
  `OnBackPressedDispatcher` delivers to the *last enabled callback registered*, and the rail's
  content composes inside `DrawerSheet` — before `content()` — so `TabBackHandler`
  (`FlowComponent.kt`) always registers later and wins. Hence:
  - a handler registered **before** the drawer, enabled only while `Closed`. Being earlier, a
    pushed tab screen still gets Back, which is right: Back should pop it rather than open the rail.
  - a handler registered **after** the drawer, enabled while `Open` or `HandingOff`. Being later,
    it beats `TabBackHandler`, which is right: the rail is what is on top. `HandingOff` is
    swallowed rather than acted on, or Back would pop the screen arriving underneath.
- **Back must still leave the app.** The rail took over the gesture that used to provide the exit:
  previously Back with the rail open was unhandled and fell through to the activity. Closing the
  rail with Back now arms an `ExitConfirmation` and shows "press Back again to exit"; a Back within
  three seconds finishes the activity. Two presses rather than one so a stray press cannot drop the
  user onto the launcher. `ExitConfirmation` takes an injectable clock and is unit-tested — its
  never-armed sentinel is `Long.MIN_VALUE`, and subtracting that overflows to a negative age that
  reads as "armed a moment ago", which sent the very first Back straight to the launcher.

### 2. Focus handoff contract

A small `ContentFocusHandoff` object lives beside `DrawerState` and is published through
`LocalContentFocusHandoff`, following the established `LocalRootAnchorCaptureRegistry`
pattern (`FlowComponent.kt:315`). It is nullable like `LocalDrawerState`: in `TopTabs`
mode it is absent and every call degrades to a no-op.

Three parties:

- **Rail — request.** `SelectTab`/`Dismiss` calls `handoff.begin()`, which returns a
  monotonic request id. The rail enters `HandingOff`.
- **Content — attempt.** `TabFlowNavigator` (`FlowComponent.kt`) observes the active request and,
  in a `LaunchedEffect` keyed on (request id, `contentInstanceKey`), retries
  `restoreFocusedChild()`, else `requestFocus()`. The retry is required because a new tab's
  focusable children do not exist in the first frame — lists arrive from Paging.

  The budget is wall-clock, not frames: `HandoffTimeoutMillis = 5_000`. Retries run every frame for
  the first `HandoffEagerFrames = 30` and then back off to `HandoffBackoffMillis = 250`.
  Frame-tight retries matter only while the content is composing; past that the wait is for data,
  and a focus search per frame for five seconds is pure cost on TV hardware. The first version of
  this budget was 30 frames outright — half a second — which is fine for a warm tab and wrong for
  the case the feature exists to serve: reopening the rail under a user one network round-trip away
  from content is worse than making a genuinely empty tab wait.

  **Only the arriving content may serve the handoff.** When `handoffExpectsNewContent` is true, an
  instance whose `remember(contentInstanceKey)` did not capture this request id was already on
  screen when the handoff began — that is the *outgoing* tab. Letting it take the focus would have
  it report a landing on content about to be torn down, closing the rail over nothing and stranding
  focus, which is the original bug in a narrower window.
- **Content — confirmation.** The content reports whether it currently holds focus and the retry
  loop reads that; only it moves the rail to `Closed`. Deliberately polled rather than pushed from
  `onFocusChanged`: a handoff can begin while the content is *already* focused — Back closing the
  rail before focus has physically moved into it — and then no focus change will ever arrive, so an
  edge-triggered confirmation would burn the whole budget and reopen the rail under the user.

On why confirmation is not simply the return value: in this Compose version
(BOM `2026.08.00`) `FocusRequester.requestFocus()` does return `Boolean`, and
`FocusTargetState.kt:208-212` already relies on it. But `true` reports only that the
request was accepted at that instant. What the rail needs to know is that focus *stayed*
out of the rail — the failure mode is a successful request followed by the focus system
bouncing focus back. `onFocusChanged` observes the settled outcome; the `Boolean` return
is used to stop retrying, not to close the rail.

If the frames are exhausted without `hasFocus`, `handoff.fail(id)` raises `HandoffFailed`
and the rail reopens with focus on the selected item. The user sees an open menu rather
than focus stranded in nothing.

Stale confirmations are ignored: `settle(id)`/`fail(id)` for a superseded id are dropped.
That is why the id is monotonic — a user can press again while a handoff is in flight,
and the abandoned attempt must not close a rail that a newer intent has just opened.

Removed by this section:

- `DrawerState.contentFocusRequester` (`ModalNavigationDrawer.kt:91`) — a raw public
  field replaced by the contract. The redirect in `DrawerSheet` (`:180`) goes through the
  handoff.
- `LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }` in
  `MainScreenContentBody` (`MainScreenComponent.kt:234-237`) — the same idea with an
  arbitrary constant instead of a confirmation. Subsumed by the retry.

Deliberately **not** touched: the `delay(100)` in `FocusOnLaunchRequester.kt:44`. It
serves the first focus of any screen, not tab switching. Separate concern, separate task.

### 3. Tab position memory

No new registry. Per-tab position already survives a tab switch:

- `PuberCurrentTab` wraps content in `tabNavigator.saveableState(currentTab.key)`
  (`TabComponent.kt:123-125`), so every `rememberSaveable` inside a tab is retained;
- focused row — `focusedRowKey = rememberSaveable(resetKey)` (`FocusTargetState.kt:236`);
- focused card within a row — `focusedItemId = rememberSaveable(rowKey)`
  (`FocusTargetState.kt:39`);
- vertical scroll — `rememberLazyListState()`, also saveable.

Everything needed for the YouTube behaviour is already stored. A parallel
`TabType → LazyAnchor` registry would duplicate it and drift out of agreement with the
original at the first divergence.

**What is missing is the trigger.** Nothing asks the content to restore into the
remembered row when a tab is re-entered. `RequestReconciledItemFocusEffects`
(`FocusTargetState.kt:79`) is built for push/pop: it gates on `rootAnchorRestoreCompletion`,
`contentFocusActive` and `rowHasFocusRef`. Additionally the `focusRestorer()` on the
`TabFlowNavigator` box holds its state in a plain `remember`, so after the tab is
recomposed `restoreFocusedChild()` has nothing saved.

The handoff from §2 is that trigger; no second mechanism is introduced. The active request id is
published downward the same way `rootAnchorRestoreCompletion` is, and drives its own
`RestoreItemFocusOnTabReentryEffect`. The rest closes on itself: the target row (`isTargetRow` from
the saved `focusedRowKey`) requests focus on the saved card, the content box reports it holds
focus, and the rail reaches `Closed`.

The request id is **latched** rather than read directly. Read directly it is null again the moment
the handoff settles, which cancels the effect mid-flight; and a tab whose first page has not
arrived has no `targetItemId` to aim at on the first pass, so the single attempt would be spent on
nothing and never repeat. Latched, the effect re-runs when the target appears. What stops it from
yanking focus back later is the row's own focus flag: once the row holds focus, the restore has
either happened or been overtaken by the user.

Edge cases fall out of existing behaviour:

- tab opened for the first time — no memory; the resolver (`FocusTargetResolver.kt:68-72`)
  falls back to the first non-empty row and first card;
- remembered row emptied by refreshed data — the same resolver moves to the nearest
  non-empty row;
- tab empty or in error, nothing focusable — the handoff exhausts its frames,
  `HandoffFailed`, the rail returns open. This is the correct outcome.

Grid screens (`VideoGridUIState.kt:141`) receive the same signal through the shared
`rememberReconciledItemFocus` and are fixed incidentally. No grid-specific design is
undertaken here.

### 4. Testing

The current instrumented test is repaired **before** the production change, so that it is
seen failing. Otherwise there is no evidence it catches the defect it names.

**Unit tests** (`app/src/test/kotlin`, JUnit 6). The §1 state machine is plain Kotlin and
is lifted out of Compose so it can be covered exhaustively:

- `Reveal`: `Closed → Open`; no-op from `Open`; **ignored from `HandingOff`** — the
  original bug expressed as a single transition;
- `SelectTab` / `Dismiss`: `Open → HandingOff`;
- `HandoffSettled` with a stale id — ignored;
- `HandoffFailed` → `Open`;
- `Saver`: `HandingOff` persists as `Closed`.

**Instrumented tests** (`app/src/androidTest/kotlin`, Compose UI):

- rewritten traversal test: content appears after several frames, modelling the tab swap.
  Asserts the rail ends `Closed` *and* focus is physically in the content — asserting
  state alone would again test the projection rather than reality;
- content that never becomes focusable: asserts `Open`, i.e. `HandoffFailed` fires rather
  than stranding focus;
- re-entering a previously visited tab restores the remembered row and card (§3).

**Device verification.** Compose tests do not exercise the real Android TV focus engine,
which is where the defect lives, so a device pass is mandatory: open the rail, select a
tab, confirm collapse; return to a previously opened tab and check position; Back with
the rail open; right-press off an item. Per `AGENTS.md:55-57`: acquire an emulator lease
first, install to the exact serial through the preserve-data adapter. Bump the version
before deploying to a TV, otherwise the settings screen cannot identify the running build.

Known baseline: the instrumented suite passes 73/76; the three failures are pre-existing
and are to be confirmed against the baseline before being attributed to this change.
`:app:detektAll` must pass with the baseline (`config/detekt/detekt-baseline.xml`) still
empty; any deliberate exception is a `@Suppress` at the declaration with a reason.

No unlinking, uninstall or data wipe on the Fire TV — preserve-data only.

## What the reviews changed

The design above is the reviewed one. An independent review (Codex) and a second reviewing agent
both landed on the same weak point — Back — and between them found six real defects in the first
implementation. Recorded because the shape of the design changed under them:

- Back could not exit the app at all, and the rail's handler could never beat `TabBackHandler`
  anyway. Both are addressed in §1; the exit gesture is now explicit rather than a side effect of
  an unhandled event.
- The outgoing tab could confirm the incoming tab's handoff (§2, `handoffExpectsNewContent`).
- A handoff beginning while the content already held focus could never be confirmed (§2, polled
  confirmation).
- The 30-frame budget was too short for a normal Paging load (§2, wall-clock budget).
- The tab-re-entry restore was a one-shot that fired before the target existed (§3, latching).

One reviewer's concrete reproduction for the Back defect was wrong — it named the details screen,
which is a `RootPuberScreen` pushed outside the drawer and unreachable from the rail. The finding
still held: `ShowAll` and its kind push *inside* the tab, and there the rail stays reachable.

## Files affected

- `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt` — state machine, delete
  `isOverlayActive` and `contentFocusRequester`
- `core/ui/uikit/component/drawer/ContentFocusHandoff.kt` — new
- `ui/feature/main/component/MainScreenComponent.kt` — intents, the two Back handlers, drop the
  `delay(100)` focus request
- `ui/feature/main/component/ExitConfirmation.kt` — new
- `res/values/main_screen_strings.xml`, `res/values-en/strings.xml` — the exit prompt
- `core/ui/navigation/component/FlowComponent.kt` — handoff attempt/confirmation in
  `TabFlowNavigator`
- `core/ui/uikit/component/moviesList/FocusTargetState.kt` — handoff request id as a
  restore ground
- `app/src/test/kotlin/.../DrawerStateTest.kt` — new
- `app/src/test/kotlin/.../ExitConfirmationTest.kt` — new
- `app/src/androidTest/kotlin/.../MainSideMenuFocusTraversalTest.kt` — repaired and extended
