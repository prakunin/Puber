# Brief: showall-rail-back-exits-app

Base f88f3079. Analyst, 2026-11-25. Builds on `research.md` (cited as research.md: section). Folds in `showall-first-entry-rail-focus` (owner, state.md Decisions 2026-11-25).

kind: bug. size: M. tracks: none (Back and focus behaviour only; nothing changes how a screen looks). Device verification: **yes** (Back, focus, navigation, D-pad on the TV box 192.168.1.110:5555).

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Request
Show all grid on a tab (Movies «Все фильмы»): with the side rail expanded, Back must close the rail and keep the grid. Today it pops Show all in one session state and closes the rail with the exit toast in another; once Back from the expanded rail left the app. Folded bug: on entry into Show all from the tab root, focus lands on the rail, which expands it.

## Root cause

| # | Defect | Cause | Status |
|---|---|---|---|
| 1 | Same Back press pops Show all or closes the rail depending on history | Three Main-level `BackHandler`s rely on registration order: RailClosed (`ui/feature/main/component/MainScreenComponent.kt:119`), TabBack (`core/ui/navigation/component/FlowComponent.kt:622,749-757`, enabled on `navigator.canPop` only, no rail check), RailOpen (`MainScreenComponent.kt:159`). activity-compose 1.13.0 registers on first entry to composition and the last enabled one wins (research.md: How the dispatcher picks). On cold start and after a tab switch the tab content composes a pass later (`core/ui/navigation/component/TabComponent.kt:109-131,146`), so TabBack registers last and pops Show all with the rail open. After a Details round trip Main re-enters in one pass, RailOpen is last and wins (research.md: Why the order flips) | confirmed: code, library bytecode, device observations `.flow/runs/catalogue-browse-all/qa-report.md:42,65` (one Back pops before a Details trip; grid stays with toast after) |
| 2 | Back from the rail can leave the app | RailOpen arms the exit on any screen, pushed or root (`MainScreenComponent.kt:205-208`); only RailClosed disarms (`:185`); a pop through TabBack does not. Rail open on Show all, three Backs within 3 s (`ui/feature/main/component/ExitConfirmation.kt:12`): close rail + arm, pop Show all, RailClosed sees `isArmed` and calls `finish()` (`:180-181`) | plausible: derived from code (research.md: Path to leaving the app), not reproduced |
| 3 | Entry into Show all puts focus on the rail | The push saves focus and replaces the opener (`FlowComponent.kt:782-786`); Show all in `Loading` has nothing focusable (header `canFocus = false`, `ui/feature/showall/content/ShowAllScreenContent.kt:80-87,107-111`). Focus reaching the rail sheet calls `drawerState.reveal()` (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:284`); once Open the content gate (`MainScreenComponent.kt:149`) and `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:64,76`) keep the grid from taking focus back. Nothing starts a handoff on an in-tab push, so `redirectFocusToContent` (`ModalNavigationDrawer.kt:281-282`) never runs | reveal-and-stay chain confirmed in code; what moves focus into the rail after the opener leaves, and whether it happens on every entry or only the first, is `unverified` |

### Verifying defect 3 before the fix (implementer, first step; qa repeats on the box)
1. Box, base f88f3079 build: cold start, Movies, Center on «Все фильмы»; screenshot at about 1 s and 3 s. Repeat as a second entry, after a Details round trip, and through a shelf «Показать все» tile. Record per path: rail expanded yes/no.
2. Instrumented test on the same box: Main drawer host plus a tab flow, push a screen that stays in a no-focusable loading state, then supply content; record the focused node per frame and `drawerState.currentValue`. Red on base when step 1 reproduces.
3. If step 1 reproduces but step 2 cannot name the node that takes focus, record that in the spec and move defect 3 back to `showall-first-entry-rail-focus` (see Q3). If step 1 does not reproduce on base, AC-8 is recorded as passing on base and no fix is made for it.

## Intended Back (research.md: Options; Q1, Q2)

| Rail | Tab stack | Back |
|---|---|---|
| Open | pushed (Show all) | close rail, focus back to the card left; no exit arm, no toast |
| Open | root | close rail, arm exit, toast (unchanged) |
| HandingOff | any | swallowed (unchanged) |
| Closed | pushed | screen dispatcher (`ui/feature/showall/vm/ShowAllVM.kt:291`), else pop; disarm exit |
| Closed | root, armed | leave app (unchanged) |
| Closed | root, not armed | open rail (unchanged) |

Approach: research.md Option A (handlers gated by state so at most one Main-level handler is enabled, independent of order); the spec decides between A and B.

## Acceptance criteria
Device ACs run on the box by remote input (D-pad, Center, Back) with screenshots; instrumented tests run through `make DEVICE=192.168.1.110:5555 itest TESTS=<class>` under a lease.

- AC-1. Given a cold start, Movies tab, Show all grid with a card focused, When Left expands the rail and Back is pressed, Then the rail closes, the grid stays, the card left is focused and no exit toast appears (device; instrumented test red on base).
- AC-2. Given AC-1 after a Details round trip from the grid, When Left then Back, Then the same result as AC-1, without the toast (device; instrumented test red on base).
- AC-3. Given AC-1 after switching tabs through the rail and back to Movies, When Left then Back, Then the same result as AC-1 (device; instrumented test).
- AC-4. Given Show all with the rail closed, in each history of AC-1 to AC-3, When Back is pressed once, Then Show all pops to the tab root with the opener focused and no toast (device).
- AC-5. Given the rail expanded over Show all, When Back is pressed 5 times about 0.5 s apart, Then the sequence is: rail closes; Show all pops; rail opens at the root; rail closes with the toast; app exits on the fifth press only (device; instrumented test on the handler sequence).
- AC-6. Given the toast was just shown at a tab root, When Show all is opened within 3 s and Back is pressed twice, Then the first pops Show all and the second opens the rail; the app stays in the foreground (instrumented test; device).
- AC-7. Given a tab root (Home, Movies, History) with the rail closed, When Back, Back, Back, Then rail opens, rail closes with the toast, app exits: unchanged from base (device).
- AC-8. Given a tab root with a card focused, When Show all is opened by Center (cold first entry, second entry, after a Details round trip, and through a shelf «Показать все» tile), Then the rail stays collapsed while loading and the first card is focused once the list lands; Left from the grid still expands the rail (device; instrumented test from the verification step). Subject to Q3.
- AC-9. Given Show all with the filter panel open, or a card context menu open, When Back, Then only the panel or menu closes and focus returns to «Фильтры» or the card (device; `ShowAllRailCloseFocusTest` stays green).
- AC-10. Given Details or the player pushed over Main, When Back, Then it pops to the screen underneath as on base (device).
- AC-11. Existing tests stay green: `MainSideMenuFocusTraversalTest`, `InitialDrawerContentFocusTest`, `ShowAllRailCloseFocusTest`, `ShowAllScreenContentFocusTest`, `TabFlowLifecycleTest`, `ExitConfirmationTest`, `DrawerStateTest`; unit suite, detekt, lint (`check.full`).

## Risks
- Exit semantics: the toast and double-Back are the only way out of the app; a wrong gate traps the user at the root or exits on a stray press (AC-5 to AC-7).
- A handoff started on an in-tab push puts the rail in `HandingOff`, where Back is swallowed (`MainScreenComponent.kt:200-202`) for up to 5 s on a slow first page (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:24`). The fix for defect 3 must not swallow Back while Show all loads.
- Every tab shares the Main handlers; the tab's `canPop` becomes state read by Main. Screen-level handlers (`ui/feature/showall/content/ShowAllFilterPanel.kt:87`, `core/ui/uikit/component/TvDialogOverlay.kt:57`) must still register after and win.
- The cold-start and tab-switch orders only appear when the tab content composes a pass after Main; a test that composes everything in one pass passes on base and proves nothing.
- Device: a worktree build can drop the box to pairing (memory: worktree-build-loses-box-session); refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before installing the worktree APK.

## Open questions
1. Back with the rail open over a pushed screen: close the rail only, or close and pop? Recommended: close the rail only; the next Back pops. Blocks: yes (AC-1 to AC-5).
2. When does the "press Back again to exit" toast appear? Recommended: only when Back closes the rail at a tab root; never on a pushed screen; any pop disarms. Blocks: yes (AC-5, AC-6).
3. If the verification step cannot name what moves focus into the rail on entry, keep defect 3 here with a mechanism-independent fix (handoff on push, Back not swallowed), or split it back out? Recommended: split it back to `showall-first-entry-rail-focus` and ship the Back fix alone; a fix for an unexplained cause is not ready. Blocks: no.

## Related
| Run | Relation |
|---|---|
| `rail-content-mismatch-rapid-back` | idea; retest after this ships (research.md: Related work) |
| `showall-rail-close-focus-shift` AC-3, `catalogue-browse-all` AC-7 step 5, `catalogue-filters` qa Manual 2 | deferred checks this run makes passable |
