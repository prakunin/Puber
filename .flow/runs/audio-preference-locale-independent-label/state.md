---
slug: audio-preference-locale-independent-label
title: "A stored audio preference uses the localized display label, so it misses after an update or a locale change"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: S
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/audio-preference-locale-independent-label
worktree: /home/dev/orca/workspaces/Puber/audio-preference-locale-independent-label
base: 6ccf2ebf
spec:
plan:
rounds: {verify: 1, review: 1, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: player-audio-unknown-language
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
| Q1 | Option A, B or C? | yes | answered | A, resolver-only, no persisted-format change (recommended) |
| Q2 | Device verification? | no | answered | unit tests for AC-1..AC-5, box smoke for AC-6 (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner; skip while quota exhausted); caps lifted (owner) | owner |
| 2026-11-27 | Q1 option A; Q2 unit tests plus box smoke | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved (S: brief ACs and Plan are the spec and plan) | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge-1 PASS) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from player-audio-unknown-language triage-1 #1 (blind F1, codex): PlayerVM.saveTrackPreferences stores the row label as audioLabel; for an API row with no language and an author the label is now the localized "Язык не указан · X" / "Unknown language · X". Old saves ("Unknown · X", "und · X") and saves made under the other app locale no longer match in AudioTrackPreferenceResolver (exactLabelMatch, normalizedLabelMatch; languageMatch skips an empty language), so the default track plays once. Options: a locale-independent identity for API rows (id or author/type key), or accept legacy prefixes in the resolver.
- 2026-11-27 preflight: base 6ccf2ebf; stage triage
- 2026-11-27 research.md (option A recommended: resolver-only title match, S)
- 2026-11-27 brief.md (bug, S, 6 ACs); stage design
- 2026-11-27 test-design.md (P0 6, P1 1, P2 1, manual 1); gate 1 approved (standing instruction); Orca worktree /home/dev/orca/workspaces/Puber/audio-preference-locale-independent-label; stage impl
- 2026-11-27 impl: 7905b9d9; 5 red on base; check.full pass; stage verify
- 2026-11-27 qa-report round 1: check.full pass, AC-1..AC-6 PASS (AC-1..5 unit-only, box smoke for AC-6); stage review
- 2026-11-27 review round 1 (blind, tests, codex, state-path; antigravity skipped: quota): triage-1: 2 cheap fixes, 2 rejected
- 2026-11-27 fix round 1: 2b5c7b32; check.full at 2b5c7b32: exit 0; stage judge
- 2026-11-27 judge-1 PASS; 3 commits, no squash needed; gate 2 ship (standing instruction); stage handover
- 2026-11-27 shipped: merge commit 27682acb (--no-ff) into master; Orca worktree removed; stage done
