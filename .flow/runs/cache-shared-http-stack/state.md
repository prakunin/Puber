---
slug: cache-shared-http-stack
title: "One shared OkHttp/Ktor stack for KinoPub, IntroDb, TheIntroDb and Coil, a TTL-respecting DNS cache in front of DoH, KinoPub HTTP cache removed after a device check, KinoPubConfig values memoised"
# Stages: idea | triage | design | plan | impl | verify | review | judge | handover | shipped | done | blocked | dropped
# dropped = an idea the owner decided not to do (owner only).
stage: done
# Size: S | M | L (set by triage)
size: M
# Tracks (optional): ui, perf
tracks: [perf]
# Kind: bug | feature | improvement | refactor | idea
kind: refactor
branch: prakunin/cache-shared-http-stack
worktree:
base: 7bcbfd83
spec: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/specs/2026-11-21-cache-shared-http-stack.md
plan: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/plans/2026-11-21-cache-shared-http-stack.md
rounds: {verify: 3, review: 3, judge: 2}
# Q-ids or a free reason, set when stage = blocked
blocked_by: []
# Slug of the run a deferred finding came from; empty otherwise
source: cache-layer-rework
created: 2026-11-21
# Set only by a migration from an older orchestrator; lint then skips history checks
migrated:
updated: 2026-11-21
---

