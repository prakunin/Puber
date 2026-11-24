# Device evidence: cached-payload-row-too-big (round 1)

Date: 2026-11-24. Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held, released after the run. auth-save taken first. Emulator not used.

Build: worktree branch prakunin/cached-payload-row-too-big at af7d459a, `./tools/agentw :app:assembleDevDebug`. Installed with `tools/android-apk-install-preserve`: outcome `installed`, classification `compatible_replace`, version 1.12.6-dev (2090000000), signer unchanged. APK sha256 2e9c40fc0594ff8aeec794255abe123a886ff795a362be2e4f67afa9f9f2cd8a matches the on-device base.apk.

## AC-6 before (installed stage build, before any install)

Steps: launch, rail to 4K, DPAD_DOWN to the «Все» shelf; then UP to the «Всё в 4K» chip, DPAD_CENTER for the browse grid.
Observed: shelf shows «Не удалось выполнить запрос. Попробуйте ещё раз.» with Повторить; the grid shows the same message. Logcat filtered to the app pid: 120 lines carrying SQLiteBlobTooBigException; the failing query is `SELECT * FROM cached_payload WHERE key IN (?,...)` (32 keys), "Row too big to fit into CursorWindow requiredPos=11, totalRows=12"; CursorWindow warnings give window size 2097152 bytes and a requested allocation of 2439355 bytes (one row of about 2.4 MB). Retry reproduces it. Status: reproduced.

## Instrumented tests (make DEVICE=192.168.1.110:5555 itest)

| Run | Commit | Result |
|---|---|---|
| PersistentPayloadStoreDbTest (red) | fd6e8233, detached; branch restored, tree clean | 2 tests, 2 failures: readReturnsNullAndDeletesARowTooBigForTheCursorWindow and readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow, both android.database.sqlite.SQLiteBlobTooBigException "Row too big to fit into CursorWindow" |
| PersistentPayloadStoreDbTest (green) | af7d459a | 4 tests, 0 failures (the two above, aMultiByteRowJustUnderTheLimitRoundTrips, aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent) |
| CachedPayloadDaoTest (green) | af7d459a | 11 tests, 0 failures, including deleteOversizedKeepsARowWithinTheLimit |

## AC-6 after (fixed APK, app data preserved)

Run 1 (pid 6195) and run 2 after am force-stop and cold start (pid 6741), each: launch, rail to 4K, DPAD_DOWN to the «Все» shelf, then UP twice to «Всё в 4K», DPAD_CENTER.
Expected: cards, no error state, no SQLiteBlobTooBigException.
Observed: both runs: the «Все» shelf shows cards (Табакошка, Семь жизней, Декстер: Воскрешение); the browse grid shows two rows of poster cards with 4K/HD badges and no error text. Logcat filtered to each pid: 0 occurrences of SQLiteBlobTooBigException; no refused-write warning line seen either (the oversized row was healed on read, nothing was written). Status: PASS.

Not exercised: D-pad movement across grid rows after load, the Retry button on a healthy screen. The rail overlay is open over the grid in the screenshots after the chip press; the same occurred before the fix and is outside this change.

## Round 2 (HEAD 69c61413, after the read cleanup change in 23dfb968)

Device: owner's TV box 192.168.1.110:5555, com.kino.puber.stage only, lease held and released. auth-save taken first.

Build: `./tools/agentw :app:assembleDevDebug` at 69c61413, installed with `tools/android-apk-install-preserve`: outcome `installed`, classification `compatible_replace`, version 1.12.6-dev, signer unchanged. APK sha256 68baf62d1c913b96127f76f46c5d78182d0aa4f3a569bbcd1b039a3aa39d7606 matches the on-device base.apk (checked after the itest runs, which reinstall the same dev APK).

### Instrumented tests (make DEVICE=192.168.1.110:5555 itest)

| Run | Result |
|---|---|
| PersistentPayloadStoreDbTest | 5 tests, 0 failures, 0 errors: readReturnsNullAndDeletesARowTooBigForTheCursorWindow, readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow, aMultiByteRowJustUnderTheLimitRoundTrips, aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent, anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore |
| CachedPayloadDaoTest | 11 tests, 0 failures, 0 errors |

### AC-6 sanity (cold start, twice)

Steps per run: am force-stop, logcat -c, launch, LEFT to the rail, DOWN to 4K, CENTER, DOWN to the «Все» shelf (screenshot), UP twice to «Всё в 4K», CENTER (screenshot).
Expected: cards, no error state, no SQLiteBlobTooBigException.
Observed: run 1 (pid 8770) and run 2 (pid 9242): the «Все» shelf shows cards (Табакошка, Семь жизней, Декстер: Воскрешение); the browse grid shows two rows of poster cards with 4K/HD badges and no error text (rail overlay open over it, as in round 1). Logcat filtered to each pid: 0 occurrences of SQLiteBlobTooBigException. Status: PASS.

### Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL, 256 unit suites, 2555 tests, 0 failures, 0 skipped.
