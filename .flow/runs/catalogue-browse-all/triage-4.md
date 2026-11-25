# Triage round 4: catalogue-browse-all

Incremental 109b7b0b..b23f7a51. Lenses: edge (clean), tests, state-path, codex (clean), antigravity (clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | state-path F1, edge unproven 1, codex P2 (rejected as unproven) | minor, fixed anyway | Replayed initial Empty can still settle and now grant ownership on a stalled frame; the owner's TV box is slow | yes, timer-only distinction (BrowseEntryFocus.kt:73-76, :96-101); SectionState.kt:19-21 documents the replay | fix | SectionVM maps the paginator's Empty to SectionState.Loading until the first Loading is dispatched; unit test in SectionVM tests (red first); keep the delay |
| 2 | tests F1 | minor | hero + all-Empty branch and production onFocusChanged untested | yes | fix | SettleScene test: hero holds launch focus, all rows Empty, hero keeps focus past the delay |
| 3 | tests F2 | minor | no XML for the fix run | yes | fix | qa round 4 commits itest-r4 XML |
| 4 | edge unproven 2 | - | all sections Empty while the context menu is open | no path | reject | not reachable |
| 5 | codex adversarial | - | AC-7 second Back after a details trip | pre-existing, rail BackHandler order | defer | showall-first-entry-rail-focus (log added) |
