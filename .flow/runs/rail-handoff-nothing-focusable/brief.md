# Brief: rail-handoff-nothing-focusable

Base c9e98129. Analyst, 2026-11-25. Builds on `research.md` (cited as research.md: section). Folds in `rail-pick-during-initial-handoff` (owner, state.md Decisions 2026-11-25).

kind: bug. size: M. tracks: none (focus and Back behaviour; nothing changes how a screen looks). Device verification: **yes** (rail, focus, Back, D-pad).

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Request
Two defects in the side-rail handoff left by `showall-rail-back-exits-app`: (1) at a tab root with nothing focusable, Back from the open rail can never reach the exit and the rail reopens after 5 s; (2) during the cold-start handoff the rail refuses a tab pick while the first tab loads. Also: the `!tab.isSelected` clause of `acceptsPickOf` is untested.

## Root cause

| # | Defect | Cause | Status |
|---|---|---|---|
| 1 | Root with nothing focusable: Back loops between rail open and collapsed; exit unreachable | `RailOpenBackHandler` starts a handoff and arms the exit (`ui/feature/main/component/MainScreenComponent.kt:223,227`). The rail is then HandingOff, where the same handler swallows every Back (`:221`) and `RailClosedBackHandler` is disabled (`core/ui/navigation/component/RailBackOwner.kt:25-30`). The content never focuses, so the effect fails after 5 s (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:24,141`); at a root `closeOnFail = { tabStack.canPop }` is false (`MainScreenComponent.kt:107`), so `failHandoff` reopens the rail (`core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:173-177`). The 3 s arm (`ui/feature/main/component/ExitConfirmation.kt:12`) has expired by then; the next Back closes the rail and re-arms, and the cycle repeats | confirmed by code trace (research.md: Behaviour per case today); not reproduced on a device |
| 2 | Cold start: Center on a rail item refused while the first tab loads; rail opens after 5 s | `beginInitialHandoff` always sets `expectsNewContent = false` (`ModalNavigationDrawer.kt:142-145`), indistinguishable from a Back/Right handoff. `acceptsPickOf` in HandingOff requires `handoffExpectsNewContent` (`MainScreenComponent.kt:304-308`), so the pick is refused. Even if accepted, `beginHandoff` returns null outside Open (`ModalNavigationDrawer.kt:136-139`), so the Initial id with `expectsNewContent = false` would stay pending and the outgoing first tab could confirm it. Initial fail reopens the rail (`reopen = !closeOnFail()`, `ContentFocusHandoff.kt:71`) | confirmed by code trace (research.md: State machine) |
| 3 | `!tab.isSelected` untested | the harness selection is static, Movies `isSelected = true` (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainHostHarness.kt:402`); the exit clock is fixed at 0 (`:126`), so an expiring arm cannot be shown | confirmed |

The handoff has no kind: Back, Right, a tab pick, cold start and the dialog re-entry (`MainScreenComponent.kt:124`) share one boolean. Approach: research.md Option A (a handoff kind `Initial | TabPick | Dismiss` in `DrawerState`; pick acceptance, restart and fail outcome per kind; an armed Back during HandingOff exits). The spec settles the API.

## Intended behaviour (Q1-Q3)

| Handoff kind | Started by | Pick of another tab while pending | Back while pending | On 5 s fail |
|---|---|---|---|---|
| Dismiss | Back or Right from the open rail | refused (unchanged) | armed: leave the app; not armed: swallowed | Closed, at root and pushed |
| Initial | cold start; dialog re-entry | accepted; restarts as TabPick with a new id | swallowed (unchanged; disabled under a dialog) | Closed |
| TabPick | rail pick | accepted when `!tab.isSelected` (unchanged) | swallowed (unchanged) | root: Open (unchanged, spec 2026-08-16 line 216) |

Nothing-focusable roots on the real app (research.md: Roots that can really have nothing focusable): Favourites `Empty` (`ui/feature/favorites/content/FavoriteScreenContent.kt:46-51`) is the only durable one with default tabs; every root's `Loading` is transient.

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

## Risks
- A failed Dismiss or Initial handoff at a root leaves the rail collapsed with nothing focused: only Left (`ModalNavigationDrawer.kt:342-359`) and Back act until the content focuses itself. Same as pushed screens since 1f6bc4b4.
- Content that loads after a Closed fail must take focus on its own. `rememberFocusRequesterOnLaunch` does (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:61-80`); readers that latch only on the handoff id (`ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`) are unverified at a root after a fail (AC-6 covers the probe only; a slow Home or Movies load on the box is not reproducible).
- Exit during HandingOff: arming happens only in `RailOpenBackHandler` at a root and is disarmed by picks and push/pop (`MainScreenComponent.kt:111-116,238-241`); a regression traps the user or exits on a stray press (AC-1, AC-10).
- Restarting Initial as TabPick must not leave the outgoing tab able to settle (`ContentFocusHandoff.kt:114-118`).
- Device: a worktree build can drop the box to pairing (memory: worktree-build-loses-box-session); refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before installing the worktree APK.

## Open questions
1. When a Back/Right handoff or the cold-start handoff fails (content not focusable within 5 s), should the rail stay collapsed instead of reopening, at a root as on pushed screens? Recommended: yes, `Closed` for Dismiss and Initial at any depth; Left reveals the rail. Blocks: yes (AC-2, AC-3, AC-6).
2. While a Back handoff is still pending and the exit is armed, does a second Back leave the app? Recommended: yes; Back, Back exits within 3 s whether or not focus landed. Blocks: yes (AC-1).
3. Keep "a tab pick into a never-focusable tab reopens the rail" (spec `docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md` line 216)? Recommended: keep; the user just used the rail and the next pick is there. Blocks: no (AC-8).

## Related
| Run | Relation |
|---|---|
| `showall-rail-back-exits-app` | source: design-architect.md Open question 1, triage-4.md D2/D6, review-state-path-4.md F1 |
| `rail-pick-during-initial-handoff` | folded in; close when this run ships |
| `rail-content-mismatch-rapid-back` | idea in the same area; retest after this ships |
