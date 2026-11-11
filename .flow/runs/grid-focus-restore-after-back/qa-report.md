## Baseline

Base e9184b79 (worktree 511590c1, production code identical). Device `emulator-5554`, dev debug APK installed with install-preserve (`installed`). Evidence: `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md` in the worktree (audit passed).

| AC | Screen | Base result (3 repeats each) |
|----|--------|------------------------------|
| AC-1 | Collections, card in lower row, OK, Back | Same card focused, viewport kept. Symptom not reproduced (also once deeper, DOWN x9) |
| AC-6 | Collections, LEFT then RIGHT on a column-1 card | Same card focused |
| AC-2 | CollectionDetail opened from Collections | No card focused after Back; first DOWN lands on the first card. Reproduced |
| AC-2 | CollectionDetail opened from Home | No card focused after Back. Reproduced |
| AC-8 | Bookmarks and History, card, OK, Back | Same card focused. Not affected |
| AC-8 | Bookmarks and History, rail LEFT then RIGHT | Same card focused |

Consequence: the CollectionDetail change is needed; Collections already restores on base, so its instrumented AC-9 test may be green on base (the `focusRestorer` plus saved index works on the device). Bookmarks and History need no code change (Q1 stays closed).

Notes: the Bookmarks and Collections tabs are off by default; they were enabled in Settings > Navigation for the run and disabled again. A rail LEFT from a column-2 card only moves to column 1, so rail checks use a column-1 card (Manual 1 and 6 read "LEFT (rail)" and need the same). No account data changed. Lease released.

## Red checkpoint

Plan step P2. Worktree `/home/user1/orca/workspaces/Puber/grid-focus-restore-after-back`, HEAD edbdd0f5, two uncommitted androidTest files, production code as at base e9184b79. Device `emulator-5554` (puber_tv_36, API 36), leased and released. Run with `make DEVICE=emulator-5554 itest TESTS=<class>`, `AGENT_GRADLE_SERIAL=1`.

| Class | Test | Result | Message |
|-------|------|--------|---------|
| CollectionDetailScreenContentFocusTest | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | FAIL | `ComposeTimeoutException: Condition still not satisfied after 5000 ms` at test line 46 (`waitUntil` focusedWith(title) after `emulateSavedInstanceStateRestore`) |
| CollectionDetailScreenContentFocusTest | `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | FAIL | same exception, test line 67 (`waitUntil` focusedWith(title)) |
| CollectionDetailScreenContentFocusTest | `collectionDetailFocusesTheFirstCardOnFirstLoad` | FAIL | same exception, test line 75 (`waitUntil` focusedWith(first item title)) |
| CollectionDetailScreenContentFocusTest | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | PASS (guard) | - |
| CollectionsScreenContentFocusTest | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` | PASS (guard) | - |

Matches the expected red/green split. Result XML copies: `/tmp/claude-1000/-mnt-data-projects-PetProjets-Puber/e4afff0d-e96a-4e93-a482-85d44130d1c3/scratchpad/d/` (CollectionDetail), logs `detail.log`, `coll.log` in the same scratchpad.

## Round 1

HEAD 5ea2899e, base e9184b79. Device `emulator-5554`. Evidence (uncommitted): `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-device.md` in the worktree (audit passed).

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | `CollectionsScreenContentFocusTest` 1/1; Manual 2 and 6 (3x each): same card, viewport kept |
| AC-2 | FAIL (instrumented) / PASS (device) | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` passes; `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` fails twice (D1). Manual 3 from Collections and Home, 3x each: same card focused (base: none) |
| AC-3 | PASS | `collectionDetailFocusesTheFirstCardOnFirstLoad`, `HomeCollectionBackFocusTest` 2/2; Manual 4 from Collections and Home, 3x each |
| AC-5 | FAIL (instrumented) / PASS (device, no item change) | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` fails twice, green on base (D2). Manual 7: menu keeps focus, 3x, items unchanged |
| AC-6 | PASS | Manual 6, 3x |
| AC-7 | PASS | unit `GridFocusMemoryTest`, in check.full, green |
| AC-8 | PASS (Show all manual NOT CHECKED) | `ShowAllScreenContentFocusTest` 5/5, `ShowAllRailCloseFocusTest` 5/5; Manual 5 Bookmarks and History 3x each; History class 20/30, the 10 failures are Russian hard-coded strings on an en-US device (not focus tests); Show all manual not run (no Show all card reached on Home) |
| AC-9a, AC-9b | FAIL | the CollectionDetail class is 2/4 (D1, D2); Collections guard green |

Defects:

