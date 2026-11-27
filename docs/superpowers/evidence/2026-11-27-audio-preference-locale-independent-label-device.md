# Device evidence: audio-preference-locale-independent-label (AC-6 / M1)

Date: 2026-11-27. Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held for the run and released afterwards.

## Build

- Worktree dev debug APK sha256 2e3fe677f879f6557010e0c68985767da7c3941a4a8ebc839fba4a4752cea45e (commit 7905b9d9), install-preserve outcome `installed`, on-device base.apk sha256 matched.
- Before: master dev APK sha256 4354d24dda6ce6a7034f5dde1fdb9e2b3a03730ea2c1decdfe8a15c6f632c128 (on-device hash matched). `make auth-save` taken first.
- After: master dev APK reinstalled with install-preserve (outcome `installed`), on-device sha256 matches 4354d24d...

## M1: stored Media3 dubbing restores after reopening the player (AC-6)

Item: "Квазимодо, душа Парижа" (History, then Смотреть фильм, Продолжить). Human pace, no API probing.

| Step | Expected | Observed |
|---|---|---|
| Open player, Settings, Audio | Audio list shown | 01. Дубляж. Видеофильм (RUS) active; list 01 RUS, 02 UKR, 03 FRE, 04 RUS AC3 |
| Pick "02. Дубляж. LeDoyen (UKR)" | UKR checked | UKR checked |
| Leave the player to the detail screen, press Смотреть фильм again | Player reopens | Reopened, playback resumed |
| Open Settings | Audio row shows UKR | "Аудио 02. Дубляж. LeDoyen (UKR)" (stored choice restored) PASS |
| Pick "01. Дубляж. Видеофильм (RUS)" again | Original choice back | RUS checked |

No crash, no default-track fallback observed. Film frames were not saved.

Scope note: this smoke covers the existing Media3 restore path only. The API-rows window targeted by AC-1..AC-5 is not reachable on the device; those rest on unit tests.
