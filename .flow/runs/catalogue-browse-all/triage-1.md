# Triage round 1: catalogue-browse-all

Base e4d75215, reviewed head 6c35732d. Lenses: blind, edge, tests, state-path, ux-designer, codex (review + adversarial, clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | tests F1, edge F1, codex (rejected as process) | major | AC-7 fixes 0a65717f/c484b503 not confirmed on device; M-3 steps 3-5 and M-2 steps 5-6 not re-run | yes, evidence names 04fa78aa | fix | qa round 2 (running) re-runs all of M-3 and M-2 on the fixed APK |
| 2 | edge F2, state-path F1, blind F2 | minor | Entry takes launch focus when section 0 is Empty or Error; owner flag kept on Retry/loading rows | yes, canFocus only excludes Loading (ContentListScreenContent.kt:465) and browseEntryFocused clears only via onSectionFocused | fix | entry focusable only after the screen's first focus landed (launchFocusSettled) or once it owned focus; clear browseEntryFocused when focus enters any section row; instrumented tests for Empty section 0 launch and Retry |
| 3 | blind F1, edge unproven | minor | anchorFocusRestored() called even when the entry never got focus | yes, BrowseAllEntry.kt:62-75 | fix | call it only when landed |
| 4 | tests F2 | minor | SRFT scenes copy production gating instead of using it | yes | fix | extract the section-0 header gating into one internal function/composable used by both |
| 5 | tests F3 | minor | ShowAllTitle.resolve untested | yes | fix | SACF cases «Фильмы · Новинки», «Все фильмы» |
| 6 | ux F1, F2 | minor | Doc series screenshot wrong; after-Back frames from the pre-fix build | yes | fix | qa round 2 recaptures |
| 7 | blind F3 | nit | owner test hard-codes initialFocusEnabled = false | yes | fix | covered by #4 |
| 8 | codex adversarial | - | Show all opens with focus on the rail | pre-existing | reject | tracked by showall-first-entry-rail-focus |
| 9 | edge unproven | - | section 0 back to Loading while the entry holds focus | no reset caller found | reject | no path |
| 10 | blind unproven | - | 30 dp band at larger font scale | TV font scale fixed in practice | reject | not seen |
| 11 | tests unproven | - | title fallback when titleRes == null | hero configs never reach Show all | reject | no path |
| 12 | qa defect 2 | - | 4K «Все» request error | pre-existing SQLiteBlobTooBigException | defer | cached-payload-row-too-big |
