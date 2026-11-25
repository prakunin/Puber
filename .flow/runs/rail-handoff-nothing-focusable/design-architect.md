## Design

Architect, 2026-11-25. Base c9e98129. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`. Inputs: `brief.md`, `research.md` (Option A), state.md Decisions Q1-Q3.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

| Alternative | Reason |
|---|---|
| research.md Option B: second boolean beside `handoffExpectsNewContent` | Two flags that must stay consistent; four combinations, three meaningful |
| research.md Option C: probe focusability at Back/Right and skip HandingOff | No Compose API for "has a focusable descendant"; content that turns focusable a moment later gets no handoff; leaves defect 2 |
| Kind rule for the fail outcome in `ContentFocusHandoff.fail` instead of `DrawerState` | Puts the rule behind a Compose facade, out of reach of `DrawerStateTest` |
| Keep the pending id on a pick during Initial and only flip `expectsNewContent` | The Initial id was taken by the first tab's effect; flipping the flag mid-effect does not restart it, and the 5 s budget would be the cold-start one |
| Exit on any armed Back during HandingOff, regardless of kind | Contradicts the brief table (Initial, TabPick swallow) |
| Stretch the exit window past 5 s | Q2 keeps the 3 s window; a stray press later would leave the app |

### Risks

| # | Area | Risk | Lives in | Check |
|---|---|---|---|---|
| 1 | Focus and D-pad | A failed Dismiss or Initial at a root now leaves the rail collapsed with nothing focused; only Left and Back act until content focuses itself. Content that loads after the fail must request focus on its own: `rememberFocusRequesterOnLaunch` does (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61-87`, blocked only while `Open`); readers that latch on the handoff id (`ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`) are unverified at a root after a fail | `ModalNavigationDrawer.kt:173-177`, `:256-267` (rail focus cleared on `Closed`), `:342-359` | AC-2, AC-3, AC-6 instrumented; device check of a slow root is not reproducible (brief: Device feasibility) |
| 2 | Exit semantics | The armed HandingOff exit fires on a Back that is not the second Back at the same root (an arm surviving a pick or push), dropping the user to the launcher; or never fires and the trap stays | `MainScreenComponent.kt:207-231`, disarms at `:111-116,238-241` | AC-1, AC-9 (`MainRailBackTest`), AC-10 on the box |
| 3 | Focus and D-pad | The Initial-to-TabPick restart leaves the outgoing first tab able to confirm (it would need `bornDuringRequest` equal to the new id, which only content composed after the click has) or the arriving tab misses the id because it composed before the click returned | `ModalNavigationDrawer.kt:147-153`, `ContentFocusHandoff.kt:108,114-118`; the swap is async (`core/ui/navigation/TabRouter.kt:19-26`) | AC-4 asserts only the arriving tab settles |
| 4 | Focus and D-pad | Re-picking the active tab becomes Dismiss: at a root with nothing focusable it now ends `Closed` instead of `Open` (Open question 1) | `MainScreenComponent.kt:324-331` | `DrawerStateTest` Dismiss fail; no AC |
| 5 | Lifecycle | Dialog re-entry (`MainScreenComponent.kt:123-125`) is Initial, so after a dialog over nothing-focusable content the rail ends `Closed` instead of `Open` | `ContentFocusHandoff.kt:112-113` gate, `ModalNavigationDrawer.kt:173-177` | `InitialDrawerContentFocusTest` stays green (AC-9) |
| 6 | Baseline profile | `DrawerState.beginHandoff(Z)` in `app/src/main/generated/baselineProfiles/baseline-prof.txt:29811` no longer matches the new signature; the method loses AOT until the next profile generation | generated profile | none; regenerate with the next profile run |

### Open questions

| # | Question | Recommended | Blocks |
|---|---|---|---|
| 1 | Re-picking the already selected tab from the open rail is classified Dismiss, so if that tab has nothing focusable the rail ends `Closed` after 5 s instead of reopening (base: `Open`). Accept? | Accept: it composes no new content and reads as closing the rail over the same screen; consistent with Back and Right | no |
