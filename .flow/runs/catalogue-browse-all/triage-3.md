# Triage round 3: catalogue-browse-all

Incremental 0c1f8378..109b7b0b. Lenses: edge, tests, codex (review + adversarial), antigravity (clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | edge F1 | major (re-graded: regression from the round-2 fix that can break AC-2's launch focus on a cold start) | All-Empty settle fires on the paginator's replayed initial Empty | yes: LaunchedEffect(everySectionEmpty) settles at once (BrowseEntryFocus.kt:76-79); SectionState.kt:19-21 documents the replay | fix | settle only after the all-Empty state lasts FOCUS_ON_LAUNCH_DELAY_MILLIS (keyed effect cancels on change); test: a one-frame all-Empty followed by Loading/Content keeps launch focus off the entry (red first) |
| 2 | codex minor, tests F1 | minor | After Loading -> all-Empty, focus is dropped until the next D-pad press; the test does not drive Loading -> Empty or D-pad | yes | fix (cheap, same code) | when the delayed all-Empty settle fires and nothing in the list holds focus, request focus on the entry; extend the test to start Loading, switch to Empty and press a D-pad key |
| 3 | tests unproven | - | Error mixed with Empty never auto-settles | Retry takes launch focus and settles | reject | traced |
