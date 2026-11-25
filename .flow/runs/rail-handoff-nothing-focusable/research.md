# Research: rail-handoff-nothing-focusable (folds rail-pick-during-initial-handoff)

## 1. Request

> Slug: rail-handoff-nothing-focusable (with folded run rail-pick-during-initial-handoff).
> Request: two related handoff defects in the side rail (ModalNavigationDrawer / ContentFocusHandoff / MainScreenComponent), both left after run showall-rail-back-exits-app (merged c9e98129; its spec docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md, run folder /home/dev/Puber/.flow/runs/showall-rail-back-exits-app/ — triage-4.md D6, review-state-path-4.md, design-architect.md Open question 1, review-edge-1.md):
> (1) /home/dev/Puber/.flow/runs/rail-handoff-nothing-focusable/state.md: at a tab ROOT whose content has nothing focusable (empty Bookmarks/History, error state without a button), Back from the open rail starts a handoff that fails after 5 s and reopens the rail (ContentFocusHandoff.kt:120-137), and the exit can never be reached by Back (the 5 s fail is longer than the 3 s exit window); only a tab pick leaves.
> (2) /home/dev/Puber/.flow/runs/rail-pick-during-initial-handoff/state.md: during the cold-start (initial) handoff the rail refuses a pick (acceptsPickOf, expectsNewContent = false) while the first tab loads; after 5 s the rail reopens. Also the !tab.isSelected clause is untested because the test probe never updates the selection.
> Map the current handoff state machine (DrawerState: beginHandoff/beginInitialHandoff/startHandoff/settleHandoff/failHandoff(reopen), handoff kinds, acceptsPickOf, the Back owner rule railBackOwner, exit confirmation), every caller, the intended behaviour per case (root vs pushed, initial vs tab pick vs Back/Right), which screens can really be "nothing focusable" at a root in this app, existing tests (MainRailBackTest, MainTabPushRailFocusTest, DrawerStateTest, MainHostHarness), and fix options with trade-offs.

## 2. Understanding

The rail hands focus to the content through a pending "handoff" (`DrawerValue.HandingOff`) that settles when the content holds focus and fails after 5 s. The handoff has no notion of *why* it started: Back, Right, a tab pick, cold start and a dialog all go through the same state with one boolean (`handoffExpectsNewContent`). Two defects follow. (1) At a tab root with nothing focusable, Back closes the rail into a 5 s HandingOff that swallows every further Back, then reopens the rail; the exit armed by that Back (3 s window) has expired by then, so Back loops forever. (2) The cold-start handoff looks like a Back/Right handoff (`expectsNewContent = false`), so a rail pick made while the first tab loads is refused. The fix is to give the handoff a kind and decide pick acceptance, fail outcome and Back-to-exit per kind.

## 3. Current state in code

All paths under `/home/dev/Puber/app/src/main/java/com/kino/puber/` unless absolute. Base c9e98129.

### State machine (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`)

| Element | Where | Behaviour |
|---|---|---|
| `DrawerValue` Closed / Open / HandingOff | :62-75 | HandingOff = rail logically closed, focus not yet landed |
| `pendingHandoffId` | :104 | monotonic id; null when no handoff |
| `handoffExpectsNewContent` | :116 | the only "kind" today: true only for a pick of a different tab |
| `reveal()` | :122-126 | Closed -> Open only (ignored in HandingOff) |
| `beginHandoff(expectsNewContent)` | :136-139 | only from Open, else returns null |
| `beginInitialHandoff()` | :142-145 | only from Closed; always `expectsNewContent = false` |
| `startHandoff` | :147-153 | new id, sets flag, value HandingOff |
| `focusExited()` | :156-160 | Open -> Closed (focus left rail without handoff) |
| `settleHandoff(id)` | :163-167 | matching id -> Closed |
| `failHandoff(id, reopen = true)` | :173-177 | matching id -> Open, or Closed when `reopen = false` |
| Saver | :181-188 | HandingOff persisted as Closed |
| Initial handoff on compose | :209-211 | `beginInitialHandoff()` when a handoff facade exists |
| Rail focus request / clear | :256-267 | Open -> request rail focus; Closed with rail focused -> `clearFocus(force)` |
| Right key | :287-296 | Open -> `beginHandoff(false)`; HandingOff -> swallowed |
| Bounce redirect | :297-311 | focus entering the rail during HandingOff -> `redirectFocusToContent()` |
| Entry gate | :315-323 | Closed rail refuses every entry except Left |
| Unhandled Left | :342-359 | Closed + content not focused -> `reveal()` |

