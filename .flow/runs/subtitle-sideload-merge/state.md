---
slug: subtitle-sideload-merge
title: "Port the rest of upstream's subtitle work: side-load API subtitles on HLS (003d4c55) and the full subtitle merge model"
stage: triage
size: M
tracks: []
kind: feature
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-12
migrated:
updated: 2026-10-12
---

## Next
/task:run subtitle-sideload-merge, which starts only once subtitle-upstream-port is on master. Step 1 of option C. Step 2 is in subtitle-restore-model.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Scope: one run (B) or two steps (C)? | yes | answered | C: this run is step 1 (side-load, text tracks to VM, merger, selector, labeler); restore across recreation goes to subtitle-restore-model |
| Q2 | Which labeler survives? | yes | answered | Upstream SubtitleLabeler/SubtitleLanguage, fed with the first batch's strings and its Russian ISO-3 rule |
| Q3 | Merge rule when manifest and API both offer a language? | yes | answered | Upstream 9bd835b0: the source with more tracks wins, HLS on a tie; unknown-language rows kept unless proven duplicates |
| Q4 | Port upstream's final state (b755826c) or replay commits? | no | assumed | Final state |
| Q5 | Order against playback-controller-split? | no | assumed | This run goes first |
| Q6 | Device-check titles (HLS title whose API subtitles differ from the manifest) | no | assumed | Owner names them before verify |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-12 | Option C, two steps; this run is step 1 (size M) | owner |
| 2026-10-12 | Labels: upstream SubtitleLabeler with the first batch's strings and Russian ISO-3 rule | owner |
| 2026-10-12 | Merge rule: upstream 9bd835b0 | owner |

## Log
- 2026-10-12 created (stage idea). Request: port the rest of upstream's subtitle work - side-load API subtitles on HLS (003d4c55) and the full subtitle merge model; starts after subtitle-upstream-port lands. Source: the "later candidates" in subtitle-upstream-port's Next.
- 2026-10-12 research.md and brief.md written; triage: feature, L as requested, split per option C. Owner answered Q1-Q3; follow-up subtitle-restore-model captured (stage idea). Stage triage.
