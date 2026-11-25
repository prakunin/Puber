LENS: edge  ROUND: 3  BASE: 0c1f8378
### F1 [minor] The new rule can settle the launch on the paginator's brief initial Empty, so the launch request could then focus the entry
- Where: BrowseEntryFocus.kt:76-79
- Problem: SectionState.Empty is also the replayed initial paginator state before Loading (SectionState.kt:19-21); one frame of all-Empty on a cold start settles the launch permanently; section 0 then becomes Content and the launch request can land on the entry.
- Fix: delay(FOCUS_ON_LAUNCH_DELAY_MILLIS) inside LaunchedEffect(everySectionEmpty) before settleLaunch (cancelled when the state changes), or ignore an Empty not preceded by Loading.
### Unproven
- The new test reaches the entry with RequestFocus, not launch focus or D-pad.
