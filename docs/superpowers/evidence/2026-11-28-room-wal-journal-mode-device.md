# Room WAL journal mode: device evidence

Date: 2026-11-28. Round 1.

- Build: worktree dev debug APK, `app-dev-debug.apk`, versionName 1.12.8-dev, sha256 886b12d617444876522db300025f3f6fb6a834433c0ac7f4bfc3c39afd84a405 (installed base.apk on the box: same sha256).
- Device: owner's TV box 192.168.1.110:5555 (Android SDK 29), package com.kino.puber.stage only, under a lease.
- Install: `tools/android-apk-install-preserve`, outcome `installed`, classification `compatible_replace`, destructive_action false. Session snapshot taken first with `make DEVICE=192.168.1.110:5555 auth-save`.
- Before the install: header bytes 18-19 of puber.db were `2 2` (WAL already, AUTOMATIC on a non-low-RAM device).

## AC-1 / AC-2 (instrumented, `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest`)

Classes: PuberDatabaseMigrationTest, CachedPayloadDaoTest, PersistentPayloadStoreDbTest, WatchStateDaoTest. Result: 33 tests, 0 failures.

First run: 1 failure, `productionBuilderSwitchesARollbackJournalFileToWal`, SQLITE_BUSY inside the test's own setup (`PRAGMA journal_mode = TRUNCATE` on the MigrationTestHelper connection, while Room's pool still held WAL connections). Test setup defect, not production. Setup now rewrites the file on a fresh `AndroidSQLiteDriver` connection and asserts header bytes `1 1` before the production builder opens it. Second run: all 33 pass, including both new tests.

## AC-3 (launch, Home, a card, back)

Steps: force-stop, launch, Home at human pace; D-pad RIGHT, DOWN, DOWN to the first card of the second row, DPAD_CENTER (item screen opened), BACK.
Expected: logged in, no crash, badges unchanged.
Observed: Home loaded logged in (no pairing screen). Badge "12" on the first card of the watching row and the progress bar on the third card of the next row are the same before the install and after it and after returning from the item screen. Focus returned to the opened card. Crash buffer: 0 entries for com.kino.puber. PASS.

## AC-4 (header and low_ram)

- `run-as com.kino.puber.stage od -An -tu1 -j18 -N2 databases/puber.db` after the run: `2   2` (WAL).
- `getprop ro.config.low_ram`: empty (not a low-RAM device).
PASS.