### Content side (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt`)

- `HandoffTimeoutMillis = 5_000` :24; eager frames :32, back-off :35, grace frames :46.
- Facade `ContentFocusHandoff` :57-80; `fail` passes `reopen = !closeOnFail()` :71.
- `ContentFocusHandoffEffect` :97-142: skips when gated by `LocalContentFocusActive` :113; outgoing-tab guard when `expectsNewContent` :114-118; settle on focus :119-137; `fail` on timeout :141.
- Only caller of the effect: `core/ui/navigation/component/FlowComponent.kt:657-662` (`TabFlowNavigator`, `restartKey = contentInstanceKey`).

### Main host (`ui/feature/main/component/MainScreenComponent.kt`)

| Element | Where | Behaviour |
|---|---|---|
| Facade wiring | :106-108 | `closeOnFail = { tabStack.canPop }`: pushed screen -> Closed on fail; root -> reopen |
| Pick disarms exit | :111-116 | any `ItemSelected` disarms |
| Dialog handoff | :123-125 | `!outerActive` -> `beginInitialHandoff()` (second Initial caller) |
| Content gate | :166 | `LocalContentFocusActive` true in HandingOff |
| `RailClosedBackHandler` | :185-204 | owner RailClosed: armed -> `finish()`, else `reveal()` |
| `RailOpenBackHandler` | :207-231 | owner RailOpen; HandingOff swallowed :221; Open -> `beginHandoff(false)` :223; arms + toast at root :226-229 |
| Disarm on push/pop | :238-241 | |
| `acceptsPickOf` | :304-308 | Open true; HandingOff `handoffExpectsNewContent && !tab.isSelected`; Closed false |
| Item click | :324-331 | pick -> `ItemSelected`, then `beginHandoff(!tab.isSelected)` (returns null in HandingOff, so a corrective pick keeps the pending id) |

- Back owner: `core/ui/navigation/component/RailBackOwner.kt:25-30`: Open or HandingOff -> RailOpen; else canPop -> Tab; else Closed -> RailClosed. Tab side: `FlowComponent.kt:755-766`.
- Exit window: `ui/feature/main/component/ExitConfirmation.kt:12` (3 000 ms), class :29-47.
- Selection update in the real app: `ui/feature/main/vm/MainVM.kt:131-135` (state first, then `openTab`).
- Other readers of the handoff id (latch it to retry focus): `ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`. Readers of `LocalDrawerState` (Open only): `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:64`, `ui/feature/device/diagnostics/NetworkDiagnosticsContent.kt:100`.

### Behaviour per case today

| Trigger | Root | Pushed screen |
|---|---|---|
| Back from Open, content focusable | settles in frames; exit armed; 2nd Back exits | settles; no arm |
| Back from Open, nothing focusable | HandingOff 5 s, Back swallowed, then **Open**; arm expired (defect 1) | HandingOff 5 s, then Closed; next Back pops (A5) |
| Right from Open, nothing focusable | 5 s then Open | 5 s then Closed |
| Tab pick, arriving tab never focusable | 5 s then Open (spec 2026-08-16 line 216: "correct outcome") | N/A: a pick replaces the stack |
| Cold start, first tab slow | HandingOff; Back swallowed; Left does nothing (reveal ignored); a rail item can hold focus but Center is refused (defect 2); after 5 s Open | N/A |
| Dialog over content (`outerActive` false) | Initial handoff pending until the gate opens; Back handlers disabled | same |

### Roots that can really have nothing focusable

Visible tabs come from `data/preferences/NavigationPreferencesRepository.kt:79-87,159` (defaults = `TabType.enabled`; Bookmarks and Collections are `enabled = false` in `domain/model/TabType.kt:20,31`).

| Root | State with nothing focusable | Where |
|---|---|---|
| Favourites | `Empty` (text only) - durable | `ui/feature/favorites/content/FavoriteScreenContent.kt:46-51` |
| Bookmarks (if made visible; unverified that the menu editor offers it) | `Error` (text only) - durable | `ui/feature/bookmarks/component/BookmarksScreenContent.kt:53-55` |
| Home, History, Favourites, Bookmarks, Settings | `Loading` (progress only) - transient, can exceed 5 s on a slow network | `home/component/HomeScreenContent.kt:81,113-126`; `history/component/HistoryScreenContent.kt:102,121`; `favorites/content/FavoriteScreenContent.kt:58`; `device/settings/DeviceSettingsContent.kt:101,116-128` |
| Content lists (Movies, Series, ...) | every section `Empty` and no hero - unverified, rare | `ui/feature/contentlist/content/SectionRow.kt:88` |

