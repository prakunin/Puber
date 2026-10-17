# Test design: tab-stack-lost-on-root-push

Base `f14dcc84`. Worktree `/home/user1/orca/workspaces/Puber/tab-stack-lost-on-root-push` (read only here). Production paths relative to `app/src/main/java/com/kino/puber/`. Inputs: brief.md (11 ACs), design-architect.md (D1-D11, R1-R9), state.md Decisions.

## Levels in play

- `unit`: JUnit 5 under `app/src/test`. Only the telemetry JVM tests apply (`NavTelemetryTest`). The bug lives in Compose, Voyager and `SaveableStateHolder`, and Robolectric is not allowed, so no new unit test can fail on the base commit for it. Said once so the gap is not read as an omission.
- `instrumented`: JUnit 4 + `createComposeRule`, the harness style of the existing androidTest classes. Run only by qa-device with `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`. Harnesses: `TabHostTelemetryWiringTest` (real `FlowComponent` + `TabComponent` + `PuberCurrentTab`, host `WiringTabHostScreen`, root `WiringDetailsScreen`) and `TabFlowLifecycleTest`.
- `device`: manual D-pad observation on an emulator, recorded in the evidence file.

## Risk profile

P = probability 1-3, I = impact 1-3. Source: brief R1-R7, design R1-R9.

| # | Risk | P | I | Score | Test at the level where it fails |
|---|---|---|---|---|---|
| K1 | The tab-local stack is still lost after a root push, because the holder swap in D1/D2 does not stop Voyager's nested dispose (read from bytecode, never run) | 2 | 3 | 6 | `instrumented` T1, T2, T3 (fail on base, see Failing-first) plus `device` M1 |
| K2 | `reappearingTop` not flipped: stack restored but Back is labelled with the tab root while ShowAll is shown (design R3) | 2 | 3 | 6 | `instrumented` T1 (asserts `navs.first().to`, `screensAfter`, `Telemetry.screen`) |
| K3 | Focus after Back lands nowhere or on the first card, because the launch flag is restored and no anchor covers grids (design R1) | 3 | 2 | 6 | `device` M1 (AC-2), `instrumented` T8 only if D9 is implemented |
| K4 | Lifecycle owner change: `PuberTab` loses its per-tab `AndroidScreenLifecycleOwner`, so `ON_RESUME` for `LifecycleAction` users or the "one live VM" count changes (design R2) | 2 | 3 | 6 | `instrumented` T5 (ON_RESUME), T6 (`TabFlowLifecycleTest`), `device` M3 |
| K5 | Saved state of a replaced MainScreen leaks into the next session: logout then fresh host restores the old stack (design R5, AC-7) | 2 | 3 | 6 | `instrumented` T4 plus `device` M4 |
| K6 | Rail tab switch regresses: the holder keyed by `currentTab.key` drops or mixes a tab's stack (design R6) | 1 | 3 | 3 | `instrumented` T2 and the existing `TabFlowLifecycleTest` tab-switch steps |
| K7 | The `TabFlowLifecycleTest` fix (`substring = true`) uncovers an unrelated red behind the first assertion (brief R6) | 2 | 2 | 4 | `instrumented` T6, run green before and after the production change to separate causes |
| K8 | A `Telemetry.isEnabled` gate is re-added to `unbind` (design R3, spec A1) | 1 | 2 | 2 | Accepted: `NavTelemetryTest.unbindWithTelemetryOffRecordsNothing` and `unbindWhileOffThen...` stay green; AC-6 is a read/grep check |
| K9 | Settings tab after a root push changes spinner behaviour, either way (design R4) | 2 | 2 | 4 | `device` M5 (observe and record only; not a fix target) |
| K10 | Removed tabs keep a small saved state; memory (design R7, D10) | 1 | 1 | 1 | Accepted with a note: dies with MainScreen, bounded by tab count |
| K11 | Voyager pre-release changes its dispose rules (design R8) | 1 | 2 | 2 | Accepted: T1 runs on every `make itest` |
| K12 | Baseline profile shifts (design R9) | 1 | 1 | 1 | Accepted: no correctness effect |
| K13 | D9 focus effect steals focus from the rail when it fires while the rail is open | 2 | 2 | 4 | `device` M2, only if D9 is implemented |

## Traceability

