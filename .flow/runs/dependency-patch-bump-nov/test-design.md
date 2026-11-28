# Test design: dependency-patch-bump-nov

Base b1b3c51a (brief ACs cite a546f5a5; same build files, `design-architect.md` header). ACs: `brief.md#acceptance-criteria`. Commits, baselines and per-commit checks: `design-architect.md#decisions` (D2-D5) and `#changes-by-layer`. Levels: `.flow/roles/test-designer.md` plus `build` (a Gradle task or file inspection in the worktree, output in the evidence file).

No new test: the change edits no production or test source. Every P0 is `guard`.

## Risk profile

| # | Risk (source) | P | I | Score | Caught by |
|---|---|---|---|---|---|
| R1 | KSP 2.3.14 (2.3.13 caching changes) drops a Room `_Impl`; a missing `AutoMigration_*_Impl` becomes a silent destructive migration (design Risk 1) | 2 | 3 | 6 | AC-4 listing vs `ksp-impl-base.txt` (build), AC-6 incl. `PuberDatabaseMigrationTest` (instrumented) |
| R2 | AGP 9.5.3 undocumented contents break R8, signing fallback, detekt classpath or lint (design Risk 3) | 2 | 3 | 6 | AC-5 (build) |
| R3 | Ktor 3.6.2 KTOR-6905 fails short streamed bodies: broken posters or failed update download (design Risk 2) | 1 | 3 | 3 | `data.api` unit tests (design D5), M3 |
| R4 | Box session lost after the worktree install (design Risk 5) | 2 | 3 | 6 | M1, M2: master refresh and `auth-save` first |
| R5 | Wrapper regeneration changes the jar or adds `gradlew.bat` CRLF churn (design Risk 4) | 2 | 2 | 4 | AC-2 (build) |
| R6 | New Gradle 9.9.3 / AGP 9.5.3 deprecation on project scripts (brief AC-3) | 1 | 1 | 1 | AC-3 log diff; accepted with a note in evidence |
| R7 | Lifecycle 2.11.1 `currentStateFlow` change stalls the session-log refresh in Device settings (design D7) | 1 | 2 | 2 | M5; accepted at device level only |
| R8 | Lifecycle 2.11.1 `minCompileSdk=37` fails AAR metadata (design Risk 6) | 1 | 2 | 2 | `check.fast` |
| R9 | A catalog key outside the brief list changes, or `composeBom` moves (brief AC-1) | 1 | 2 | 2 | AC-1 diff |

## Traceability

| AC | Level | Priority | Test | Proves (P0: `guard` or `red`) |
|---|---|---|---|---|
| AC-1 | build | P0 | `git diff b1b3c51a -- gradle/libs.versions.toml` | guard: fails if a To value differs from the brief, any other key changes, or `composeBom` leaves 2026.09.00 |
| AC-2 | build | P0 | `git diff b1b3c51a -- gradle/wrapper/gradle-wrapper.properties`; `sha256sum gradle/wrapper/gradle-wrapper.jar` against `services.gradle.org/distributions/gradle-9.9.3-wrapper.jar.sha256`; `git diff --stat b1b3c51a -- gradlew gradlew.bat` | guard: fails if more than `distributionUrl` changes, the jar sha differs from the published one, or `gradlew.bat` shows whole-file line-ending churn |
| AC-3 | build | P1 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw help --warning-mode all` after C3, diffed against `warnings-base.log` (D2a) | Fails if Gradle 9.9.3 or AGP 9.5.3 adds a deprecation not recorded with its source |
| AC-4 | build | P0 | `rm -rf app/build/generated/ksp`; `./tools/agentw :app:kspDevDebugKotlin :app:kspProdReleaseKotlin`; sorted `find app/build/generated/ksp -name '*_Impl.kt'` per variant diffed against `ksp-impl-base.txt` (D2b), after C1 and C3; `git status --porcelain app/schemas` empty | guard: fails if any of the base `_Impl` files (database, three DAOs, three auto-migrations) is missing in either variant, or the schema JSON changes |
| AC-5 | build | P0 | `check.full` from `.flow/config.yaml`, then `./tools/agentw :app:assembleProdRelease`; APK under `app/build/outputs/apk/prod/release/`; `git diff b1b3c51a -- app/build.gradle.kts config/detekt app/lint*.xml` empty | guard: fails if AGP 9.5.3 breaks R8, the signing fallback, detekt or lint, or a finding is hidden by a config edit; `BUILD SUCCESSFUL` quoted for the run including `:app:detektAll` |
| AC-5 | unit | P1 | `KinoPubApiClientDownloadTest.downloadUpdateAsset_writesBytesToDiskAsTheyArrive`, `ImageHttpClientTest.imageRequest_carriesNoKinoPubAuthHeaderOrQuery`, `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (run in C2 via `--tests 'com.kino.puber.data.api.*'`); `git diff b1b3c51a -- app/src/test` empty | Fails if Ktor 3.6.2 changes streamed download, the image client or the bearer failure path; the empty diff fails if a test was edited to pass |
| AC-6 | instrumented | P0 | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`, then the same for `CachedPayloadDaoTest`, `PersistentPayloadStoreDbTest`, `PuberDatabaseMigrationTest` | guard: fails if KSP-generated DAOs or auto-migrations change results or `PuberDatabase.create` drops watch state (`productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`) |
| AC-7 | device | P0 | M1-M5 | guard: fails on a crash, a lost session, broken posters or watch badges, playback not starting, or a dead session-log toggle |

## Failing-first test

N/A: no bug fix; every P0 is `guard`.

## Manual checks

TV box `192.168.1.110:5555` only, under a lease (`tools/emulator-resource-lock.sh`), every adb call with `-s 192.168.1.110:5555`, package `com.kino.puber.stage` only. Ordinary use at human pace, no scripted API calls. Evidence: screenshots and filtered logcat, scanned with `tools/mobile-evidence-audit.sh`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | If the box session is stale, install a master dev build first and open Home; `make DEVICE=192.168.1.110:5555 auth-save`; install the worktree dev APK with `tools/android-apk-install-preserve`; compare on-device `base.apk` sha256 with the built APK | Install succeeds, hashes match | Uninstall, data clear, downgrade, replaced signer; touching `com.kino.puber`; a failed install treated as anything but a blocker |
| M2 | `adb -s 192.168.1.110:5555 shell monkey -p com.kino.puber.stage 1` | Home opens logged in | Pairing or login screen; crash |
| M3 | On Home press `KEYCODE_DPAD_DOWN` through 3 rows and `KEYCODE_DPAD_RIGHT` 5 times in one row; screenshot; `KEYCODE_DPAD_CENTER` on a card; screenshot the details | Posters and watch badges on visible cards; details show poster and backdrop | Placeholder or error images (KTOR-6905); missing badges; focus loss |
| M4 | On the details screen start playback with `KEYCODE_DPAD_CENTER`; wait 30 s; screenshot; `KEYCODE_BACK` | Video plays and position advances; back returns to details | Black screen, endless buffering, error overlay, crash |
| M5 | Open Device settings; toggle the session-log row with `KEYCODE_DPAD_CENTER`; `KEYCODE_HOME`, relaunch with monkey; return to Device settings and toggle again; screenshot each state | The toggle flips each time and its description follows after returning | Toggle stuck or description stale after resume (`DeviceSettingsContent.kt:213-214`); crash |

## Perf track

N/A: perf track off.

## UNTESTABLE

None.
