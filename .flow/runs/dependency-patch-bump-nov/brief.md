# Brief: dependency-patch-bump-nov

Kind: improvement   Size: M   Tracks: none   Base: a546f5a5
Device verification: yes (build toolchain, networking client, Room code generation).
Research: `research.md` (versions, release notes, sha256 values, options; not repeated here).

## Sizing
The request holds two shippable goals, so it is split (research.md: Options, option A):

| # | Run | Size | Order |
|---|---|---|---|
| 1 | this run: AGP 9.5.3, Gradle 9.9.3, KSP 2.3.14, Ktor 3.6.2, Lifecycle 2.11.1 | M | first (Gradle 9.9.2 security fixes) |
| 2 | `compose-1-13-bump`: "Bump Compose BOM 2026.11.01 (Compose 1.13, material3 1.5) with compileSdk 37.1", tracks ui | M | after 1 |

Run 1 is M, not S: it touches the build variants, networking (Ktor) and persistence (Room through KSP) risk areas of `.flow/roles/analyst.md`. No production source change is expected: the edits are `gradle/libs.versions.toml:2,4,12,16` and `gradle/wrapper/gradle-wrapper.properties:3` (plus `gradlew`/`gradlew.bat` if the wrapper task rewrites them). `composeBom` (`libs.versions.toml:8`) and `Versions.CompileSdk` (`buildSrc/src/main/kotlin/Versions.kt:12`) stay as they are.

## Acceptance criteria
- **AC-1** Given the change, When `git diff a546f5a5 -- gradle/libs.versions.toml` is read, Then exactly `agp` 9.5.3, `ksp` 2.3.14, `lifecycleRuntimeKtx` 2.11.1 and `ktor` 3.6.2 differ, and `composeBom` is still 2026.09.00.
- **AC-2** Given the change, When `gradle-wrapper.properties` and `gradle/wrapper/gradle-wrapper.jar` are read, Then `distributionUrl` names `gradle-9.9.3-bin.zip`, the other properties are unchanged, and the jar sha256 equals services.gradle.org `gradle-9.9.3-wrapper.jar.sha256` (research.md: External findings); `git diff --stat` shows no CRLF churn in `gradlew.bat`.
- **AC-3** Given the worktree, When `./tools/agentw help --warning-mode all` runs before and after the change, Then the deprecation warnings after are a subset of those before, or each new one is listed in the evidence with its source.
- **AC-4** Given `app/build/generated/ksp` deleted, When `:app:kspDevDebugKotlin` and `:app:kspProdReleaseKotlin` run, Then every Room `*_Impl` class generated at base is generated again (same file list in both variants).
- **AC-5** Given the change, When `check.full` (`.flow/config.yaml`) and `./tools/agentw :app:assembleProdRelease` run in the worktree, Then both pass (R8 and the signing fallback at `app/build.gradle.kts:171-178` untouched).
- **AC-6** Given a lease on `192.168.1.110:5555`, When `make DEVICE=192.168.1.110:5555 itest TESTS=<each class in app/src/androidTest/kotlin/com/kino/puber/data/db/>` runs, Then all pass.
- **AC-7** Given the worktree dev APK installed with install-preserve on the box after `auth-save`, When Home is opened, posters load, a title card and its details are opened, a playback starts and Device settings is opened and left at normal pace, Then there is no crash, the session stays logged in, images and watch badges show, and the session-log toggle in Device settings still reacts after leaving and returning (`DeviceSettingsContent.kt:213-214`, `currentStateFlow`). (device observation, no scripted API calls)

## Risks
| Risk | Handling |
|---|---|
| Ktor 3.6.2 now fails incomplete streamed responses (KTOR-6905) instead of reading them silently; Coil images go through Ktor (`data/api/network/ImageHttpClient.kt`, `coil-network-ktor3`) | AC-7 posters and card images on the box; a broken-image regression blocks |
| KSP 2.3.14 carries 2.3.13's caching changes; a missing Room `_Impl` fails only at runtime | AC-4, AC-6 |
| AGP 9.5.2/9.5.3 contents undocumented (research.md: External findings) | AC-3, AC-5 |
| Lifecycle 2.11.1 AARs declare `minCompileSdk=37` | Already met by `Versions.kt:12`; `checkDevDebugAarMetadata` in check.fast confirms |
| Box session lost after a worktree install | Refresh with a master build and `auth-save` first (memory: worktree-build-loses-box-session) |
| Merge overlap with `gradle-build-cache` (in review) | No shared lines; rebase on master before ship |

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Split Compose BOM 2026.11.01 into its own run? | Yes: capture `compose-1-13-bump` (stage idea, track ui, `source: dependency-patch-bump-nov`); it needs compileSdk 37.1, `platforms;android-37.1` in the box and CI, `InputMode.Keyboard` in the D-pad UI tests and a visual review | yes |
| Q2 | Regenerate the baseline profile here? | No; it belongs to `compose-1-13-bump` | no |
| Q3 | Add `distributionSha256Sum` to the wrapper properties? | No; keep the previous run's decision, AC-2 checks the jar | no |
