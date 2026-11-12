LENS: edge  ROUND: 1  BASE: 4db9c0d4

### F1 [nit] The spec still lists media3 1.11.1 as the AC-1 target in three places, after the gate-1 delta that set it to 1.11.0
- Where: /home/user1/orca/workspaces/Puber/dependency-update-stable/docs/superpowers/specs/2026-11-12-dependency-update-stable.md:36, :64, :95
- Problem: The Target versions row (`media3` 1.11.0 -> 1.11.1) still says 1.11.1. So do AC-9's wording ("Media3 1.11.1") and the C3 row of Changes by layer. Only the decision log at :223 says the AC-1 target is 1.11.0. The catalog keeps `media3 = "1.11.0"`, so AC-1 read on its own ("every version equals the To column") fails against the spec's own table.
- Failure scenario: The final judge or a later reader checks AC-1 against the table and finds media3 off target. They also read AC-9/R10 as evidence that Media3 1.11.1 HLS was verified on a device, but it never shipped.
- Fix: Change the To cell on :36 to 1.11.0, with a pointer to the :223 decision. Drop "1.11.1" from AC-9 (:64), from the C3 row (:95) and from R10 (:160).

Checked and handled, so not reported:
- The release-signing fallback (`app/build.gradle.kts:173-178`) only runs when there is no `keystore.properties`, no `RELEASE_KEYSTORE_BASE64` and no `STOREPASS`.
  - CI release (`.github/workflows/release.yml:65-94`) checks those secrets before it builds, so it never reaches the fallback.
  - `app/debug.jks` is tracked, and AC-5 evidence shows the signer equals the dev debug certificate.
  - The `keystore.properties` and CI branches are unchanged.
- The `debug` signingConfig (`:139-141`) still sets only `storeFile`. Debug builds pass on AGP 9.5.1 because AGP pre-fills the debug credentials. Every build type that uses it passed `check.full` and the device install.
- JDK: CI pins Java 21, and the toolchain comes from `Versions.kt`, which is unchanged. Nothing in the build scripts, the workflows or `docs/architecture` hardcodes the old AGP, Gradle or Kotlin versions.
- KSP generation was checked after a clean of `app/build/generated/ksp`, for both devDebug and prodRelease (exactly 5 `_Impl` files), and `app/schemas` is clean (qa-report AC-6). A database upgraded from v2 therefore keeps its auto-migration.
- The `ktor` -> `okhttp` coupling is covered: both moved together in the catalog.
- The catalog changes no keys outside the target list, and `detekt-compose-rules` stays at 1.4.0.

### Unproven
- /home/user1/orca/workspaces/Puber/dependency-update-stable/gradle/wrapper/gradle-wrapper.jar: the regenerated jar's checksum was not compared with the official Gradle 9.9.1 wrapper checksum, and there is no `distributionSha256Sum` (a stated choice, D4). It becomes real if CI's `gradle/actions/setup-gradle@v5` wrapper validation rejects the jar.
- Build configuration: AC-2 found 1 new Gradle deprecation (`Configuration.setVisible`, no build script named; qa-report AC-2). It comes from a plugin, so it becomes real when Gradle 10 makes it an error, or if a stack trace pins it to kover 0.9.11 or baselineprofile 1.5.0 and an update exists.
- /home/user1/orca/workspaces/Puber/dependency-update-stable/gradlew.bat: the file went from LF to CRLF line endings (194-line diff, 52 lines once CR is ignored), and the repo has no `.gitattributes`. It becomes real if a Windows checkout or a `core.autocrlf` setting flips it back and causes churn in a later run.
