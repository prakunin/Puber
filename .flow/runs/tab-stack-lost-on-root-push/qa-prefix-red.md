# QA pre-fix check: tab-stack-lost-on-root-push

Worktree a41722c2 (tests 57f15531, fix not applied). Device emulator-5554 (puber_tv_36, API 36), leased and released.
Command: `make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=<fqcn>` (AGENT_GRADLE_SERIAL=1).

## TabHostTelemetryWiringTest: 10 tests, 3 failed, 7 passed (matches expectation)
Red (all `androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 1000 ms`, raised in waitForTag, i.e. the expected tab-local screen never reappears after Back):
- tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack - TabHostTelemetryWiringTest.kt:171
- rootPushAndBackRestoresBothTabStacks - TabHostTelemetryWiringTest.kt:192 (click(BACK_TAG, OTHER_CHILD_TAG))
- resumeIsDeliveredToRestoredTabLocalScreenAfterRootPushAndBack - TabHostTelemetryWiringTest.kt:258
Green (7): backFromRootPushedScreenNeverRecordsMain, backOntoTheHostRecordsNoTabReannounce, openingAnotherTabIsRecordedAndSetsTheScreen, reselectingTheCurrentTabWithANewInstanceIsRecorded, tabSwitchThenRootPushAndBackNamesTheNewTab, replacedHostStartsAtTabRootsWhenShownAgain, resumeIsDeliveredToTabRootAfterRootPushAndBack.
(Per-test pass names inferred: runner reported 3 failures of 10, the red three are named above.)

## TabFlowLifecycleTest: 1 test, 1 passed (recorded as is, risk K7)
Starting 1 tests / Finished 1 tests, BUILD SUCCESSFUL, no failures.

No production or test code changed; app data preserved; emulator left running.
