# Research: main-dead-code-leftovers

## 1. Request

> remove the uncalled MainVM.onSearchClick / onSettingsClick (app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt:191,195) and key the unkeyed remember { vm::onAction } in MainScreenComponent.kt:77 as other screens do (remember(vm)). Rest of port P2 of upstream-sync-triage (theme T12); PagingColumn is already gone. Branch upstream-sync-triage did this in b5bd1cca (local branch). Check callers (incl. tests, baseline profiles, androidTest), whether anything else in Main became dead, and whether the existing dead-code-sweep run (.flow/runs/dead-code-sweep) overlaps.

## 2. Understanding

Delete two public `MainVM` methods that nothing calls, and give the Main screen's action-lambda `remember` the `vm` key every other screen uses. The premise "PagingColumn is already gone" is wrong: `PagingColumn` is still on master with no callers, so the full P2 scope (all three edits of b5bd1cca) is still open. No runtime behaviour changes.

## 3. Current state in code

- `app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt:191` `onSearchClick()` and `:195` `onSettingsClick()` navigate via `router.screens.search()` / `deviceSettings()`. No caller in `app/src/main`, `app/src/test`, `app/src/androidTest`, `baselineprofile`; no line in `app/src/main/generated/baselineProfiles/baseline-prof.txt` (MainVM entries at :35547-35568 list neither). Other `onSettingsClick` hits are the unrelated player parameter (`ui/feature/player/component/PlayerControlsOverlay.kt:43`, `PlayerButtonRow.kt:223`, `PlayerOverlayLayers.kt:87`).
- After deletion `router` stays used (`MainVM.kt:36` super call, `:38` `TabAppRouterHolder(router.screens)`); no import in `MainVM.kt` serves only the two methods. `Screens.search()` / `deviceSettings()` keep callers in `ui/feature/main/model/MainUIMapper.kt` (`tabScreen`).
- `app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:77` `remember { vm::onAction }`; the lambda goes to `LifecycleAction` (:80) and `DrawerMainContent` (:85). Convention `remember(vm) { vm::onAction }`: `docs/architecture/ui-and-compose.md:63`, `docs/architecture/screens-and-di.md:26`, and 13 screens (e.g. `ui/feature/home/component/HomeScreen.kt:55`, `ui/feature/root/component/App.kt:138`).
- `core/ui/uikit/component/LifecycleAction.kt:17` keys its `DisposableEffect` on `lifecycleOwner` only, so the observer keeps the first `onAction` it saw whatever the `remember` key.
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/PagingListState.kt:63` `PagingColumn` still exists (file 123 lines; imports at :3-13 serve only it, except `Immutable`). No caller anywhere, no baseline-profile line. `LoadMoreHandler` (`ui/feature/contentlist/content/SectionRow.kt:224`) and `ListItemError` (`ui/feature/history/component/HistoryScreenContent.kt:348`) have other callers and stay; `PagingListState` / `PagingListPageError` stay.
- Rest of Main checked, nothing else dead: `MainAction.RefreshTab` / `Resumed`, `MainUIMapper.buildViewState` / `updateSelectedTab` / `buildTabContent`, `ExitConfirmation`, `DrawerMainContent` (also used by `app/src/androidTest/.../main/component/MainHostHarness.kt:466`) all have callers. `MainSideMenuPreview.kt` is a `@Preview` file.
- Tests around it: `app/src/test/kotlin/com/kino/puber/ui/feature/main/vm/MainVM{RefreshLifecycle,WatchStateSync,StartupSlots,DeviceInfo}Test.kt`, `main/model/MainUIMapperTest.kt`, `main/component/ExitConfirmationTest.kt`; androidTest `MainSideMenuFocusTraversalTest.kt`, `TabHostTelemetryWiringTest.kt`. None references the removed code.
- History: both `onSearchClick` and the unkeyed `remember` date from `88cdf071` (Release 1.0.0); `PagingListState.kt` last touched there too.

## 4. Related work

| Item | Relation |
|---|---|
| `upstream-sync-triage` (run + local branch, tip 43b9538e) | Source. Spec T12 row and P2 row, D5, AC-8, AC-9, D-1/D-2: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`. State log 2026-11-26 moved P2 rest here. |
| `b5bd1cca` (on that branch, not on master) | Does all three edits: deletes `PagingColumn` + imports, the two MainVM methods, keys the `remember`. Also edits `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md`, which does not exist on master, so a cherry-pick conflicts on that file; the code hunks apply by content (MainVM lines moved 169 -> 191). |
| `dead-code-sweep` (stage idea) | No overlap. Scope is audit BP-1, BP-7, STR-4 (`docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:118,124,49`): dead `KinoPubApiClient` endpoints and models, `Json` config, docs; `ContentFilter` excluded since 2026-11-24. Audit line 29 notes `upstream-sync-triage` "already deletes PagingColumn", i.e. the audit left it to this port. |
| `diagnostics-transport-label` | Sibling P3 port, merged (11fc5bed); same cherry-pick pattern. |

Duplicate: none.

## 5. External findings

External findings: off (local research only)

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Apply the code part of b5bd1cca: delete `PagingColumn` and its imports, the two MainVM methods, key `remember(vm)`. Cherry-pick with `--no-commit` and drop the plan-doc hunk, or redo by hand. | Closes P2 / AC-8 completely; three files, no contract change. | S |
| B | Only the request as worded (MainVM methods + `remember(vm)`). | Leaves `PagingColumn` dead with no run owning it (audit deferred it to this port). | S |
| C | A plus making `LifecycleAction` pick up a changed `onAction` (`rememberUpdatedState`). | Fixes the real staleness path the key alone does not; touches a shared component used by other screens, beyond port scope. | S |

## 7. Risks

- Low. Deleted code has no callers in any source set (R12 of the triage spec). `remember(vm)` changes nothing at runtime while `vm` is stable inside its `DIScope`; the Resumed observer keeps its first lambda regardless (`LifecycleAction.kt:17`).
- Baseline profile: nothing to regenerate (no profile line names the removed members).

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Include `PagingColumn` (`PagingListState.kt:63`), which the request believed gone? | Yes (option A): it is part of P2/AC-8 and nothing else owns it. |
| Q2 | Device verification: needed? | Compile + unit tests + detekt; one smoke on the TV box (Main opens, background/resume, rail tab pick) per AGENTS.md since screen wiring is touched. |

## 9. Sources

- Files: `MainVM.kt`, `MainScreenComponent.kt`, `MainAction.kt`, `MainViewState.kt`, `MainUIMapper.kt`, `ExitConfirmation.kt`, `PagingListState.kt`, `LifecycleAction.kt`, `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `docs/architecture/ui-and-compose.md`, `docs/architecture/screens-and-di.md`, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`.
- Commits: b5bd1cca, 88cdf071, 43b9538e (branch tip), base 55bf932d.
- Runs: `.flow/runs/main-dead-code-leftovers`, `.flow/runs/upstream-sync-triage`, `.flow/runs/dead-code-sweep`.
- Spec: `upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` (T12, P2, D5, AC-8, AC-9, D-1, D-2, R12).
