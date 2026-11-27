---
slug: subtitle-und-preferred-fallback
title: "An undetermined subtitle row with no selector match no longer falls back to an \"und\" track"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: S
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/subtitle-und-preferred-fallback
worktree: /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback
base: 874b049f
spec:
plan:
rounds: {verify: 1, review: 1, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: player-audio-und-label
created: 2026-11-27
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-27
---

## Next
none (done)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Unmatched "und" API row: subtitles stay off (A), Media3 picks an "und" track (B), exact single "und" match (C)? | yes | answered | A (recommended) |
| Q2 | Instrumented case asserting preferredTextLanguages? | no | answered | no, helper unit test (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1: option A, keep "" and pin it with a helper test | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: no instrumented case | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS: call site untested (R3), Media3 outcome for "" with an "und" track unobserved (R4)) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-audio-und-label judge-1 (CONCERNS) and review-tests-2.md F1: PlaybackController.applySubtitleTrackSelection now passes knownLanguageOrNull(track.language).orEmpty() to setPreferredTextLanguage. Media3 1.11.0 getFormatLanguageScore returns 4 for an exact "und" match before it normalizes "und" (judge read the bytecode), so on base a preferred "und" picked an "und" track when SubtitleTrackSelector.select returned null; now no subtitles show. No known caller reaches it (needs an "und" row whose identity match fails). Options: pass track.language unchanged when the selector finds no target, or add a test pinning the intended outcome.
- 2026-11-27 preflight: base 874b049f; stage triage
- 2026-11-27 research.md (option A recommended: keep "", extract helper, unit-test)
- 2026-11-27 brief.md (bug, S, 5 ACs); stage design (S: test-designer only)
- 2026-11-27 test-design.md (P0 3, P1 2, manual 1); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback; stage impl
- 2026-11-27 impl done: ce8deff2; check.fast green (conductor); stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-5 PASS (AC-4 neighbouring path only: no title with an "und" API row known); stage review
- 2026-11-27 review round 1 (blind, tests, codex; antigravity skipped: quota): triage-1: 1 minor comment fix, 2 rejected
- 2026-11-27 fix: be73cdee (comments only, head be73cdee); stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix); 3 commits, no squash needed; gate 2 ship (standing instruction); stage handover
- 2026-11-27 shipped: merge commit 09f35bd4 (--no-ff) into master; Orca worktree removed; stage done
