# Test design: dependency-update-stable

Base 4db9c0d4. ACs: `brief.md#acceptance-criteria`. Commits and per-commit checks: `design-architect.md#changes-by-layer`. Levels: `.flow/roles/test-designer.md` plus `build` (a Gradle task or a file inspection in the worktree, result in the evidence file).

No new test. The change edits no production or test source; every defect it can cause shows up in a build output, in the existing suites or on the device. All P0 rows are `guard`: they protect behaviour that exists on base.

## Risk profile

| # | Risk (source) | P | I | Score | Caught by |
|---|---|---|---|---|---|
| R1 | KSP drops a Room `_Impl` silently; a missing `PuberDatabase_AutoMigration_2_3_Impl` turns into a destructive migration, not a crash (design Risk 2) | 2 | 3 | 6 | AC-6 listing after a clean of `app/build/generated/ksp` (build), AC-10 (device) |
| R2 | AGP 9.5.1 + coil < 3.6.3 or a new R8 rule breaks the release build (design Risk 4) | 2 | 3 | 6 | AC-5 (build) |
| R3 | Session lost on the device after install: Ktor bearer KTOR-9827, OkHttp 5.5 hostname verifier or DoH (design Risk 1) | 1 | 3 | 3 | AC-4 (unit), the `markAsRefreshTokenRequest` grep (build), AC-7 (device), `auth-save` before install |
| R4 | AGP 9.5 breaks `detektAll` (custom classpath, `app/build.gradle.kts:307-322`) or adds lint errors (design Risk 3) | 2 | 2 | 4 | AC-3 (build) |
| R5 | `detektAll` JVM crash read as a pass (research.md: 7) | 2 | 2 | 4 | AC-3: evidence quotes `BUILD SUCCESSFUL` for the run that includes `:app:detektAll` |
| R6 | Room 3.0.3 / sqlite 2.7.1 transaction or DAO behaviour change (design Risk 5) | 1 | 3 | 3 | Instrumented DAO suites (P1), AC-10 (device) |
| R7 | New Gradle 9.9 / AGP 9.5 deprecation on the build scripts (brief AC-2) | 2 | 1 | 2 | AC-2 log diff |
| R8 | mockk 1.14.12 changes verification counts (design Risk 6) | 2 | 1 | 2 | Full unit suite (AC-3); accepted: test-only fallout |
| R9 | kotlinx-serialization 1.13.0 decoding change (design Risk 7) | 1 | 2 | 2 | `data.api` unit tests (AC-4 run), AC-8 |
| R10 | Compose 1.12.1 focus or Media3 1.11.1 HLS regression (design Risk 8) | 1 | 2 | 2 | AC-8, AC-9 (device) |
| R11 | Release build passes but crashes at runtime under R8 (stripped reflective class) | 1 | 3 | 3 | M6 (P2, conditional) |
| R12 | Baseline profile rules go stale (design Risk 9) | 2 | 1 | 2 | Accepted: startup cost only, perf track off; design Open question 1 |
| R13 | A catalog key other than the brief's list changes, or `detekt-compose-rules` moves (brief AC-1) | 1 | 2 | 2 | AC-1 diff |

## Traceability

