---
slug: baseline-mock-protocol-parity
title: "Keep HTTP/1.1 and serialization paths in the mock-generated baseline profile (okhttp3/internal/http1 51 -> 0)"
stage: done
size: M
tracks: [perf]
kind: improvement
branch: prakunin/baseline-mock-protocol-parity
worktree:
base: 08f50b81
spec: docs/superpowers/specs/2026-11-26-baseline-mock-protocol-parity.md
plan: docs/superpowers/plans/2026-11-26-baseline-mock-protocol-parity.md
rounds: {verify: 2, review: 2, judge: 1}
blocked_by: []
source: hermetic-baseline-generation
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
Design in progress.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q7 | (plan) bookmark_items.json still lists id 2 as a movie while items.json makes it a series; leave it? | no | answered | Yes, leave (caches cleared every iteration) |
| Q5 | Also allow subtitles on the never-played series episodes (warms SubtitleLink)? | no | answered | Yes |
| Q6 | Rename the fixtures ban test row and add series test rows in the test design? | no | answered | Yes |
| Q4 | AC-7: kotlinx/serialization/descriptors came out 149 vs threshold 163 (base 137, real-account 203); accept 149 / amend the threshold, or add series fixtures (seasons key, currently banned)? | yes | answered | Add series fixtures in this run (allow the seasons key), regenerate; threshold 163 stays |
| Q1 | Accept the inferred split (HTTP/1.1 for API, images, stream; HTTP/2 for DoH and GitHub) without per-host confirmation? | no | answered | Accept; the mock serves both, no live API traffic |
| Q2 | Add imdb or trailer to fixtures to warm two icons? | no | answered | No; they start external lookups or a preview |
| Q3 | Fix the hardcoded test port 18765 here? | no | answered | Yes, where the second port touches it; the late-check minor only if touched |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | Q4: "добить" - add series fixtures in this run, keep the AC-7 threshold | owner |
| 2026-11-27 | Q5, Q6 as recommended | owner (standing instruction: recommended) |
| 2026-11-27 | AC-4/AC-6 amended; player-count drop accepted, deferred to baseline-player-track-coverage | owner (standing instruction: recommended) |
| 2026-11-27 | Gate 2: ship (judge CONCERNS: player-count drop deferred; fixed 1.5 s Browse rest accepted) | owner (standing instruction: recommended) |
| 2026-11-26 | Q1-Q3 as recommended; design starts after baseline-generation-hardening is merged (same files, one emulator) | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (M, perf, 8 ACs) | owner (standing instruction: recommended) |

