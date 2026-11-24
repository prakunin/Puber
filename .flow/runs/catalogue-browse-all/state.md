---
slug: catalogue-browse-all
title: "Browse a whole content type (all movies, all series) as a paged grid, not just one shelf"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [ui]
# Kind: bug | feature | improvement | refactor | idea
kind: feature
branch: prakunin/catalogue-browse-all
worktree: /home/dev/orca/workspaces/Puber/catalogue-browse-all
base: e4d75215
spec: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/specs/2026-11-24-catalogue-browse-all.md
plan: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/plans/2026-11-24-catalogue-browse-all.md
rounds: {verify: 5, review: 5, judge: 1}
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
nothing; follow-ups: catalogue-filters, showall-first-entry-rail-focus (second Back), cached-payload-row-too-big (4K «Все»); codex on the round-5 diff after the limit resets

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Where the browse entry sits | yes | answered | One focusable button above the first shelf of every enabled catalogue tab, 4K included; the «Все» shelf stays and opens the same grid (same screen key) |
| Q2 | Grid header content | yes | answered | Title only on every Show all grid, no item count; catalogue-filters adds controls later |
| Q3 | dead-code-sweep vs ContentFilter | no | answered | Keep ContentFilter; dropped from dead-code-sweep's scope while catalogue-filters is open |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Q1-Q3 on the analyst's recommendations | owner (standing instruction: recommended) |
| 2026-11-24 | Architect Q1 (AC-2 Given: the tab opens on the first card) and Q2 (part 3 makes the config changeable) on recommendation | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-24 | antigravity review on every round | owner |
| 2026-11-24 | codex review on every round; skip at usage limit | owner |
| 2026-11-24 | caps lifted | owner |
| 2026-11-24 | device evidence on the TV box 192.168.1.110:5555 only | owner |

## Log
- 2026-11-24 created (stage idea); owner request: today Show all only opens one shelf from ContentListVM.kt:141 (ShowAllScreen(config: SectionConfig)); there is no way to see all series or all movies. Add an entry per content type that pages the whole catalogue in the grid of catalogue-poster-grid. Part 2 of 3, after catalogue-poster-grid; catalogue-filters builds on it.
- 2026-11-24 research.md (Option A); triage brief.md M ui, 11 ACs; Q1-Q3 on recommendation; stage design
- 2026-11-24 design-architect.md (15 decisions), design-ux.md, test-design.md (P0 18, manual 6); stands catalogue (resynced to code) and showall (title) updated, published; spec committed 98ef4665; gate 1 approved; stage plan
- 2026-11-24 plan (5 tasks), AC-2 Given amended, readiness-1 READY (f0431e98); catalogue stand republished at a new URL (old artifact deleted); stage impl
- 2026-11-24 impl 10f2ca53..04fa78aa; check.fast green; all 24 named tests present; stage verify
- 2026-11-24 qa round 1: AC-7 FAIL (Back from the browse grid lands on a shelf card), 4K «Все» grid and shelf show a request error (regression unknown), AC-11 owner; evidence 5e7b721e
- 2026-11-24 verify fix: 0a65717f, c484b503 (AC-7, red run recorded); defect 2 deferred as cached-payload-row-too-big (pre-existing SQLiteBlobTooBigException in cached_payload); implementer accidentally opened a film for seconds on the box (possible tiny watch position)
- 2026-11-24 review round 1: triage-1.md (fix #2-#7; #12 deferred cached-payload-row-too-big); codex clean (review-codex-1.md); antigravity clean (review-antigravity-1.md, owner added it today); qa round 2: AC-7 PASS on device, SectionRowFocusTraversalTest launchFocusSkipsTheEntryWhenSectionZeroIsEmpty red at c0664ac0 (fix round in progress)
- 2026-11-24 review r2 (triage-2: all-Empty unreachable entry fixed 109b7b0b), r3 (triage-3: settle on replayed Empty, focus drop after all-Empty); qa r3 at 109b7b0b: AC-7 FAIL at M-3 step 5 (after a details round trip, the first Back from the browse grid does not leave the grid), rest PASS (4b8febf2)

- 2026-11-24 review r4 (triage-4: replayed Empty fixed at the source 538f5a48, hero test f4d4b22e), r5 (triage-5 clean; codex skipped, usage limit); qa r4 and r5 PASS except known deferred M-3 step 5 and 4K «Все»; stage judge
- 2026-11-24 judge-1 CONCERNS (no must fix: AC-7 step 5 pre-existing, 4K «Все» deferred, codex r5 skipped, accidental short playback); gate 2 ship; stage handover
- 2026-11-24 squashed to 4 commits (tree 884d033f unchanged); merged into master as 28db3a78; worktree removed, branch deleted; stage done