Not affected: History `Empty` has a Refresh button (`HistoryScreenContent.kt:396-433`); every `FullScreenError` has a Retry button (`core/ui/uikit/component/Errors.kt:49-54`); Home and Settings errors have buttons; Search always has its text field (`search/content/SearchScreenContent.kt:159-164`). The request's "empty History" example does not hold at base.

### Tests

| Test | What it covers | Gap for this run |
|---|---|---|
| `app/src/test/.../drawer/DrawerStateTest.kt` | transitions incl. fail with/without reopen, expectsNewContent flag | no kind; no pick-during-initial |
| `app/src/test/.../navigation/component/RailBackOwnerTest.kt` | owner table | unchanged unless Back in HandingOff changes |
| `app/src/test/.../main/component/ExitConfirmationTest.kt` | window | - |
| `app/src/androidTest/.../main/component/MainRailBackTest.kt` | Back order, A5 pushed fail (:151-183), Center refused in Back handoff (:185-206), corrective pick (:208-228), root exit (:230-245) | no root-with-nothing-focusable case; no initial-handoff pick |
| `app/src/androidTest/.../main/component/MainTabPushRailFocusTest.kt` | push into Loading, unhandled Left (:80-103) | - |
| `app/src/androidTest/.../drawer/InitialDrawerContentFocusTest.kt` | initial handoff lands late content, gated, dialog | no pick |
| `app/src/androidTest/.../main/component/MainHostHarness.kt` | probe host | selection is static (`isSelected = true` on Movies, :399-418), so `!tab.isSelected` is never exercised; exit clock fixed at 0 (:126), so an arm never expires and the 3 s vs 5 s race cannot be shown; `loadingRoot` (:102,432) already gives a root with nothing focusable |

## 4. Related work

| Item | Relation |
|---|---|
| run `showall-rail-back-exits-app` (done, merge 06413281, record c9e98129) | source of both defects: design-architect.md Open question 1 (defect 1), triage-4.md D6 + review-state-path-4.md F1 (defect 2), D2 (untested `!tab.isSelected`), review-edge-1.md (pushed variant, fixed as A5) |
| run `rail-pick-during-initial-handoff` (idea) | folded into this run; proposes kinds Initial / TabPick / Exit |
| run `side-rail-focus-handoff` (done); spec `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md` (:120-140 timeout rationale, :216 "tab empty or in error ... rail returns open. This is the correct outcome") | origin of the handoff; its decision for empty tabs is what this run partly reverses |
| run `rail-content-mismatch-rapid-back` (idea, not analysed) | same area (rapid Back vs rail); not a duplicate |
| commits 1f6bc4b4 (rail owns Back, entry gate, unhandled Left), eac8c56d (content focus gate), 5f0fcda8 (initial drawer focus) | the code paths above |

Duplicate: none.

## 5. External findings

External findings: off (local research only)

## 6. Options

| | A. Handoff kind in DrawerState (recommended) | B. Two flags, no enum | C. Skip the handoff when the content cannot take focus |
|---|---|---|---|
| Change | Replace `handoffExpectsNewContent` with `HandoffKind { Initial, TabPick, Dismiss }` (Dismiss = Back or Right); `expectsNewContent` derived (`== TabPick`). (a) `acceptsPickOf` in HandingOff: `kind != Dismiss && !tab.isSelected`. (b) A pick during Initial (or TabPick) restarts the handoff as TabPick with a new id, so only the arriving tab confirms. (c) Fail outcome per kind: reopen only for TabPick at a root; Dismiss and Initial settle Closed. (d) `RailOpenBackHandler` in HandingOff: armed exit -> `finish()`, else swallow as now | Keep the boolean, add `isInitialHandoff` and a "dismiss" flag read by `closeOnFail` and `acceptsPickOf`; same four behaviours | At Back/Right, probe whether the content can take focus; if not, go straight to Closed without HandingOff |
| Fixes (1) | yes: Back, Back exits inside 3 s; a failed Dismiss leaves the rail collapsed, Left reveals it | yes | yes, no 5 s wait |
| Fixes (2) | yes | yes | no |
| Cost | DrawerState API change; 3 production files (`ModalNavigationDrawer.kt`, `ContentFocusHandoff.kt`, `MainScreenComponent.kt`) + DrawerStateTest, MainRailBackTest, MainHostHarness (dynamic selection, controllable clock) | fewer lines; two booleans that must stay consistent, harder to test exhaustively | no reliable Compose API to ask "has a focusable descendant" (rejected in the prior run, design-architect.md A5 row); loading content may become focusable a moment later and then nobody hands focus |
| Size | M (focus / navigation risk area) | M | M, and leaves (2) open |

