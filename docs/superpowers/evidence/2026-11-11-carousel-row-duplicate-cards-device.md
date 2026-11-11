# Device evidence: carousel-row-duplicate-cards (round 1)

- Build: worktree HEAD 9cad7b83, `:app:assembleDevDebug`, `com.kino.puber.stage` 1.10.0-dev, APK sha256 `1eb26945...2044dc288` (installed copy matches on-device).
- Install: `tools/android-apk-install-preserve`, outcome `installed` (`compatible_replace`, no destructive action).
- Device: `emulator-5554` (AVD `puber_tv_36`, Android 16 TV), leased and released.
- Setup: Settings > Content > "Anime in general lists" switched off (local setting, restored to on afterwards); "Hide watched" off. No title saved, unsaved or marked watched.
- Method: D-pad only. Card titles were read from a `uiautomator` dump after each step into a local list (not stored here), merged by overlap and checked for repeats. No raw dump, logcat or account data is kept.

## AC-7 Series tab, Popular row ("show anime" off)

Steps: Series tab, Popular row, `DPAD_RIGHT` x80 (81 cards, 3+ logical pages, all distinct). Focused card "В кругу / El Círculo". `DPAD_CENTER` (details screen opens), `BACK`. Continued `DPAD_RIGHT` to the "Show all" button.

- Expected: focus back on the opened card; each title once.
- Observed: focus returned to "В кругу / El Círculo" with the same neighbours. The full row, 84 cards from the first card to the last before "Show all", holds 84 distinct titles, none twice. No `Key ... was already used` and no `FATAL EXCEPTION` in the logcat buffer (counted, not stored).
- Limit: the row was not observed refreshing (no log of the network reads), so the run does not show that the restore ran at depth. The unit tests carry that proof.
- Result: PASS for the observed behaviour (no duplicates, focus restored, no crash); the refresh itself is not shown.

## AC-8 Popular "show all" grid ("show anime" off)

Steps: opened "Show all" from the row, walked `DPAD_DOWN` through the 3-column grid (27 rows, 81 cards, all distinct). Focused "Кукла / Lalka" (row 15), `DPAD_CENTER`, `BACK`, scrolled up and down through the whole grid again.

- Expected: focus on the opened card, each title once, no empty grid.
- Observed: details screen opened and `BACK` returned with focus on "Кукла / Lalka" in place. Second full pass: rows 0..26, 80 titles read (one cell not captured), all distinct, none twice (the cells reported for rows 27+ were the last row re-read after focus stopped moving at the end of the grid, not extra cards). No crash.
- Result: PASS (same refresh limit as above).

## Manual 3 ("show anime" on)

NOT CHECKED: not run; the default-setting case does not use the read-ahead path the fix targets.

## Audit

`tools/mobile-evidence-audit.sh` run on this directory; result in the QA report.

# Round 2: refresh triggered by a display-setting flip

- Build: worktree HEAD a6af2082; `git diff afc1e706..HEAD -- app/src/main` is empty, so the round 1 APK was reused. On-device base.apk sha256 `1eb26945...2044dc288` equals the fresh `:app:assembleDevDebug` output.
- Device: `emulator-5554`, leased and released. D-pad only; no save, unsave or watched mark.
- Trigger: Settings > Content > "Show watched indicators" (`displaySettingsChanges` -> `refreshFirstPage` in `SectionVM` and `ShowAllVM`). "Anime in general lists" was off for the whole run.
- Proof the reload ran: the request log (URL lines counted, nothing stored) was cleared before each flip. Counts of `items/popular?type=serial&page=N` lines are in the table; the app's Ktor logger writes each request as 3 lines.

| Step | Popular `page=1..6` request lines after the flip |
|------|---------------------------------------------------|
| AC-7: row walked to its last card (focused "Революционеры"), flip indicators on -> off, return to Series | 3 per page (one request each, pages 1..6 once) |
| AC-8: "Show all" grid walked 24 rows down (focused "Кид против Кэт", row 24), flip off -> on, return to Series | 6 per page (two requests each: the row VM and the grid VM, pages 1..6 once each) |

Each refresh asked for pages 1..6 once and no page twice: the restore walked the loaded depth without re-requesting read-ahead pages.

## AC-7 Series tab, Popular row

- Observed after the reload: the Series tab was rebuilt by the tab switch and focus sat on the first card ("Кэрри / Carrie"), not on the card focused before. The screen, not the row, owns focus here; no restore of the last card was observed.
- Walk back from the last card to the first, one `DPAD_LEFT` per step, windows read from a `uiautomator` dump: 80 titles recovered, none repeated, no gap between windows (the walk tool misses a few cards the round 1 count of 84 had; the tail, "В долгу" then "Революционеры", and the head match round 1). No `was already used`, no `FATAL EXCEPTION`.
- Result: PASS for what a setting flip can show: the refresh ran at depth (6 pages), no title twice, no crash. Focus after the reload is the first card, set by the tab rebuild.

## AC-8 Popular "show all" grid

- The grid stayed on the Series stack while Settings was open. After the flip and return, the grid showed the same view with focus still on "Кид против Кэт / Kid vs. Kat" (row 24), so focus survived the reload.
- Walk from the top row to the last row, 32 steps: 85 distinct titles, none in two places, windows contiguous, last cards "В долгу" then "Революционеры". No `was already used`, no `FATAL EXCEPTION`.
- Result: PASS.

## Settings restored

"Show watched indicators" on (flipped twice per run, ends on), "Anime in general lists" on, "Hide watched content" off (toggled on by a misstep and toggled off at once, never active during a walk), "Trailer on focus" on. Read back from the Settings screen at the end.
