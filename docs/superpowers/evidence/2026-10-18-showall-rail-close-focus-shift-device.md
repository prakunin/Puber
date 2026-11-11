# Device evidence: showall-rail-close-focus-shift

- Worktree HEAD dda4eb23 (branch prakunin/showall-rail-close-focus-shift), base 6b087697.
- Device: emulator-5554 (AVD puber_tv_36, API 36 TV), leased for the session, D-pad only (`adb -s emulator-5554 shell input keyevent`).
- HEAD APK: `app-dev-debug.apk` sha256 6a900477a41a86a1a26382135027d19eefbdf67797d0f02517e1be7ade9375eb, installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, destructive_action false, same signer); on-device base.apk sha256 equal.
- Base comparison APK: HEAD with only `ShowAllScreenContent.kt` reverted to 6b087697 (sha256 b178c231...), installed the same way; production file restored afterwards (`git diff --exit-code` empty) and the HEAD APK reinstalled.
- Method for "same card": screenshot before LEFT and after the RIGHT/Back close, compared byte for byte (identical screen means same card, same scroll, rail closed), plus a visual read of the key frames. Screenshots stay out of the repo.

## Instrumented (`make DEVICE=emulator-5554 itest TESTS=...`)

| Run | Build | Result |
|-----|-------|--------|
| `ShowAllRailCloseFocusTest` (2 tests) | HEAD | 2/2 passed |
| `ShowAllRailCloseFocusTest` | base production file | `showAllRailCloseKeepsFocusOnTheLowerRowCard` FAILED: `expected:<Card 16> but was:<Card 13>` (one row up); top-row test passed on base (it is a guard, the lower-row test is the red one) |
| `ShowAllScreenContentFocusTest` (5 tests) | HEAD | 5/5 passed (AC-8) |

## Manual checks

| Check | AC | Steps | Expected | Observed | Result |
|-------|----|-------|----------|----------|--------|
| D1 | AC-1 | Movies, "All" row, Show all, RIGHT into grid, DOWN to a left-column card in the lower visible row; LEFT (rail opens), RIGHT. 3 repeats on 3 different cards | same card, screen identical to before | 3/3 byte-identical; first repeat card "Krovavaya nadpis" | PASS |
| D1 base | AC-9 | same, base build, same card "Krovavaya nadpis" | shifts one row up | 3/3 different; closed on "Korol shantazha" (row above) | red confirmed |
| D2 | AC-2 | top of grid (first card) and a scrolled position (7 rows down), LEFT, RIGHT, 3 repeats each | same card | 3/3 byte-identical for both | PASS |
| D3a | AC-3 | Back with the rail open | rail closes, same card | NOT APPLICABLE as specified: on Show all (a pushed screen) Back with the rail open pops the screen to the Movies tab, as on base. Not a closing of the rail | NOT CHECKED |
| D3b | AC-3 | rail open, OK on the current tab (Movies), 3 repeats, scrolled position | rail closes, same card | 3/3 byte-identical | PASS |
| D4 | AC-4 | enter Show all fresh (rail has focus), RIGHT | first card focused, as base | first card ("Tenzing") focused, rail closed | PASS |
| D5a | AC-5 | OK on a card (Details), Back, LEFT after 1.0 s, 4 cycles | rail opens and stays open, not pulled back | rail open after 2.5 s in 4/4; RIGHT then closes to the same card | PASS |
| D5b | AC-5 | same with LEFT after 0.1, 0.3, 0.5, 0.8 s | no focus theft | LEFT is swallowed (rail stays closed, grid keeps the card) at 0.1-0.8 s; identical on base (0.1, 0.5, 0.8 s checked). Not caused by the change | PASS (no theft), observation |
| D5c | AC-5 | long-press OK on a card (context dialog "Watch movie / Add to bookmarks / Close"), wait 3 s | dialog keeps focus | dialog unchanged after 3 s. LEFT while the dialog is up opens the rail behind it (dialog dimmed); Back closes the dialog, RIGHT closes the rail on the same card. The handoff answer does not run in that sequence | PASS (no theft), observation |
| D6 | AC-8 | History tab, DOWN x2 to a second-row card, LEFT, RIGHT | same card (History untouched) | identical card before and after | PASS |

## Slips
- After Back popped Show all, an extra RIGHT then OK landed on the Matchbox card, which opened Details, and a later OK started the Trailer for a few seconds (player time 0:18). Backed out; nothing was played in full, no bookmark, no watched-state change attempted. No uninstall, data clear or downgrade.