Test names are the proposal; the implementer may adjust naming but not the asserted behaviour. All new instrumented cases go into `app/src/androidTest/kotlin/com/kino/puber/core/ui/navigation/component/TabHostTelemetryWiringTest.kt` and reuse its fixture (see "Fixture additions").

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 | T1 `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (flipped, existing) and T2 `rootPushAndBackRestoresBothTabStacks`. Both wait for `CHILD_TAG` after Back and assert the tab root `HOME_TAG` is absent | The tab-local screen (stand-in for Show all) is shown after a root push and Back, not the tab root |
| AC-1 (scroll) | device | P0 | M1 steps 5-6 | Scroll position of the real grid survives. Not instrumentable cheaply: `ShowAllContentBody` is private, scroll comes from `rememberLazyGridState` (saveable) and is restored with the stack |
| AC-2 | device | P1 | M1 steps 7-8 | Focus is on the card that opened Details, or at least inside the grid (fallback per Q1) |
| AC-2 | instrumented | P2 (conditional on D9) | T8 `showAllFocusReturnsToTheFocusedCardAfterReentry`, new class `ShowAllScreenContentFocusTest` using `StateRestorationTester` as in `NetworkDiagnosticsContentFocusTest.kt:161`. Needs `ShowAllContentBody` made `internal` or its public wrapper driven with a `ShowAllViewState.Content` of 20+ items | After recreation with a remembered id, the card of that id is focused while `LocalContentFocusActive` is true, and nothing is focused when it is false |
| AC-3 | instrumented | P0 | T2 `rootPushAndBackRestoresBothTabStacks`: Home pushes Child, switch to Other, Other pushes `WiringOtherChildScreen`, open Details, Back; then assert Other-child shown; switch to Home, assert Child shown; Back-stack depth: `router back` on each lands on the tab root | Both stacks are intact after a root push and Back, and after switching |
| AC-4 | instrumented | P0 | T1 (same test, assertions: `navs.first().cmd == BACK`, `navs.first().to == WiringChildScreen`, every `screensAfter(mark)` is `WiringChildScreen`, `Telemetry.screen == WiringChildScreen`, no `Nav` after the mark names `WiringHomeScreen`) | `reappearingTop` is the live top, and no later line overwrites it with the tab root |
| AC-4 | unit | P1 | `NavTelemetryTest.unbindWhileOffThenEnabledThenBackNamesTheReappearingTop` (rename of `:232`, body unchanged) and `unbindFreezesTheReappearingTopNotTheLiveTop` unchanged | Semantics of the supplied lambda are still frozen at `unbind`; the name no longer claims "tab root" |
| AC-5 | instrumented | P0 | T1 plus the other five existing `TabHostTelemetryWiringTest` cases, whole class run through `itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest` | Flipped AC-6 is green and the sibling cases stay green |
| AC-6 | manual (read) | P1 | `grep -n "isEnabled" NavTelemetry.kt`: the only hit is the gate at `:64` outside `unbind` (`:32-55`); `NavTelemetryTest` green | `unbind` has no `Telemetry.isEnabled` gate |
| AC-7 | instrumented | P0 | T4 `replacedHostStartsAtTabRootsWhenShownAgain`: Home pushes Child, a root screen (`WiringAuthScreen`) replaces the host via the root navigator `replaceAll`, then a fresh `WiringTabHostScreen` replaces it; assert `HOME_TAG` and `CHILD_TAG` absent. Also assert `tabAppRouterHolder` reuse does not matter by creating a new one | The holder dies with the host: a fresh MainScreen restores nothing |
| AC-7 | device | P1 | M4 | Same through the real logout and relogin path |
| AC-8 (switch) | instrumented | P0 | T2 (switch step) and the existing `TabFlowLifecycleTest` tab-switch steps (`:117-125`) | Rail switching still keeps each tab's stack |
| AC-8 (Settings) | device | P2 | M5 | Settings tab after a root push: observed and recorded, not required to be fixed |
| AC-9 | instrumented | P1 | T5 `resumeIsDeliveredToTabScreenAfterRootPushAndBack`: add a `LifecycleEventEffect(ON_RESUME)` counter to `WiringHomeScreen` and `WiringChildScreen`; after root push and Back assert the counter of the shown screen increased past its pre-push value | Lifecycle owner swap (K4) keeps `ON_RESUME` for tab screens |
| AC-9 | device | P1 | M3 | Real `LifecycleAction` user (ContentList refresh) |
| AC-10 | instrumented | P0 | T6 `TabFlowLifecycleTest` with `assertTextContains(..., substring = true)` at `:122,131,134,137,175` | The root push lifecycle checks and "one live VM" run and pass |
| AC-11 | unit + static | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (qa-device static gate); doc check: `docs/architecture/navigation.md` Tabs names the holder owner, lifetime and why | Green gate and docs |

Counts: see Return block in the handoff; summary: P0 = 8 rows (AC-1 x2, AC-3, AC-4, AC-5, AC-7, AC-8 switch, AC-10, AC-11 counted as written), distinct new or changed tests T1, T2, T4, T6 are P0.

## Tests, one by one

All instrumented, JUnit 4 + `createComposeRule`, no sleeps (`waitUntil` on tags, as in the class helpers).

- **T1** (changed, existing, P0): D7. Replace the pinning comment at `:148-151`. Steps: `showHost`, `openTab(homeTab, HOME_TAG)`, `click(HOME_LOCAL_TAG, CHILD_TAG)`, `click(CHILD_DETAILS_TAG, DETAILS_TAG)`, keep the PUSH assertions at `:159-160`, `mark()`, `click(BACK_TAG, CHILD_TAG)`. Assert `HOME_TAG` node absent. Navs: first is BACK to `WiringChildScreen`; all `screensAfter` are `WiringChildScreen`; `Telemetry.screen == WiringChildScreen`; none names `WiringHomeScreen`.
- **T2** (new, P0): `rootPushAndBackRestoresBothTabStacks`, AC-3 and AC-8 switch. Described in the traceability row. Also asserts Back (`tabRouter`/in-tab back) from the restored Child reaches that tab's root, so the stack depth is 2 and not a recreated child over nothing.
- **T4** (new, P0): `replacedHostStartsAtTabRootsWhenShownAgain`, AC-7. Needs `WiringAuthScreen` fixture (see below). On the base commit this passes (nothing is restored either way); it is a guard for the new retention, not a failing-first test. It proves the opposite direction of T1: retention must end with the host.
- **T5** (new, P1): ON_RESUME counter. Passes on the base for the Home root (stack lost, Home recreated: counter is a fresh composition), so it is a guard for K4 and not failing-first.
- **T6** (changed, existing, P0): D8. Only the five `substring = true` edits. Run it twice for K7: first with only the test edit on the base commit (record whether it is green; if red, the cause is independent of this fix and goes to the run notes), then after the production change.
- **T8** (conditional, P2): only if D9's ShowAll-local fix is implemented.

## Fixture additions (TabHostTelemetryWiringTest.kt, test code only)

- `WiringOtherScreen` gets a `localTag` (`OTHER_LOCAL_TAG`) that pushes `WiringOtherChildScreen` (tag `OTHER_CHILD_TAG`, its own details button `OTHER_CHILD_DETAILS_TAG`). Distinct screen so T2 can tell the two stacks apart. Existing cases are unaffected (they never click it).
- `WiringAuthScreen`: a non-host root `PuberScreen` with a button (`AUTH_TAG`, `AUTH_RELOGIN_TAG`) that `replaceAll`s the root `Navigator` to a new `WiringTabHostScreen`. A "logout" button (`LOGOUT_TAG`) on `WiringDetailsScreen` or on the child content does `replaceAll(WiringAuthScreen)`. It mimics `AuthVM.kt:134` / `App.kt:101` without the real app.
- `WiringHomeScreen`/`WiringChildScreen`: a resume counter exposed through a test object, for T5.

## Failing-first

`TabHostTelemetryWiringTest.tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack`, flipped per D7, written first (before the production change).

Check by reading the old code, base `f14dcc84`: `TabComponent.kt:128-137` stores the subtree with `tabNavigator.saveableState(currentTab.key)`, so Voyager's dispose of the covered `PuberTab` removes the tab-flow stack. `FlowComponent.kt:537-542` then restarts at `TabRootScreen`. After Back the node with `CHILD_TAG` never appears: `waitForTag(CHILD_TAG)` times out (the old test itself waits for `HOME_TAG` instead, which is the documented pin). Also on the base `reappearingTop = { navigator.items.firstOrNull() }` (`FlowComponent.kt:596`) would name `WiringHomeScreen`, so the telemetry assertions fail even if the wait were skipped. After the fix both hold. T2 fails on the base for the same reason (Child and Other-child are lost). T1 is the first one to write and run red on the base before any production edit.

Order for the implementer: (1) flip T1 and write T2, run both red on the base through qa-device (this is also the device reproduction of the bug for the baseline); (2) apply D1/D2/D4/D5; (3) run green; (4) T6 edit and run; (5) T4, T5 guards.

## Manual device checks

Run by qa-device with a lease, `adb -s <serial>`, the worktree's own APK installed through `tools/android-apk-install-preserve`, D-pad keyevents only. Evidence file records each step; no credentials, no raw logcat.

**M1. Show all after a root push (AC-1, AC-2).** Run first on the base build as the baseline, then on the fix.
1. Launch, focus the rail, select Concerts (or Home).
2. D-pad down into content, open a "Show all" grid (DPAD_CENTER on a row header or button).
3. Scroll down the grid at least three rows (DPAD_DOWN x3 or more) and stop on a card, note its title.
4. Press DPAD_CENTER on that card: Details opens.
5. Press BACK once.
6. Expected: the Show all grid is shown (not the tab root), at the same scroll position, the noted card still on screen.
7. Expected: the noted card has the focus ring.
8. Press DPAD_RIGHT then DPAD_LEFT: focus moves between neighbouring cards.
9. Press BACK: the tab root shows; BACK again goes to the rail behaviour as before.
Must not happen: tab root shown after step 5; no focus ring anywhere (D-pad dead); focus on the first card while the grid was scrolled; crash; a Back that needs two presses to leave Details.
Fallback (Q1): focus is inside the grid but on a different card: record exactly which and whether a ShowAll-local fix fits the D9 budget; if it does not, record a follow-up idea.

**M2. Rail open (K13, only if D9 implemented).**
1. Repeat M1 steps 1-4, then in Details press BACK so the grid returns.
2. At once press DPAD_LEFT to the rail, open the rail, and wait 3 seconds.
Expected: focus stays on the rail after the restore effect would have run; DPAD_CENTER on a rail item switches the tab. Must not happen: focus pulled back into the grid while the rail is open.

**M3. Lifecycle (AC-9).**
1. Open a screen using `LifecycleAction(ON_RESUME)` (ContentList with refresh, History or Home) in its tab, open an item to Details, press BACK.
Expected: the screen refreshes or runs its resume work as it did on the base. Must not happen: stale data with no refresh; duplicated refreshes (two per return); crash from a missing `LifecycleOwner`.
Also: open a trailer or player from a tab screen, return, confirm the tab is unchanged.

**M4. Logout (AC-7).**
1. Open a tab-local push (Show all) in a tab, leave it open.
2. Trigger the session end through the app's own logout or a session expiry path that does not need re-pairing. If the only way requires data clear or re-pairing on a build that cannot log in again, stop and ask the owner: do not clear data or uninstall.
3. After the auth screen, log back in and land on Main.
Expected: the tab opens at its root. Must not happen: the previous Show all grid shown. If the worktree build cannot complete login (no credentials, see AGENTS.md), report M4 as not runnable on device and rely on T4.

**M5. Settings tab (AC-8, observe and record).**
1. Open the Settings tab, press DPAD_CENTER on an entry that opens a root screen (or use the remote hotkey), then BACK.
2. Expected: Settings content visible. Record what happens: stuck spinner or not, and whether it differs from the base.
Must not happen: a crash. A spinner is not a failure of this run: record it in `settings-tab-spinner-stuck`.

**M6. Tab switch (AC-8).**
1. Push a local screen in tab X, switch the rail to tab Y, push a local screen, switch back to X, then to Y.
Expected: each tab shows its own pushed screen. Must not happen: a tab root shown, or the wrong tab's screen.

## UNTESTABLE

- AC-1 scroll position and AC-2 exact card focus: not testable at `unit` or in the wiring harness (the grid and its focus are real UI state). Covered by M1 on a device, and by T8 only if D9 is built. Not silently dropped.
- AC-7 through the real logout path: depends on a device that can log in again; if the build cannot (no credentials in a worktree), M4 is a documented gap and T4 stands in.

## Handoff summary

P0: T1, T2, T4, T6 plus the static gate (AC-1, AC-3, AC-4, AC-5, AC-7, AC-8 switch, AC-10, AC-11). P1: T5, M3, M4, AC-4 unit rename, AC-6 read, AC-2 device. P2: T8 (conditional), M5.
