# Device evidence: player-audio-unknown-language (AC-7)

- Build: worktree dev debug APK, sha256 6e888ae6...c3425f (matches the on-device base.apk after install-preserve, outcome "installed", compatible_replace)
- Device: 192.168.1.110:5555 (owner's TV box), package com.kino.puber.stage only, RU UI
- Preparation: master dev APK (sha256 4354d24d...c128) was installed and logged in; app launched once, `make auth-save` done; restored afterwards with install-preserve (on-device sha256 matches master APK again). Lease released.
- No film-frame screenshots are stored.

## AC-7 regression check

Steps (D-pad only, human pace): History -> "Квазимодо, душа Парижа" -> Watch -> Continue -> player controls -> gear -> Audio.

| Step | Expected | Observed | Result |
|---|---|---|---|
| Open the player and settings | Settings panel lists Аудио with the current track | "Аудио 01. Дубляж (RUS)" | PASS |
| Open the Audio list | Named rows, current track checked, focus on it | 4 rows: "01. Дубляж (RUS)" (checked), "02. Дубляж (UKR)", "03. Оригинал (FRE)", "04. Дубляж (RUS) AC3"; focus on row 01 | PASS |
| Move focus down (2 steps, then 3) | Focus follows, no focus loss, playback continues | Focus moved to 02, then 04; video kept playing | PASS |
| Look for English "Unknown" or raw "und" | None | None in any row | PASS |
| Leave the player with Back | No crash; stored audio choice unchanged | No selection was made; no crash entry for the package in the crash buffer | PASS |

Not covered on device: a row with no API language (not reachable without probing); covered by unit tests AC-1..AC-4.
