---
slug: catalogue-poster-grid
title: "Vertical poster card with badges and ratings for the Show all grid"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [ui]
# Kind: bug | feature | improvement | refactor | idea
kind: feature
branch: prakunin/catalogue-poster-grid
worktree: /home/dev/orca/workspaces/Puber/catalogue-poster-grid
base: ad2c7f0c
spec: /home/dev/orca/workspaces/Puber/catalogue-poster-grid/docs/superpowers/specs/2026-11-24-catalogue-poster-grid.md
plan: /home/dev/orca/workspaces/Puber/catalogue-poster-grid/docs/superpowers/plans/2026-11-24-catalogue-poster-grid.md
rounds: {verify: 3, review: 3, judge: 1}
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
nothing; follow-ups: catalogue-browse-all, catalogue-filters, catalogue-card-audio-subtitle-badges, showall-rail-back-exits-app

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Column count, card size, medium or big poster | no | answered | Decide on the stand, start at 6 columns; medium if the poster is <= 125 dp wide at density 2.0, else big |
| Q2 | List endpoint without an int subtitles: what happens to CC | yes | answered | Ship without CC, no per-card details fetch; record a follow-up idea |
| Q3 | Keep track perf | no | answered | No; card-image-size-tier owns the wide-decode cost, AC-8 covers the poster tier |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Q1-Q3 take the analyst's recommendations (brief.md Open questions) | owner (standing instruction: recommended) |
| 2026-11-24 | codex review on every round | owner (codex: required) |
| 2026-11-24 | caps lifted | owner |
| 2026-11-24 | Stand agreed as it opens; year in the original-title line; no CC / AC3 (follow-up catalogue-card-audio-subtitle-badges) | owner |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-24 | device evidence on the TV box 192.168.1.110:5555 only | owner |

## Log
- 2026-11-24 created (stage idea); owner request: rework the Show all grid after the KinoPub web grid (screenshot in reference-web-grid.png): vertical poster (posters.medium/big instead of the 4K wide poster), badges CC / AC3 / HD / 4K top left, ratings KinoPub likes / Kinopoisk / IMDb on the poster bottom, Russian title and original title under it, focus frame. Design stand first through designing-a-screen, then port to Compose and switch ShowAllScreen (LazyVerticalGrid of VideoItemHorizontal today, ShowAllScreenContent.kt) to it. Check which badge fields Item carries (subtitles, ac3, quality). Related: card-image-size-tier (4K wide decode cost; this run removes it from the grid). Part 1 of 3 with catalogue-browse-all and catalogue-filters.
- 2026-11-24 research.md (Option A recommended); triage: brief.md, M, ui, 12 ACs; Q1-Q3 answered on recommendation; stage design
- 2026-11-24 design-architect.md (13 decisions, Q1-Q3 recommended), design-ux.md (no stand governs Show all), test-design.md (P0 26, P1 11, manual 8); stand docs/design/showall built from the box's .auth snapshot, published https://claude.ai/artifact/JUZUGvKBu9iXjd7ZcmMqdc; ground truth: quality 2160 for 4K, ac3 = 0 on all 208 cached items, item-level subtitles not in cache (type unknown)
- 2026-11-24 owner asked to add the release year to the card; stand gained a "год выхода" switch (line with original title "Orig · 2020", badge on poster, off), default line; AC to add at spec
- 2026-11-24 owner agreed the stand; brief, design and test design updated (12 ACs, P0 30, manual 6); spec assembled and committed de9217e4; gate 1 approved; stage plan
- 2026-11-24 plan (3 tasks) and readiness-1 READY committed 50ba951b; stage impl
- 2026-11-24 impl: 5e4f7185, 02aa3f14, b3e01e31; check.fast green; check-named-tests: only the dropped conditional test missing (decision); stage verify
- 2026-11-24 qa round 1: all ACs PASS except AC-1 (owner) and AC-12 partial (schedule, player episode panel not opened); evidence f68f3524; observations: context menu close leaves focus on the rail, unrounded KP/IMDb pill text, pill legibility; stage review
- 2026-11-24 review round 1: triage-1.md (9 fix, 1 accept, 3 reject; codex skipped, usage limit); fixes 507cc91c..3759abcf
- 2026-11-24 qa round 2: all ACs PASS (AC-1 owner; AC-12 Episode schedule cards not on device: worktree build has no TMDB token); triage-2: load-more stall fixed 210540e6; check.full green at f41bd66f
- 2026-11-24 review rounds 2 and 3: triage-2 (load-more stall fixed), triage-3 (no blockers or majors; one-page prefetch accepted); ux visual matches; stage judge
- 2026-11-24 judge-1 CONCERNS (no must fix); stand anonymised 3aabfd58; qa round 3 on head: AC-9..11 PASS, M7 paging PASS, combined-run crash not recurred (116594ff); gate 2 ship (standing instruction); stage handover
- 2026-11-24 squashed to 4 commits (tree 9b1e867c unchanged); merged into master as 0c22bc33; worktree removed, branch deleted; stage done
- 2026-11-24 post-merge codex review (review + adversarial): clean, review-codex-post-merge.md
- 2026-11-24 post-merge Antigravity review: clean, review-antigravity-post-merge.md
