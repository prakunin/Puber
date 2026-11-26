---
slug: diagnostics-transport-label
title: "Show the connection transport (Ethernet / Wi-Fi / cellular) on the network diagnostics screen"
stage: done
size: M
tracks: [ui]
kind: improvement
branch: prakunin/diagnostics-transport-label
worktree:
base: 4c398e07
spec: docs/superpowers/specs/2026-11-26-diagnostics-transport-label.md
plan: docs/superpowers/plans/2026-11-26-diagnostics-transport-label.md
rounds: {verify: 3, review: 2, judge: 0}
blocked_by: []
source: upstream-sync-triage
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run diagnostics-transport-label

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Show "other" and "none" as two outcomes or one Unknown? | yes | answered | Two: Other and None |
| Q2 | Label wording ru/en? | no | answered | "Подключение: %1$s" / "Connection: %1$s"; Ethernet, Wi-Fi, Мобильная сеть / Cellular, Другое / Other, Нет подключения / No connection |
| Q3 | Check a second transport on a device? | no | answered | No; TV box on Ethernet only, unit tests cover the mapping |
| Q4 | Should "Нет подключения" stand out (for example red)? (ux-designer) | no | answered | No; same muted colour for every value |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q3 as recommended; codex and antigravity on every review round; caps lifted; device evidence on the TV box only | owner (standing instruction: recommended) |
| 2026-11-26 | Q4 as recommended; Gate 1: spec approved (M, ui, 8 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship, with AC-7 device part (M1, M2) and AC-8 (M3) NOT CHECKED (box account banned); owner checks them after the ban is lifted | owner |

## Log
- 2026-11-26 created (stage idea) from the upstream (rovkinmax/Puber) re-check: upstream has no app-code commits after b755826c (17 new commits, all `.kent/` tooling), so the open items are the unmerged ports of upstream-sync-triage. Spec with the triage table: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`. Branch upstream-sync-triage is 31 commits ahead of its base and master is 376 ahead, so cherry-pick into a fresh worktree rather than rebase the branch.
- Port P3 (triage T9). Upstream source: `ConnectionTransportProvider` (activeNetwork + NetworkCapabilities.hasTransport mapper) with its unit test, from upstream commits dfd94862..569a1589. Already ported on branch upstream-sync-triage: 3209128b (provider), f037c052 (label on the screen). Open from that run: label wording, waited for the owner on the D-3 screenshots. Device: yes (look at the screen).
- 2026-11-26 /task:run started (base 4c398e07); research
- 2026-11-26 research.md written (recommended B: apply app/ paths of 3209128b + f037c052, split Unknown into Other and None); analyst
- 2026-11-26 brief.md written: size M, track ui, 8 ACs; Q1-Q3 taken as recommended. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/diagnostics-transport-label (prakunin/diagnostics-transport-label); architect design + ux-designer
- 2026-11-26 design-architect.md (11 decisions, 7 risks) and design-ux.md (copies the subtitle line style; no stand needed; recommends muted colour for every value) written; test-designer
- 2026-11-26 test-design.md (P0 13, P1 5, P2 1, manual 3); spec assembled and committed; gate 1 approved (standing instruction). Stage plan.
- 2026-11-26 plan written (3 tasks); readiness
- 2026-11-26 readiness-1 NOT READY: AC-7 device row is P0 without red/guard mark or named defect; test-designer fixes the row
- 2026-11-26 readiness-2 NOT READY (conductor splice duplicated spec sections), spec restored to 57c2287a with only the AC-7 row changed; readiness-3 READY, pasted into the plan. Stage impl.
- 2026-11-26 impl done: 7cc2c53f, 51cf2be6, 1ddd9fc4; conductor check: 14 files, check.fast green, named tests 0 missing, co-authored 0. Stage verify (TV box).
- 2026-11-26 qa round 1 on the TV box: check.full green; AC-1..AC-6 PASS; AC-7 NOT CHECKED (itest transportLine_isShownAndNotFocused_whenEthernet fails: assertIsNotFocused on a non-focusable Text; M1/M2 unavailable: Settings returns Unauthorized: user_banned, the 2026-11-25 ban still stands); AC-8 NOT CHECKED (M3). serverCard_startsThatServerOnPress also fails on base (pre-existing). Evidence b29ca6c8. Implementer fixes defect 1.
- 2026-11-26 fix 03265fbc (itest assertion). qa round 2 (itest) and review round 1 at 03265fbc in parallel.
- 2026-11-26 qa round 2: itest transportLine test now passes; serverCard_startsThatServerOnPress fails on base too (deferred); M1/M2 still blocked by the ban. Review round 1 at 03265fbc: tests 1 major + 1 minor, codex 1 minor, blind/edge/state-path/antigravity/ux clean. triage-1.md: fix #1-#3, reject #4-#7, defer #8 -> diagnostics-server-card-itest-red. Implementer fix round.
- 2026-11-26 fix round 1: 32682fc3, 7f81684c, 58773294 (triage-1 #1-#3); check.fast BUILD SUCCESSFUL in 2s; implementer check.full green. Review round 2 on 03265fbc..58773294 + qa round 3 (unit, no device).
- 2026-11-26 review round 2: edge, tests, state-path, codex, antigravity clean; qa round 3 check.full green. triage-2.md: nothing to fix. Stage judge.
- 2026-11-26 judge-1 PASS (manual: AC-7 device part M1/M2, AC-8 M3; screen unreachable, box account Unauthorized: user_banned). Gate 2 asked of the owner (AGENTS.md: device unavailable -> say so and ask), not taken by the standing instruction.
- 2026-11-26 gate 2: ship (owner, explicit). Squash before ship.
- 2026-11-26 squashed to 3 commits (12ca5349, 7f7fe326, 229283b5); tree identical to 58773294; co-authored 0. Next: /task:ship diagnostics-transport-label (owner).
- 2026-11-26 shipped: merged prakunin/diagnostics-transport-label into master as 11fc5bed (--no-ff); Orca worktree removed.
