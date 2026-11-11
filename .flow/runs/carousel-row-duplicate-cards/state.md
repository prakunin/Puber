---
slug: carousel-row-duplicate-cards
title: Same title repeats within one horizontal carousel row
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/carousel-row-duplicate-cards
worktree: /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards
base: 901d820d
spec: docs/superpowers/specs/2026-11-11-carousel-row-duplicate-cards.md
plan: docs/superpowers/plans/2026-11-11-carousel-row-duplicate-cards.md
rounds: {verify: 2, review: 2, judge: 2}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source:
created: 2026-11-11
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-11
---

## Next
Nothing: merged. Owner manual check of AC-7 open-title route in the next build.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-11 | Q1 research: fix the restoreLoadedDepth path only; shared paginator dedupe stays with paginator-hardening | owner (standing instruction: recommended) |
| 2026-11-11 | Q2 research: "top series" read as the Series tab Popular row; reproduce with show-anime off or hide-watched on | owner (standing instruction: recommended) |
| 2026-11-11 | Q3 test design: keep a separate request counter (<= MAX_RESTORED_PAGES - 1) beside the maxOf cursor | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approved as M, tracks none, AC-1..AC-8 | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 2: ship (AC-7 open-title route stays NOT CHECKED: needs an owner-authorised save/unsave; owner manual check) | owner (standing instruction: recommended) |

## Log
- 2026-11-11 created (stage triage) from owner bug report: duplicates in top-series row when scrolling right
- 2026-11-11 research.md written (root cause: restoreLoadedDepth page cursor and missing dedupe)
- 2026-11-11 brief.md written: bug, size M (refresh publishes VM state, a risk area), tracks none, 8 ACs
- 2026-11-11 worktree created by orca at /home/user1/orca/workspaces/Puber/carousel-row-duplicate-cards
- 2026-11-11 design-architect.md and test-design.md written; spec assembled; gate 1 approved (standing instruction)
- 2026-11-11 plan written; readiness-1 NOT READY (3 test-guard items), plan and test-design revised; readiness-2 READY
- 2026-11-11 impl done: f309a928, 9cad7b83; 5 red runs recorded; check.full green (implementer); check.fast green (conductor); all 12 named tests present
- 2026-11-11 qa round 1: check.full green, AC-1..AC-6 PASS, AC-7/AC-8 PASS (partial: refresh not observed on device, no account mutation authorized), Manual 3 not run; evidence afc1e706
- 2026-11-11 review round 1: blind/edge no findings; tests F1 major (device refresh not exercised, needs unauthorised account mutation -> AC-7/AC-8 refresh half NOT CHECKED), F2 minor, F3 nit; codex 1 minor (test race); triage-1.md
- 2026-11-11 fix round 1: caabfc89 (tests only) + test-design revision; review round 2 (edge, tests, codex incremental): no findings; triage-2.md
- 2026-11-11 judge-1 CONCERNS: triage #1 premise wrong - displaySettingsChanges (hideWatched / showWatchedIndicators) also triggers refreshFirstPage in SectionVM and ShowAllVM, so AC-7/AC-8 refresh half is checkable without an account mutation; verify round 2 ordered
- 2026-11-11 qa round 2 (5893b70a): refresh triggered by the watched-indicators flip, ran at depth (pages 1..6 once); row 80 and grid 85 distinct titles; grid kept focus, row focus went to the first card because the Series tab is rebuilt after Settings
- 2026-11-11 judge-2 PASS (1 manual item); gate 2: ship (standing instruction)
- 2026-11-11 squashed to 3187cfc1 (app) + e37c97b1 (docs); tree 03977a0d unchanged; check.fast green
- 2026-11-11 shipped: merge 5fed9ecb into master (--no-ff); orca worktree removed