Recommended: A.

## 7. Risks

- **Closed with nothing focused** after a failed Dismiss/Initial: only Left (unhandled-key path, `ModalNavigationDrawer.kt:342-359`) and Back act; Up/Down/Right do nothing until content focuses itself. Already the pushed-screen behaviour since 1f6bc4b4; needs a device check at a root.
- **Late content after a Closed fail**: content that loads after 5 s must take focus on its own. Screens using `rememberFocusRequesterOnLaunch` do (`FocusOnLaunchRequester.kt:61-80`, only blocked while Open); screens relying solely on the handoff id latch (`BrowseAllEntry.kt:50`, `ShowAllScreenContent.kt:349`, `FocusTargetState.kt:81`) were not checked for a root - unverified.
- **Pick during Initial without a new id**: with the current `beginHandoff` (Open only) the pending Initial id stays and `expectsNewContent` stays false, so the outgoing first tab could confirm the handoff; (b) above avoids this.
- **Exit during HandingOff**: arming happens only in `RailOpenBackHandler` at a root and is cleared by picks and push/pop, so an armed Back in HandingOff is always the second Back at the same root.
- **Dialog handoff** (`MainScreenComponent.kt:123-125`) also uses Initial; Back handlers are disabled while `outerActive` is false, and picks cannot reach the rail, so (a)-(d) do not change it - confirm in tests.
- **Test timing**: a real 5 s timeout and 3 s window; the harness clock must be controllable to prove the window race, or tests assert the exit inside the HandingOff period instead.
- On-device verification is required (TV focus, Back, rail); a Favourites-empty account is not guaranteed on the box - use a slow/forced `Loading` root or the harness `loadingRoot`.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | After a Back or Right handoff fails at a tab root, should the rail stay collapsed (nothing focused, Left reveals it) instead of reopening? | Yes, Closed for Dismiss at any depth, matching A5 on pushed screens |
| Q2 | While that handoff is still pending and the exit is armed, does a second Back leave the app? | Yes; Back, Back exits within the 3 s window regardless of whether focus landed |
| Q3 | When the cold-start handoff fails (first tab loads longer than 5 s), collapse or open the rail? | Collapse (Closed); the tab's own launch focus takes over when it loads. Open on its own at startup reads as a glitch |
| Q4 | Keep "tab pick into a never-focusable tab reopens the rail" (spec 2026-08-16 line 216)? | Keep: the user just used the rail and a reopened rail offers the next pick |

## 9. Sources

- Code: `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`, `core/ui/uikit/component/drawer/ContentFocusHandoff.kt`, `ui/feature/main/component/MainScreenComponent.kt`, `ui/feature/main/component/ExitConfirmation.kt`, `core/ui/navigation/component/RailBackOwner.kt`, `core/ui/navigation/component/FlowComponent.kt`, `ui/feature/main/vm/MainVM.kt`, `data/preferences/NavigationPreferencesRepository.kt`, `domain/model/TabType.kt`, screen contents listed in section 3, `core/ui/uikit/component/Errors.kt`, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`.
- Tests: `MainHostHarness.kt`, `MainRailBackTest.kt`, `MainTabPushRailFocusTest.kt`, `InitialDrawerContentFocusTest.kt`, `DrawerStateTest.kt`, `RailBackOwnerTest.kt`.
- Commits: c9e98129, 06413281, 1f6bc4b4, eac8c56d, 5f0fcda8 (`git log -- <drawer, MainScreenComponent, RailBackOwner>`).
- Runs: `.flow/runs/showall-rail-back-exits-app/` (design-architect.md, triage-4.md, review-state-path-4.md, review-edge-1.md, state.md), `rail-pick-during-initial-handoff`, `side-rail-focus-handoff`, `rail-content-mismatch-rapid-back`.
- Specs: `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md`, `docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md` (via the run). `docs/architecture/` has no rail or handoff coverage.
