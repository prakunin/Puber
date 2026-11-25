# Research: showall-rail-back-exits-app (with folded showall-first-entry-rail-focus)

## Request

Fix Back handling around the expanded side rail in Show all grids. Read both runs' state.md Logs: /home/dev/Puber/.flow/runs/showall-rail-back-exits-app/state.md and /home/dev/Puber/.flow/runs/showall-first-entry-rail-focus/state.md. Observed on the TV box: (1) Show all grid (Movies «Все фильмы»), Right, Left (rail expands), Back -> the Show all screen pops instead of closing the rail; same from a card; once Back from the expanded rail left the app. (2) On first entry into a Show all grid from the tab root, focus sits on the expanded side rail; after a details round trip RailOpenBackHandler (ui/feature/main/component/MainScreenComponent.kt:199) registers after TabBackHandler (core/ui/navigation/component/FlowComponent.kt:752) and wins, so the first Back only closes the rail; another time the first Back showed the exit toast «Нажми «Назад» ещё раз, чтобы выйти» while the grid stayed. Map every Back handler involved (Compose BackHandler / OnBackPressedDispatcher registration order, Voyager navigator, rail state, exit confirmation), how order changes across recomposition and details round trips, the intended Back behaviour per state (rail open / closed, root / pushed screen), the first-entry focus logic, existing instrumented tests around Back/rail, and fix options with trade-offs. Note related runs (rail-content-mismatch-rapid-back, player-back-press-count, tab-stack-lost-on-root-push history) and docs/architecture/ navigation doc.

## Understanding

Back on the Main host is resolved by three `BackHandler`s whose winner is "the last enabled one registered". The design (2026-08-16) assumed a fixed registration order: RailClosed, then the tab's TabBackHandler, then RailOpen. That order holds only when the tab content composes in the same pass as Main, which is true after a root push returns (Details round trip) and false on cold start and after every tab switch, where the tab content composes a frame later and TabBackHandler ends up last. So the same key press closes the rail in one session state and pops Show all in another. Independently, RailOpen arms the exit prompt even on a pushed screen, and nothing disarms it when a later Back pops a screen, so three quick Backs from an open rail over Show all can leave the app. The folded focus bug is that a Show all push from the tab root can leave focus on the rail, which opens it.

## Current state in code

All paths under `app/src/main/java/com/kino/puber/` unless absolute.

### Back handlers on the Main host

| Handler | Where | Enabled when | Does |
|---|---|---|---|
| Root Voyager `Navigator(onBackPressed = …)` | `core/ui/navigation/component/FlowComponent.kt:127-129`, `:185-191` | root stack can pop (Details, Player pushed over Main) | `router.dispatchBackPressed()` else Voyager pops |
| `RailClosedBackHandler` | `ui/feature/main/component/MainScreenComponent.kt:119`, `:171-187` | `outerActive && rail == Closed` | armed: `activity.finish()`; else `drawerState.reveal()`; always `disarm()` |
| `TabBackHandler` | `core/ui/navigation/component/FlowComponent.kt:622`, `:749-757` | `navigator.canPop` (tab stack > 1); no rail check | `tabRouter.dispatchBackPressed()` (e.g. `ShowAllVM.onBackPressed`, `ui/feature/showall/vm/ShowAllVM.kt:291`) else `puberPop()` |
| `RailOpenBackHandler` | `MainScreenComponent.kt:159`, `:191-210` | `outerActive && rail != Closed` | HandingOff: swallow; Open: `beginHandoff`, `exitConfirmation.arm()`, toast `main_press_back_again_to_exit` |
| Screen/overlay handlers | `ShowAllFilterPanel.kt:87`, `core/ui/uikit/component/TvDialogOverlay.kt:57`, player panels | while shown | register when shown, so they are last and win |

- The tab Voyager navigator has `onBackPressed = null` (`FlowComponent.kt:593-596`), so TabBackHandler is the only tab-level Back path.
- `ExitConfirmation` window is 3 s (`ui/feature/main/component/ExitConfirmation.kt:12`, `:28-46`); only RailClosed disarms it (`MainScreenComponent.kt:185`).
- `AppRouter.dispatchBackPressed` pops the last registered VM dispatcher (`core/ui/navigation/AppRouter.kt:111-119`).

