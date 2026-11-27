---
slug: player-audio-unknown-language
title: "Audio rows built from the API show a hardcoded English \"Unknown\" when the language is missing"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: S
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/player-audio-unknown-language
worktree: /home/dev/orca/workspaces/Puber/player-audio-unknown-language
base: 0bcd89c7
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
| Q1 | Wording: localized prefix "Язык не указан · X" or "Track N"? | no | answered | localized prefix, ru "Язык не указан", en "Unknown language" (recommended) |
| Q2 | Also route PlaybackController.kt:873-875 through ResourceProvider? | no | answered | no, separate idea run player-track-label-resource-provider (recommended) |
| Q3 | Mark a row with neither language nor author/type as isFallbackLabel? | yes | answered | yes (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 localized prefix; Q2 separate idea run; Q3 isFallbackLabel for an empty row | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS: AC-7 track switching not verified on device, manual 1; stored-label risk deferred) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-audio-und-label Q2 (also manual-quality-hls4 D2): PlayerUIMapper.kt:20-33 (line 24) labels an API audio track with no language as the hardcoded English "Unknown"; move it to strings.xml through ResourceProvider.
- 2026-11-27 preflight: base 0bcd89c7; stage triage
- 2026-11-27 research.md (option A, S)
- 2026-11-27 brief.md (bug, S, 7 ACs); stage design
- 2026-11-27 test-design.md (P0 5, P1 2, manual 1); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/player-audio-unknown-language; stage impl
- 2026-11-27 impl done: 437e914d, 7f1198d6; check.fast green (conductor); stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-7 PASS (AC-1..4 unit-only); stage review
- 2026-11-27 review round 1 (blind, tests, codex, state-path; antigravity skipped: quota): triage-1: 2 test items to fix, 1 deferred as audio-preference-locale-independent-label
- 2026-11-27 fix: d914810b (tests only, head d914810b); stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix, manual 1); 4 commits, no squash needed; gate 2 ship (standing instruction); stage handover
- 2026-11-27 shipped: merge commit 0f654bc6 (--no-ff) into master; Orca worktree removed; stage done
