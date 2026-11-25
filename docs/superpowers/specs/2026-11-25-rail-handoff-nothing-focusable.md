# A handoff kind for the side rail: Back can leave a root with nothing focusable, and a pick works during the cold-start handoff

Status: approved
Date: 2026-11-25
Slug: rail-handoff-nothing-focusable
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

Two defects in the side-rail handoff left by `showall-rail-back-exits-app`: (1) at a tab root with nothing focusable, Back from the open rail can never reach the exit and the rail reopens after 5 s; (2) during the cold-start handoff the rail refuses a tab pick while the first tab loads. Also: the `!tab.isSelected` clause of `acceptsPickOf` is untested.

### Root cause

| # | Defect | Cause | Status |
|---|---|---|---|
| 1 | Root with nothing focusable: Back loops between rail open and collapsed; exit unreachable | `RailOpenBackHandler` starts a handoff and arms the exit (`ui/feature/main/component/MainScreenComponent.kt:223,227`). The rail is then HandingOff, where the same handler swallows every Back (`:221`) and `RailClosedBackHandler` is disabled (`core/ui/navigation/component/RailBackOwner.kt:25-30`). The content never focuses, so the effect fails after 5 s (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:24,141`); at a root `closeOnFail = { tabStack.canPop }` is false (`MainScreenComponent.kt:107`), so `failHandoff` reopens the rail (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:173-177`). The 3 s arm (`ui/feature/main/component/ExitConfirmation.kt:12`) has expired by then; the next Back closes the rail and re-arms, and the cycle repeats | confirmed by code trace (research.md: Behaviour per case today); not reproduced on a device |
| 2 | Cold start: Center on a rail item refused while the first tab loads; rail opens after 5 s | `beginInitialHandoff` always sets `expectsNewContent = false` (`ModalNavigationDrawer.kt:142-145`), indistinguishable from a Back/Right handoff. `acceptsPickOf` in HandingOff requires `handoffExpectsNewContent` (`MainScreenComponent.kt:304-308`), so the pick is refused. Even if accepted, `beginHandoff` returns null outside Open (`ModalNavigationDrawer.kt:136-139`), so the Initial id with `expectsNewContent = false` would stay pending and the outgoing first tab could confirm it. Initial fail reopens the rail (`reopen = !closeOnFail()`, `ContentFocusHandoff.kt:71`) | confirmed by code trace (research.md: State machine) |
| 3 | `!tab.isSelected` untested | the harness selection is static, Movies `isSelected = true` (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainHostHarness.kt:402`); the exit clock is fixed at 0 (`:126`), so an expiring arm cannot be shown | confirmed |

The handoff has no kind: Back, Right, a tab pick, cold start and the dialog re-entry (`MainScreenComponent.kt:124`) share one boolean. Approach: research.md Option A (a handoff kind `Initial | TabPick | Dismiss` in `DrawerState`; pick acceptance, restart and fail outcome per kind; an armed Back during HandingOff exits). The spec settles the API.

### Intended behaviour (Q1-Q3)

| Handoff kind | Started by | Pick of another tab while pending | Back while pending | On 5 s fail |
|---|---|---|---|---|
| Dismiss | Back or Right from the open rail | refused (unchanged) | armed: leave the app; not armed: swallowed | Closed, at root and pushed |
| Initial | cold start; dialog re-entry | accepted; restarts as TabPick with a new id | swallowed (unchanged; disabled under a dialog) | Closed |
| TabPick | rail pick | accepted when `!tab.isSelected` (unchanged) | swallowed (unchanged) | root: Open (unchanged, spec 2026-08-16 line 216) |

Nothing-focusable roots on the real app (research.md: Roots that can really have nothing focusable): Favourites `Empty` (`ui/feature/favorites/content/FavoriteScreenContent.kt:46-51`) is the only durable one with default tabs; every root's `Loading` is transient.

### Related

| Run | Relation |
|---|---|
| `showall-rail-back-exits-app` | source: design-architect.md Open question 1, triage-4.md D2/D6, review-state-path-4.md F1 |
| `rail-pick-during-initial-handoff` | folded in; close when this run ships |
| `rail-content-mismatch-rapid-back` | idea in the same area; retest after this ships |

## Acceptance criteria

Instrumented tests run on the box via `make DEVICE=192.168.1.110:5555 itest TESTS=<class>` under a lease, using the harness `loadingRoot` (`MainHostHarness.kt:102,178,432`) as the root with nothing focusable; the harness gains a selection that follows picks and a controllable exit clock. Device ACs run on the TV box `192.168.1.110:5555` by remote input (D-pad, Center, Back) with screenshots.

- AC-1. Given a tab root in `loadingRoot` with the rail open, When Back and then Back again within 3 s (before the 5 s fail), Then the first collapses the rail and shows the exit toast, and the second finishes the activity (instrumented, red on base; device only if Favourites is empty on the box account, see Feasibility).
- AC-2. Given AC-1 after one Back and no further input, When the handoff fails at 5 s, Then the rail stays collapsed (`Closed`, not `Open`); Left then expands it, and Back (arm expired) expands it (instrumented with the controllable clock, red on base).
- AC-3. Given a tab root in `loadingRoot` with the rail open, When Right is pressed and the handoff fails, Then the rail ends `Closed` (instrumented, red on base).
- AC-4. Given a cold start with the first tab in `loadingRoot` (initial handoff pending) and a rail item for another tab focused, When Center, Then that tab becomes selected, a TabPick handoff with a new id is pending, and only the arriving tab's content settles it (instrumented, red on base).
- AC-5. Given AC-4's setup, When Center on the item of the tab already selected, Then nothing switches (instrumented).
- AC-6. Given a cold start with the first tab in `loadingRoot`, When the initial handoff fails and the tab then supplies focusable content, Then the rail is `Closed` throughout and the content takes focus (instrumented, red on base for the `Closed` part).
- AC-7. Given a TabPick handoff at a root (dynamic selection), When Center on the now-selected tab's item, Then it is refused; Center on a third tab switches to it (instrumented; exercises `!tab.isSelected`).
- AC-8. Given a rail pick into a tab that stays in `loadingRoot`, When the handoff fails, Then the rail reopens (unchanged; instrumented).
- AC-9. Given a pushed never-focusable screen, Back from the rail, Center during a Dismiss handoff, the corrective pick, the root exit sequence and a dialog over content, Then behaviour is as on base: `MainRailBackTest`, `MainTabPushRailFocusTest`, `InitialDrawerContentFocusTest`, `MainSideMenuFocusTraversalTest`, `ShowAllRailCloseFocusTest` stay green.
- AC-10. Given the box, dev APK, a root with content (Home, Movies, History), When Back, Back, Back about 0.5 s apart, Then rail opens, rail collapses with the toast, app exits (device, unchanged).
- AC-11. Given the box, cold start, When a rail pick switches Home to Movies and Back is pressed over Movies with the rail open, Then the tab switches with focus on its content and the rail collapses onto the card left (device, unchanged).
- AC-12. `DrawerStateTest` covers every kind x {settle, fail} and the Initial-to-TabPick restart; `check.full` is green.

### Device feasibility
| AC | On the box |
|---|---|
| AC-1 | only if the Favourites tab is visible and `Empty` on the owner's account; qa observes, never adds or removes favourites (an account mutation) |
| AC-2, AC-3, AC-6, AC-8 | not feasible: need a root that stays non-focusable past 5 s; instrumented only |
| AC-4, AC-5, AC-7 | not feasible by remote input: during the initial handoff focus bouncing into the rail is redirected to the content (`ModalNavigationDrawer.kt:297-311`), and the load window is short; instrumented only |
| AC-10, AC-11 | feasible |

### Risks (brief)

- A failed Dismiss or Initial handoff at a root leaves the rail collapsed with nothing focused: only Left (`ModalNavigationDrawer.kt:342-359`) and Back act until the content focuses itself. Same as pushed screens since 1f6bc4b4.
- Content that loads after a Closed fail must take focus on its own. `rememberFocusRequesterOnLaunch` does (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61-80`); readers that latch only on the handoff id (`ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`) are unverified at a root after a fail (AC-6 covers the probe only; a slow Home or Movies load on the box is not reproducible).
- Exit during HandingOff: arming happens only in `RailOpenBackHandler` at a root and is disarmed by picks and push/pop (`MainScreenComponent.kt:111-116,238-241`); a regression traps the user or exits on a stray press (AC-1, AC-10).
- Restarting Initial as TabPick must not leave the outgoing tab able to settle (`ContentFocusHandoff.kt:114-118`).
- Device: a worktree build can drop the box to pairing (memory: worktree-build-loses-box-session); refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before installing the worktree APK.