### How the dispatcher picks, in the version used

- `activity-compose` 1.13.0, `navigationevent` 1.0.0, Compose BOM 2026.09.00 (`gradle/libs.versions.toml:7,12`; navigationevent resolved in the Gradle cache).
- Bytecode of `androidx.activity.compose.BackHandlerKt` 1.13.0: with `ActivityFlags.isOnBackPressedLifecycleOrderMaintained` (default `true` in activity 1.13.0 `ActivityFlags.<clinit>`) the handler is added to the dispatcher in a `DisposableEffect(dispatcher, handler)`, and a `LifecycleStartEffect` only toggles `isBackEnabled`. Registration order is therefore the order in which the BackHandler call sites first enter composition; `enabled` changes and lifecycle stop/start do not reorder. The dispatcher is `NavigationEventDispatcher` when `LocalNavigationEventDispatcherOwner` is present, else `OnBackPressedDispatcher`.
- Last enabled registration wins: stated in `MainScreenComponent.kt:116-118,156-158` and the design spec, and consistent with every device observation below; not re-verified against navigationevent source.

### Why the order flips

| Moment | What composes when | Resulting order (first to last) | Back with rail Open on Show all |
|---|---|---|---|
| Cold start | `TabComponent` starts on `LoadingTab` (`core/ui/navigation/component/TabComponent.kt:109-110`); the real tab arrives via `TabCommand.Open` collected in a `LaunchedEffect` (`:117-131`) | RailClosed, RailOpen, TabBack | TabBack wins: Show all pops, rail stays open |
| Tab picked from the rail | `PuberCurrentTab` is `key(currentTab.key)` (`TabComponent.kt:146`), so a new `TabFlowNavigator` and TabBackHandler compose later | RailClosed, RailOpen, TabBack | TabBack wins |
| Return from a root push (Details) | Main left composition (root `CurrentScreen` composes only `lastItem`, `FlowComponent.kt:501-515`) and re-enters with the TabNavigator's saved current tab in the same pass (inferred from the replay comment `TabComponent.kt:124-126`) | RailClosed, TabBack, RailOpen | RailOpen wins: rail closes, toast, grid stays |

This matches catalogue-browse-all qa: "Before any details trip one BACK pops … after a details round trip … grid stays … toast" (`.flow/runs/catalogue-browse-all/qa-report.md:42,65`).

### Path to leaving the app (derived from code, not reproduced)

Rail-wins order, rail Open on Show all: Back 1 closes the rail and arms the exit (toast) although the screen can pop; Back 2 (rail Closed, TabBack last and enabled) pops Show all without disarming; Back 3 within 3 s at the tab root: RailClosed sees `isArmed` and calls `finish()`. The TabBack-wins order gives a similar three-press exit (pop with rail still open, then RailOpen arms, then RailClosed exits).

### First-entry focus

- A tab push saves focus and pushes (`FlowComponent.kt:778-788`); the opener's focused card leaves composition.
- Show all in `Loading` has nothing focusable: header «Фильтры» is `canFocus = false` until the first list lands (`ui/feature/showall/content/ShowAllScreenContent.kt:80-87`, `ShowAllHeader.kt:77`), the body is a progress indicator (`ShowAllScreenContent.kt:157`).
- Focus arriving at the rail sheet reveals it: `onFocusChanged` → `drawerState.reveal()` unless a handoff is pending (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:273-286`). Comments in `ShowAllScreenContent.kt:95-96,228-229` record that lost focus "would fall to the side rail".
- Once Open, the content is gated: `LocalContentFocusActive` false (`MainScreenComponent.kt:148-149`), `rememberFocusRequesterOnLaunch` skips while the drawer is Open (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:64,76`), so the grid never claims focus and the rail stays open.
- The initial-launch handoff (`ModalNavigationDrawer.kt:198-200`, `MainScreenComponent.kt:108-110`, `drawer/ContentFocusHandoff.kt:93-139`) redirects such a bounce back to content, but nothing starts a handoff on an in-tab push.
- Which component moves focus to the rail after the opener card leaves, and why later entries (cached first page) do not hit it: `unverified`.

### Tests around Back and the rail

