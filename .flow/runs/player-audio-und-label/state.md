---
slug: player-audio-und-label
title: "Audio row shows the raw language code \"und\" for an undetermined-language track"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: bug
branch: prakunin/player-audio-und-label
worktree: /home/dev/orca/workspaces/Puber/player-audio-und-label
base: cb7fcd45
spec: docs/superpowers/specs/2026-11-27-player-audio-und-label.md
plan: docs/superpowers/plans/2026-11-27-player-audio-und-label.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: manual-quality-hls4
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
| Q1 | Fix audio only, or audio and subtitles? | yes | answered | both, option B (recommended) |
| Q2 | Also replace the hardcoded "Unknown" API audio language (PlayerUIMapper.kt:20-33)? | no | answered | no, separate idea run player-audio-unknown-language (recommended) |
| Q3 | Route SubtitleTrackMerger.orderingLanguage through knownLanguageOrNull too? | no | answered | no (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | codex: required (owner); antigravity on every round (owner); caps lifted (owner) | owner |
| 2026-11-27 | Q1: audio and subtitles (option B) | owner (standing instruction: recommended) |
| 2026-11-27 | Q2: "Unknown" localization -> idea run player-audio-unknown-language | owner (standing instruction: recommended) |
| 2026-11-27 | Q3: orderingLanguage unchanged | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-27 | Readiness note 1: AC-4 "rus" is the manifest code; the test asserting Media3-normalized "ru" satisfies it, AC text unchanged | conductor |
| 2026-11-27 | Gate 2: ship (judge-1 CONCERNS: PlaybackController.kt:906 preferred-language fallback for an unmatched "und" row removed, narrow; deferred to subtitle-und-preferred-fallback) | owner (standing instruction: recommended) |

## Log
- 2026-11-27 created (stage idea) from manual-quality-hls4 fix round 1 (implementer note): Media3 reports language "und" for a progressive MP4 audio track; buildAudioRows (PlayerAudioTracks.kt, before that PlaybackController.notifyTracksUpdated on base) uses format.label ?: format.language, so an http fallback stream shows a row named "und" instead of the localized fallback label. Pre-existing on base. Treat "und" (C.LANGUAGE_UNDETERMINED) as no language.
- 2026-11-27 preflight: base cb7fcd45; stage triage
- 2026-11-27 research.md (option B recommended, S)
- 2026-11-27 brief.md (bug, M, 8 ACs); stage design
- 2026-11-27 Orca worktree /home/dev/orca/workspaces/Puber/player-audio-und-label, branch prakunin/player-audio-und-label
- 2026-11-27 design-architect.md (6 decisions, 5 risks)
- 2026-11-27 test-design.md (P0 13, P1 5, manual 1); spec assembled; gate 1 approved (standing instruction); stage plan
- 2026-11-27 plan (2 tasks) written
- 2026-11-27 readiness-1 READY; stage impl
- 2026-11-27 impl done: 369cc973, 7248c54c; check.fast exit 0 (conductor); itest red 13/1 -> green 13/0; stage verify
- 2026-11-27 qa-report round 1: check.full pass; AC-1..6, AC-8 PASS; AC-7 audio PASS, subtitle rows NOT CHECKED (movie had no subtitles); fresh box auth snapshot copied to main .auth (old kept as .bak); stage review
- 2026-11-27 review round 1 (6 lenses): triage-1: 1 major + 1 minor to fix (subtitle selector after "und" normalization), AC-7 subtitle device check in qa round 1b
- 2026-11-27 fix round 1: d1ed06ad (head d1ed06ad); itest-fix1 13/0
- 2026-11-27 qa round 1b (box, pre-fix build 3fab644b): AC-7 subtitle rows PASS (11 named rows, no UND); review round 2: state-path, edge, codex clean; tests F1 minor (no test for setPreferredTextLanguage change); antigravity skipped (quota, resets ~136h)
- 2026-11-27 qa round 2 (box, fixed build): check.full pass, AC-4/5/7 PASS; triage-2: 1 minor accepted; stage judge
- 2026-11-27 judge-1 CONCERNS (no must-fix); triage-2 #1 rationale corrected by the judge, deferred as subtitle-und-preferred-fallback; gate 2 ship (standing instruction); stage handover
- 2026-11-27 squashed to 3 commits 45fd685d..d3c9b014 (tree cc0eef63 unchanged)
- 2026-11-27 shipped: merge commit 53f9b97e (--no-ff) into master; Orca worktree removed; stage done
