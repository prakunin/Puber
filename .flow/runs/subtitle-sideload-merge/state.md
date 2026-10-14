---
slug: subtitle-sideload-merge
title: "Port the rest of upstream's subtitle work: side-load API subtitles on HLS (003d4c55) and the full subtitle merge model"
stage: done
size: M
tracks: []
kind: feature
branch: subtitle-sideload-merge
worktree: /home/user1/orca/workspaces/Puber/subtitle-sideload-merge
base: a97ee2b1
spec: docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md
plan: docs/superpowers/plans/2026-10-14-subtitle-sideload-merge.md
rounds: {verify: 3, review: 3, judge: 1}
blocked_by: []
created: 2026-10-12
migrated:
updated: 2026-10-14
---

## Next
Nothing; shipped. Follow-ups: subtitle-restore-model, manual-quality-hls4, playback-controller-split (narrowed).

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Scope: one run (B) or two steps (C)? | yes | answered | C: this run is step 1 (side-load, text tracks to VM, merger, selector, labeler); restore across recreation goes to subtitle-restore-model |
| Q2 | Which labeler survives? | yes | answered | Upstream SubtitleLabeler/SubtitleLanguage, fed with the first batch's strings and its Russian ISO-3 rule |
| Q3 | Merge rule when manifest and API both offer a language? | yes | answered | Upstream 9bd835b0: the source with more tracks wins, HLS on a tie; unknown-language rows kept unless proven duplicates |
| Q4 | Port upstream's final state (b755826c) or replay commits? | no | assumed | Final state |
| Q5 | Order against playback-controller-split? | no | assumed | This run goes first |
| Q6 | Device-check titles (HLS title whose API subtitles differ from the manifest) | no | assumed | Owner said proceed on recommendations; qa-device picks titles itself (candidates Psyren, Outlander) |
| Q7 | AC-4 wording (stripped group ids all "0") | yes | answered | Reword per architect: source identity, then full group id + index, then unambiguous language; never position |
| Q8 | AC-9 evidence | no | answered | Media3 trace D15 + P6 hermetic test; device check if a failing title turns up |
| Q9 | Debug stream-source host for side-loads | no | answered | Move to playback-controller-split |
| Q10 | Embedded container subtitles on progressive | no | assumed | Left out (keeps AC-5) |
| Q11 | Manual quality plays url.hls (no subtitle renditions) | no | assumed | Separate run manual-quality-hls4 |
| Q12 | Re-label Outlander as manifest-only (AC-2), keep AC-1 open, qa counts S1E5 API subtitles without storing the response | no | assumed | Yes |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-12 | Option C, two steps; this run is step 1 (size M) | owner |
| 2026-10-12 | Labels: upstream SubtitleLabeler with the first batch's strings and Russian ISO-3 rule | owner |
| 2026-10-12 | Merge rule: upstream 9bd835b0 | owner |
| 2026-10-14 | AC-4 reworded (Q7) | owner |
| 2026-10-14 | AC-9 evidence: Media3 trace + P6 (Q8) | owner |
| 2026-10-14 | Debug host filter to playback-controller-split (Q9) | owner |
| 2026-10-14 | Gate 1: spec approved | owner |
| 2026-10-14 | Gate 2: ship | owner |

## Log
- 2026-10-12 created (stage idea). Request: port the rest of upstream's subtitle work - side-load API subtitles on HLS (003d4c55) and the full subtitle merge model; starts after subtitle-upstream-port lands. Source: the "later candidates" in subtitle-upstream-port's Next.
- 2026-10-12 research.md and brief.md written; triage: feature, L as requested, split per option C. Owner answered Q1-Q3; follow-up subtitle-restore-model captured (stage idea). Stage triage.
- 2026-10-14 resumed with /task:run after subtitle-upstream-port shipped (master a97ee2b1). Triage kept (brief.md; Q1-Q3 answered); brief line numbers refer to be8395a4. Orca worktree subtitle-sideload-merge created on a97ee2b1. Stage design.
- 2026-10-14 design-architect.md (D1-D16, R1-R10) and test-design.md (P0 20) written; spec assembled (8cdf90e3); gate 1 approved with AC-4 reworded and AC-9 evidence set. Stage plan.
- 2026-10-14 owner: proceed on recommendations without stopping (gate 2 and authorised device actions still go to the owner). Plan written (3b979408): 8 tasks, refinements P-a/P-b/P-c. Readiness round 1 started.
- 2026-10-14 readiness-1 READY (5 advisories; 1-4 passed to the implementer); pasted into the plan (dc2628e0). Stage impl.
- 2026-10-14 implementer done: 8 commits bd1917a5..c6ea3789, check.full green per implementer; conductor checked diff --stat (26 files, +2585/-322), co-authored 0, check.fast green. Failing-first seen red only for tasks 1, 2, 7. Stage verify.
- 2026-10-14 review round 1 ran in parallel with verify: blind, edge, tests, codex (review + adversarial). triage-1.md: fix #1-#6, reject #7 (fatal side-load error; Media3 trace) and #8 (count rule = owner Q3).
- 2026-10-14 qa-device round 1 (qa-report.md, evidence a703075f/1b48aacd): check.full green 1561 tests; AC-3/7/11 PASS, AC-1 partial, AC-8 unit; AC-4/6 FAIL on device (quality switch wipes subtitle rows and pick); AC-2/5/9 NOT CHECKED (no H1/P1/dead-subtitle title); AC-10 observation only. Outlander saved subtitle changed AI-generated -> Russian (AI row missing on this build; investigated in the fix round). Implementer fix round started.
- 2026-10-14 fix round 1: 36da8957..7ca85b93 (defect 1 VM transient, triage-1 #1-#6); AI row: not a merge drop, API appears not to offer it (unverified, no raw list).
- 2026-10-14 round 2: edge major F1 + codex minor (text-less report always transient), tests minor F1; qa round 2 (1b79c661): AC-7, D-7, D-8 PASS; AC-4/6 FAIL on device (picker says Russian, no cues on manual-quality hls stream); AC-2/5/9 NOT CHECKED. triage-2.md: fix #1, #2. Verify and review caps reached; continuing one more round under the owner's 'proceed on recommendations' instruction (recorded for gate 2). Architect delta requested.
- 2026-10-14 design-architect-2.md: Outlander subtitles are manifest-only (~90%); D17-D20 replace the transient rule; spec amended. Q11/Q12 assumed as recommended; run manual-quality-hls4 created (stage idea). Implementer fix round 2 started.
- 2026-10-14 fix round 2: 5a4b89e9, f4b311e0 (D17-D20). Round 3 (beyond caps, owner's standing instruction): edge F1 + codex (rebuild nulls intent) and tests F1 / edge F2 (rebuild untested); qa round 3 (2903fc50): AC-4/6/7, D-4, D-8 PASS, AC-1 PASS on Psyren S1E1 manual quality, AC-2/5/9 NOT CHECKED, no defects. triage-3.md: fix #1-#2. Implementer fix round 3 started.
- 2026-10-14 fix round 3: 871b06a6 (rebuild keeps intent; rebuild tests). Round 4 edge + tests on the increment: no findings. Conductor: check.full green, 1593 tests; co-authored 0. Stage judge.
- 2026-10-14 judge-1.md: CONCERNS, no must-fix (check.full re-run by the judge: 1593 tests green). Co-authored 0. Stage handover.
- 2026-10-14 gate 2: ship (owner).
- 2026-10-14 shipped: merged subtitle-sideload-merge into master as 6fcaddf0 (--no-ff); Orca worktree removed.
