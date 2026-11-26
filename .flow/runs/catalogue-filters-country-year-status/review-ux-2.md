Round 2 (incremental 1f6b03b0..e286d189). One nit.

What matches: round-1 F1 is fixed (off-list stored year shows as one checked row after «Любой год» with first focus, ShowAllFilterPanel.kt:256-264; focus cannot be split). The row uses TvSidePanelItem and yearLabel, same look. Height 426 -> 466 dp only in the off-list case, under PanelMaxHeight 500, no scroll. The new focus test yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus checks the right path.

### F1 [nit] The stand README doesn't describe the off-list «Год» row
- Where: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/docs/design/showall/README.md:201
- Problem: the README lists «Любой год» plus 8 presets at 426 dp only; the code now adds a 10th checked row at 466 dp when the stored range is off-list.
- Failure scenario: the owner judging the page against the stand (AC-11 / M3) sees a 10-row page with no reference for it.
- Fix: add one sentence to that README row: "a stored range that is no longer a preset shows as one checked row after «Любой год» and takes first focus; 466 dp, no scroll".

VISUAL: not measured: the box session is rejected (HTTP 403 user_banned). Code matches the stand's starting values apart from the nit; AC-11 / M3 still needs a working session.
