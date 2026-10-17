# Pre-fix red check: TabHostTelemetryWiringTest

Worktree: /home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label at 8b84a6cf (Tasks 3-4 fix not applied).
Device: emulator-5554 (puber_tv_36, API 36), leased, released after the run, left running.
Command: `AGENT_GRADLE_SERIAL=1 make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`
`make` builds and runs through `./tools/agentw` this way, so the guard does not refuse it; the worktree's own APKs were built and the app was not uninstalled or cleared.
Outcome: 6 tests, 4 failed, 2 passed (exit 2 = red as expected overall).

| Test | AC | Expected | Result | Failure |
|------|----|----------|--------|---------|
| backFromRootPushedScreenNeverRecordsMain | AC-7 | red | RED | assertEquals at TabHostTelemetryWiringTest.kt:100: expected WiringHomeScreen but was WiringTabHostScreen (Back recorded the host class, not the tab content) |
| backOntoTheHostRecordsNoTabReannounce | AC-4 | red | RED | assertTrue at kt:113 (a `Nav TAB` is recorded after Back, or the screen is rewritten) |
| tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen | AC-6 | red | RED, but at a wait, not at a bug assertion | ComposeTimeoutException "Condition still not satisfied after 1000 ms" in click(BACK_TAG, CHILD_TAG), kt:155 |
| tabSwitchThenRootPushAndBackNamesTheNewTab | AC-3 | red | RED | assertEquals at kt:175: expected WiringOtherScreen but was WiringTabHostScreen |
| openingAnotherTabIsRecordedAndSetsTheScreen | AC-5 | green | PASS | n/a |
| reselectingTheCurrentTabWithANewInstanceIsRecorded | AC-5 | green | PASS | n/a |

Caveats for the conductor:
- AC-6 is red because the Child tag never reappears within 1 s after Back, not because the telemetry assertion (`nav back to=` / `Telemetry.screen`) failed. Whether that wait failure is a consequence of the bug (for example the replayed `Open` resetting the tab-local stack) or a fixture timing issue is not established; the reds of AC-7, AC-4 and AC-3 are genuine assertion failures. Worth confirming that the AC-6 test goes green after the fix and that its failure mode before the fix is the intended one.
- The AC-7 and AC-3 failures show the host class (WiringTabHostScreen) rather than the `MainScreen` fallback named in the test design; the fixture's host is that class.

No production code or tests were modified. Raw log kept out of the repo.
