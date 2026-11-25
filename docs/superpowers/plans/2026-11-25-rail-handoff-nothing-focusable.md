# A handoff kind for the side rail - plan

**Spec:** `docs/superpowers/specs/2026-11-25-rail-handoff-nothing-focusable.md`
**Goal:** Give every rail handoff a kind (`Initial`, `TabPick`, `Dismiss`) so an armed Back during a Dismiss leaves the app, Dismiss and Initial fail `Closed`, and a tab pick during the cold-start handoff restarts it as a TabPick with a new id.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build).
- androidTest compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`. Unit tests of a task: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- Instrumented (qa-device only, under a lease, from the worktree): `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>[,<fqcn>|#method]` (`Makefile:23` defaults `GRADLE` to `./gradlew`; `TESTS` feeds the runner's `class` argument, `Makefile:129-137`).
- Worktree `/home/dev/orca/workspaces/Puber/rail-handoff-nothing-focusable`; never `./gradlew` there.
- One task = green fast gate + androidTest compile + green tests of the touched code + one commit; message one imperative English sentence, no prefixes, no `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec (test steps: spec "Test design" > Traceability, Harness seams), and the files cited below.
- Paths without a prefix are under `app/src/main/java/com/kino/puber/`; `androidTest/` is `app/src/androidTest/kotlin/com/kino/puber/`; `test/` is `app/src/test/kotlin/com/kino/puber/`. Lines are at base c9e98129.
- The implementer has no device. Instrumented and manual runs are qa-device's (Device work).
- Out of scope: retry focus of id-latching readers after a failed handoff (`ui/feature/contentlist/content/BrowseAllEntry.kt:50`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:81`, `ui/feature/showall/content/ShowAllScreenContent.kt:349`); any visual change; `railBackOwner` (`core/ui/navigation/component/RailBackOwner.kt:25-30`) stays as is.
- Generated baseline profiles are not edited (design Risk 6). Stale after Task 2, until the next `tools/generate-baseline-profile.sh` run: `app/src/main/generated/baselineProfiles/baseline-prof.txt:29811` `beginHandoff(Z)`, `:29819` `setHandoffExpectsNewContent(Z)V`, `:29822` `startHandoff(Z)J`; `startup-prof.txt:18854` `setHandoffExpectsNewContent(Z)V`, `:18857` `startHandoff(Z)J`.

---

## Tasks

### Task 1: Add the harness seams and the red instrumented tests

Compiles against base production code; no production file changes. qa-device runs the red run (Device work R) on this commit, before Task 2.

**Covers:** AC-1 to AC-9 (instrumented rows), R6
**Files:**
- Modify: `androidTest/ui/feature/main/component/MainHostHarness.kt` - seams below.
- Create: `androidTest/ui/feature/main/component/MainRailHandoffKindTest.kt` - class `MainRailHandoffKindTest`, set up as `MainRailBackTest` (`androidTest/ui/feature/main/component/MainRailBackTest.kt:21-32`: `createMainHostRule()`, `MainHostHarness(rule)`, `@Before setUp`, `@After tearDown`). Must not reference `HandoffKind` or any API Task 2 adds.

Harness seams (spec Test design > Harness seams):

| Seam | Change in `MainHostHarness.kt` |
|---|---|
| `ProbeState` (`:96-120`) | add `selectedTab: TabType by mutableStateOf(TabType.Movies)`, `railPicks = AtomicInteger()`, `@Volatile exitNowMillis = 0L`, `loadingFollowsPick = false`, `rootNamesByType: Map<TabType, String>`; `reset()` (`:111-119`) restores each default |
| exit clock (`:122-126`) | `ExitConfirmation(nowMillis = { ProbeState.exitNowMillis })`; KDoc: the clock stands still unless a test advances it |
| `setUp` (`:149-161`) | `rootNamesByType` = Movies "first", History "other", Series "third", beside `tabsByType` (`:156-160`); `PuberTab.screen` is private (`core/ui/navigation/PuberTab.kt:19`) |
| `startMain` (`:175-202`) | new parameter `loadingFollowsPick: Boolean = false`, stored in `ProbeState` |
| `ProbeMainHostScreen` state (`:400-407`) | tabs built from `ProbeState.selectedTab` (`isSelected = type == ProbeState.selectedTab`, `selectedTab = ProbeState.selectedTab`), same order and icons |
| `ProbeMainHostScreen` `onAction` (`:408-414`) | on `ItemSelected`: `railPicks.incrementAndGet()`; `selectedTab = type`; when `loadingFollowsPick`, `loadingRoot = rootNamesByType.getValue(type)`; then `openTab` (MainVM order, `ui/feature/main/vm/MainVM.kt:131-135`) |
| new members of `MainHostHarness` | `val railPicks: Int`; `fun advanceExitClock(ms: Long)` (adds to `exitNowMillis`); `fun setLoadingRoot(name: String?)` (`runOnUiThread`, then `waitForIdle`); `fun focusSelectedRailItem()` (`rule.onNode(isSelectable() and isSelected()).requestFocus()`, then `waitForIdle`; matcher as `railItemFocused`, `:346`) |