## Log
- 2026-11-26 created (stage idea), deferred from hermetic-baseline-generation triage-2: qa round 2 rule-count comparison of the regenerated baseline-prof.txt vs base 5cd3e37e shows drops over 20 %: okhttp3/internal/http1 51 -> 0, kotlinx/serialization/internal 197 -> 140, kotlinx/serialization/descriptors 73 -> 51, phosphoricons 69 -> 51 (evidence docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md). Likely cause: the HTTPS loopback mock negotiates HTTP/2 while the real API is served over HTTP/1.1 (check from code/headers seen in ordinary use, never by probing the API); fixtures are smaller than real payloads. Options: force HTTP/1.1 on the mock (MockWebServer protocols), richer fixtures, guard rules in BaselineProfileContentTest.
- 2026-11-26 /task:run started (base b27d8e40); research
- 2026-11-26 research.md (recommended A: HTTP/1.1-only mock for API/images/stream + HTTP/2 mock on a second port for DoH/GitHub; richer fixtures; guards; 3 questions). Analyst. Implementation waits for baseline-generation-hardening (shared files, one emulator).
- 2026-11-26 brief.md: M, track perf, 8 ACs; Q1-Q3 as recommended. Paused before design until baseline-generation-hardening merges.
- 2026-11-26 resumed after baseline-generation-hardening merged (5fcdb277); base 08f50b81. Stage design.
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/baseline-mock-protocol-parity; architect design + qa baseline (perf)
- 2026-11-26 qa-baseline.md (base vs 5cd3e37e rule counts) and design-architect.md (10 decisions, 8 risks; two servers: HTTP/1.1 :18765, HTTP/2 :18766) written. test-designer.
- 2026-11-26 test-design.md (P0 20, P1 1, P2 2, manual 3; perf thresholds from qa-baseline); spec assembled and committed; gate 1 approved. Stage plan.
- 2026-11-26 plan (4 tasks; Task 4 regenerates on the emulator). Readiness.
- 2026-11-26 readiness-1 NOT READY: phosphoricons perf row must use grep -c Lcom/adamglin/phosphoricons/ (51/69, threshold 56). test-designer + architect fix.
- 2026-11-26 readiness-2 READY, pasted into the plan. Stage impl.
- 2026-11-26 impl: 2b16b50a, 25019133, 0372b1a1, 6fcea29c; generation clean (40 iterations, unknown=[]; 22 tests, 0 failures; BaselineMockSelfTest 16/16); check.full green. Counts: http1 54 (>=40 ok), serialization/internal 188 (>=188 ok), descriptors 149 (<163 MISS), phosphoricons 57 (>=56 ok). Blocked on Q4 (AC-7 threshold) - asking the owner.
- 2026-11-27 owner answered Q4: add series fixtures here (allow seasons), keep threshold 163. Architect design delta.
- 2026-11-27 design round 2 delta (D11-D17: series id 2 fixture, required route in Browse, ban -> imdb|trailer, seasons/subtitles only in item_serial.json); Q5, Q6 as recommended. test-designer delta.
- 2026-11-27 test-design round 3 (P0 22); spec re-assembled. Architect plan delta.
- 2026-11-27 plan round 3: Tasks 5-7 (Task 7 = emulator regeneration); Q7 as recommended. Readiness round 3.
- 2026-11-27 readiness-3 READY. Stage impl (Tasks 5-7).
- 2026-11-27 impl Tasks 5-7: 78695874, 923439cf, f04f6e36; generation clean (32 iterations + 4 late checks unknown=[] missing=[]; items/2 matched in all Browse iterations; self-tests 19/19); counts http1 54, internal 235, descriptors 203, phosphoricons 57 - all thresholds met; check.full green. qa + review round 1 at f04f6e36.
- 2026-11-27 qa round 1: check.full green; AC-1..AC-8 PASS; perf re-count matches (descriptors 203); no evidence file yet for the emulator run (to add in the fix round). Stage review.
- 2026-11-27 review round 1 at f04f6e36: blind 1 major + 2 minor + player-coverage unproven, tests 1 major + 2 minor, edge 1 minor, codex 1 minor, antigravity clean. triage-1.md: fix #1-#7, reject #8. Implementer fix round with regeneration.
- 2026-11-27 fix round 1: 9d76588b, ac31a23c, c8d70d90, f4086c71, d4f81e41; regeneration run 4 clean (41 iterations, items/2 and get-item-folders matched in all 11 Browse iterations; 26 tests, 0 failures). Thresholds met (http1 54, internal 237, descriptors 205, phosphoricons 63; folder guard 8/21). Player counts still below base (exoplayer -5%, hls -10%, track resolvers -16..-25%), not caused by the watched fixture; non-gating per test design -> accepted, investigation deferred to baseline-player-track-coverage. AC-4 watched/bookmarked movie moved off item.json (watched: items.json id 3; bookmarked: series + folder lookup) -> analyst amends AC-4. Browse journey now rests 1.5 s before OK (generator change).
- 2026-11-27 AC-4/AC-6 amended, spec re-assembled (8bda22ae). qa round 2 (evidence) + review round 2 on f04f6e36..8bda22ae.
- 2026-11-27 qa round 2: check.full green; AC-1..AC-8 PASS; evidence b2a8c42f (audited). Player counts vs real-account 5cd3e37e: exoplayer 3313 vs 3203, hls 348 vs 376, AudioTrackPreferenceResolver 48 vs 46 - close to the real-account profile; the drop is relative to the earlier mock base only.
- 2026-11-27 review round 2: codex, antigravity clean; edge 2 minor + nit; tests 1 stale major (evidence - already written by qa r2) + 2 minor + nit. triage-2.md: accept #2-#6, reject #1 (resolved), #7. Stage judge.
- 2026-11-27 judge-1 CONCERNS (no must-fix). Gate 2: ship (standing instruction). Squash.
- 2026-11-27 squashed to 4 commits (7d5cf63f, 624a8247, 367bc6d2, 8905af7d); tree identical to b2a8c42f; co-authored 0. Next: /task:ship baseline-mock-protocol-parity (owner).
- 2026-11-26 shipped: merged prakunin/baseline-mock-protocol-parity into master as 048f17d5 (--no-ff); compile green on merged master; Orca worktree removed.
