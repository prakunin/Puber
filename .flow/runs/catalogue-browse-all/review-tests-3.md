LENS: tests  ROUND: 3  BASE: 0c1f8378
### F1 [minor] The all-Empty test does not cover Loading -> Empty or a D-pad move (SectionRowFocusTraversalTest.kt:519-541)
- Fix: start rows Loading, switch to Empty, press D-pad, assert the entry focused; or a device step on a tab with no results.
Checked: red run recorded; r3 XML 17/17 at head; SettleScene uses production rememberBrowseEntryFocus; no weakened assertions.
### Unproven
- BrowseEntryFocus.kt:76 Error mixed with Empty never auto-settles; Retry takes launch focus and settles normally.
