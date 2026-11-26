---
slug: catalogue-filters-country-year-status
title: "Country, year and series-status filters in the catalogue filter panel"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [ui]
# Kind: bug | feature | improvement | refactor | idea
kind: feature
branch: prakunin/catalogue-filters-country-year-status
worktree: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status
base: 90e69aff
spec: docs/superpowers/specs/2026-11-25-catalogue-filters-country-year-status.md
plan: docs/superpowers/plans/2026-11-25-catalogue-filters-country-year-status.md
rounds: {verify: 4, review: 2, judge: 2}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: catalogue-filters
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-26
---

## Next
nothing

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-25 | Q1: year presets only, sent as y or y1-y2 (this year, last year, decades, «до 1980», «Любой»); free range as a follow-up idea | owner (standing instruction: recommended) |
| 2026-11-25 | Q2: status «Все» / «Завершённые» / «Выходящие» on serial, docuserial, tvshow; drop «Выходящие» if finished=0 is ignored, drop a type where finished is ignored; never on 4K | owner (standing instruction: recommended) |
| 2026-11-25 | Q3: country single-select like genre; API order, alphabetical if unordered | owner (standing instruction: recommended) |
| 2026-11-25 | D1 year labels as numbers, stored year keeps its number; D2 one-line summary cut-off; UX1 «Статус» door; UX2 country page fixed 320 dp LazyColumn | owner (standing instruction: recommended) |
| 2026-11-25 | Gate 1: approved (M, ui, AC-1..AC-11; «Выходящие» dropped per AC-1 evidence) | owner (standing instruction: recommended) |
| 2026-11-25 | Plan Q1: the old-store fixture uses upper-case enum names (as the old write() stored); test-design row superseded by plan Task 4 | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship (judge-2 PASS; manual: AC-11 look from screenshots, M5 player panels need playback) | owner (standing instruction: recommended) |

## Log
- 2026-11-24 created (stage idea) from the L split of catalogue-filters (brief.md Split; owner standing instruction: recommended): country (fix the countries list decoding), year presets/range, finished for series types; adds the API params getItems lacks. Part 2 of the catalogue-filters split.
- 2026-11-25 research.md (option A recommended); triage brief.md: M, track ui, 11 ACs, Q1-Q3 answered on recommended; stage design
- 2026-11-25 worktree /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status; AC-1 box probe PASS (countries wrapped, 102; finished=0 ignored); design-architect.md + design-ux.md reconciled; test-design.md (P0 22, P1 12, manual 6); spec committed 2de3a556; gate 1 approved; stage plan
- 2026-11-25 plan (7 tasks) + readiness-1 READY; committed; stage impl
- 2026-11-25 impl done (implementer left changes uncommitted per conductor brief; conductor committed one commit per plan task 88bc33ee..b5e33752); check.fast green; named tests 34/34; implementer check.full green; stage verify
- 2026-11-25 qa round 1: check.full green (2642 unit), ShowAllFilterFocusTest 27/27 on the box; M1-M6 and AC-11 NOT CHECKED: every API call from the box account returns 403 user_banned; evidence 1f6b03b0; review started in parallel
- 2026-11-25 review round 1: antigravity clean, state-path/edge/blind no findings, codex 1 minor, ux 1 minor, tests 1 major (device gap) + 1 minor + 1 nit; triage-1.md: fix rows 2-5, row 1 waits for the box session
- 2026-11-25 fix round 1 e286d189 (cache key keeps the old shape unfiltered, off-list year row, two tests); review round 2: no blockers/majors; triage-2.md: 2 nits accepted, focus itest to run in qa round 2
- 2026-11-25 qa round 2: check.full green, ShowAllFilterFocusTest 28/28 on the box; box still 403 user_banned, M1-M6/AC-11 NOT CHECKED; evidence 7c3fd1b6; stage judge
- 2026-11-25 judge-1 CONCERNS (no must-fix; 7 manual items need a working box session); co-authored-by count 0; stage handover, gate 2 held: device checks not run (AGENTS.md: unavailable device = ask, not skip)
- 2026-11-25 owner re-ran /task:run: qa-device round 3 for the device rows (M1-M6, AC-11); caps lifted (owner 2026-11-20)
- 2026-11-25 qa round 3: box still 403 user_banned after cold launch; UNAVAILABLE device; nothing re-paired/cleared; evidence 40a9d4aa; stage blocked
- 2026-11-25 owner: KinoPub banned the account for suspicious activity; scripted API probing is not acceptable. All device/API work stopped.
- 2026-11-26 owner paired the box to a new KinoPub account on a master build (5cd3e37e, install-preserve by the conductor); Home loads; auth-save taken; repo rule against API probing added (uncommitted); qa round 4 started under the volume rule
- 2026-11-26 qa round 4 (user pace, no 401/403): M1-M4, M6 PASS; M5 partial (player panels need playback, NOT CHECKED); AC-11 screenshots captured (scratchpad); evidence f28a8437; stage judge
- 2026-11-26 judge-2 PASS; squashed to 4 commits 79e449a6..87c15927 (same tree as f28a8437; backup branch backup/catalogue-filters-country-year-status-presquash); co-authored-by 0; gate 2 ship
- 2026-11-26 shipped: merge 81996b90 into master (compile + unit tests green on the merge); worktree removed