## Out of scope

- Screens that wait for the handoff id to retry focus at a tab root after a failed handoff (BrowseAllEntry, FocusTargetState, ShowAllScreenContent): accepted, untested risk.
- Any visual change.

## Design

Architect, 2026-11-25. Base c9e98129. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Inputs: `brief.md`, `research.md` (Option A), state.md Decisions Q1-Q3.

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Kind representation | `enum class HandoffKind { Initial, TabPick, Dismiss }` next to `DrawerValue`; `DrawerState.handoffKind: HandoffKind?` (snapshot state, private set) replaces the stored `handoffExpectsNewContent` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:116`), which becomes `get() = handoffKind == HandoffKind.TabPick`. `handoffKind` is written with `pendingHandoffId` (`:104`) in `startHandoff` and cleared with it in `settleHandoff`/`failHandoff`; not saved (Saver keeps `Closed`) | One field carries "why"; `ContentFocusHandoff.expectsNewContent` (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:66-67`) and its outgoing-tab guard (`:114-118`) keep reading the same name |
| Begin API | `beginHandoff(kind: HandoffKind): Long?` replaces `beginHandoff(expectsNewContent: Boolean)` (`ModalNavigationDrawer.kt:136-139`); `require(kind != Initial)`. From `Open`: starts `kind`. From `HandingOff`: only `kind == TabPick` while `handoffKind != Dismiss`, which starts a new TabPick with a new id (the old id is superseded). Otherwise `null`. `beginInitialHandoff()` (`:142-145`) starts `Initial` from `Closed` as today | Brief defect 2: the pick during Initial needs a new id with `expectsNewContent`, or the outgoing first tab confirms it. A corrective pick during TabPick also gets a new id, so the mistaken tab (composed during the old id, `ContentFocusHandoff.kt:108`) cannot confirm, and the 5 s budget restarts for the tab actually chosen |
| Fail outcome per kind (Q1, Q3) | `failHandoff(requestId, reopen: Boolean = true)` (`ModalNavigationDrawer.kt:173-177`) sets `Open` only when `reopen && handoffKind == TabPick`, else `Closed`. `reopen` keeps its host meaning (`ContentFocusHandoff.kt:71`, `!closeOnFail()`; Main passes `{ tabStack.canPop }`, `ui/feature/main/component/MainScreenComponent.kt:106-108`), now "the host allows reopening after a pick" | Rule lives in `DrawerState`, so `DrawerStateTest` covers kind x {settle, fail} (AC-12) without Compose |
| Pick acceptance | `acceptsPickOf` (`MainScreenComponent.kt:304-308`) in `HandingOff`: `handoffKind != HandoffKind.Dismiss && !tab.isSelected`; `Open` and `Closed` unchanged | Brief: Intended behaviour column "Pick of another tab" |
| Kind of each caller | Rail item click (`MainScreenComponent.kt:329`): `if (tab.isSelected) Dismiss else TabPick`. `RailOpenBackHandler` (`:223`) and Right in `DrawerSheet` (`ModalNavigationDrawer.kt:292`): `Dismiss`. Cold start (`ModalNavigationDrawer.kt:209-211`) and dialog re-entry (`MainScreenComponent.kt:123-125`): `Initial` via `beginInitialHandoff()` | A re-pick of the active tab composes no new content (today `expectsNewContent = false`), so it is a dismiss of the rail over the same content; see Open question 1 |
| Back during HandingOff (Q2) | `RailOpenBackHandler` (`MainScreenComponent.kt:207-231`): when `currentValue != Open` and `handoffKind == Dismiss` and `exitConfirmation.isArmed`, `activity?.finish()` then `disarm()`; every other HandingOff Back is swallowed as at `:221`. The activity is resolved as in `RailClosedBackHandler` (`:192-193`, `findActivity` `:243`) | Arming happens only here at a root (`:226-229`) and is cleared by picks (`:111-116`) and push/pop (`:238-241`), so an armed Back in a Dismiss is the second Back at the same root. Gating on Dismiss keeps Initial and TabPick swallowing (brief table) |
| Back owner | `railBackOwner` (`core/ui/navigation/component/RailBackOwner.kt:25-30`) unchanged: HandingOff stays `RailOpen` | The exit is decided inside the owning handler; `RailBackOwnerTest` unchanged |
| Harness seams (AC-2, AC-7) | `MainHostHarness` (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainHostHarness.kt`): the `MainViewState` at `:399-418` comes from `ProbeState` snapshot state and `ItemSelected` updates `isSelected`/`selectedTab` before `openTab` (MainVM order, `ui/feature/main/vm/MainVM.kt:131-135`); the exit clock at `:126` reads a `ProbeState` value the test advances (default 0 keeps existing tests) | Static selection never exercises `!tab.isSelected`; a fixed clock cannot expire an arm. Test names and fixtures belong to test-design.md |

Prior spec `docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md`, Design rows this run changes:

| Prior row | Change |
|---|---|
| Decisions "Failed handoff over a pushed screen (A5)": "At a tab root ... a failed handoff reopens the rail as on base" | Reopen now needs `reopen` and kind TabPick; Dismiss and Initial settle `Closed` at any depth |
| Alternatives rejected "A5: settle `Closed` on every failed handoff" | Adopted for Dismiss and Initial; TabPick keeps reopening at a root (Q3) |
| Risk 4 "Back during `HandingOff` ... always swallowed" | An armed Back during a Dismiss handoff leaves the app |
| Risk 1 Check "At a tab root with nothing focusable the rail reopens after 5 s" | Ends `Closed` after a Back or Right; reopens only after a pick |
| Open question 1 | Resolved by this run |

#### Changes by layer

API / repository: N/A: no data path changes.

ViewModel state and actions: N/A: `MainVM.onTabSelected` (`ui/feature/main/vm/MainVM.kt:131-135`) unchanged.

Screen and DI module: N/A: no Koin, screen or key change.

Resources: N/A: the toast string `main_press_back_again_to_exit` is reused.

Compose content:

| File | Change |
|---|---|
| `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:62-75` | `HandoffKind` enum after `DrawerValue` |
| `ModalNavigationDrawer.kt:107-177` `DrawerState` | `handoffKind`; derived `handoffExpectsNewContent`; `beginHandoff(kind)`, `beginInitialHandoff`, `startHandoff(kind)`, `settleHandoff`, `failHandoff` per Decisions; KDoc of each updated |
| `ModalNavigationDrawer.kt:292` Right key | `beginHandoff(HandoffKind.Dismiss)` |
| `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:54-71` | KDoc of `closeOnFail`: applies to a TabPick fail only; code unchanged |
| `ui/feature/main/component/MainScreenComponent.kt:207-231` `RailOpenBackHandler` | Resolve the activity; armed Dismiss exit per Decisions; `beginHandoff(HandoffKind.Dismiss)`; comment at `:219-221` |
| `MainScreenComponent.kt:298-308` `acceptsPickOf` | Kind rule and KDoc per Decisions |
| `MainScreenComponent.kt:324-331` item click | `beginHandoff(if (tab.isSelected) HandoffKind.Dismiss else HandoffKind.TabPick)`; comment at `:327-328` |
| `MainScreenComponent.kt:104-108` | Comment: closed-on-fail now covers Dismiss and Initial at any depth |
| `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt:23-125` | Callers move to `beginHandoff(HandoffKind.…)`; new cases per test-design.md |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:150` | `beginHandoff(HandoffKind.Dismiss)` (compile fix) |
| `MainHostHarness.kt:96-126,395-420` | Seams per Decisions |

#### Alternatives rejected

| Alternative | Reason |
|---|---|
| research.md Option B: second boolean beside `handoffExpectsNewContent` | Two flags that must stay consistent; four combinations, three meaningful |
| research.md Option C: probe focusability at Back/Right and skip HandingOff | No Compose API for "has a focusable descendant"; content that turns focusable a moment later gets no handoff; leaves defect 2 |
| Kind rule for the fail outcome in `ContentFocusHandoff.fail` instead of `DrawerState` | Puts the rule behind a Compose facade, out of reach of `DrawerStateTest` |
| Keep the pending id on a pick during Initial and only flip `expectsNewContent` | The Initial id was taken by the first tab's effect; flipping the flag mid-effect does not restart it, and the 5 s budget would be the cold-start one |
| Exit on any armed Back during HandingOff, regardless of kind | Contradicts the brief table (Initial, TabPick swallow) |
| Stretch the exit window past 5 s | Q2 keeps the 3 s window; a stray press later would leave the app |

#### Risks

| # | Area | Risk | Lives in | Check |
|---|---|---|---|---|
| 1 | Focus and D-pad | A failed Dismiss or Initial at a root now leaves the rail collapsed with nothing focused; only Left and Back act until content focuses itself. Content that loads after the fail must request focus on its own: `rememberFocusRequesterOnLaunch` does (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61-87`, blocked only while `Open`); readers that latch on the handoff id (`ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`) are unverified at a root after a fail | `ModalNavigationDrawer.kt:173-177`, `:256-267` (rail focus cleared on `Closed`), `:342-359` | AC-2, AC-3, AC-6 instrumented; device check of a slow root is not reproducible (brief: Device feasibility) |
| 2 | Exit semantics | The armed HandingOff exit fires on a Back that is not the second Back at the same root (an arm surviving a pick or push), dropping the user to the launcher; or never fires and the trap stays | `MainScreenComponent.kt:207-231`, disarms at `:111-116,238-241` | AC-1, AC-9 (`MainRailBackTest`), AC-10 on the box |
| 3 | Focus and D-pad | The Initial-to-TabPick restart leaves the outgoing first tab able to confirm (it would need `bornDuringRequest` equal to the new id, which only content composed after the click has) or the arriving tab misses the id because it composed before the click returned | `ModalNavigationDrawer.kt:147-153`, `ContentFocusHandoff.kt:108,114-118`; the swap is async (`core/ui/navigation/TabRouter.kt:19-26`) | AC-4 asserts only the arriving tab settles |
| 4 | Focus and D-pad | Re-picking the active tab becomes Dismiss: at a root with nothing focusable it now ends `Closed` instead of `Open` (Open question 1) | `MainScreenComponent.kt:324-331` | `DrawerStateTest` Dismiss fail; no AC |
| 5 | Lifecycle | Dialog re-entry (`MainScreenComponent.kt:123-125`) is Initial, so after a dialog over nothing-focusable content the rail ends `Closed` instead of `Open` | `ContentFocusHandoff.kt:112-113` gate, `ModalNavigationDrawer.kt:173-177` | `InitialDrawerContentFocusTest` stays green (AC-9) |
| 6 | Baseline profile | `DrawerState.beginHandoff(Z)` in `app/src/main/generated/baselineProfiles/baseline-prof.txt:29811` no longer matches the new signature; the method loses AOT until the next profile generation | generated profile | none; regenerate with the next profile run |

#### Open questions

| # | Question | Recommended | Blocks |
|---|---|---|---|
| 1 | Re-picking the already selected tab from the open rail is classified Dismiss, so if that tab has nothing focusable the rail ends `Closed` after 5 s instead of reopening (base: `Open`). Accept? | Accept: it composes no new content and reads as closing the rail over the same screen; consistent with Back and Right | no |

## Test design

Base c9e98129. Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md), [research.md](research.md). Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. perf track: off.

Levels (`.flow/roles/test-designer.md`): `unit` (JUnit 5, `./tools/agentw testDevDebugUnitTest`), `instrumented` (`make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>` under a lease, run by qa-device), `device` (remote input on the box, evidence file).

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Exit trap stays, or an armed HandingOff Back exits on a stray press (brief Risks 3, design Risk 2) | 2 | 3 | 6 | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`, `backDuringDismissHandoffAfterArmExpired_isSwallowed`, `MainRailBackTest` (AC-9), device D1 |
| R2 | Initial-to-TabPick restart lets the outgoing first tab confirm, or keeps the cold-start budget (brief Risks 4, design Risk 3) | 2 | 3 | 6 | `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`, DrawerStateTest restart cases |
| R3 | Failed Dismiss/Initial at a root leaves the rail collapsed with nothing focused and no way back (design Risk 1) | 2 | 2 | 4 | AC-2, AC-3, AC-6 tests assert Left and Back reveal the rail |
| R4 | Content that loads after a Closed fail does not take focus (design Risk 1) | 2 | 2 | 4 | AC-6 test covers `rememberFocusRequesterOnLaunch` only; the id-latching readers (`BrowseAllEntry.kt:50`, `FocusTargetState.kt:81`, `ShowAllScreenContent.kt:349`) stay unverified at a root, accepted: a slow root load is not reproducible on the box |
| R5 | Kind misassigned at a caller: re-pick of the selected tab as TabPick hangs 5 s over focusable content then reopens (design Risk 4) | 1 | 3 | 3 | `rePickOfSelectedTab_closesRailOntoContent` |
| R6 | Re-pick at a never-focusable root now ends `Closed` (design Risk 4, Open question 1 accepted) | 1 | 2 | 2 | accepted; `rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed` P2 |
| R7 | Dialog re-entry is Initial and now fails `Closed` (design Risk 5) | 1 | 2 | 2 | accepted; `InitialDrawerContentFocusTest` guard (AC-9) |
| R8 | Exit gate loses its `handoffKind == Dismiss` check (exit during an armed Initial/TabPick) | 1 | 3 | 3 | no test: the only armed non-Dismiss HandingOff is a dialog re-entry, which the harness cannot raise; code review |
| R9 | Harness seam differs from the app (selection updated after `openTab`, unlike `ui/feature/main/vm/MainVM.kt:131-135`) and hides a defect | 2 | 2 | 4 | seam order is fixed below; reviewed with the harness diff |
| R10 | Baseline profile entry `DrawerState.beginHandoff(Z)` stale (design Risk 6) | 3 | 1 | 3 | none; regenerate with the next profile run |
| R11 | Worktree build drops the box to pairing (brief Risks 5) | 2 | 2 | 4 | device precondition D0 |

### Harness seams (`MainHostHarness.kt`)

New instrumented tests drive only keys, Back and these seams; none of them references `HandoffKind`, so the class compiles against base production code for the red runs.

| Seam | Behaviour |
|---|---|
| dynamic selection | `ProbeState.selectedTab` snapshot state builds `MainViewState` (`isSelected`, `selectedTab`); `ItemSelected` sets it **before** `openTab` (MainVM order); default Movies keeps existing tests |
| `host.railPicks` | count of `CommonAction.ItemSelected` the rail emitted: a refused Center leaves it unchanged |
| `host.advanceExitClock(ms)` | `ExitConfirmation(nowMillis = { ProbeState.exitNowMillis })`; default 0 keeps the frozen clock of existing tests |
| `host.setLoadingRoot(name)` | changes `ProbeState.loadingRoot` mid-test (root drops or gains focusable content) |
| `startMain(loadingFollowsPick = true)` | on `ItemSelected` the picked tab becomes `loadingRoot` in the same action, before `openTab`, so the outgoing tab turns focusable while the swap is in flight |
| `host.focusSelectedRailItem()` | `requestFocus` on the selected rail item by `isSelectable() and isSelected()` (labels are absent while collapsed) |

Fixture "rail open over a never-focusable root": `startMain()`, `awaitFocused(ROOT_CARD_FIRST)`, `setLoadingRoot("first")`, `assertNothingFocused()`, `sendLeftThroughInputPipeline()`, `awaitDrawer(Open)`, `awaitRailItemFocused()`. Identical on base and after the change.

### Traceability

New instrumented tests live in `app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainRailHandoffKindTest.kt` (class `MainRailHandoffKindTest`, harness as in `MainRailBackTest`). Unit cases are in `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/drawer/DrawerStateTest.kt`. Base: `red` fails on c9e98129 production code; `red (M#)` cannot compile on base (new API) and is proven red by the named mutation run; `guard` passes on base.

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `MainRailHandoffKindTest.backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`: fixture; Back -> `HandingOff`, `isExitArmed`; Back while still `HandingOff` -> `isFinishing` | fails if the armed Dismiss exit branch is removed (base swallows every HandingOff Back) |
| AC-1 | instrumented | P0 | guard | `MainRailHandoffKindTest.backDuringDismissHandoffAfterArmExpired_isSwallowed`: fixture; Back; `advanceExitClock(3_001)`; Back -> not finishing, still `HandingOff` | fails if the exit ignores `isArmed` (M3) |
| AC-2 | instrumented | P0 | red | `MainRailHandoffKindTest.failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`: fixture; `startSampling`; Back; `advanceExitClock(3_001)`; `awaitDrawer(Closed)`; no `Open` sampled after Back; `assertNoRailItemFocused`; Left through the input pipeline -> `Open` | fails if a failed Dismiss reopens at a root (base `Open`), or the fail leaves a focused item in the collapsed rail |
| AC-2 | instrumented | P0 | red | `MainRailHandoffKindTest.failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail`: as above to `Closed`; Back -> `Open`, not finishing | fails if the fail ends `Open` (base), or Back after an expired arm exits |
| AC-3 | instrumented | P0 | red | `MainRailHandoffKindTest.rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed`: fixture; Right -> `HandingOff`, not armed; `awaitDrawer(Closed)`, no `Open` sampled | fails if Right starts a TabPick kind or the fail ignores the kind (base `Open`) |
| AC-4 | instrumented | P0 | red | `MainRailHandoffKindTest.pickDuringInitialHandoff_switchesToPickedTab`: `startMain(loadingRoot = "first")`; `assertDrawer(HandingOff)`; `focusSelectedRailItem`; Down; Center -> `awaitRoot("other")`, `assertRootAbsent("first")`, `awaitFocused(ROOT_CARD_FIRST)`, `awaitDrawer(Closed)`, `railPicks == 1` | fails if `acceptsPickOf` refuses picks during Initial (base: `handoffExpectsNewContent` false) |
| AC-4 | instrumented | P0 | red | `MainRailHandoffKindTest.pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`: `startMain(loadingRoot = "first", loadingFollowsPick = true)`; wait 1.5 s; `startSampling`; focus rail, Down, Center; `awaitRoot("other")`; still `HandingOff` 4 s after Center (past the cold-start 5 s); then `awaitDrawer(Open)`; no `Closed` sampled between Center and `Open` | fails if the pick keeps the Initial id (the outgoing first tab, now focusable, settles it: `Closed`), keeps the cold-start budget, or keeps kind Initial (fail ends `Closed`) |
| AC-5 | instrumented | P1 | guard | `MainRailHandoffKindTest.centerOnSelectedTabDuringInitialHandoff_switchesNothing`: `startMain(loadingRoot = "first")`; `focusSelectedRailItem`; Center -> `railPicks == 0`, root `first` shown, `HandingOff` | fails if the `!tab.isSelected` clause is dropped (M7) |
| AC-6 | instrumented | P0 | red | `MainRailHandoffKindTest.coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus`: `startSampling` before `startMain(loadingRoot = "first")`; `awaitDrawer(HandingOff)` then `awaitDrawer(Closed)`; `setLoadingRoot(null)`; `awaitFocused(ROOT_CARD_FIRST)`; drawer `Closed`; no `Open` sampled | fails if a failed Initial reopens at a root (base `Open`) or late content does not focus after a Closed fail |
| AC-7 | instrumented | P1 | guard | `MainRailHandoffKindTest.centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches`: `startMain(loadingRoot = "other")`; open rail; pick History; `awaitRoot("other")`, `HandingOff`, `railPicks == 1`; `focusSelectedRailItem` (History); Center -> `railPicks == 1`; Down, Center -> `awaitRoot("third")`, `awaitFocused(ROOT_CARD_FIRST)`, `railPicks == 2` | fails if `!tab.isSelected` is dropped (M7) or the probe selection does not follow picks |
| AC-8 | instrumented | P1 | guard | `MainRailHandoffKindTest.tabPickIntoNeverFocusableTab_failedHandoffReopensRail`: `startMain(loadingRoot = "other")`; open rail; pick History; `awaitDrawer(Open)` after the fail | fails if TabPick fail ends `Closed` at a root (M5) |
| AC-8 | instrumented | P1 | guard | `MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail` (existing) | same, at the drawer without Main |
| AC-9 | instrumented | P0 | guard | `MainRailBackTest`, `MainTabPushRailFocusTest`, `InitialDrawerContentFocusTest`, `MainSideMenuFocusTraversalTest`, `ShowAllRailCloseFocusTest` (existing, whole classes) | fails on any regression of pushed-screen fail (A5), Center during Dismiss (`centerOnCollapsedRail_duringHandoff_doesNotSwitchTab`, M8), corrective pick, root exit, dialog re-entry |
| AC-9 | instrumented | P1 | guard | `MainRailHandoffKindTest.rePickOfSelectedTab_closesRailOntoContent`: `startMain()`; open rail from `ROOT_CARD_FIRST`; Center on the selected item -> `awaitDrawer(Closed)` within 1 s, `awaitFocused(ROOT_CARD_FIRST)`, no `Open` sampled | fails if a re-pick is classified TabPick (content composed before the pick cannot confirm; 5 s then `Open`) (M10) |
| AC-10 | device | P0 | guard | D1 | exit sequence at a root with content unchanged |
| AC-11 | device | P0 | guard | D2 | tab pick and Back over Movies unchanged |
| AC-1 | device | P2 | red | D3 (only if Favourites is visible and `Empty`) | the trap on the real app |
| AC-12 | unit | P0 | red (M4) | `` DrawerStateTest.`a failed dismiss handoff leaves the rail closed even when reopening is allowed` `` | fails if `failHandoff` ignores the kind |
| AC-12 | unit | P0 | red (M4) | `` DrawerStateTest.`a failed initial handoff leaves the rail closed even when reopening is allowed` `` | same, Initial |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a failed tab pick handoff reopens the rail` `` (renames `a failed handoff returns the rail to open`) | fails if every fail ends `Closed` (M5) |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a failed tab pick handoff that must not reopen leaves the rail closed` `` (renames `a failed handoff that must not reopen leaves the rail closed`) | fails if `reopen = false` is ignored for TabPick |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`settling the active handoff closes the rail` `` as `@ParameterizedTest @EnumSource(HandoffKind::class)` (Initial started from `Closed` via `beginInitialHandoff`) | fails if settle depends on the kind or leaves `handoffKind` set |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`a tab pick during the initial handoff restarts it as a tab pick with a new id` `` | fails if `beginHandoff(TabPick)` from an Initial HandingOff returns null or reuses the id; asserts `handoffKind == TabPick`, `handoffExpectsNewContent` |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`the superseded initial id can neither settle nor fail the tab pick` `` | fails if the old id still matches |
| AC-12 | unit | P0 | red (M9) | `` DrawerStateTest.`a tab pick during a tab pick handoff restarts it with a new id` `` | fails if a corrective pick keeps the mistaken tab's id |
| AC-12 | unit | P0 | guard | `` DrawerStateTest.`a tab pick during a dismiss handoff is refused` `` | fails if `beginHandoff` restarts a Dismiss (returns non-null, kind changes) |
| AC-12 | unit | P1 | guard | `` DrawerStateTest.`a dismiss is refused unless the rail is open` `` | fails if Dismiss restarts a pending handoff |
| AC-12 | unit | P1 | guard | `` DrawerStateTest.`only a tab pick handoff waits on new content` `` (`@EnumSource`; replaces the two `handoffExpectsNewContent` cases) | fails if the derived flag is true for Dismiss or Initial |
| AC-12 | unit | P2 | guard | `` DrawerStateTest.`an initial handoff cannot be begun as a rail handoff` `` | fails if `require(kind != Initial)` is removed |
| AC-12 | unit | P2 | guard | `` DrawerStateTest.`a failed handoff clears its kind` `` | fails if `handoffKind` survives `failHandoff` |
| AC-12 | unit | P0 | guard | `check.full` (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) green | compile fix at `MainSideMenuFocusTraversalTest.kt:150`, detekt, lint |
| R6 | instrumented | P2 | red | `MainRailHandoffKindTest.rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed`: fixture; Center on selected item; `awaitDrawer(Closed)` | records Open question 1 (base `Open`) |

Unchanged and green: `RailBackOwnerTest`, `ExitConfirmationTest`.

### Failing-first test

Two defects, written first, red on base before any production edit:
- defect 1: `MainRailHandoffKindTest.backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`; base `MainScreenComponent.kt:221` returns on every HandingOff Back, so `isFinishing` never turns true.
- defect 2: `MainRailHandoffKindTest.pickDuringInitialHandoff_switchesToPickedTab`; base `acceptsPickOf` (`MainScreenComponent.kt:304-308`) requires `handoffExpectsNewContent`, which `beginInitialHandoff` (`ModalNavigationDrawer.kt:142-145`) sets false.

Red run (qa-device, box, lease): every `red` instrumented row with production at base. In the worktree after the harness and test commit: `git checkout c9e98129 -- app/src/main app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt`, `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.main.component.MainRailHandoffKindTest`, then `git checkout HEAD -- app/src/main app/src/androidTest`. Expected: the `red` rows fail at the assertion named in Proves (not in the fixture), the `guard` rows pass.

### Mutation runs

Each production change is proven by at least one test failing under its mutation. qa-device applies the mutation in the worktree, runs the listed tests, records pass/fail, reverts (`git checkout HEAD -- <file>`). Instrumented runs: `TESTS=com.kino.puber.ui.feature.main.component.MainRailHandoffKindTest` unless named otherwise.

| # | Production change | Mutation | Must fail |
|---|---|---|---|
| M1 | `RailOpenBackHandler` armed Dismiss exit | delete the `finish()` branch (HandingOff always swallowed) | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` |
| M3 | same, `isArmed` gate | finish on every Dismiss HandingOff Back | `backDuringDismissHandoffAfterArmExpired_isSwallowed` |
| M4 | `failHandoff` outcome per kind | `Open` whenever `reopen` (base rule) | DrawerStateTest dismiss and initial fail cases; `failedBackHandoffAtNeverFocusableRoot_*`, `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed`, `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` |
| M5 | same, TabPick keeps reopening | always `Closed` | `a failed tab pick handoff reopens the rail`; `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`; `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` |
| M6 | `acceptsPickOf` kind rule | restore `handoffExpectsNewContent && !tab.isSelected` | `pickDuringInitialHandoff_switchesToPickedTab` |
| M7 | `acceptsPickOf` `!tab.isSelected` | drop the clause | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches`, `centerOnSelectedTabDuringInitialHandoff_switchesNothing` |
| M8 | `acceptsPickOf` `!= Dismiss` | drop the clause | `MainRailBackTest.centerOnCollapsedRail_duringHandoff_doesNotSwitchTab` |
| M9 | `beginHandoff` restart from HandingOff | return null outside `Open` (base); second variant: restart with the same id | DrawerStateTest restart cases; `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` (run 3 times: detection needs the outgoing tab to compose one frame before the swap) |
| M10 | item click kind | `TabPick` for the selected tab | `rePickOfSelectedTab_closesRailOntoContent` |
| M11 | Right key kind | `TabPick` | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` |
| M12 | `RailOpenBackHandler` kind | `TabPick` | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt` (fail ends `Open`) |

Not mutation-proven: the `handoffKind == Dismiss` part of the exit gate (R8).

### Manual device checks

Box `192.168.1.110:5555`, package `com.kino.puber.stage` only, lease held, worktree dev APK via `tools/android-apk-install-preserve`, keys by `adb -s 192.168.1.110:5555 shell input keyevent <code>` (BACK 4, DPAD_LEFT 21, DPAD_DOWN 20, DPAD_CENTER 23), a screenshot after each expected state. Must not happen in any check: adding or removing a favourite, a long press or context menu on a card, touching `com.kino.puber`, uninstall or data clear.

D0. Precondition (R11): before installing the worktree APK, refresh the session with a master build and `make DEVICE=192.168.1.110:5555 auth-save`. Expected: the app opens on Home, not pairing.

D1 (AC-10). Cold start, wait until Home content is focused. BACK, BACK, BACK about 0.5 s apart. Expected: rail opens; rail collapses and the "press Back again" toast shows; the launcher is shown. Must not: rail reopening by itself, an ignored Back, exit on the first or second Back. Repeat on Movies and History (pick them from the rail first, wait for content focus).

D2 (AC-11). Cold start, Home content focused. DPAD_LEFT (rail opens), DPAD_DOWN to Movies, DPAD_CENTER. Expected: Movies shown, a Movies card focused, rail collapsed. DPAD_LEFT, then BACK. Expected: rail collapses, focus on the card it was opened from. Must not: rail open again after 5 s, nothing focused, Home still shown.

D3 (AC-1, AC-2; only if the Favourites tab is visible and its screen shows the empty text; otherwise record "not run: Favourites not empty" and stop). Pick Favourites from the rail; wait 6 s (TabPick fail: rail reopens, AC-8). BACK. Expected: rail collapses, toast. BACK within 2 s. Expected: launcher. Relaunch, pick Favourites, wait 6 s, BACK, wait 6 s. Expected: rail stays collapsed, nothing focused, no toast reappearing. DPAD_LEFT. Expected: rail opens. Must not: rail reopening by itself after the collapse, Back doing nothing.

### UNTESTABLE

None. AC-2 to AC-8 are instrumented-only on the device side (brief: Device feasibility).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | A failed Back/Right or cold-start handoff: leave the rail collapsed instead of reopening (root and pushed)? | yes | answered | yes, Closed; Left reveals the rail |
| Q2 | Second Back during a pending Back handoff with the exit armed: leave the app? | yes | answered | yes, within the 3 s window |
| Q3 | Keep "a tab pick into a never-focusable tab reopens the rail"? | no | answered | keep |
| D1 | Re-picking the selected tab is Dismiss, so with nothing focusable the rail ends Closed after 5 s (base: Open) | no | answered | accept |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | rail-pick-during-initial-handoff folded into this run | owner |
| 2026-11-25 | Q1-Q3, D1 on the recommended answers; design Option A (HandoffKind) | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved as designed (size M, no tracks, AC-1..AC-12) | owner (standing instruction: recommended) |