Tests in `MainRailHandoffKindTest` (steps, fixture and Proves: spec Test design > Traceability):

| Test | AC | Base |
|---|---|---|
| `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` | AC-1 | red |
| `backDuringDismissHandoffAfterArmExpired_isSwallowed` | AC-1 | guard |
| `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt` | AC-2 | red |
| `failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail` | AC-2 | red |
| `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` | AC-3 | red |
| `pickDuringInitialHandoff_switchesToPickedTab` | AC-4 | red |
| `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` | AC-4 | red |
| `centerOnSelectedTabDuringInitialHandoff_switchesNothing` | AC-5 | guard |
| `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` | AC-6 | red |
| `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches` | AC-7 | guard |
| `tabPickIntoNeverFocusableTab_failedHandoffReopensRail` | AC-8 | guard |
| `rePickOfSelectedTab_closesRailOntoContent` | AC-9 | guard |
| `rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed` | R6 | red |

**Consumes:** existing harness API (`startMain`, `back`, `press`, `openRailFrom`, `pickRailTabBelow`, `sendLeftThroughInputPipeline`, `awaitDrawer`, `assertDrawer`, `awaitRoot`, `assertRootAbsent`, `awaitFocused`, `assertNothingFocused`, `awaitRailItemFocused`, `assertNoRailItemFocused`, `startSampling`, `sampledDrawerValues`, `isFinishing`, `isExitArmed`; `:175-385`).
**Produces:** the seams above; `MainRailHandoffKindTest`.

- [x] Add the seams; existing harness users (`MainRailBackTest`, `MainTabPushRailFocusTest`) need no edit: defaults keep Movies selected, the clock at 0, `loadingFollowsPick` off
- [x] Write the tests in the table
- [x] Run the fast gate and the androidTest compile; commit

**Done when:** both compiles are green with `git diff c9e98129 --stat -- app/src/main` empty; Device work R records every `red` row failing at its Proves assertion and every `guard` row passing.

---

### Task 2: Give DrawerState a handoff kind with per-kind restart and fail outcome

**Covers:** AC-2, AC-3, AC-6, AC-8, AC-12, R6; part of AC-4 (restart)
**Files:**
- Modify: `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`
  - after `DrawerValue` (`:62-75`): `enum class HandoffKind { Initial, TabPick, Dismiss }`, each value with a one-line KDoc (cold start or dialog re-entry; rail pick of another tab; Back, Right or re-pick of the selected tab).
  - `handoffExpectsNewContent` (`:107-117`): replace the stored field with `var handoffKind: HandoffKind? by mutableStateOf(null)` (private set) and `val handoffExpectsNewContent: Boolean get() = handoffKind == HandoffKind.TabPick`; keep the KDoc on the derived property.
  - `beginHandoff` (`:128-139`): `fun beginHandoff(kind: HandoffKind): Long?`; `require(kind != HandoffKind.Initial)`; `Open` starts `kind`; `HandingOff` starts a new `TabPick` only when `kind == TabPick && handoffKind != Dismiss` (old id superseded); else `null`. KDoc states the restart and why (outgoing tab, `ContentFocusHandoff.kt:108,114-118`).
  - `beginInitialHandoff` (`:141-145`): `startHandoff(HandoffKind.Initial)`.
  - `startHandoff` (`:147-153`): takes `kind`, writes `handoffKind` with `pendingHandoffId`.
  - `settleHandoff` (`:162-167`): also clears `handoffKind`.
  - `failHandoff` (`:169-177`): `Open` only when `reopen && handoffKind == HandoffKind.TabPick`, else `Closed`; clears `handoffKind`; KDoc per kind.
  - Right key (`:292`): `beginHandoff(HandoffKind.Dismiss)`.
