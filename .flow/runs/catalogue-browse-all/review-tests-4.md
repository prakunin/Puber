LENS: tests  ROUND: 4  BASE: 109b7b0b
No blockers or majors. Triage-3 fixes covered by tests that fail on regression.
### F1 [minor] The "list already has focus" branch (BrowseEntryFocus.kt:75) and the production onFocusChanged (ContentListScreenContent.kt:127) are untested; hero + all-Empty sections is reachable.
- Fix: SettleScene test with a hero holding launch focus and all rows Empty: hero keeps focus past the delay.
### F2 [minor] No XML for the 18/18 run at the fix; itest-r4/ empty.
- Fix: commit the head run XML into itest-r4/ (qa round 4 does this).
