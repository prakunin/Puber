# Gate - startup-first-input-on-tv - round 2

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** ea26f4f1   **Base:** c506d0bd

## Done as asked

Unchanged since judge-1.md#done-as-asked. `git diff --stat 80ca6fb6 ea26f4f1` lists only the three new evidence files (verify-3, verify-4, verify-5), so the code tree is the one judge-1 read (79afebd3 at ce3d859e). check.full was not re-run because no code changed.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1, AC-2, AC-3, AC-5, AC-9 | as in judge-1.md#traceability | unchanged; no code change since | PASS (carried) |
| AC-6, as installed, anchor A | Manual check 3 | verify-2-device.md | PASS (observation, carried) |
| AC-6, as installed, anchors B and C | Manual check 3 | verify-2-device.md: B +1220..+2063 ms, C +1800..+2712 ms after `revealed` | NOT VERIFIED (not met as specified, carried). The box is now force-compiled, so this state cannot be re-measured without a reinstall |
| AC-6, compiled half | Manual check 4 | 2026-11-23-...-verify-5-device.md: filter `speed-profile` (reason=unknown) before and after; 15/15 content starts (5 per anchor); anchors A, B and C met; `key_lat` on 1 start (67/8 ms); on 14 starts there is no line, so the key took 50 ms or less, and a focus-ring screenshot proves each key was handled; 3 atrace starts | PASS (observation). Per-anchor median and p95 cannot be computed: the encoder writes no line at 50 ms or less, so a bound stands in for them |

## Findings

No new reviews or triage since judge-1. Every row of judge-1.md#findings stands. The rejection there (blind-1 F1) is still accepted.

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| verify-4 and verify-5 claim that round 2 reached first content in "3.5-5.2 s" | judge-2 | accepted (evidence wording) | verify-2-device.md has no `first_content` column, and 3521-5355 ms is its `ready_req` range. The as-installed reference is round 1 (verify-device.md: `first_content` 1122-1419 ms) and the baseline (1400/1479/1767 ms) |

## Is round 2 vs round 5 confounded by API health?

Not by API health. The comparison is confounded by other factors, so the drop from about 3.6 s to 50 ms or less is consistent with the compile but does not prove it.

| Start set | API | `first_content` ms | First-key latency |
|---|---|---|---|
| Baseline 5fb5de17, as installed | healthy | 1400-1767 | median about 3.2 s |
| Round 1, as installed | healthy | 1122-1419 | 2494-3891 ms |
| Round 3, compiled | HTTP 502 | 3485-8546 | no `key_lat` line in 6/6 (unconfirmed by screenshot) |
| Round 5, compiled | healthy | 792-961 | 50 ms or less on 14/15 (confirmed), 67 ms on 1 |

The keys were slow while the API was healthy and fast while it failed, so API health does not explain the gap. These confounders remain:

| Confounder | Detail |
|---|---|
| Box load (1 min) | 4.8-6.1 at baseline and 5.69 in round 2, against 2.7-3.9 in rounds 3-5 |
| Compile state | The filter was `speed-profile` both times. "Compiled" means a forced re-dexopt with the profile collected since install (reason install changed to unknown), not a change of filter |
| Key timing | Round 5 reveals by `ready` at about 2.15 s, so anchor A lands 0.5-0.9 s after the reveal. In round 2 it landed before the reveal (median -344 ms) |
| Control | Rounds 2 and 5 are a day apart, and no as-installed control was run on the same day |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Unchanged from judge-1.md#non-functional. The rows above confound the compile-state observation. It is input for `release-startup-compilation`, not a claim this run makes |
| Reliability | Unchanged from judge-1 |
| Maintainability | Unchanged from judge-1 |
| Security / privacy | `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence/` passed again here (80 files). Round 5 screenshots show account data and were not committed |

## Manual checks for the owner

N/A: the compiled half has evidence now. Nothing that is manual by nature is still NOT CHECKED.

## Must fix

none

## Concerns for the owner

1. Concerns 1-5 of judge-1 still stand. Concern 1 now has more weight: round 5 hit B and C from outside the app once the reveal frame was fast. This means the round-2 explanation ("cannot be done from outside the app") depended on that slow frame. Re-measuring as installed now needs a same-signer `install -r`.
2. Treat the round 2 vs round 5 gap as an observation with confounders (see the section above), not as a measured effect of compilation. `release-startup-compilation` should measure both states on the same day at matching box load.
3. The branch has 6 commits (3 code and docs, 3 evidence). Per the owner's squash rule, fold the evidence commits into ce3d859e before `/task:ship` (`state.md` Log, 2026-11-22).