- Modify: `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:54-55` - KDoc of `closeOnFail`: read only for a failed TabPick; Dismiss and Initial always fail `Closed`. Code unchanged.
- Modify: `ui/feature/main/component/MainScreenComponent.kt`
  - `:223`: `beginHandoff(HandoffKind.Dismiss)`.
  - `:327-329`: `beginHandoff(if (tab.isSelected) HandoffKind.Dismiss else HandoffKind.TabPick)`; comment: a re-pick composes no new content, so it dismisses the rail over the tab on screen.
  - `:104-105` comment: Dismiss and Initial fail `Closed` at any depth; `closeOnFail` decides only a failed TabPick.
- Modify: `androidTest/ui/feature/main/component/MainSideMenuFocusTraversalTest.kt:150` - `beginHandoff(HandoffKind.Dismiss)` (compile fix).
- Test: `test/core/ui/uikit/component/drawer/DrawerStateTest.kt` (JUnit 5; `@ParameterizedTest @EnumSource` from `junit-jupiter`, as in `test/data/cache/CachedFeedTest.kt`).

DrawerStateTest:

| Case | Change |
|---|---|
| `reveal is ignored while handing off` (`:20-28`), `begin handoff moves an open rail to handing off` (`:30-38`), `begin handoff is refused unless the rail is open` (`:40-46`), `settling a superseded handoff is ignored` (`:69-80`), `handing off is persisted as closed` (`:122-128`) | callers move to `beginHandoff(HandoffKind.TabPick)` |
| `settling the active handoff closes the rail` (`:58-67`) | `@ParameterizedTest @EnumSource(HandoffKind::class)`; Initial via `beginInitialHandoff` from `Closed`; asserts `handoffKind` null |
| `a failed handoff returns the rail to open` (`:82-91`) | renamed `a failed tab pick handoff reopens the rail` |
| `a failed handoff that must not reopen leaves the rail closed` (`:93-102`) | renamed `a failed tab pick handoff that must not reopen leaves the rail closed`; TabPick |
| `a handoff records whether it waits on content that does not exist yet` (`:104-111`), `dismissing the rail waits on the content already on screen` (`:113-120`) | replaced by `only a tab pick handoff waits on new content` (`@EnumSource`) |
| `a failed dismiss handoff leaves the rail closed even when reopening is allowed` | new |
| `a failed initial handoff leaves the rail closed even when reopening is allowed` | new |
| `a tab pick during the initial handoff restarts it as a tab pick with a new id` | new; asserts `handoffKind == TabPick`, `handoffExpectsNewContent` |
| `the superseded initial id can neither settle nor fail the tab pick` | new |
| `a tab pick during a tab pick handoff restarts it with a new id` | new |
| `a tab pick during a dismiss handoff is refused` | new; returns null, kind stays Dismiss |
| `a dismiss is refused unless the rail is open` | new; from `Closed` and from a pending handoff |
| `an initial handoff cannot be begun as a rail handoff` | new; `assertThrows<IllegalArgumentException>` |
| `a failed handoff clears its kind` | new |

**Consumes:** nothing from Task 1.
**Produces:** `HandoffKind`; `DrawerState.handoffKind: HandoffKind?`; `DrawerState.beginHandoff(kind: HandoffKind): Long?`; derived `handoffExpectsNewContent`.

- [x] Write the new DrawerStateTest cases first; with Task 2 production absent they do not compile (spec: `red (M#)`, proven by mutation runs M4, M9)
- [x] Change `DrawerState`, the three callers and the androidTest compile fix
- [x] Run the fast gate, the androidTest compile, `--tests 'com.kino.puber.core.ui.uikit.component.drawer.DrawerStateTest'`; commit

**Done when:** DrawerStateTest green; `grep -rn "expectsNewContent =" app/src/main app/src/test app/src/androidTest` finds nothing; both compiles green.

---

### Task 3: Accept picks by kind and let an armed Back leave during a Dismiss handoff

