# Gate - carousel-row-duplicate-cards - round 1

**Decision:** CONCERNS
**Head:** a6af2082   **Base:** 901d820d

## Done as asked

The production diff is `ContentListPagingVM.restoreLoadedDepth` only (`ContentListPagingVM.kt:199-232`). It adds a `requests` counter bounded by `MAX_RESTORED_PAGES - 1`, moves the cursor with `page = maxOf(page, next.pagination.current)`, and de-duplicates with a `LinkedHashMap.putIfAbsent` (first occurrence wins). This matches the spec Design as amended by Q3. `Paginator`, Home rows and the interactor are untouched (Out of scope respected). `ShowAllVM` inherits the change and has no override.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds`, `..._walksOnFromTheLastServerPageRead` | check.full green at a6af2082 (judge run, rc 0); red on base recorded in the plan | PASS |
| AC-2 | `refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder`, `ShowAllVMTest.retry_ofAGridWithOverlappingPages_publishesEachIdOnce` | same | PASS |
| AC-3 | four existing `SectionVMRefreshDepthTest` cases, unchanged (diff has no `-` lines in tests) | same | PASS |
| AC-4 | `refresh_ofARowDeeperThanTheRestoreCeiling_...`, `refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling`, `refresh_whosePagesReportALowerPageThanRequested_...`, `refresh_whoseFirstPageReportsPageZero_...` | same | PASS |
| AC-5 | `refresh_throughEmptyPagesThatReadAhead_stopsAfterTheEmptyPageBudget` | same | PASS |
| AC-6 | `refresh_whoseLaterPageFails_leavesTheRowAsItWas` | same | PASS |
| AC-7 | Manual 1 | evidence file AC-7: walk + open + BACK; no refresh ran, so the changed code was not reached | NOT CHECKED (refresh half) |
| AC-8 | Manual 2 | evidence file AC-8: same limit | NOT CHECKED (refresh half) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Device run never triggered the refresh | tests F1, codex 2 (major) | carried as NOT CHECKED | triage-1 #1. Its reason is not accepted as stated (see Concerns 1). |
| Lost `MAX_RESTORED_PAGES` coercion was not caught | tests F2 (minor) | fixed | caabfc89, `refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling` |
| `synchronizedList` iteration race in the ShowAll test | codex 1 (minor) | fixed | caabfc89, `CopyOnWriteArrayList` |
| Test-design mutation claims overstated | tests F3 (nit) | fixed | a6af2082, test-design.md revised |
| Codex adversarial "needs-attention" on `restoreLoadedDepth` | codex 1 | rejected, accepted | Re-traced: the loop is bounded by `requests`; the cursor only moves forward; `CancellationException` is rethrown |
| Unproven: a reply with `current` lower than requested publishes a shallow pagination | blind, edge | accepted as unproven | `fetchFilteredPage` enforces `current == requested` (`ContentListInteractor.kt:147`); `FreshSectionPager` answers `current = requestedPage` (`FreshSectionPager.kt:56`) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One map holding at most 10 pages of items per restore. Fewer requests than base on filtered rows. |
| Reliability | Failure, cancellation and the empty-chain paths are unchanged (AC-5, AC-6). The ShowAll test polls in real time with a 2 s timeout, following the existing pattern in that file. |
| Maintainability | KDoc and the inline comment are updated. The spec Design "Decisions" and "Alternatives rejected" rows still say "no second counter"; that is superseded by the Q3 row in the same spec. Stale text, not a defect. |
| Security / privacy | No secrets or account data in the diff. The evidence keeps titles only, and qa reports that the audit passed. |
| TV | Lazy keys and focus by id are unchanged. Focus after de-duplication at depth is not observed on device (Concerns 1). |

## Manual checks for the owner

1. AC-7 and AC-8 as written (needs an account mutation the owner must authorise). Use a dev build of a6af2082 on a leased emulator. Turn Settings > Content "Anime in general lists" off. On the Series tab Popular row, press `DPAD_RIGHT` past 75 cards and note the focused title. Open it with `DPAD_CENTER`, save or unsave it, then press `BACK`. Check that focus is on the noted card, then scroll to the end and check that no title repeats. Repeat in Popular "Show all". Undo the save or unsave afterwards.

## Must fix

none

## Concerns for the owner

1. The triage claim that a refresh "needs a save/unsave/watched account mutation" is refuted by the code. A local display-setting flip also triggers a refresh. `NavigationPreferencesRepository.kt:48` emits on `hideWatched` or `showWatchedIndicators`. `SectionVM.kt:70` and `ShowAllVM.kt:56` call `refreshFirstPage()` on that signal at the current depth. So the restore path, including focus after de-duplication, could have been run on device without any account mutation. Example: walk the Popular row deep with anime off, toggle "show watched indicators" or "hide watched", return. That check was not attempted, and focus after de-duplication has no unit test. The fix's runtime effect is therefore unobserved on device, and no red on base was shown there. Recommended: one qa-device pass using a setting flip before ship.
2. Manual 3 ("show anime" on) was not run. It does not exercise the read-ahead path, so the risk is low.
