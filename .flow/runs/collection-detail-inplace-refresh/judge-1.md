# Gate - collection-detail-inplace-refresh - round 1

**Decision:** PASS
**Head:** 28f986b0   **Base:** 0e4449e2

## Done as asked

`git diff 0e4449e2...HEAD -- app/src/main` touches three files:
- `CollectionDetailVM.kt`: `Loading` is published only when the state is not `Content`; there is one `loadJob`, which is cancelled when a new load starts; a load awaits pending mutations before it publishes; `isSaved` is patched only for ids whose saves are still active. `launchMutation` and `awaitPendingMutations` are not edited.
- `FocusTargetState.kt`: the change is behind `holdPendingFocusTarget`, which defaults to false. Each changed branch falls back to the base code when the flag is false.
- `CollectionDetailScreenContent.kt`: one argument, `holdPendingFocusTarget = true`.

The brief listed `CollectionDetailScreenContent.kt` and the shared helper as out of scope. The owner widened scope to both (state.md Decisions, addenda after verify round 1 and review round 1). Nothing else the brief excluded is in the diff: Loading on first load and on Retry is unchanged, there is no typed remap, and there is no hoisted UI state.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | unit `returnedChanges_publishRefetchedContentWithoutLoading`, `playerReturn_publishRefetchedContentWithoutLoading`; device Manual 1 | qa-report Review fix round 1: no spinner frame, 4/4 on 48ff9085. Unit class 17/17 in this gate's check.full | PASS |
| AC-2 (Details: watched, bookmark) | device Manual 1-2; instrumented `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | evidence doc :111, same card `[662,144][1259,480]`, 4/4 and 4/4; instrumented 3/3 | PASS |
| AC-2 (Back from the player after playback) | Manual 3 | not run: playback writes watch progress to the account (triage-1 row 7) | NOT CHECKED |
| AC-3 | unit `firstLoad_publishesLoadingBeforeContent`, `retry_publishesLoadingAfterError` | test lines 115-142, green | PASS |
| AC-4 | unit `failedRefresh_keepsContentAndShowsMessage` | test :145-157 asserts the same Content and the message, green | PASS |
| AC-5 | unit `overlappingRefreshes_publishOnlyTheLatestFetch` | test :160-180 completes the fetches in reverse order, green | PASS |
| AC-6 | unit `toggleDuringRefresh_*` x2, `settledToggleWhileAnotherSaveIsPending_*`, `failedToggleDuringRefresh_*`, `refreshAfterPublishedToggle_*`, `gridToggleBeforeReturn_*`, `toggleBeforeFailedRefresh_*`; device Manual 2 | green; the strengthened test :212 catches a missing await (review-tests-2); device 4/4 | PASS |
| AC-7 | instrumented `CollectionDetailScreenContentFocusTest` (5 tests) | qa-report Review fix round 1: 5/5, 3 runs of 3 | PASS |
| AC-8 | unit `emptyReturnedChanges_doNotRefetch`, `returnedChanges_areForwardedToParentOnBack`, `back_waitsForSaveAndForwardsBookmarkChange` | green | PASS |

check.full re-run here at 28f986b0: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> BUILD SUCCESSFUL, exit 0; the only commit after 48ff9085 is docs. Worktree clean.

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| Pending request pins a stale target in non-target rows | blind-1 F1, edge-1 F1 (major) | fixed | 48ff9085 (opt-in flag); guard `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` red on a021e92c, green on HEAD |
| `resolvedItemId` undoes the initial-focus effect | codex-1 (major) | fixed | 48ff9085; guard `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` red on a021e92c, green on HEAD |
| Shared helper change with no guard on other callers | blind-1 F2 | fixed | 48ff9085: flag default false; helper regression classes green (qa Review fix round 1) |
| `savedSinceLastPublish` expires per batch | codex-1 (minor) | fixed | 48ff9085 `saveJobItemIds`; test `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` |
| No test catches a missing `awaitPendingMutations()` | tests-1 F1 | fixed | 48ff9085; assertion at `CollectionDetailVMTest.kt:212` |
| `firstLoad_*` overclaims the Loading guard | tests-1 F2 | fixed (doc) | test-design row restated |
| AC-2 player clause not run | tests-1 F3 | rejected as a code finding; carried here | progress write needs the owner's explicit authorization (AGENTS.md); listed in Manual checks |
| Retry gate for default callers untested; test-design R9 and the `removedCardInAnUntargeted...` row still claim it is covered | tests-2 F1 (minor) | accepted | real, small; the gate is a one-line branch, and the base path is unchanged when the flag is false |
| Plan Task 4 record cites `:200`, the assertion is at `:212` | tests-2 F2 (nit) | accepted | still `:200` at plan :315; fix at squash |

Rejections spot-checked:
- triage-1 row 7: playback is an account mutation (AGENTS.md, Runtime). I accept it.
- triage-2 row 1: I accept the reviewer's trace. In the untargeted-row guard, the first `requestFocus()` succeeds, so the guard does not exercise the retry gate.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One fetch per return, as on base. The previous fetch is cancelled. The retry adds at most `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` frames, and only on CollectionDetail. `saveJobItemIds` is pruned at each load. |
| Reliability | A failed refresh keeps `Content` and shows the message. The first-load and Retry error paths are unchanged. Cancelling `loadJob` does not reach the exception handler. Rapid toggles can delay a publish until the saves settle (edge-1 unproven); this is acceptable for a grid. |
| Maintainability | Follows the conventions (JUnit 5, `PuberVM`, pure content). The flag has KDoc. No dead code or duplicated helper. The committed spec's Design and Test design copies predate both addenda; the plan header points to them. |
| Security / privacy | No secrets or account data in the diff. `tools/mobile-evidence-audit.sh` on the evidence doc: `evidence_audit_status=passed`. Account toggles end in their starting state (evidence :16, :53, :89). Installs are `compatible_replace` through install-preserve; there was no owner-authorised install. |
| TV | Focus, scroll and the absence of a spinner were checked on emulator-5554 (AVD puber_tv_36). Home, SectionRow, VideoGrid, Show all and Collections focus classes are green. The `HomeSectionRemovalFocusTest` failure is pre-existing (run `home-section-removal-focus-test-red`). |

## Manual checks for the owner

1. AC-2, return from the player (Manual 3). This writes watch progress to the account.
   1. Install the worktree dev APK with `tools/android-apk-install-preserve` on a leased emulator.
   2. Open a collection and press DOWN x3, then RIGHT, to focus a card in row 3 or lower.
   3. Long-press the card and choose Play. Let it play at least 30 s, then press BACK until CollectionDetail shows.
   4. Expect: no full-screen spinner, the same card focused, the scroll position kept, and the card's watched or progress state updated. Repeat 3 times.

## Must fix

none

## Concerns for the owner

none
