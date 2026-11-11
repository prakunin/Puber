LENS: tests  ROUND: 1  BASE: 6b087697

Suite state: I did not re-run `check.full`. The qa report records it green at dda4eb23, and production code is unchanged since. The report also records `ShowAllRailCloseFocusTest` 2/2 green on HEAD, and the lower-row test red on base (`expected:<Card 16> but was:<Card 13>`). That meets the red-run rule for the one new P0 instrumented test (T-I1). The new unit cases T-U1 to T-U7 are red by absence of `onHandoff` on base, as the test design states.

### F1 [minor] AC-3 "Back closes the rail" has no test and no recorded manual check
- Where: `/mnt/data/projects/PetProjets/Puber/.flow/runs/showall-rail-close-focus-shift/brief.md` (AC-3); `/mnt/data/projects/PetProjets/Puber/.flow/runs/showall-rail-close-focus-shift/qa-report.md` (AC-3 row)
- Problem: AC-3 has two halves. Re-picking the tab was checked on the device only (D3b, 3/3). There is no automated test for it, because T-I6 was never written. The Back half is "NOT CHECKED". qa found that Back with the rail open on Show all pops the screen, the same as on base, so the AC wording is not reachable.
- Failure scenario: If a later change breaks the re-pick path (`beginHandoff(expectsNewContent = false)` at `MainScreenComponent.kt:272`), no test fails. The AC stays marked as met although half of it was never exercised.
- Fix: Reword AC-3 in the brief to re-pick only, and note that Back cannot reach the rail here. Optionally add T-I6 if the harness can call `beginHandoff(false)` directly.

### F2 [minor] Focus-stealing and recomposition risks (score 6) rely on unit tests plus device checks; their instrumented tests are not written
- Where: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt` (T-I2, T-I3, T-I4 missing)
- Problem: The test design says every risk scoring 6 or more gets a test at the level where it fails. R2 (focus stealing) and R3 (latch re-fires after recomposition) have only the unit cases T-U3, T-U4 and T-U5 plus manual D5. The unit harness uses a fake clock with no recomposition, so it cannot see a bug in the `latchedHandoffId` / `LaunchedEffect(latchedHandoffId, contentFocusActive)` wiring in `rememberGridFocusMemory`.
- Failure scenario: Someone reworks the latch, for example reads `pendingRequestId` directly. The effect then restarts on `contentFocusActive` changes and the `answeredHandoffId` guard is bypassed. Every unit test stays green, because the guard is inside `GridFocusMemory`. Only device runs would catch focus being pulled back.
- Fix: Add T-I4: after the rail closes, move focus to another card, force a recomposition or toggle `contentFocusActive`, and assert focus stays. Also add T-I2 for a null memory. The record's "Not written" list already admits these were skipped.

### F3 [nit] `showAllRailCloseKeepsFocusOnTheTopRowCard` passes on base and is not labelled as a guard
- Where: `ShowAllRailCloseFocusTest.kt`, `showAllRailCloseKeepsFocusOnTheTopRowCard`
- Problem: On base the blind landing is the top-left card of the topmost row, which is the card already focused. The test cannot fail on the bug. qa notes this in its report, but the test file does not say so.
- Failure scenario: A reader takes it for AC-2 coverage of the fix. It only guards against the fix moving focus off the top row.
- Fix: Add a one-line comment in the file saying it is a guard and passes on base. The "scrolled position" part of AC-2 has no automated case (only D2 on the device), so either add that case or say so in the doc.

### Unproven
- `GridFocusMemoryTest.kt` `onHandoff_leavesPendingEntryUntouched`: asserts that a handoff followed by `reconcile()` restores twice (`restore:5:true` twice). That pins the double-restore as intended. It is only a problem if Back-from-Details together with a rail close can happen in one entry window, and nothing shows that it does.