| Test | Covers | Gap |
|---|---|---|
| `app/src/test/.../ui/feature/main/component/ExitConfirmationTest.kt` | arm window, disarm | not who arms or disarms |
| `app/src/test/.../core/ui/uikit/component/drawer/DrawerStateTest.kt` | rail state machine | no Back |
| `app/src/androidTest/.../ui/feature/main/component/MainSideMenuFocusTraversalTest.kt` | rail stand-in `RailScaffold` (:175), handoff | no BackHandlers, no tab navigator |
| `app/src/androidTest/.../core/ui/uikit/component/drawer/InitialDrawerContentFocusTest.kt` | initial handoff, gated content, dialog dismiss | no push inside a tab |
| `app/src/androidTest/.../ui/feature/showall/content/ShowAllRailCloseFocusTest.kt` | rail close by RIGHT, context menu Back | no Back with rail open |
| `app/src/androidTest/.../core/ui/navigation/component/TabFlowLifecycleTest.kt` | tab flow lifecycle across player/details | no Back order |
| `PlayerPromptFocusAndKeysTest.kt:160` | pattern: `activity.onBackPressedDispatcher.onBackPressed()` on the UI thread | reusable for a Back-order test |

No test composes RailClosed + TabBackHandler + RailOpen together; none covers the cold-start or tab-switch order.

## Related work

| Item | Relation |
|---|---|
| `showall-first-entry-rail-focus` | folded here (state.md Decisions 2026-11-25) |
| `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md:36-38,95-110` (run `side-rail-focus-handoff`, shipped in 04915c81) | introduced the two rail handlers and ExitConfirmation; assumes the static order this bug breaks |
| `showall-rail-close-focus-shift` (done) | AC-3 Back part NOT CHECKED: "Back with the rail open on Show all pops the screen … same on base" (`.flow/runs/showall-rail-close-focus-shift/qa-report.md:13`) |
| `catalogue-browse-all` (done) | AC-7 step 5 deferred: first Back after a details trip stays on the grid with the toast (`judge-1.md:20,69`) |
| `catalogue-filters` (done) | qa round 2 Manual 2 close-with-Back blocked by the same defect (state.md log 2026-11-24) |
| `catalogue-poster-grid` (done) | source of this run (judge-1 concern 5) |
| `tab-stack-lost-on-root-push` (done, `docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md`) | host-owned tab subtree holder (`TabComponent.kt:146-153`); its qa first logged rail focus on first Show all entry (`qa-report.md:32`) |
| `rail-content-mismatch-rapid-back` (idea) | rapid Backs leave the rail showing Home over History; unanalysed; plausibly the same order/handoff interplay; retest after this fix |
| `player-back-press-count` (idea) | player needs 1-3 Backs; player has its own handlers (`PlayerVM.kt:650,693`); not the rail path |
| `favourites-launch-focus-ignores-rail` (idea) | sibling focus-gate defect in `rememberFocusRequesterOnLaunch` users |
| `docs/architecture/navigation.md` | lists `addBackDispatcher`/`dispatchBackPressed`; says nothing about BackHandler order or the rail |

Commits: 04915c81 (2026-08-16, rail handlers + ExitConfirmation), 1674417d (2026-08-25, single-menu redesign, TabBackHandler), eac8c56d (2026-11-20, content focus gate on Main). Duplicate: none.

## External findings

External findings: off (local research only). Library behaviour above comes from the locally cached activity-compose 1.13.0 and navigationevent 1.0.0 bytecode.

## Options

Intended Back per state (basis for all options; to confirm in Q1-Q2):

| Rail | Tab stack | Back |
|---|---|---|
| Open | any | close rail, focus back to content; exit armed only at tab root |
| HandingOff | any | swallowed |
| Closed | pushed (Show all) | screen dispatcher, else pop; disarm exit |
| Closed | root, armed | leave app |
| Closed | root, not armed | open rail |