**Covers:** AC-1, AC-4, AC-5, AC-7, AC-9
**Files:**
- Modify: `ui/feature/main/component/MainScreenComponent.kt`
  - `acceptsPickOf` (`:298-308`): `HandingOff -> handoffKind != HandoffKind.Dismiss && !tab.isSelected`; `Open`, `Closed` unchanged; KDoc: Dismiss refuses (the user cannot see the rail), Initial and TabPick accept another tab and restart the handoff (`beginHandoff`, Task 2).
  - `RailOpenBackHandler` (`:206-231`): resolve `activity` as `RailClosedBackHandler` does (`:192-193`, `findActivity` `:243-247`). When `currentValue != Open`: if `handoffKind == HandoffKind.Dismiss && exitConfirmation.isArmed`, `activity?.finish()` then `exitConfirmation.disarm()`; return in every case. Replace the comment at `:219-220`: an armed Dismiss is the second Back at the same root (arming `:226-229`, disarms `:111-116`, `:238-241`); every other HandingOff Back is swallowed so it cannot pop the screen arriving underneath. KDoc at `:206` names the exit.

**Consumes:** `DrawerState.handoffKind`, `HandoffKind` (Task 2); `ExitConfirmation.isArmed`/`disarm` (`ui/feature/main/component/ExitConfirmation.kt:34-46`).
**Produces:** nothing new.

- [x] Change `acceptsPickOf` and `RailOpenBackHandler`
- [x] Run the fast gate, the androidTest compile and the full gate; commit

**Done when:** full gate green; Device work G and M settle the instrumented rows.

---

### Task 4: Point the prior spec at the rows this run changes

**Covers:** spec Design "Prior spec ... rows this run changes"
**Files:**
- Modify: `docs/superpowers/specs/2026-11-25-showall-rail-back-exits-app.md` - one line after `Tracks: none` (`:8`): `Amended by \`2026-11-25-rail-handoff-nothing-focusable.md\` (Design, prior rows): Decisions A5 (:98), Alternatives A5 (:145), Risk 1 check (:153), Risk 4 (:156), Open question 1 (:167), manual M11 step 5 (:292).`

`docs/superpowers/specs/2026-08-16-side-rail-focus-handoff-design.md:216-217` (empty tab after a pick reopens the rail) stays: TabPick keeps it (spec Q3).

- [x] Add the line; commit

**Done when:** `git diff HEAD~1 --stat` shows that one file, one line.

---

## Device work

qa-device, box `192.168.1.110:5555` under a lease, package `com.kino.puber.stage` only. `itest` installs the worktree build, so D0 precedes R. Evidence: `docs/superpowers/evidence/2026-11-25-rail-handoff-nothing-focusable-device.md`, audited with `tools/mobile-evidence-audit.sh`. Fqcn prefix `com.kino.puber.`.

| # | After | Run | Expected | On failure |
|---|---|---|---|---|
| D0 | before R | master build installed with `tools/android-apk-install-preserve`, launch, `make DEVICE=192.168.1.110:5555 auth-save` (spec Manual device checks D0) | app opens on Home, not pairing | pairing: stop and ask the owner |
| R | Task 1 commit (production at base) | `TESTS=com.kino.puber.ui.feature.main.component.MainRailHandoffKindTest` | `red` rows fail at their Proves assertion, not in the fixture; `guard` rows pass | a red row passes or fails in the fixture: back to Task 1, R repeats |
| G | Task 3 commit | `ui.feature.main.component.MainRailHandoffKindTest`, `ui.feature.main.component.MainRailBackTest`, `ui.feature.main.component.MainTabPushRailFocusTest`, `ui.feature.main.component.MainSideMenuFocusTraversalTest`, `core.ui.uikit.component.drawer.InitialDrawerContentFocusTest`, `ui.feature.showall.content.ShowAllRailCloseFocusTest` | all pass | back to the task owning the file |
| M | G green | spec Mutation runs M1, M3-M12, each applied in the worktree, run, reverted with `git checkout HEAD -- <file>`; unit parts with `--tests 'com.kino.puber.core.ui.uikit.component.drawer.DrawerStateTest'`; M8 with `MainRailBackTest#centerOnCollapsedRail_duringHandoff_doesNotSwitchTab`; M9 instrumented row 3 times | every listed test fails under its mutation; `git status` clean after each | a surviving mutation: back to the task that owns the code, strengthen the test |
| D1 | G green | spec D1 on the worktree dev APK (`tools/android-apk-install-preserve`) | AC-10 | blocks gate 2 |
| D2 | G green | spec D2 | AC-11 | blocks gate 2 |
| D3 | G green | spec D3, only if Favourites is visible and empty; else record `not run: Favourites not empty` | AC-1 on the real app | blocks gate 2 |

