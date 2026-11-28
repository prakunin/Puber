# watch-state-batch-in-progress: device evidence

Date: 2026-11-28
Device: owner's TV box 192.168.1.110:5555 (lease held and released), package com.kino.puber.stage only.
Build: worktree dev debug APK at HEAD 1fb420e1, version 1.12.8-dev (2090000000), sha256 a05e05b59a5265cb2cefac8ea2a9902edae5adb3f758c3a4a52f168fa89f0245.
Install: tools/android-apk-install-preserve, outcome `installed`, classification `compatible_replace`, no destructive action. On-device base.apk sha256 matched the built APK after the install and again after the itest run (which reinstalls the same build).
Session protection: the previously installed stage app was launched first (logged in, Home shown), then `make DEVICE=192.168.1.110:5555 auth-save` stored the app data. The worktree build opened on Home, logged in, not the pairing screen.

## AC-4 (instrumented)

Command: `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`
Expected: `markInProgressInOneTransactionEmitsNoPartialList` green: no observeAll emission holds only one or two of the three ids, the last holds all three.
Observed: suite com.kino.puber.data.db.WatchStateDaoTest, tests=12 failures=0 errors=0 skipped=0, including markInProgressInOneTransactionEmitsNoPartialList. PASS.

## AC-5 (device observation, one user at normal pace, no scripted API calls)

Steps (D-pad only): Home -> Новинки row, movie "Квазимодо, душа Парижа" (in-progress bar on its card, detail showed "Остановились, осталось 1 ч 14 мин") -> open -> Смотреть фильм -> resume prompt "Продолжить просмотр с 32:11?" -> Продолжить -> played about 35 s (picture and audio path rendering) -> BACK -> waited about 8 s -> detail screen -> BACK -> Home.
Expected: no crash, returns to previous screens, badges match the state before.
Observed:
- Player exit returned to the detail screen with focus on the play button; the process stayed alive (same pid before and after); no pairing screen.
- Detail showed "осталось 1 ч 13 мин" (was 1 ч 14 мин), the in-progress mark advanced as expected for the played movie.
- Home after the exit: the "Я смотрю" card (episode badge 12) unchanged; the Новинки row cards unchanged; the played movie kept its in-progress bar (about 28% before, about 29% after); no watched card turned in progress or the reverse; focus stayed on the played card.
PASS.

Limit: the catch-up writes of several items in one list are not directly observed on the device; one-transaction behaviour is proven by AC-1 (unit) and AC-4 (instrumented).

## Round 2: AC-4 with the control case (commit 3318a62e)

Device: owner's TV box 192.168.1.110:5555, lease acquired and released (no lease left in `status`). No install-preserve or app launch in this round; the itest installs its own test APK and keeps the app.
Command: `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`
Expected: `markInProgressInOneTransactionEmitsNoPartialList` green (no partial list emitted) and the control `markInProgressInSeparateCommitsEmitsAPartialList` green (separate commits do emit a partial list, so the first test can fail when the transaction is missing).
Observed: suite com.kino.puber.data.db.WatchStateDaoTest, tests=13 failures=0 errors=0 skipped=0, exit code 0. Both named tests present and passed (1.123 s and 1.721 s). PASS.
