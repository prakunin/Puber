# QA report - showall-rail-close-focus-shift

## Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug (wt, HEAD dda4eb23) -> pass (BUILD SUCCESSFUL; mostly up-to-date from the implementer's identical run, lintDevDebug re-executed)
DEVICE: emulator-5554 (puber_tv_36), leased, released; left running. HEAD APK sha256 6a900477... installed with install-preserve (installed / compatible_replace, no destructive action), on-device sha equal. Base comparison = HEAD with only ShowAllScreenContent.kt reverted to 6b087697, restored afterwards (git diff empty) and HEAD APK reinstalled.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | D1: lower-row left-column card, LEFT then RIGHT, 3/3 screens byte-identical (same card). Base build, same card "Krovavaya nadpis": 3/3 closed one row up on "Korol shantazha". Instrumented T-I1 green on HEAD |
| AC-2 | PASS | D2: top-of-grid card and a scrolled position, 3/3 each byte-identical. T-I1b (top row) green on HEAD (note: it also passes on base, it is a guard) |
| AC-3 | PASS (re-pick) / NOT CHECKED (Back) | D3b: re-pick current tab with rail open, 3/3 same card. Back with the rail open on Show all pops the screen to the Movies tab (same on base), so "Back closes the rail" is not reachable here as the AC states it; not exercised |
| AC-4 | PASS | D4: fresh entry, RIGHT from the rail gives the first card focus, rail closed. Unit T-U2 green. Instrumented T-I2 was not written |
| AC-5 | PASS | Unit T-U3/T-U4/T-U5 green. D5a: Details, Back, LEFT at 1.0 s keeps the rail open 4/4. D5b: LEFT at 0.1-0.8 s is swallowed, identical on base, no focus theft. D5c: context dialog holds focus 3 s; Back closes it; RIGHT then closes the rail on the same card. Instrumented T-I3/T-I4 not written |
| AC-6 | PASS (unit only) | T-U6 neighbour restore green; not reproducible on device |
| AC-7 | PASS | 8 new GridFocusMemoryTest cases + existing, green in testDevDebugUnitTest |
| AC-8 | PASS | ShowAllScreenContentFocusTest 5/5 green on HEAD; History tab: card kept after LEFT/RIGHT (D6). Diff scope: I did not review the diff beyond the implementation record (one production file) |
| AC-9 | PASS | ShowAllRailCloseFocusTest 2/2 green on HEAD; on base production file showAllRailCloseKeepsFocusOnTheLowerRowCard FAILED "expected:<Card 16> but was:<Card 13>" (the top-row test passes on base) |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/showall-rail-close-focus-shift/docs/superpowers/evidence/2026-10-18-showall-rail-close-focus-shift-device.md (audit passed; committed in wt as 97be2417)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Observations for the owner (not defects of this change): (1) LEFT within about 0.8 s after Back from Details is swallowed, the same on base. (2) LEFT while the long-press context dialog is open opens the rail behind it; not compared on base, not touched by the handoff answer. (3) Slip: one stray OK started a trailer for a few seconds on the emulator; nothing was saved by me, no uninstall/clear/downgrade.

## Round 2

Scope: HEAD e9595021, tests only (`ShowAllRailCloseFocusTest.kt`); production code unchanged since round 1.

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass. First run with `--rerun-tasks` and lint excluded (34 tasks executed, so the new file was linted by detekt), then the plain command with lint (BUILD SUCCESSFUL).
DEVICE: emulator-5554 (puber_tv_36), booted by me (none was running), leased, released; left running. Worktree's own APK, installed by `make itest`; no uninstall, clear or downgrade.

| Run | Result |
|-----|--------|
| `make DEVICE=emulator-5554 itest TESTS=...ShowAllRailCloseFocusTest` | 5/5 passed, 0 failed, 0 skipped |
| `make DEVICE=emulator-5554 itest TESTS=...ShowAllScreenContentFocusTest` | 5/5 passed, 0 failed, 0 skipped |

ShowAllRailCloseFocusTest tests (names from source; the XML of this run was overwritten by the second run, the console reported 5 completed, 0 failed): `showAllRailCloseKeepsFocusOnTheLowerRowCard`, `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling`, `showAllRailCloseWithoutMemoryBehavesAsBase`, `showAllHandoffResponseDoesNotStealFocusFromADialog`, `showAllRecompositionAfterSettleDoesNotRefocus`.

ACS changed since round 1:
| AC | Status | Evidence |
|----|--------|----------|
| AC-2 | PASS | Round 1 device D2, plus `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling` green (adds the scrolled case) |
| AC-3 | PASS (re-pick) / NOT CHECKED (Back) | Unchanged from round 1 |
| AC-4 | PASS | Round 1 D4 and unit T-U2, plus `showAllRailCloseWithoutMemoryBehavesAsBase` green |
| AC-5 | PASS | Round 1 D5a-c and unit T-U3..T-U5, plus `showAllHandoffResponseDoesNotStealFocusFromADialog` and `showAllRecompositionAfterSettleDoesNotRefocus` green |

AC-1, AC-6, AC-7, AC-8 and AC-9 unchanged from round 1 (ShowAllScreenContentFocusTest 5/5 again for AC-8). Mutation check (new tests failing on base) not run this round.

TESTS ADDED: none
EVIDENCE: round 1 file; no new device evidence file (instrumented test run only).
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
