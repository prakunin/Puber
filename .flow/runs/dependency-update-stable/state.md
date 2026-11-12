---
slug: dependency-update-stable
title: Update library dependencies to the latest stable versions
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: []
# Kind: bug | feature | improvement | refactor | idea
kind: improvement
branch: prakunin/dependency-update-stable
worktree: /home/user1/orca/workspaces/Puber/dependency-update-stable
base: 4db9c0d4
spec: docs/superpowers/specs/2026-11-12-dependency-update-stable.md
plan: docs/superpowers/plans/2026-11-12-dependency-update-stable.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source:
created: 2026-11-12
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-12
---

## Next
nothing; shipped

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-12 | KSP 2.3.12, not the defective 2.3.13 | owner (standing instruction: recommended) |
| 2026-11-12 | detekt Compose-rules swap goes to a follow-up run; 1.4.0 line untouched here | owner (standing instruction: recommended) |
| 2026-11-12 | AGP 9.5.1 + Gradle 9.9.1 in this run; coil 3.6.3 lands with AGP | owner (standing instruction: recommended) |
| 2026-11-12 | Q4: baseline profile not regenerated in this run | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1: approved (size M, no tracks, 10 ACs, commits C1-C4) | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1 delta: media3 stays at 1.11.0 (spec Fallbacks); 1.11.1 removes DefaultMediaSourceFactory.setExperimentalEnableHagcPlayback with no replacement (no HAGC symbol in the 1.11.1 class), used at PlaybackController.kt:498 behind a user setting. AC-1 target for media3 becomes 1.11.0. Follow-up run media3-hagc-removal captured | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1 delta (supersedes the AGP 9.4.1 fallback): AGP stays at 9.5.1. AGP 9.4+ rejects the release signingConfig fallback (`storeFile = file("debug.jks")` with no passwords or alias, app/build.gradle.kts:172-174) in :app:validateSigningProdRelease; 9.4.1 fails the same way, so the 9.4.x fallback does not help. Fix in C4: the fallback sets storePassword "android", keyAlias "androiddebugkey", keyPassword "android" explicitly; app/debug.jks is the committed standard debug keystore (checked with keytool), so the produced signature is unchanged and the keystore.properties / CI paths are untouched | owner (standing instruction: recommended) |
| 2026-11-12 | Verify cap reached with AC-10 NOT CHECKED: carry it to the gate as is, no extra DatabaseTransaction instrumented test in this run. Room 3.0.1->3.0.3 and sqlite 2.7.0->2.7.1 are patch bumps; WatchStateDaoTest 8/8 and CachedPayloadDaoTest 6/6 pass on the device, including the @Transaction upsertAllFromServer; the progress-save failure reproduces on base and belongs to resume-progress-anomaly | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 2: ship (judge-1 CONCERNS: AC-10 NOT CHECKED, accepted; stale baseline profile per Q4; manual check for AC-10 listed) | owner (standing instruction: recommended) |

## Log
- 2026-11-12 created (stage triage)
- 2026-11-12 research.md written (researcher); three questions answered by standing instruction
- 2026-11-12 brief.md written (analyst: M, improvement, no tracks, 10 ACs); worktree created; follow-up run detekt-compose-rules-revive captured (stage idea)
- 2026-11-12 design-architect.md and test-design.md written; spec assembled and committed (619fbd8d); gate 1 approved by standing instruction
- 2026-11-12 plan written; readiness-1 READY; plan committed
- 2026-11-12 impl: C1 33352773, C2 e5064d1b committed; blocked on media3 1.11.1 (HAGC API removed); resolved by keeping 1.11.0
- 2026-11-12 impl: C3 09a930ab committed; C4 blocked on AGP 9.5.1 release signing fallback; resolved by AGP 9.4.1
- 2026-11-12 AGP 9.4.1 failed the release signing the same way; root cause is the passwordless debug.jks fallback; decision: explicit debug credentials in the fallback, AGP 9.5.1; follow-up run agp-9-5-release-signing withdrawn (never committed)
- 2026-11-12 impl done: C1 33352773, C2 e5064d1b, C3 09a930ab, C4 f7198347 (+docs); check.full green (implementer); named tests 0 missing; stage verify
- 2026-11-12 qa-device verify round 1: check.full pass, AC-1..9 PASS, AC-10 NOT CHECKED (progress save fails with `watching/marktime` on base 4db9c0d4 too; not caused by the bump); evidence c62b3ae1; lease released; stage review
- 2026-11-12 review round 1: blind, edge, tests, codex (review + adversarial); triage-1: #1 major fix (DAO itests), #2/#3 fixed (bebab8ec, 393bd7fe, 742fd2e5), #4 deferred to ktor-readremaining-deprecation; wrapper jar sha256 matches the official 9.9.1 checksum
- 2026-11-12 qa-device verify round 2: DAO itests 8/8 + 6/6 PASS on emulator-5554; AC-10 stays NOT CHECKED (withWriteTransaction not exercised); evidence 2d24287e
- 2026-11-12 review round 2 (edge + codex, incremental): triage-2: AC-10 finding returned, accepted per standing instruction (spec Decisions dd62e708); plan nit accepted; stage judge
- 2026-11-12 judge-1: CONCERNS, no must-fix; check.full green at dd62e708; Co-Authored-By count 0; gate 2 ship by standing instruction; stage handover
- 2026-11-12 squashed to 3 commits (44e5601a, dba6ae5f, 9576a987), tree ebcee222 unchanged; backup ref backup/dependency-update-stable-presquash
- 2026-11-12 shipped: merged into master as e2bccaf5 (--no-ff); Orca worktree removed; branch and backup ref deleted
