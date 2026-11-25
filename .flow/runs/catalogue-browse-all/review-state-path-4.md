LENS: state-path  ROUND: 4  BASE: 109b7b0b
### F1 [minor] A replayed all-Empty that stays composed past the launch delay makes the entry the owner, so it takes launch focus when the cards arrive
- Where: BrowseEntryFocus.kt:73-76, :96-101
- Problem: only a 100 ms timer separates the paginator's replayed initial Empty from a real one; with listHasFocus false at launch the settle now sets isOwner (sticky, saved), disabling row initial focus.
- Failure scenario: slow cold start, frame stall >= 100 ms between the replayed Empty and Loading -> tab opens on «Все фильмы» (AC-2/M-1 broken); from the rail, Right hands focus to the entry.
- Fix: remove the replay at its source: SectionVM.dispatchListState maps Paginator.State.Empty to SectionState.Loading until the first Loading was dispatched.
Checked: AC-7 return, rail handoff, shimmer removal with no focus.