| Option | Change | Pros | Cons | Size |
|---|---|---|---|---|
| **A. State-gated handlers (recommended)** | Make at most one Main-level handler enabled per state: TabBackHandler also requires rail Closed (read `LocalDrawerState`, null outside Main); RailClosed also requires tab-at-root (expose the current tab's `canPop` upward, e.g. a state on `TabFlowSession`/`TabAppRouterHolder`); RailOpen arms the exit only at tab root; disarm on any tab pop. First-entry: start a content handoff on an in-tab push so a bounce into the rail is redirected (reuses `ContentFocusHandoff`). | Order-independent; keeps the three handlers and the tested handoff; small diff in `MainScreenComponent.kt`, `FlowComponent.kt`, `TabComponent.kt`/`PuberTab.kt` | tab `canPop` becomes shared state; a HandingOff window would swallow Back during a push (needs a push-specific rule) | M (focus, Back, navigation risk area) |
| B. One Back coordinator in Main | Replace RailClosed/RailOpen/TabBackHandler (for tab flows) with a single handler in `DrawerMainContent` that applies the table, calling the tab router's dispatcher then pop | One place owns the rules; trivially testable as a pure function | Main needs the current tab's navigator (lives inside `TabFlowNavigator`); moves Back out of the generic tab flow; larger refactor | M |
| C. Priority / re-registration | Register RailOpen with `NavigationEventDispatcher.PRIORITY_OVERLAY` (constant exists in navigationevent 1.0.0; the compose `NavigationBackHandler` 1.0.0 takes no priority, so it means a manual `addHandler`), plus arm-only-at-root | Smallest diff | Priority semantics `unverified`; RailClosed vs TabBack still rely on composition order; bypasses `BackHandler`/`OnBackPressedDispatcher` fallback | S |

## Risks

- Focus/Back regressions on every tab, not only Show all: Home, History, Bookmarks, Settings share Main. Device check on the box is required (AGENTS.md runtime rules).
- Exit semantics: changing when the toast appears changes the only way out of the app; a wrong gate either traps the user or exits on a stray press.
- HandingOff swallows Back (`MainScreenComponent.kt:200-202`); reusing the handoff for pushes can swallow Back for up to 5 s (`ContentFocusHandoff.kt:24`) on a slow first page.
- Screen-level handlers (filter panel, dialogs) must keep winning; any rework must not register Main handlers after them.
- Instrumented tests must run through `make DEVICE=<serial> itest`; a test of the cold-start order needs the Loading-tab-then-Open sequence reproduced, not a single-pass composition.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Back with the rail open over a pushed screen (Show all): close the rail only, or close and pop? | Close the rail only; the next Back pops. |
| Q2 | When should the "press Back again to exit" toast appear? | Only when closing the rail at a tab root; never on a pushed screen; any pop disarms. |
| Q3 | First entry into Show all: where should focus land? | First card once the list lands; the rail stays closed while loading. |
| Q4 | Pin the fix with an instrumented test composing the real Main handlers and a tab flow, covering cold start, tab switch and Details-return orders? | Yes, plus unit tests for the Back table if Option B's shape is chosen for the rules. |

## Sources

- Code: `ui/feature/main/component/MainScreenComponent.kt`, `ExitConfirmation.kt`; `core/ui/navigation/component/FlowComponent.kt`, `TabComponent.kt`; `core/ui/navigation/PuberTab.kt`, `AppRouter.kt`; `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`, `ContentFocusHandoff.kt`; `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`; `ui/feature/showall/content/ShowAllScreenContent.kt`, `ShowAllHeader.kt`, `ShowAll*.kt`; `ui/feature/showall/vm/ShowAllVM.kt`; `MainActivity.kt`; `gradle/libs.versions.toml`.
- Library bytecode (javap): `~/.gradle/caches/modules-2/files-2.1/androidx.activity/activity-compose/1.13.0`, `androidx.activity/activity/1.13.0` (`ActivityFlags`), `androidx.navigationevent/navigationevent-android/1.0.0`, `navigationevent-compose-android/1.0.0`.
- Tests listed in the table above.
- Docs: `docs/architecture/navigation.md`, `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md`, `docs/superpowers/specs/2026-10-17-tab-stack-lost-on-root-push.md`.
- Commits: 04915c81, 1674417d, eac8c56d.
- Runs: showall-rail-back-exits-app, showall-first-entry-rail-focus, side-rail-focus-handoff, showall-rail-close-focus-shift, catalogue-browse-all, catalogue-filters, catalogue-poster-grid, tab-stack-lost-on-root-push, rail-content-mismatch-rapid-back, player-back-press-count, favourites-launch-focus-ignores-rail.
