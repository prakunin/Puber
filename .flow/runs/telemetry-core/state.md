---
slug: telemetry-core
title: "Session log core: bounded JSONL sink, Settings toggle with indicator, auto-off, share export, nav/ready/act/err events"
stage: done
size: M
tracks: [perf, ui]
kind: feature
branch: telemetry-core
worktree: /home/user1/orca/workspaces/Puber/telemetry-core
base: 39903d31
spec: docs/superpowers/specs/2026-10-15-telemetry-core.md
plan: docs/superpowers/plans/2026-10-15-telemetry-core.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
created: 2026-10-15
migrated:
updated: 2026-10-15
---

## Next
Done; merged as 3d6bd6dc. Follow-ups: telemetry-ready-data-states, telemetry-uptime-autooff, then the remaining series slices.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Design stand for the Settings change (no docs/design stand covers Settings; Developer section becomes visible in prod)? | yes | answered | No stand: reuse existing toggle/row styles, ux-designer reviews visually; indicator wording judged by the owner at release. |
| Q2 | Stable names under R8 (AC-8)? | no | answered | -keepnames for PuberScreen, UIAction, ViewState implementers plus a telemetry name accessor falling back to simpleName; PuberScreen.key unchanged. |
| Q3 | What does Share log include? | no | answered | Rotate current file, zip only closed files, replace previous archive, archive kept inside the telemetry directory. |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | AC-8 minified check runs on the `deploy` variant (R8 + resource shrinking, debug-signed): `assembleDevRelease` cannot sign in a worktree. SUPERSEDED below | owner (standing instruction: recommended) |
| 2026-10-15 | Minified device checks (AC-8, AC-10 release path) use `devBenchmarkRelease` (minified, shrunk, debug-signed, app/build.gradle.kts:212-215); `deploy` is unminified (:202) and is used only for the Developer-section-in-non-debug check. Baseline's 25.2 MB deploy APK is not an R8 build | owner (standing instruction: recommended; architect plan Q1) |
| 2026-10-15 | Gate 1: spec approved as assembled (M, ui+perf, AC-1, AC-1a, AC-2..AC-10, AC-10a) | owner (standing instruction: recommended) |
| 2026-10-15 | Review cap exceeded with one open major (triage-3 row 1): one bounded extra fix round for rows 1-3, edge+tests re-check of that diff only; row 4 deferred to telemetry-uptime-autooff | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 2: ship (judge-1 CONCERNS, no must-fix) | owner (standing instruction: recommended) |
| 2026-10-15 | Before ship, squash the branch into fewer than 5 logical commits (after review, tree unchanged) | owner |
| 2026-10-15 | Q1-Q3 take the analyst's recommendations; Q1 overrides the escalate_also design-stand route because only existing row styles are reused | owner (standing instruction: recommended) |

## Log
- 2026-10-15 created (stage idea) as part 1/5 of the split of
  usage-telemetry-log (size L). Analyst estimate M. Scope: AC-1, AC-1a, AC-2 to AC-10. Everything else depends on it; stable-names decision (AC-8) is made here.
  Source: .flow/runs/usage-telemetry-log/brief.md (event catalogue, ACs, owner answers Q1-Q3)
  and research.md.
- 2026-10-15 /task:run started (standing instruction: run end to end on recommended answers); research.md inherited from usage-telemetry-log; base 39903d31.
- 2026-10-15 triage: brief.md (feature, M, perf+ui, 12 ACs incl. AC-10a); Q1-Q3 answered on recommendation; stage design.
- 2026-10-15 design: design-ux-designer.md, design-architect.md (16 decisions; onAction final -> handleAction in 19 VMs; narrowed R8 log rule); test-designer started; qa baseline running.
- 2026-10-15 baseline.md (cold start median 2804 ms on emulator-5554; devDebug 36.2 MB; deploy 25.2 MB; frame stats software-GPU only). test-design.md (P0 40). Spec assembled; gate 1 approved on standing instruction; stage plan.
- 2026-10-15 plan written (10 tasks); minified-check variant corrected to devBenchmarkRelease; readiness check next.
- 2026-10-15 readiness-1.md READY (7 non-blocking notes); pasted into plan; stage impl.
- 2026-10-15 impl done: 10 commits 01d22f63..c66264d4, 96 files +5941/-58; fast check green (conductor), full check green per implementer (1741 tests); stage verify.
- 2026-10-15 verify round 1: qa-report.md; check.full green; AC-8 FAIL (release act.a shows Outer$Inner; nav.from obfuscated for private LoadingTab object); rest PASS (AC-1a update URI and forced network failure NOT CHECKED on device). Evidence eea7207d. Note: qa accidentally played Psyren S1E1 ~6 s on the emulator account (History entry). Implementer fix round.
- 2026-10-15 verify round 2: AC-8 PASS after fix 4d786240; check.full green; evidence fc5738b1. Stage review.
- 2026-10-15 review round 1: blind, edge, tests, codex (review+adversarial), ux-designer (match). triage-1.md: 16 rows fix (4 major), row 11 deferred to telemetry-ready-data-states. Implementer fix round.
- 2026-10-15 review fixes d962b687..0f20e63a (rows 1-10,12-17); fast check green (conductor). Deviations: row 7 keeps stored enabled_at (no clamp), row 4 first_content = first ready of any VM, row 3 full binary names in et/frames. Incremental review + qa next.
- 2026-10-15 review round 2 (incremental): edge 2 major (zip eviction, first_content on error/overlay states) + 2 minor, tests 2 minor, codex 1 (= edge F1); qa round 3 PASS (T05 itest 21/21, AC-1a, AC-9, AC-10, AC-10a; release retrace NOT CHECKED). triage-2.md: 6 rows fix.
- 2026-10-15 round 2 fixes 12abafde..bbe17d3e; fast check green (conductor). Empty states of Favorites/History/Search also count as first content (implementer judgment, accepted). Final incremental re-review + qa next.
- 2026-10-15 re-review of round-2 fixes: edge 1 major (content-list first_content regression) + 1 minor, tests 1 minor, codex clean + 1 optional minor; qa round 4 PASS incl. release retrace; one unrelated flake HomeVMIncrementalPublishTest under load (see run unit-suite-flaky). triage-3.md; extra bounded fix round.
- 2026-10-15 extra round fixes 1ea95f0f, 9e0dad08, 9ce699af; edge re-check clean, tests re-check 1 minor fixed. Stage judge.
- 2026-10-15 judge-1.md CONCERNS (no must-fix; 1770 tests green at 9ce699af). Gate 2: ship on standing instruction. Squash before ship next.
- 2026-10-15 squashed 33 commits into 4 (a8b74f38, 97a0fca2, b115cbbf, be0cb3a2); tree 046aa3fd identical to reviewed 9ce699af; intermediate commits not built individually. Ship next.
- 2026-10-15 shipped: merge 3d6bd6dc (--no-ff) into master; Orca worktree removed.