| AC | Level | Priority | Test | Proves (P0: `guard` or `red`) |
|---|---|---|---|---|
| AC-1 | build | P0 | `git diff 4db9c0d4 -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties` | guard: Fails if any To value differs, an unlisted key changes, or `detekt-compose-rules` leaves 1.4.0 |
| AC-2 | build | P1 | `./tools/agentw :app:compileDevDebugKotlin --warning-mode all` on base and after C4, lines naming `build.gradle.kts`, `settings.gradle.kts` or `buildSrc` diffed (design D8) | Fails if Gradle 9.9 or AGP 9.5 adds a deprecation against this project's scripts |
| AC-3 | build | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` with `AGENT_GRADLE_SERIAL=1`; then `git diff 4db9c0d4 --stat -- config/detekt app/lint*.xml` and `git diff 4db9c0d4 -G@Suppress -- app/src` | guard: Fails if a bump breaks the detekt classpath, adds a lint error, or a finding is hidden by a baseline or `@Suppress`; a JVM crash is retried, not counted |
| AC-4 | unit | P0 | `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized`, `KinoPubApiClientAuthProbeTest.nothingIsRecordedWhenTheLogIsOff`, `KinoPubApiClientFailureTest`, `HistorySerializationTest`; `git diff 4db9c0d4 -- app/src/test/kotlin/com/kino/puber/data/api` is empty | guard: Fails if Ktor 3.6.1 changes the failed-refresh path (no `Unauthorized`, no auth record) or serialization 1.13.0 changes decoding; the empty diff fails if the test was edited to pass |
| AC-4 | build | P1 | `git grep -n markAsRefreshTokenRequest -- app/src/main` prints nothing | Fails if the refresh request becomes marked, the one case where KTOR-9827 changes headers (design D7) |
| AC-5 | build | P0 | `./tools/agentw :app:assembleProdRelease`; APK present under `app/build/outputs/apk/prod/release/` | guard: Fails if AGP 9.5.1 + coil or any library breaks R8 minify or resource shrinking |
| AC-6 | build | P0 | `rm -rf app/build/generated/ksp`, build devDebug (after C1 and C4) and prodRelease (after C4), list `*_Impl.kt` under `app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db/`; `git status --porcelain app/schemas` is empty | guard: Fails unless exactly CachedPayloadDao_Impl, PuberDatabase_AutoMigration_2_3_Impl, PuberDatabase_Impl, WatchStateDao_Impl, WatchStateSyncDao_Impl exist, or if the schema JSON changes |
| AC-6 | instrumented | P1 | `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`, then the same with `TESTS=com.kino.puber.data.db.CachedPayloadDaoTest` | Fails if Room 3.0.3 / sqlite 2.7.1 generated DAOs change upsert, sync, prune or delete results (R6) |
| AC-7 | device | P0 | M1, M2 | guard: Fails if the install or the libraries lose the stored session |
| AC-8 | device | P0 | M3 | guard: Fails if Coil 3.6.3 / Ktor 3.6.1 stop loading posters or D-pad focus breaks in rows |
| AC-9 | device | P0 | M4 | guard: Fails if Media3 1.11.1 HLS does not start, resume after seek, or stalls within 30 s |
| AC-10 | device | P0 | M5 | guard: Fails if the Room DB does not open or watch progress does not persist across a restart |
| AC-5 | device | P2 | M6 | Fails if R8 strips a class the release build needs at startup (R11) |

## Failing-first test

N/A: no bug fix; every P0 is `guard`.

## Manual checks

Emulator only, under a lease (`tools/emulator-resource-lock.sh`), every adb call with `-s <serial>`, dev package `com.kino.puber.stage`. Evidence: screenshots and filtered logcat only (`tools/mobile-evidence-audit.sh` before sharing).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | `make DEVICE=<serial> auth-save`; build the worktree dev debug APK; install it with `tools/android-apk-install-preserve`; compare on-device `base.apk` sha256 with the built APK | Install succeeds, hashes match | Uninstall, data clear, downgrade or `-r` without authorization; a failed install treated as anything but a blocker |
| M2 | Launch the app (`adb -s <serial> shell monkey -p com.kino.puber.stage 1`) | Home opens | Login or pairing screen; crash |
| M3 | On Home press `KEYCODE_DPAD_DOWN` through at least 3 rows and `KEYCODE_DPAD_RIGHT` 5 times in one row; screenshot | Every focused and visible card shows its poster; focus stays on a card | Placeholder-only cards; focus jump to the rail or loss of focus |
| M4 | Open a title, start playback with `KEYCODE_DPAD_CENTER`; after it plays press `KEYCODE_DPAD_RIGHT` to seek forward; wait 30 s; screenshot before and after | Video starts, resumes after the seek, position advances over 30 s | Black screen, endless buffering, error overlay, crash |
| M5 | Back out of the player; `adb -s <serial> shell am force-stop com.kino.puber.stage`; launch again; open the same title; `adb -s <serial> logcat -d --pid=<pid>` filtered for `Room\|SQLite` | The title shows the saved progress | Progress missing; any Room or SQLite exception in the filtered log |
| M6 | Only if `com.kino.puber` is not installed on the leased emulator: install the AC-5 APK with `adb -s <serial> install` and launch it | App reaches its first screen (login, as the worktree has no credentials) without a crash | Install over an existing `com.kino.puber` (replaced signer); crash on start |

## Perf track

N/A: perf track off.

## UNTESTABLE

None.
