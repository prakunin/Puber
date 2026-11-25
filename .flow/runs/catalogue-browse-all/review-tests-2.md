LENS: tests  ROUND: 2  BASE: 6c35732d
### F1 [major] No test or device evidence for the shipped focus gating; the only results in the repo are red
- Saved SRFT XML (a8e4cbbc) is red at c0664ac0; 96c7a9c3 changed production gating; 80158854 moved onRowFocused with no device re-run; M-2/M-3 ran at c0664ac0.
- Fix: run SRFT and ShowAllTitleResolveTest at head, save XML, relabel the red ones; re-run M-3 steps 3-5 and M-2 rail round trip on the head APK.
### F2 [minor] Tests exercise the scene's copy of the ownership-clearing call, not production (ContentListScreenContent.kt:372)
- Fix: shared helper in BrowseEntryFocus.kt used by sectionItem and the scenes, or record M-3 step 5 as its guard.
### Unproven
- BrowseEntryFocus.kt:70-79 100 ms settle vs a late launch request when the tab opens from the rail with Empty/Error section 0.
