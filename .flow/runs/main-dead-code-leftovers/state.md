---
slug: main-dead-code-leftovers
title: "Remove the uncalled MainVM search/settings handlers and key the Main onAction remember"
stage: done
size: S
tracks: []
kind: refactor
branch: prakunin/main-dead-code-leftovers
worktree:
base: 55bf932d
spec:
plan:
rounds: {verify: 2, review: 2, judge: 0}
blocked_by: []
source: upstream-sync-triage
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run main-dead-code-leftovers

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Include PagingColumn (PagingListState.kt:63), which the request believed gone? | no | answered | Yes; part of P2, no other run owns it |
| Q2 | If the banned box account keeps Main from loading, may AC-4 close on compile + unit tests and be recorded as not reachable? | no | answered | Yes; recorded as NOT CHECKED with the reason |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q2 as recommended; size S: brief ACs and Plan are the spec and plan; no test-designer (refactor, no runtime effect); Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship | owner (standing instruction: recommended) |

## Log
- 2026-11-26 created (stage idea) from the upstream (rovkinmax/Puber) re-check: upstream has no app-code commits after b755826c (17 new commits, all `.kent/` tooling), so the open items are the unmerged ports of upstream-sync-triage. Spec with the triage table: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`. Branch upstream-sync-triage is 31 commits ahead of its base and master is 376 ahead, so cherry-pick into a fresh worktree rather than rebase the branch.
- Rest of port P2 (triage T12). `PagingColumn` is already gone from master. Left: `MainVM.onSearchClick` / `onSettingsClick` have no callers (app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt:191,195); `remember { vm::onAction }` has no key (app/src/main/java/com/kino/puber/ui/feature/main/component/MainScreenComponent.kt:77), other screens use `remember(vm)`. Branch upstream-sync-triage did this in b5bd1cca. Device: compile + smoke.
- 2026-11-26 /task:run started (base 55bf932d); research
- 2026-11-26 research.md written: PagingColumn is still on master (PagingListState.kt:63, no callers) - the created-run note was wrong; recommended A: apply the code part of b5bd1cca. Analyst.
- 2026-11-26 brief.md: S refactor, 4 ACs; Q1-Q2 as recommended; gate 1 approved (standing instruction). Stage impl.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/main-dead-code-leftovers (prakunin/main-dead-code-leftovers); implementer
- 2026-11-26 impl done: 33b6ac16, 48772497, b91ab060; conductor check: 3 files, check.fast BUILD SUCCESSFUL in 1s, co-authored 0. Stage verify (TV box smoke).
- 2026-11-26 qa round 1: check.full green; AC-1..AC-4 PASS (box smoke: Main opens, resume, rail tab; content rows fail because of the ban). Review round 1 at c8799377.
- 2026-11-26 review round 1 at c8799377: blind 1 unproven (confirmed), codex 1 minor, tests/state-path/antigravity clean. triage-1.md: fix #1 (delete PagingListState.kt), #2 (evidence wording), reject #3.
- 2026-11-26 fix 2e3ef77c (triage-1 #1); implementer check.fast + check.full green. qa round 2 (evidence #2, re-smoke) and review round 2 on c8799377..2e3ef77c.
- 2026-11-26 qa round 2: AC-1..AC-4 PASS (cold launch reaches Main before HOME; evidence 2f78b8af). Review round 2: edge, codex, antigravity clean. triage-2.md: nothing to fix. Stage judge.
- 2026-11-26 judge-1 PASS (no manual items; scope widened by triage-1 #1 accepted). Gate 2: ship (standing instruction). Squash.
- 2026-11-26 squashed to 2 commits (d9ec531e app, e407c7f7 evidence); tree identical to 2f78b8af; co-authored 0. Next: /task:ship main-dead-code-leftovers (owner).
- 2026-11-26 shipped: merged prakunin/main-dead-code-leftovers into master as 933471c1 (--no-ff); Orca worktree removed.