1. D1 `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry`: after restore, a request on the first card, then `waitUntil` the saved card focused times out at 5000 ms (test line 67). Red on base, still red. The same card does return on the device (Manual 3), so this is either the test seam or the first-focus redirect not winning against an early landing (risk R1).
2. D2 `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`: guard, green on base, now fails: after the focused card is removed from `items` with the menu open, "Watch movie" is not focused (test line 104). The item-focus redirect in `CollectionDetailScreenContent.kt` takes focus from the dialog (readiness-1 note 2).

Lease: the 600 s lease expired during the manual run; released answered `resource_already_unlocked`. Rail tabs switched off again.

## Round 2

HEAD 3e1530f0, base e9184b79. Device `emulator-5554`. Evidence (uncommitted): `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-device.md` in the worktree (audit passed). Tests added: none.

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL). Detekt was UP-TO-DATE in that run; rerun with `--rerun`: `:app:detektDevDebugUnitTest` prints "There were 7 compiler errors found during analysis" on the worktree and, identically, on the main checkout at e9184b79; `:app:detektDevDebug` prints 83 on the worktree. Both tasks pass; the message is not new.

Red on base (`CollectionDetailScreenContent.kt` from e9184b79, restored after; no diff left):

| Test | Base | HEAD |
|------|------|------|
| `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | FAIL (line 56) | PASS |
| `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` | FAIL (line 76) | FAIL x3 (line 76, `ComposeTimeoutException` 5000 ms) |
| `collectionDetailFocusesTheFirstCardOnFirstLoad` | FAIL (line 84) | PASS |
| `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | PASS (guard) | PASS |

`CollectionsScreenContentFocusTest` 1/1 and `home.component.HomeCollectionBackFocusTest` 2/2 pass on HEAD.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unchanged from round 1; `CollectionsScreenContentFocusTest` 1/1 again on HEAD |
| AC-2 | FAIL (instrumented) / PASS (device) | `...FocusReturnsToTheFocusedCardAfterReentry` passes; the host-entry test fails (D3). Manual 3 from Collections and Home, 3x each: same card `[662,415][1259,751]` (base: none) |
| AC-3 | PASS | `...FocusesTheFirstCardOnFirstLoad`, `HomeCollectionBackFocusTest` 2/2; Manual 4: first card on load and BACK to the opener, 6 of 6 |
| AC-5 | PASS | D2 fixed: `...ItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` green; Manual 7 from both origins, 3x each: "Watch movie" focused, and after BACK the same card focused (D16) |
| AC-6 | PASS | Unchanged from round 1 (Manual 6, 3x) |
| AC-7 | PASS | Unchanged from round 1 (`GridFocusMemoryTest`, in check.full, green) |
| AC-8 | PASS (Show all manual NOT CHECKED) | Unchanged from round 1 (Show all classes 5/5 and 5/5, Manual 5 on Bookmarks and History; History class 20/30, 10 failures from Russian hard-coded strings on an en-US device, not focus tests; no Show all card reached on Home) |
| AC-9a, AC-9b | FAIL | CollectionDetail class 3/4: host-entry test red on HEAD (D3); Collections guard green |

Defects:

3. D3 `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` (test-design row AC-2, AC-9, red): fails at HEAD with the same `ComposeTimeoutException` at its focus wait (test line 76) as on base, in all 3 runs. Reproduce: `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`. After `host.requestFocus()` fired once `Card 1` is composed and before the entry frame, the saved card is not focused within 5 s. The device does restore the card (Manual 3), so this is the R1 ordering the test targets (the entry landing on `Card 1`) or a seam gap; the test does not tell the two apart. Not adjusted by qa.

Lease: 5400 s, released. The emulator was not running at the start (started `-no-window`); it died once after all checks, was restarted, app data and login intact. Collections tab switched off again.

## Review fix round 1

HEAD 4efeed73 (tests only), emulator-5554, `make DEVICE=emulator-5554 itest TESTS=<FQCN>`, lease released. Evidence: `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-device.md` (audit passed, not committed).

| AC | Status | Evidence |
|----|--------|----------|
| AC-5 | PASS | `CollectionDetailScreenContentFocusTest` 3/3 on HEAD (two runs). The menu test sends `KEYCODE_BACK` via `Instrumentation.sendKeyDownUpSync`; the menu closes through its BackHandler and the neighbour card is focused. |
| AC-8 | PASS (Show all manual NOT CHECKED) | `HistoryScreenContentTest` fails the same 10 tests on HEAD and on base (production files restored from e9184b79, then reverted; `git status` empty). Triage #3 is pre-existing, not caused by this run. |

Note: the diff holds two production files (`CollectionDetailScreenContent.kt`, `ShowAllScreenContent.kt`), not three.