## Next
triage with /task:run cache-shared-http-stack

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | DNS cache: OkHttp DnsCache or a project TTL wrapper? | no | answered | DnsCache, gated by AC-2; wrapper (option C) only if it fails (recommended) |
| Q2 | Merge IntroDb, TheIntroDb and TMDB into one Ktor client (option B)? | no | answered | No, share the OkHttpClient only (recommended) |
| Q3 | If the KinoPub cache is dropped, delete the existing cacheDir/okhttpcache once? | no | answered | Yes, from CachedPayloadSweeper's idle pass (recommended) |
| Q4 | AC-7: count all resolver queries (DoH plus Coil's system lookups before, DoH after) instead of DoH queries only? | no | answered | Yes; AC-7 wording becomes "resolver queries" (recommended) |
| Q5 | Per-request Url parsing in shouldSendKinoPubToken (no AC)? | no | answered | Leave it for network-timeouts-failover or a later run (recommended) |
| Q6 | Fix the TmdbApiClient KDoc that claims a shared HTTP cache? | no | answered | Yes, in the stack task (recommended) |
<!-- Blocks: yes when the answer changes scope or an AC. Status: open / assumed / answered. -->

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Gate 2: ship (judge-2 CONCERNS accepted: possible watch-progress change from the accidental playback, part of 3b on an expired lease, startup measured only on a loaded host) | owner (standing instruction: recommended) |
| 2026-11-21 | AC-7 resolver queries are counted in one unit before and after: host lookups (dnsStart summed over the app and image labels, one per host whatever the resolver); DoH call counts stay recorded as secondary. Reason: a DoH host costs two calls (A and AAAA) where a system lookup counted one, so the Q4 sum was not like for like | owner (standing instruction: recommended) |
| 2026-11-21 | KinoPub HTTP cache: KEEP (baseline on dd19f70d: cacheHit 1 and cacheConditionalHit 1, the owner rule drops it only at zero); task 5 is no code change | owner rule applied by qa-device measurement |
| 2026-11-21 | Plan Q: keep saveCustomDomain_evictsTheDnsCacheBeforeReturning and test saveCustomDomain directly | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approved as written | owner (standing instruction: recommended) |
| 2026-11-21 | Architect Q1-Q3 (Q4-Q6 here): AC-7 counts resolver queries; shouldSendKinoPubToken parsing left out; TmdbApiClient KDoc fixed | owner (standing instruction: recommended) |
| 2026-11-21 | Q1 DnsCache gated by AC-2; Q2 no Ktor client merge; Q3 delete okhttpcache once from the sweeper idle pass if the cache is dropped; implement on master now, resolve the PuberApp.kt/ApiDomainInteractor.kt overlap with startup-splash-preload at merge or rebase | owner (standing instruction: recommended) |
| 2026-11-21 | caps lifted (owner) | owner |
| 2026-11-21 | codex: required (owner) | owner |
| 2026-11-21 | Owner decision inherited from cache-layer-rework Q3: drop the OkHttp HTTP cache for KinoPub if a device session shows no 304 or cache hits on KinoPub responses; keep it otherwise | owner |

## Log
- 2026-11-21 created (stage idea) as split item B2 of cache-layer-rework (owner Q1, 2026-11-21); scope, seed ACs and risks in .flow/runs/cache-layer-rework/brief.md and research.md. Absorbs coil-shared-http. Before telemetry-network. Q3: drop the KinoPub OkHttp cache only if a device session shows no 304 or cache hits on KinoPub responses.
- 2026-11-21 /task:run started; base 88fa7303; stage triage
- 2026-11-21 research.md written (DUPLICATE_OF none; recommended option A; IntroDb/TheIntroDb/TMDB already share the OkHttpClient, only Coil and the DoH bootstrap stand apart)
- 2026-11-21 triage: brief.md (analyst stalled once, resumed), refactor, size M, track perf, 8 ACs, device verification yes; Q1-Q3 recommended
- 2026-11-21 Orca worktree /home/user1/orca/workspaces/Puber/cache-shared-http-stack (branch prakunin/cache-shared-http-stack) at 88fa7303; stage design
- 2026-11-21 baseline round 1: UNAVAILABLE (emulator down); conductor booted AVD puber_tv_36 headless without wipe; baseline re-run
- 2026-11-21 design-architect.md written (10 decisions; comparable before-counts need debug counters from plan task 1, so the comparable baseline is measured on that commit; baseline at base still records okhttpcache content for the AC-5 decision)
- 2026-11-21 baseline attempt 2 UNAVAILABLE (lease held by startup-splash-preload); comparable baseline moves to the task-1 commit per design; test-design.md written; spec assembled and committed; gate 1 approved (standing instruction); stage plan
- 2026-11-21 plan written (5 tasks; device baseline gate after task 1; DROP/KEEP branch in task 5)
- 2026-11-21 readiness-1 READY; plan committed; stage impl (task 1, then the device baseline gate)
- 2026-11-21 task 1 dd19f70d (NetStats debug counters, explicit Coil fetcher); device baseline gate next
- 2026-11-21 device baseline on dd19f70d: KEEP; AC-7 before medians: resolver queries 3, new connections 5; qa accidentally started one episode for ~15 s (account mutation possible: resume position)
- 2026-11-21 impl done: dd19f70d, df2199da, 9210b11d, afb4e6ea, faa99d8e; conductor check: check.fast green, no Co-Authored-By, named tests present except the DROP-branch tests (not applicable under KEEP); stage verify
- 2026-11-21 verify round 1: qa-report.md AC-1..6, AC-8 PASS; AC-7 FAIL on resolver queries 4 vs 3 (unit mismatch), connections 5 vs 5; evidence ddc6a79a; qa started one episode for a few seconds by mistake
- 2026-11-21 verify round 1 addendum: AC-7 PASS in one unit (host lookups 3 to 3, connections 5 to 5; re-measured both builds in one sitting); all ACs PASS; cold-start timing NOT CHECKED reliably (host load); evidence 780aa9ca; stage review
- 2026-11-21 review round 1 on 780aa9ca: triage-1.md 7 fix (4 major), 2 reject; codex clean
- 2026-11-21 fix round 1 f84af6cd, e3a8cdb3; review round 2: edge clean, codex clean, tests 1 minor (accepted); qa round 2: check.full green, AC-7 PASS with images over the network (host lookups 7 to 6, Q4 8 to 6, connections 9 to 7), evidence 63dc038e; stage judge
- 2026-11-21 judge-1 CONCERNS, no must-fix: master moved (startup-splash-preload merged 8c6fc926); PuberApp.kt conflicts; integration needed before ship: rebase, check.full, device M1/M2 on the merged head, review of the resolution
- 2026-11-21 rebased onto master 7bcbfd83: 13 commits, head 04bb948c; one conflict (PuberApp.kt imports); test helper fix 04bb948c; check.full green; base updated to 7bcbfd83; review round 3 (integration)
- 2026-11-21 review round 3 (integration): edge clean, codex clean on retry (first attempt hit the usage limit); qa round 3 PASS, evidence d376f671; open: reveal 4.0-4.3 s vs 2.0-2.7 s in the splash run evidence - A/B against master ordered
- 2026-11-21 qa round 3b A/B: revealed median head 3922 ms vs master 4640 ms (ranges overlap, host loaded); no regression; lease likely expired during segment B (noted); evidence d84d47c2; stage judge (round 2)
- 2026-11-21 judge-2 CONCERNS, no must-fix; Co-Authored-By count 0; gate 2 ship; squash next
- 2026-11-21 squashed to 4 commits (tree 7a6c3695 unchanged); shipped: merged --no-ff into master as 5cebf8f8; Orca worktree removed; branch deleted (merged, -d)
