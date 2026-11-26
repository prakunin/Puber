# Brief: main-dead-code-leftovers

Kind: refactor   Size: S   Tracks: none   Device verification: yes, smoke only (screen wiring line in `MainScreenComponent`; no runtime change expected)

## Request

Rest of port P2 of `upstream-sync-triage` (theme T12): remove the uncalled `MainVM.onSearchClick` / `onSettingsClick` and key the Main screen's `remember { vm::onAction }` with `vm`. The request's premise that `PagingColumn` is gone is wrong: it is still on master with no callers (`research.md: Understanding`), so scope is the full code part of `b5bd1cca` (research option A).

## Findings

Code map, related runs and options: `research.md: Current state in code`, `Related work`, `Options`. Re-checked at 55bf932d (paths under `app/src/main/java/com/kino/puber/`):

| Where | Fact |
|---|---|
| `ui/feature/main/vm/MainVM.kt:191-197` | `onSearchClick`, `onSettingsClick`; no caller in `app` (all source sets) or `baselineprofile`; `router` stays used (`:36`, `:38`) |
| `ui/feature/main/component/MainScreenComponent.kt:77` | `remember { vm::onAction }` without key; convention `remember(vm)` (`docs/architecture/ui-and-compose.md:63`, `ui/feature/home/component/HomeScreen.kt:55`) |
| `core/ui/uikit/component/PagingListState.kt:62-123` | `PagingColumn`, no caller; imports `:3-10`, `:12-13` serve only it; `PagingListState`, `PagingListPageError`, `Immutable` stay |
| `app/src/main/generated/baselineProfiles/baseline-prof.txt` | No line names any removed member |
| `.flow/runs/dead-code-sweep` | No overlap (`research.md: Related work`) |

## Size

S: one goal, three production files, no data shape or contract change. The `remember` key is a runtime no-op while `vm` is stable in its `DIScope`.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | the run's branch | `rg -n "PagingColumn\|fun onSearchClick\|fun onSettingsClick" app/src/main/java/com/kino/puber/ui/feature/main app/src/main/java/com/kino/puber/core app/src/test app/src/androidTest baselineprofile` | no match |
| AC-2 | the run's branch | `MainScreenComponent.kt` is read | the action lambda is `remember(vm) { vm::onAction }` |
| AC-3 | the run's worktree | `check.full` runs (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) | it passes with no new unused-import or unused-member finding |
| AC-4 | the worktree's dev APK install-preserved on the box `192.168.1.110:5555` (`com.kino.puber.stage`) | the app is launched to Main, sent to background with Home and resumed, and a rail tab is picked with the D-pad | Main is shown after resume, the picked tab opens, no crash in the app's own process |

## Risks

- AC-4 reachability: the box account is banned (`user_banned`), and a worktree build without a client secret can drop an expired session to pairing (memory: worktree build loses box session). If Main is not reachable, AC-4 is recorded as blocked and the owner decides; AC-1..AC-3 still hold.
- Cherry-picking `b5bd1cca` conflicts on `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md` (absent on master); take only the code hunks.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Include `PagingColumn` (`PagingListState.kt:63`), which the request thought already gone? | Yes: it is part of P2 / triage AC-8 and no other run owns it | no |
| Q2 | If the banned box account keeps Main from loading, may AC-4 close on compile + unit tests alone? | Yes, record the device check as not reachable in the QA report; the change has no runtime effect | no |

## Plan

1. `core/ui/uikit/component/PagingListState.kt`: delete `PagingColumn` (`:62-123`) and the imports only it uses (`:3-10`, `:12-13`); keep `Immutable` and the two data classes. Or `git cherry-pick --no-commit b5bd1cca` and drop the plan-doc hunk.
2. `ui/feature/main/vm/MainVM.kt:191-197`: delete `onSearchClick` and `onSettingsClick`.
3. `ui/feature/main/component/MainScreenComponent.kt:77`: `remember(vm) { vm::onAction }`.
4. `./tools/agentw :app:compileDevDebugKotlin`, then `check.full` (AC-3); AC-1 grep; AC-4 smoke on the box under a lease.
