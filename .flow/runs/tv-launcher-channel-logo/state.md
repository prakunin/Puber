---
slug: tv-launcher-channel-logo
title: "Android TV launcher channel is never created: ensureChannel fails with Failed to add logo, no Puber row on the launcher"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: S
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/tv-launcher-channel-logo
worktree: /home/user1/orca/workspaces/Puber/tv-launcher-channel-logo
base: e11d1b66
spec:
plan:
rounds: {verify: 1, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-targeted-invalidation
created: 2026-11-22
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
Done. Owner: check the channel logo on the launcher "Customize channels" screen

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Channel logo: ic_launcher or a new opaque 80dp icon? | no | answered | ic_launcher now; a separate icon only if it looks bad on the box |
| Q2 | Decouple Watch Next from channel creation (B) or logo only (A)? | yes (AC-4) | answered | B |
| Q3 | Box check: TV provider rows or visible launcher row? | no | answered | provider rows are the criterion; visible row recorded if the launcher shows app channels |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-22 | Add the JVM test seam (TvProviderGateway, TvHomeStore, channelLogoUri) as test-design recommends | owner (standing instruction: recommended) |
| 2026-11-22 | Run end to end on recommended answers, gates and ship included | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required on every round (owner) | owner |
| 2026-11-22 | caps lifted (owner) | owner |
| 2026-11-22 | Gate 1: approve (S, brief ACs and Plan as spec and plan) | owner (standing instruction: recommended) |
| 2026-11-22 | Q1: logo = mipmap/ic_launcher | owner |
| 2026-11-22 | Q2: option B, Watch Next published even when channel creation fails | owner |
| 2026-11-22 | Q3: pass/fail on TV provider rows, launcher row recorded when shown | owner |

## Log
- 2026-11-22 created (stage idea)- 2026-11-22 request: Android TV launcher channel is never created: on the owner TV box 192.168.1.110:5555 (com.kino.puber.stage, clean HEAD of cache-targeted-invalidation) the first reconcile logs "IOException: Failed to add logo, so channel (ID=269) was not created" from AndroidTvHomePublisher.ensureChannel, so no Continue watching / Puber row ever appears on the launcher. Pre-existing (core/tvhome unchanged between 40762767 and ef2d1c20). Source: cache-targeted-invalidation qa round 3, evidence docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-verify-box.md (in worktree /home/user1/orca/workspaces/Puber/cache-targeted-invalidation, commit 7415b586).
- 2026-11-22 research.md: logo never set, tvprovider 1.1.0 deletes the channel and throws, Watch Next blocked too; since 46c9d9a1; option B recommended
- 2026-11-22 brief.md: S, bug, no tracks, 6 ACs, 3 questions
- 2026-11-22 /task:run started from master e11d1b66; gate 1 approved on standing instruction; test-designer spawned
- 2026-11-22 Orca worktree /home/user1/orca/workspaces/Puber/tv-launcher-channel-logo (branch prakunin/tv-launcher-channel-logo) from e11d1b66
- 2026-11-22 test-design.md: 9 risks, P0 9, P1 6, P2 1, MANUAL 6; seam recommended and taken; implementer spawned
- 2026-11-22 impl done: 5fd0bb06, d7fbb00d; check.fast green (conductor); all 13 named tests present (check-named-tests.py parses 0 names from this test-design format, checked by hand); 0 Co-Authored-By; check.full green per implementer; qa-device verify round 1 spawned
- 2026-11-22 qa-report round 1: check.full green; D0 reproduced Failed to add logo on base; AC-1..3 PASS indirect (stored IDs + launcher row; TV provider tables unreadable via adb content query on the box), AC-4 PASS JVM, AC-5 row visible, logo look NOT CHECKED (text-only header), AC-6 PASS; evidence 9d80b868; review round 1 spawned at 9d80b868
- 2026-11-22 review round 1: blind 1 minor + 1 nit, tests 4 minor, codex clean; triage-1: fix T1 (test for a kept preview while the channel is down), reject B1, T3 (carried to gate), accept T2, T4; implementer fix mode spawned
- 2026-11-22 fix round 1: 6196df8b (test only, mutation-checked); AndroidTvHomePublisherTest green (conductor, exit 0); review round 2 (edge, tests, codex) on 9d80b868..6196df8b spawned
- 2026-11-22 review round 2: edge, tests, codex clean; triage-2 written; final-judge spawned at 6196df8b
- 2026-11-22 judge-1: CONCERNS, no must-fix (AC-1..3 indirect: provider rows unreadable over adb on the box; AC-5 logo look manual). check.full green at 6196df8b (judge). Co-Authored-By 0; 4 commits, no squash needed. Gate 2: ship on standing instruction
- 2026-11-22 shipped: merged into master as 4fb6da73 (--no-ff), Orca worktree removed
