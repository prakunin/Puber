LENS: tests  ROUND: 2  BASE: f68f3524 (incremental to 3759abcf)

### F1 [minor] The plan does not show that the new menu-close test passes on head
- Fix: qa round 2 runs ShowAllRailCloseFocusTest and VideoItemPosterTest on head and records green.
### F2 [minor] The test design names the wrong class and setup for the menu-close test
- Fix: AC-9 row: ShowAllRailCloseFocusTest, long press, row 6 column 0.
### F3 [minor] Nothing tests the new load-more threshold
- Fix: optional unit check or M4 line.

No blockers or majors. VideoItemUIMapperTest green on head; new red runs recorded.
