# Device evidence: dependency-update-stable (round 1)

Build: worktree HEAD d8ffc10e, `app-dev-debug.apk` sha256 `166e811fd39600e053001bd8816e8eca07672dfd66db14a2704a035eb58a6dbe`, signer `6b2918...a83e`.
Device: emulator-5554 (AVD `puber_tv_36`, API 36 TV), leased. Package `com.kino.puber.stage`.
Install: `tools/android-apk-install-preserve` -> `installed`, `compatible_replace`, `destructive_action: false`. On-device `base.apk` sha256 equals the built APK. `make auth-save` ran before.

| AC | Steps | Expected | Observed | Status |
|----|-------|----------|----------|--------|
| AC-7 | Install, `am start` MainActivity | Home, no login | Home with rows and "update available" dialog; no login or pairing screen | PASS |
| AC-8 | Home: D-pad down x3, right x5; open a card | Posters visible, focus on a card | Poster art in Continue watching, My bookmarks, New releases rows; focused card outlined; Details backdrop loaded | PASS |
| AC-9 | Details of a series, play S1E1, D-pad right (seek), wait ~45 s | Video starts, resumes after seek, advances | Frame at start differs from frame after seek and from frame 30 s later; no error overlay, no crash | PASS |
| AC-10 | Back out, `force-stop`, relaunch, reopen title; `logcat --pid` filtered `room\|sqlite\|FATAL` | Saved progress shown; no Room/SQLite exception | Room DB opens, Continue watching rows survive restart, filtered log empty. Saved progress NOT shown: logcat shows `Playback progress save failed` twice per playback. Same two lines on the base build (4db9c0d4, built from `git archive`, installed with install-preserve, same scenario) | NOT CHECKED (progress save) |
| AC-5 (M6) | `com.kino.puber` absent; install prod release via install-preserve (`package_absent`), launch | First screen, no crash | Launches to the update dialog, filtered log for FATAL empty; release signer sha256 equals the dev debug signer `6b2918...a83e` | PASS |

Screenshots were reviewed in the session and not stored (they show account titles).

## Round 2: instrumented DAO suites (AC-10, Room 3.0.3 / sqlite 2.7.1 write path)

Worktree HEAD 742fd2e5, device emulator-5554 (puber_tv_36, API 36), lease held. Command, run once per class from the worktree:
`AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=<class>`. The app stayed installed (`com.kino.puber.stage` still listed); no uninstall, clear or downgrade.

| Suite | Result | Write operations run on the new generated DAOs (in-memory Room database) |
|-------|--------|-------------------------------------------------------------------------|
| `com.kino.puber.data.db.WatchStateDaoTest` | 8 tests, 0 failures, 0 errors, 0 skipped | insert and update of server rows, sync via `upsertAllFromServer` (`@Transaction`), pending row preserved by sync, history insert, mark-in-progress, `pruneOlderThan` |
| `com.kino.puber.data.db.CachedPayloadDaoTest` | 6 tests, 0 failures, 0 errors, 0 skipped | upsert insert and replace, touch, delete, deleteByPrefix, clear |

AC-10 verdict: NOT CHECKED. The DAO writes, the `@Transaction` DAO method and prune pass on Room 3.0.3. Not executed: `withWriteTransaction` in `DatabaseTransaction.kt:22` (the suites call the DAOs directly; `WatchStateRepositoryTest` uses `DatabaseTransaction.Direct`), and the repository-level write through it on a file-backed database. Remaining: a test or device run that goes through `DatabaseTransaction` with the real database, or the owner accepting the partial result.
