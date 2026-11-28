---
slug: upstream-sync-triage
title: "Sort upstream (rovkinmax/Puber) commits missing from this fork into take / adapt / skip"
stage: impl
size: L
tracks: []
kind: refactor
branch: upstream-sync-triage
worktree:
base:
spec: docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md
plan: docs/superpowers/plans/2026-10-11-upstream-sync-triage.md
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-11
migrated: 2026-10-12
updated: 2026-11-28
---

## Next
369 commits on upstream/master (up to b755826c, 2026-10-05) not in master; no common merge-base (unrelated histories), so match by content, not hashes. Scope: everything, app code and tooling (release, CI, hermetic player tests, Detekt baseline). Subtitle commits belong to subtitle-upstream-port. Scope widened by the user: triage plus six ports in this branch (P1 CI hardening + signer pin, P2 Main dead code, P3 diagnostics transport label, P4 settings Back check, P5 hermetic baseline profiles, P6 player hermetic fixtures). Spec approved at gate 1 (route standard, 20 ACs). P6 waits for subtitle-upstream-port; P6 part B waits for player-overlays-port. Signer pin waits for the release cert SHA-256 from the user. Tasks 1, 3-6 done (2a4bc402..f037c052), Task 2 waits for the release cert SHA-256. Device pass on emulator-5554 after a user-authorised same-signer `install -r` (installed base.apk had failed its own v2 check): D-1..D-3 PASS, D-4/D-5 do not reproduce, so Task 7 is skipped. Transport label wording waits for the user (D-3 screenshots). P5 done (a57a3238..5709c601), qa run 4: AC-14, AC-15 dev, AC-16 PASS; AC-15 prod device part NOT VERIFIED (no prod package). Next: S2, P6 waits for subtitle-upstream-port on master; Task 2 waits for the cert.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-10-11 | Gate 1: spec approved (route standard, 20 ACs); scope widened to triage plus ports P1-P6 | owner |

## Log
- 2026-10-11 created on the board (stage impl)
- spec header (in the worktree): Kind refactor, Size L, Route standard
- worktree files: reviews/2026-10-11-upstream-sync-triage.md, evidence/2026-10-11-upstream-sync-triage-device.md
- 2026-10-12 migrated from the former board file (board stage `impl` -> `impl`); the board row Notes are copied verbatim into ## Next
- 2026-10-15 owner (via subtitle-restore-model triage): prioritise P6-A (player hermetic fixtures) now that subtitle-upstream-port is done; run subtitle-switch-text-disable waits for it.
- 2026-11-26 upstream re-checked up to 47f89dab (2026-11-18): 17 new commits, all `.kent/` tooling, nothing to take. Open ports moved to their own runs: diagnostics-transport-label (P3), main-dead-code-leftovers (P2 rest), ci-workflow-hardening (P1), hermetic-baseline-profiles (P5), player-hermetic-fixtures (P6). This branch is now only a source for cherry-picks.
- 2026-11-28 worktree field cleared: /home/user1/orca/workspaces/Puber/upstream-sync-triage was on the previous host and does not exist here; branch upstream-sync-triage is kept locally; /task:run creates a new Orca worktree on resume