## Coverage

| AC | Task | Tests |
|---|---|---|
| AC-1 | 1, 3 | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff`, `backDuringDismissHandoffAfterArmExpired_isSwallowed`; M1, M3; D3 |
| AC-2 | 1, 2 | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`, `failedBackHandoffAtNeverFocusableRoot_expiredArmBackReopensRail`; M4, M12 |
| AC-3 | 1, 2 | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed`; M4, M11 |
| AC-4 | 1, 2, 3 | `pickDuringInitialHandoff_switchesToPickedTab`, `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`; M5, M6, M9 |
| AC-5 | 1, 3 | `centerOnSelectedTabDuringInitialHandoff_switchesNothing`; M7 |
| AC-6 | 1, 2 | `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus`; M4 |
| AC-7 | 1, 3 | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches`; M7 |
| AC-8 | 1, 2 | `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`, `MainSideMenuFocusTraversalTest.selectingATabWhoseContentNeverTakesFocus_reopensTheRail`; M5 |
| AC-9 | 1, 2, 3 | G classes, `rePickOfSelectedTab_closesRailOntoContent`; M8, M10 |
| AC-10 | - | D1 |
| AC-11 | - | D2 |
| AC-12 | 2, 3 | DrawerStateTest cases of Task 2; full gate |
| R6 | 1, 2 | `rePickOfSelectedTabAtNeverFocusableRoot_failedHandoffLeavesRailClosed` |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Commit |
|---|---|
| 1 | `dfca2744` Add the handoff kind harness seams and the instrumented rail handoff tests |
| 2 | `b3ed05c5` Give DrawerState a handoff kind that decides restart and fail outcome |
| 3 | `ad0cc5bf` Accept rail picks by handoff kind and let an armed Back leave during a dismiss handoff |
| 4 | `b539a6cb` Point the show-all rail spec at the rows the handoff kind run amends |
| Review 1, E2 | `60705da9` Say in the tab push rail test that a failed Dismiss also ends Closed with focus cleared |
| Review 1, E3 | `b163ed42` Hold the tab swap in the outgoing-tab test until the outgoing root holds focus and has had time to settle |

**Red run** (P0 `red` tests on the unchanged code):

- Instrumented `red` rows of `MainRailHandoffKindTest`: done by qa at `dfca2744`, every red row failed at its named assertion: `docs/superpowers/evidence/2026-11-25-rail-handoff-nothing-focusable-device.md` "Red run R".
- Unit `red (M#)` rows, mutation runs on Task 2 code (`--tests '...DrawerStateTest'`, 25 cases):

| Mutation | Failing cases, failure line |
|---|---|
| M4 | `a failed dismiss handoff leaves the rail closed even when reopening is allowed`, `a failed initial handoff leaves the rail closed even when reopening is allowed` - expected: <Closed> but was: <Open> |
| M5 | `a failed tab pick handoff reopens the rail` - expected: <Open> but was: <Closed>; `settling a superseded handoff is ignored` - Required value was null |
| M9 (null outside Open) | `a tab pick during the initial handoff restarts it as a tab pick with a new id` - expected: not <null>; `the superseded initial id can neither settle nor fail the tab pick`, `a tab pick during a tab pick handoff restarts it with a new id` - Required value was null |
| M9 (same id) | `a tab pick during the initial handoff restarts ...`, `a tab pick during a tab pick handoff restarts ...` - expected: not equal but was: <1>; `the superseded initial id ...` - expected: <HandingOff> but was: <Closed> |

**Mutation edits for qa** (lines at `b539a6cb`; `MSC` = `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt`, `MND` = `app/src/main/java/com/kino/puber/core/ui/uikit/component/drawer/ModalNavigationDrawer.kt`):

