No findings.

VISUAL: match

Measured on emulator-5554 (1920x1080, dp = px/2), branch devDebug (on-device sha 9197bfca... = app/build/outputs/apk/dev/debug/app-dev-debug.apk); nothing installed; lease released; session log switched back off.

| Check | Expected (delta) | Measured |
|---|---|---|
| Section list | 7 rows, no scroll, Developer last | 7 rows, "For developers" last, list ends ~905 px, no scroll |
| Toggle row height, off and on | >= 48 dp, same in both states | 228-332 px = 52 dp both states, no jump |
| Supporting text | one line every state | off: "Writes app activity to a local file, turns off after 7 days"; on: "Recording · 6 d 23 h left"; one line |
| Share row | SettingsListItem, same shape as toggle | 340-444 px = 52 dp |
| Debug overlay row | 48 dp, last row | 452-548 px = 48 dp |
| Gap between rows | 4 dp | 8 px = 4 dp |
| Left edges | row panel+12 dp, text row+16 dp | panel 800, row 824, text 856; row width 488 dp as other sections |
| Focused look | onSurface fill, 12 dp corner, no scale; indicator inverts | matches on all three rows; no tint, dot or extra colour |
| Focus order | Right->toggle, Down->Share, Down->overlay, Left->section | matches |
| Focus after OK on Share | stays on Share | stays (emulator only has Bluetooth target, system toast "This action isn't supported") |

Code diff holds to the delta: section filter removed, BuildConfig.DEBUG wraps only the debug-overlay item; Share uses enabled = shareEnabled && !sharing with focusableWhenDisabled = true; shared constants reused, none retuned; ru and en define all new strings, Russian supporting texts <= 56 chars.

Not measured on device (not deviations): disabled Share state (needs data clear; same path as "Rebuild the index"; QA saw it in round 1); "Preparing the archive…" (archive ready in ~150 ms); focus after closing a real share sheet (no chooser on emulator; in QA MANUAL); Russian layout on screen (checked by character count only); overlay row hidden in deploy/release (QA confirmed round 1).

Screenshots kept in the session scratchpad only (Home/Details captures show account content).
