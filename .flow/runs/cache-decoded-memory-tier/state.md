---
slug: cache-decoded-memory-tier
title: "Typed values decoded once per load in the memory tier, decode off Main, plain JSON in Room behind a namespace bump, watch_state written without a full-table re-read"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [perf]
# Kind: bug | feature | improvement | refactor | idea
kind: refactor
branch: prakunin/cache-decoded-memory-tier
worktree: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier
base: e9c383d0
spec: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/docs/superpowers/specs/2026-11-22-cache-decoded-memory-tier.md
plan: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/docs/superpowers/plans/2026-11-22-cache-decoded-memory-tier.md
rounds: {verify: 2, review: 2, judge: 1}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-layer-rework
created: 2026-11-21
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-22
---

## Next
nothing (shipped)

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Run end to end on recommended answers, gates and ship included | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required (owner) | owner |
| 2026-11-22 | caps lifted (owner) | owner |
| 2026-11-22 | Q1: typed tier option A, one CachedFeed per record type | owner (standing instruction: recommended) |
| 2026-11-22 | Q2: take clearAllPending after StartupSettled.await() from startup-main-thread-io, keep the eager snapshot read; mark that item done there at ship | owner (standing instruction: recommended) |
| 2026-11-22 | Design Q1: count decodes via FakePayloadStore rows for records, a counting KSerializer for payloads, dispatcher check in fake store onRead and fake loader | owner (standing instruction: recommended) |
| 2026-11-22 | Design Q2: if AC-10 shows heap growth, lower the item tier cap in a follow-up run | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 1: approve | owner (standing instruction: recommended) |
| 2026-11-22 | Gate 2: ship | owner (standing instruction: recommended) |
| 2026-11-22 | Q3: leave content:v1 rows to the sweeper, no Room schema bump | owner (standing instruction: recommended) |

## Log
- 2026-11-21 created (stage idea) as split item B1 of cache-layer-rework (owner Q1, 2026-11-21); scope, seed ACs and risks in .flow/runs/cache-layer-rework/brief.md and research.md. After startup-splash-preload (shares the Home warm-up path). Fold any idea runs startup-splash-preload records for main-thread JSON decode.
- 2026-11-21 puber-ea folded home-decode-off-main into this run. Related idea startup-main-thread-io holds the WatchStateRepository-on-construction item (eager full watch_state read + clearAllPending at construction); this run's watch_state item is the full re-read per write. Decide at triage whether to take both together
- 2026-11-22 /task:run started from master e9c383d0 (startup-splash-preload and startup-settled-signal merged); researcher spawned
- 2026-11-22 research.md written (option A recommended, 4 questions); analyst spawned
- 2026-11-22 brief.md: M, refactor, perf, 10 ACs, 3 questions answered on recommendation; Orca worktree created at /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier (branch prakunin/cache-decoded-memory-tier)
- 2026-11-22 design-architect.md: 13 decisions, 9 risks, 2 questions answered on recommendation; test-designer spawned
- 2026-11-22 test-design.md: 11 risks, P0 18, P1 5, MANUAL 3; waiting for qa baseline
- 2026-11-22 baseline.md: cold start Main decodes 66/31 (two runs), Details opens Main decode non-zero, heap alloc 26.2-26.3 MB; Details exact count UNAVAILABLE (full trace kills app). Spec assembled and committed 1c44ece6 Add the spec and baseline evidence of the decoded memory tier; gate 1 approved on standing instruction
- 2026-11-22 plan written (5 tasks + qa hand-off); readiness judge spawned
- 2026-11-22 readiness-1: NOT READY (apiCall decode on Main not covered; AC-4 device / AC-10 rows lack marks); architect plan round 2 + test-designer fix spawned. Chosen: add an apiCall decode-off-Main task (keeps AC-10 as approved, no gate 1 rerun)
- 2026-11-22 plan round 2 (Task 6: apiCall/thirdPartyApiCall/handleApiResponse decode on injected dispatcher), test-design round 2; spec Test design synced; committed a11614d1 Move the API response decode off Main in the plan and sync the test design; readiness-2 spawned
- 2026-11-22 readiness-2: READY (note: token refresh KinoPubApiClient.kt:207 and ApiFailures.kt:66,73 decodes remain in caller context, for verify); pasted into plan, 40657e42 Record the READY readiness verdict in the decoded memory tier plan; implementer spawned
- 2026-11-22 impl done: 6 task commits a07da9c8..a27f1036 + record 4d9a50e0; check.fast green (conductor); named tests: all 56 present, none skipped (check-named-tests.py parsed 0 rows: test-design traceability header is not "| AC", checked by grep instead); 0 Co-Authored-By; qa-device verify round 1 spawned
- 2026-11-22 qa-report round 1: check.full green (2279 tests), AC-1..AC-10 PASS; Main decode 0 after (cold 31-66 before); manual check 2 NOT CHECKED (account change); review round 1 spawned at 803329d9
- 2026-11-22 review round 1 at 803329d9: codex clean, blind 1 minor, edge 0, tests 3 minor; triage-1: 3 fix (tests), 1 defer -> typed-feeds-memory-budget; implementer fix spawned
- 2026-11-22 fix round 1: c93c20c6 (tests only: ContentCacheRepositoryTest, KinoPubApiClientDecodeDispatcherTest); qa round 2 (triage #2 base-DAO red run) + review round 2 (tests, edge, codex on 803329d9..c93c20c6) spawned
- 2026-11-22 review round 2: edge clean, codex clean, tests 1 nit (accepted); qa round 2: AC-7 red on base DAO, green on head, evidence 6adf8e1b; triage-2 written; final judge spawned at 6adf8e1b
- 2026-11-22 judge-1: PASS (check.full at 6adf8e1b green, 2283 tests; MANUAL 1: mark watched within 5 s of cold start, NOT CHECKED, account change). Co-Authored-By count 0. Gate 2: ship on standing instruction; squash spawned (tree dbdee1887af94f22f2aed61f648bed21088206b9)
- 2026-11-22 squashed to 4 commits (5b627683, 269e9e9b, 3c2a2002, e4ed2490), tree dbdee188 unchanged, check.fast green; /task:ship next
- 2026-11-22 shipped: merge 3d820f83 into master (on top of release 1.12.4 commits, no overlap; :app:compileDevDebugKotlin green on the merge); worktree removed, branch deleted