| # | File:line | Edit |
|---|---|---|
| M1 | `MSC:231-234` | delete the `if (drawerState.handoffKind == HandoffKind.Dismiss && exitConfirmation.isArmed) { ... }` block, keep `return@BackHandler` |
| M3 | `MSC:231` | condition becomes `drawerState.handoffKind == HandoffKind.Dismiss` |
| M4 | `MND:207` | `val reopenRail = reopen` |
| M5 | `MND:207` | `val reopenRail = false` |
| M6 | `MSC:322` | `DrawerValue.HandingOff -> handoffExpectsNewContent && !tab.isSelected` |
| M7 | `MSC:322` | `DrawerValue.HandingOff -> handoffKind != HandoffKind.Dismiss` |
| M8 | `MSC:322` | `DrawerValue.HandingOff -> !tab.isSelected` |
| M9 null | `MND:163` | the `HandingOff` branch becomes `null` |
| M9 same id | `MND:163` | `if (kind == HandoffKind.TabPick && handoffKind != HandoffKind.Dismiss) { handoffKind = kind; pendingHandoffId } else null` |
| M10 | `MSC:345` | `drawerState.beginHandoff(HandoffKind.TabPick)` |
| M11 | `MND:326` | `DrawerValue.Open -> drawerState.beginHandoff(HandoffKind.TabPick) != null` |
| M12 | `MSC:238` | `drawerState.beginHandoff(HandoffKind.TabPick)` |

**Review round 1 fixes** (`.flow/runs/rail-handoff-nothing-focusable/triage-1.md`):

| Finding | Fix |
|---|---|
| E2 | `MainTabPushRailFocusTest.kt:93-95`: the comment says a failed Dismiss also ends Closed with focus cleared, so the focused child card proves the landing |
| E3 | harness seam `startMain(holdTabSwap = true)` keeps the pick's tab in `ProbeState.heldTab` until `releaseTabSwap()`; `pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle` waits for `ROOT_CARD_FIRST` focused on the outgoing root, asserts `other` absent, waits `OUTGOING_SETTLE_WINDOW_MILLIS` (1 s), asserts `HandingOff` (`:130`), then releases the swap; later assertions unchanged |

