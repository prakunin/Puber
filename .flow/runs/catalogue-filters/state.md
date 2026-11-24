---
slug: catalogue-filters
title: "Filter and sort panel for the catalogue grid driven by ContentFilter"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [ui]
# Kind: bug | feature | improvement | refactor | idea
kind: feature
branch: prakunin/catalogue-filters
worktree: /home/dev/orca/workspaces/Puber/catalogue-filters
base: b7a7d99e
spec: /home/dev/orca/workspaces/Puber/catalogue-filters/docs/superpowers/specs/2026-11-24-catalogue-filters.md
plan: /home/dev/orca/workspaces/Puber/catalogue-filters/docs/superpowers/plans/2026-11-24-catalogue-filters.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source:
created: 2026-11-24
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-24
---

## Next
nothing; follow-ups: catalogue-filters-country-year-status, catalogue-filters-persist, catalogue-filters-ratings-quality, catalogue-filters-cartoon-anime-grids

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Accept the split and narrow this run to item 1 | yes | answered | Yes; items 2-4 created as idea runs (catalogue-filters-country-year-status, catalogue-filters-persist, catalogue-filters-ratings-quality) |
| Q2 | How long a filter lives | yes | answered | For the screen instance (survives details/player and Back, cleared when the grid closes) |
| Q3 | 4K tab genre list | no | answered | genres?type=4k if the box check returns a non-empty list, else the untyped list; no status filter there |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Size L split; this run narrowed to item 1 (M); Q1-Q3 on recommendation | owner (standing instruction: recommended) |
| 2026-11-24 | Architect Q1 (no button on all_cartoon/all_anime; idea run) and Q2 (focus to the first card when the new list lands; added to AC-5 as a device check) on recommendation; Q3: run the box API check before plan | owner (standing instruction: recommended) |
| 2026-11-24 | Plan Q1: after «Сброс» focus follows AC-5 (Фильтры, then card 1 when the list lands) | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-24 | codex and antigravity review on every round; skip at usage limit | owner |
| 2026-11-24 | caps lifted | owner |
| 2026-11-24 | device evidence on the TV box 192.168.1.110:5555 only | owner |

## Log
- 2026-11-24 created (stage idea); owner request: the catalogue has no filtering. domain/model/ContentFilter.kt (genre, country, sort and direction, year range, Kinopoisk and IMDb rating ranges, quality, finished) exists but nothing uses it. Add a D-pad friendly filter and sort panel on the browse-all grid; resetPaging() after each change (docs/architecture/paging-and-filters.md). Part 3 of 3, after catalogue-browse-all.
- 2026-11-24 research.md (Option A); triage: L, split into 4; this run narrowed to item 1 (M, ui, 13 ACs); parts 2-4 created; stage design
- 2026-11-24 design-architect.md (22 decisions), design-ux.md (showall stand extended in the run); box API check started
- 2026-11-24 box API check PASS (evidence 5a8d3c03): genres wrapped in {status,items}; genres?type=4k gives 30 items; all 5 sort fields work both ways; "+" suffix is ignored, ascending is the empty suffix; genre works on serial and 4K. Session paused at the owner's usage limit.
- 2026-11-24 architect delta for the API check; AC-5/AC-10 updated; test-design.md (P0 31, P1 24, manual 7); showall stand «catalogue-filters» scene published; spec committed 1928ce5f; gate 1 approved; stage plan
- 2026-11-24 plan (7 tasks); readiness-1 NOT READY (reset focus decision not in plan/spec; P0 device rows without tests), fixed; readiness-2 READY (59009bee); stage impl
- 2026-11-24 impl 842b2dce..f1ee2102 (7 tasks); check.full green; itests 13/9/7 on the box; all 46 named tests present; stage verify
- 2026-11-24 qa round 1: no defect from the change; NOT CHECKED: AC-7/AC-8 on device (unit), AC-9 (show-anime on), player leg of AC-11 and player panel look; 4K unfiltered grid shows the deferred cached-payload-row-too-big error; evidence 027ed8d3; stage review
- 2026-11-24 review round 1: triage-1.md (fix #1-#8, accept #9 for the owner, defer #13); codex: changes needed; antigravity clean
- 2026-11-24 fix round 1 (3d913e2e..426b5e99); review round 2: triage-2 (docs fixes; two minors accepted); qa round 2: PASS except Manual 2 close-with-Back (pre-existing: Back with the rail open pops Show all; logged to showall-rail-back-exits-app); accidental ~3 s playback on the box; spec synced 184e0163; stage judge
- 2026-11-24 judge-1 CONCERNS (no must fix: AC-2 shelf grid not opened on device, accidental short playback, two accepted minors); gate 2 ship; stage handover
- 2026-11-24 squashed to 4 commits (tree eb43ec56 unchanged); merged into master as 948783d1; worktree removed; stage done
