# Device evidence: subtitle-und-preferred-fallback (AC-4 / M1)

Date: 2026-11-27. Run: subtitle-und-preferred-fallback, round 1.

## Build and device

- Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held for the whole session and released after the master APK was put back.
- Worktree head: ce8deff2 (base 874b049f). APK: worktree app-dev-debug.apk, sha256 572a53d96513f64492c07a9ac994892ec6f45fe1758d3453ecffb7a73fe9108e.
- Before: master dev APK on the device (sha256 4354d24dda6ce6a7034f5dde1fdb9e2b3a03730ea2c1decdfe8a15c6f632c128), logged in, launched once, make auth-save done (.auth/, not committed).
- Install: tools/android-apk-install-preserve, outcome installed, classification compatible_replace, destructive_action false. On-device base.apk sha256 equal to the worktree APK sha256.
- After: master APK reinstalled the same way, outcome installed, on-device sha256 4354d24d... again, app launches and shows the home screen.

## M1 steps (human pace, one title, no scripted requests)

Title: "Квазимодо, душа Парижа" (found through the app's search with a typed query, opened, "Продолжить" from the stored position 23:28).

| Step | Expected | Observed | Result |
|------|----------|----------|--------|
| Open Settings in the player | Audio 01. Дубляж (RUS), Subtitles Off, Quality Auto | exactly that | PASS |
| Settings, Subtitles: pick "Русский · вариант 1" | check mark on that row, Russian cue renders | check mark moved to the row; a Russian cue line was drawn at the bottom of the picture | PASS |
| Settings, Subtitles: pick "Выкл." | check mark on Off, no cue | check mark on Off; two later frames about 5 s and 10 s apart, during on-screen speech, show no cue | PASS |
| Crash, lost focus, picker/track mismatch | none | none seen | PASS |

Not exercised: the step "pick a language row again" of M1 (the first two transitions already show render and no render both ways). Rows with lang "und" do not occur in this title's list (11 rows: uk x3, ru x3, fr x2, en x3 labelled by language and variant), so the changed branch itself (an "und" row without a selector match) was not reached on the device; this smoke covers the neighbouring path (known language rows and Off) for regressions only.

Screenshots: none kept in the repository (film frames carry the picture; not stored).

## Final state

Quality Auto, Subtitles Off, Audio 01. Dub (RUS) untouched. Playback position of this title advanced by about 1.5 minutes (progress write by ordinary playback).