qa reruns M9 same id (edit unchanged, `MND:163`; compiles, checked and reverted) 3 times on `MainRailHandoffKindTest#pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle`. Exact edit: replace `if (kind == HandoffKind.TabPick && handoffKind != HandoffKind.Dismiss) startHandoff(kind) else null` with `if (kind == HandoffKind.TabPick && handoffKind != HandoffKind.Dismiss) { handoffKind = kind; pendingHandoffId } else null`. Expected failure: `MainRailHandoffKindTest.kt:130` `assertDrawer(HandingOff)`, expected HandingOff but was Closed (the outgoing root's first-id effect settles once its card holds focus).

**Deviations from the plan:**

| Where | Deviation | Why |
|---|---|---|
| `pickDuringInitialHandoff_*` | `assertEquals(1, host.railPicks)` right after Center, before `awaitRoot("other")` | base fails on an assertion, not on a 10 s wait timeout |
| red rows ending `Closed` | wait until the drawer leaves `HandingOff` (8 s), then `assertDrawer(Closed)`, instead of `awaitDrawer(Closed)` | same |
| sampling in `failedBackHandoff*`, `rightFromRail*`, `rePickOfSelectedTab_closesRailOntoContent` | starts after Back, Right or Center | sampling the fixture's `Open` rail would fail "no `Open` sampled" on every run |
| `a dismiss is refused unless the rail is open` | the pending case runs for all three kinds | covers Dismiss over Initial and TabPick, not one of them |
| Task 4 checkbox | ticked in the record commit | keeps the Task 4 commit to one file, one line |

**Files touched outside the plan:** none.

**For the reviewers:**

- R8: the `handoffKind == HandoffKind.Dismiss` part of the exit gate (`MSC:231`) has no test.
- `focusSelectedRailItem` during an Initial or TabPick handoff enters the rail while `HandingOff`, which calls `redirectFocusToContent` (`MND:339-340`); the tests rely on the Loading root having nothing focusable, so focus stays on the rail item. A red row failing before its Proves line on the box points here.
- Stale baseline-profile entries listed in Global constraints are left as planned.

**Blocked:** none.

## Readiness

# Readiness 1: rail-handoff-nothing-focusable

Verdict: READY

Checked against worktree HEAD 0550d0a5 (base c9e98129 + spec commit); plan read from the worktree.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1, 3; D3 | `backTwiceFromRailAtNeverFocusableRoot_exitsDuringHandoff` (red), `backDuringDismissHandoffAfterArmExpired_isSwallowed` |
| AC-2 | 1, 2 | `failedBackHandoffAtNeverFocusableRoot_railStaysClosed_leftReopensIt`, `..._expiredArmBackReopensRail` (red) |
| AC-3 | 1, 2 | `rightFromRailAtNeverFocusableRoot_failedHandoffLeavesRailClosed` (red) |
| AC-4 | 1, 2, 3 | `pickDuringInitialHandoff_switchesToPickedTab`, `..._restartsAsTabPick_outgoingTabCannotSettle` (red) |
| AC-5 | 1, 3 | `centerOnSelectedTabDuringInitialHandoff_switchesNothing` |
| AC-6 | 1, 2 | `coldStartIntoNeverFocusableTab_failedInitialHandoffLeavesRailClosed_thenContentTakesFocus` (red) |
| AC-7 | 1, 3 | `centerOnNowSelectedTabDuringTabPick_isRefused_thirdTabSwitches` |
| AC-8 | 1, 2 | `tabPickIntoNeverFocusableTab_failedHandoffReopensRail`, existing `selectingATabWhoseContentNeverTakesFocus_reopensTheRail` |
| AC-9 | 1, 2, 3; Device work G | five existing classes, `rePickOfSelectedTab_closesRailOntoContent` |
| AC-10 | Device work D1 | D1 |
| AC-11 | Device work D2 | D2 |
| AC-12 | 2, 3 | DrawerStateTest cases, `check.full` |

## Citations sampled (at c9e98129)

| Citation | Claimed | Found |
|---|---|---|
| `ModalNavigationDrawer.kt:136-139` | `beginHandoff` returns null outside Open | yes |
| `ModalNavigationDrawer.kt:142-145` | `beginInitialHandoff` sets `expectsNewContent = false` | yes |
| `ModalNavigationDrawer.kt:173-177` | `failHandoff(requestId, reopen)` | yes |
| `ModalNavigationDrawer.kt:292` | Right key `beginHandoff(expectsNewContent = false)` | yes |
| `MainScreenComponent.kt:221,223,226-229` | HandingOff swallow, Back handoff, arm at root | yes |
| `MainScreenComponent.kt:304-308,329` | `acceptsPickOf`, item click | yes |
| `MainScreenComponent.kt:107,124` | `closeOnFail = { tabStack.canPop }`, dialog re-entry | yes |
| `ContentFocusHandoff.kt:24,71,108,114-118` | 5 s budget, `reopen = !closeOnFail()`, outgoing-tab guard | yes |
| `RailBackOwner.kt:25-30`, `ExitConfirmation.kt:12,34-46` | HandingOff is RailOpen; 3 s window, `isArmed`/`disarm` | yes |
| `MainHostHarness.kt:102,126,402,406` | `loadingRoot`, frozen clock, static selection | yes |
| `MainSideMenuFocusTraversalTest.kt:150` | `beginHandoff(expectsNewContent = false)` | yes |
| `DrawerStateTest.kt` ranges in Task 2 | named cases | yes, each name at the cited line |
| `baseline-prof.txt:29811,29819,29822`, `startup-prof.txt:18854,18857` | stale `DrawerState` entries | yes |
| prior spec `:8,:98,:145,:153,:156,:167,:292` | rows Task 4 points at | yes |
| `Makefile:23,129-137` | `GRADLE ?= ./gradlew`, `TESTS` to runner `class` | yes |

## Build order

Task 1 touches only androidTest and compiles on base (no `HandoffKind` reference). Task 2 changes the `beginHandoff` signature together with every caller found by `grep -rn beginHandoff app/src` (`MainScreenComponent.kt:223,329`, `ModalNavigationDrawer.kt:292`, `MainSideMenuFocusTraversalTest.kt:150`, `DrawerStateTest`); no other module references it. Task 3 consumes Task 2's API; Task 4 is docs only.

## P0 rows

Every P0 row is `red` or `guard`, names the defect or mutation (M1, M3-M12) and the backticked test. AC-10 and AC-11 are device checks D1 and D2, with steps in Manual device checks.

## Scope and questions

The plan stays inside Out of scope (id-latching readers, visuals, `railBackOwner`). Q1 and Q2 (blocking) are answered; plan question 1 (prior spec `:292`) is answered and carried out in Task 4.

## Notes for the conductor

1. The plan file is untracked in the worktree (`?? docs/superpowers/plans/2026-11-25-rail-handoff-nothing-focusable.md`); commit it before Task 1.
2. R8 (the `handoffKind == Dismiss` part of the exit gate) has no test and relies on code review; reviewers should check it.
