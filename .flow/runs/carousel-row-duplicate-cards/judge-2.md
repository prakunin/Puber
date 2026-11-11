# Gate - carousel-row-duplicate-cards - round 2

**Decision:** PASS
**Head:** 5893b70a   **Base:** 901d820d

## Done as asked

Unchanged from [judge-1.md](judge-1.md#done-as-asked). `git diff a6af2082..HEAD` touches only the evidence file. `git diff 901d820d...HEAD -- app/src/main` is still `ContentListPagingVM.restoreLoadedDepth` and its KDoc.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1..AC-6 | unit tests as in [judge-1.md](judge-1.md#traceability) | check.full green at a6af2082 (judge run); tests unchanged since; red on base recorded in plan Implementation record | PASS |
| AC-7 (no title twice after a refresh at depth) | Manual 1, refresh from a watched-indicators flip | evidence round 2: Popular `page=1..6` requested once each after the flip; 80 titles walked, none repeated, no crash | PASS |
| AC-7 (focus on the opened card after a refresh) | Manual 1 as written (open, save/unsave, BACK) | Not run: needs an account mutation. Focus by id after open + BACK without a refresh: PASS in round 1. Focus kept through the same restore in the grid: PASS in round 2 (AC-8). | NOT CHECKED (manual, section 6) |
| AC-8 | Manual 2, same flip with the grid at row 24 | evidence round 2: pages 1..6 once per VM; focus stayed on "Кид против Кэт"; 85 distinct titles | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Device run never triggered the refresh | tests F1, codex 2 (major); judge-1 Concern 1 | fixed | 5893b70a, evidence round 2 (request counts per page after the flip) |
| Row focus went to the first card after the flip | qa round 2 | rejected: not this change, not the AC-7 route | Settings is a main-screen tab (`MainUIMapper.kt:112`, `NavigationPreferencesRepository.kt:163-164`), so returning rebuilds the Series tab. The diff has no UI or focus code. AC-7 names open-title-and-return, which keeps the tab. |
| Round 2 row walk recovered 80 titles against 84 in round 1 | qa round 2 | accepted (minor) | The walk tool skipped a few cards. Round 1 read all 84 with no repeats. The no-repeat proof is carried by the AC-1/AC-2 unit tests. |
| No red on base on device | qa round 1 and 2 | accepted | Live data did not read ahead on the emulator. Red on base exists for the five P0 unit tests (plan, Red run). |
| tests F2, codex 1, tests F3, codex adversarial, unproven lower-`current` reply | review round 1 | as in [judge-1.md](judge-1.md#findings) | caabfc89, a6af2082 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Unchanged from judge-1. The device run shows one request per page per VM on refresh. |
| Reliability | Unchanged from judge-1. No crash and no `was already used` across both device rounds. |
| Maintainability | Unchanged from judge-1. The spec Design text saying "no second counter" is stale; the Q3 row supersedes it. |
| Security / privacy | `tools/mobile-evidence-audit.sh` re-run by judge on `docs/superpowers/evidence`: `passed`. The evidence holds titles and request counts only. Settings were restored and read back. |
| TV | Grid focus survives the restore. Row focus after a refresh on the AC-7 route is in section 6. |

## Manual checks for the owner

1. AC-7 focus after a refresh (needs an owner-authorised save or unsave). Use a dev build of 5893b70a on a leased emulator with "Anime in general lists" off. On the Series tab Popular row, press `DPAD_RIGHT` past 75 cards and note the focused title. Press `DPAD_CENTER`, save or unsave the title, then press `BACK`. Check that focus is on the noted card. Undo the save or unsave afterwards.

## Must fix

none

## Concerns for the owner

none
