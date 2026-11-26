LENS: tests  ROUND: 2  BASE: 1f6b03b0 (branch base 90e69aff)

### F1 [minor] The new year-page instrumented test has never been run, so the off-list year row is untested in practice
- Where: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt:560 (`yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus`); production change ShowAllFilterPanel.kt:254-264.
- Problem: plan Fix round 1 row 3 says "compiled only, not run on a device". It is the only test for the new YearPage branch. ShowAllFilterFocusTest is a Compose test and does not need a KinoPub session, so the account ban does not block it.
- Failure scenario: a runtime-only mistake (firstFocus on two nodes, broken traversal) ships with a test that never executed.
- Fix: run `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest` and record the result for row 3 in the plan and qa-report.

### F2 [nit] The test name says "is the checked row", but nothing checks the selected state or the click
- Where: ShowAllFilterFocusTest.kt:560-571
- Problem: the test only waits for focus on the "2003–2007" row; `selected = false` or a wrong onClick would still pass.
- Fix: press Center and assert `filterActions().last() == ShowAllAction.YearChosen(offList)`; assert selected semantics if TvSidePanelItem exposes it.

Checked, nothing to report: touched unit tests run (ContentListInteractorBrowseFilterTest 3/3, ShowAllVMTest 53/53); row 2 test would fail on 1f6b03b0 and pins the 90e69aff key shape; row 4 test asserts state (config and draft), green on old code as the plan says; row 5 assertion fails on its own.

### Unproven
- ContentListInteractorBrowseFilterTest.kt:27 checks the old key only for default preferences; matters only if browseFilterKeyParts ever depended on preferences.
