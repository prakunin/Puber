# Device evidence: watch-index-catch-up (round 1)

- Build: worktree dev debug APK, base e4c3b5aa, head c7df62a8; sha256 29d8b18599c320b91a7db032dad00e2702328c52631cc3c1838dc5e07038bc2d (equal on device after install-preserve and after the itest run).
- Device: TV box 192.168.1.110:5555 (KM6, Android 10), package com.kino.puber.stage only, under lease; install-preserve outcome `installed` (`compatible_replace`).
- Session limit: the box session's access token expired during the run (a history request returned 401 at 08:35); the refresh call was rejected with `incorrect_client_credentials` because a worktree build carries no client secret (AGENTS.md: Build And Worktrees). From then on the app shows the loading spinner on cold start, so every observation that needs the catalogue (tabs, AC-9, the extra Series observation) was not possible.

## AC-8: history order (PASS)

Steps: cold start; Settings > Data > "Rebuild index" (Пересоздать индекс); read the debug line `history order page=N entries=E ordered=B` (page, count, ordered only; no titles, ids or timestamps recorded).

Expected: ordered=true for pages 1-3 (non-increasing `last_seen`, including across page boundaries).

Observed (perpage=50): pages 1 to 75 of the rebuild walk, each entries=50 ordered=true (the walk was cut at page 75 when the 401 above hit); across all `history order` lines in the log buffer (catch-up page-1 reads plus the walk) none had ordered=false. Pages 1-3 specifically: ordered=true, ordered=true, ordered=true. The check includes the page-to-page boundary (previous page tail vs next page head).

## Catch-up on start (AC-5 start, observation)

Cold start with a valid session: one history page-1 request with `perpage` was made and logged `page=1 entries=50 ordered=true`, and no deeper page was read (index already at the mark). A second page-1 read followed about 1 min 45 s later while the app was open (Details of a movie opened and left, then Settings). Not asserted beyond the unit tests.

## Instrumented tests on the box

- `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest`: KM6 4 tests, 0 failures (includes migrate4To5RescalesTheResumePageOfAnUnfinishedWalk and migrate4To5LeavesAFinishedWalkAlone): PASS.
- `... TESTS=com.kino.puber.data.db.WatchStateDaoTest`: KM6 11 tests, 0 failures (includes raiseHistoryNewestSeenNeverLowersTheMark): PASS.
- Note: the run also executed on the host emulator (Gradle reported "Running tests on devices: puber_tv_36(AVD) - 16, KM6 - 10") although only the box was named; see the report. On the emulator the migration run had one `testRunFailed` entry; it is not counted as evidence.

## AC-9 and the Znieczulenie observation: NOT CHECKED

AC-9 needs an account mutation by the owner on another client. The extra observation (is the already fully watched series still listed on the Series tab after a catch-up) could not be made: the session expired before the Series tab was opened (see Session limit).

# Round 2 (after review fix rounds 1-7)

- Build: worktree dev debug APK at head 210f055e (base e4c3b5aa); sha256 dc575ebb72daddd76d4dfb98825e0bb259396be7d6775fc74b28648e212e3f19. Installed with install-preserve, outcome `installed` (`compatible_replace`, same signer as the installed build).
- Device: TV box 192.168.1.110:5555 (KM6, Android 10), package com.kino.puber.stage only, under lease.
- Session: stage was logged in (Home screen). Per the caution, the master devDebug APK was installed first and launched once to refresh the token, `make DEVICE=192.168.1.110:5555 auth-save` stored the snapshot, then the worktree APK was installed. The session survived: Home, Movies, Series, Settings opened with the worktree build.
- Tooling note: `tools/android-apk-install-preserve` failed with "apksigner did not report a certificate SHA-256 digest" because apksigner 36.0.0 prints "Signer #1 certificate SHA-256 digest" without the colon its regex expects. Both installs ran through a temporary SDK shim outside the repo that only inserts the colon in apksigner's output; the script's own checks were unchanged.
- Log lines used: only `history order page=N entries=E ordered=B` (debug line); no titles, ids or tokens recorded. Times are device time.

## AC-2: catch-up reads page 1 while a walk is parked (PASS)

Steps: Settings > Data > Rebuild index; the walk read pages 1-15 (17:06:15-17:06:22) and parked; D-pad to the Movies tab (tab entry requests a catch-up).
Expected: the catch-up reads page 1 during the park; the walk then continues from its own page.
Observed: `p15` at 17:06:22, `p1` at 17:06:31 (catch-up, inside the park), `p16` at 17:06:42, then p17-p30 in order. The walk resumed at its bookmark, not at page 1. An earlier run of the same sequence (resume from HOME at the end of a 23 s park) also showed `p31 p32 p1 p33`.

## AC-7: a catch-up with nothing new changes nothing visible (PASS, observation plus unit tests)

Steps: cold start; Home, Movies, Series; wait over the 60 s floor; Movies then Series again.
Observed: each catch-up read only page 1 (cold start 16:58:40/16:58:47, tab entry 17:01:26), no deeper page, no failure line, no crash; the Series grid kept its layout after the second entry (screenshots, no account data). Tab entry inside the 60 s floor (16:59:25 and 17:00:10) sent no request. The rewrite-nothing and no-announcement rows are covered by the unit tests carried from round 1.

## AC-4 and AC-9: NOT CHECKED on device

AC-4 (a series that left the watching list gets one Details request) has no on-device signal without an account mutation on another client. AC-9 needs that mutation too. Both go to the owner (MANUAL in the report).

## Order check (AC-8, repeated)

One complete rebuild walk (pages 1-106, perpage=50), a second one read to page 30 or more, plus the catch-up page-1 reads: `ordered=false` lines in the buffer: 0; `ordered=true` lines: 157.
