# Release And Baseline Profiles

## GitHub Release

Releases are published by `.github/workflows/release.yml`.

The canonical release path is the Kent `Puber Release` workflow described by
`.kent/commands/release.md`. It performs the complete lifecycle:

1. Create `release/<version>` from the current `origin/master`.
2. Update `currentVersion`, verify it, and deliver the version-bump PR.
3. Wait until GitHub reports that PR merged; neither the agent nor the workflow merges it.
4. Prepare concise user-facing release notes in Russian.
5. After publication approval, create `v<version>` on the merged `master` commit and push it.
6. Monitor release automation, apply the prepared notes, verify the GitHub Release, and clean up conservatively.

The default bump is the next minor version. Patch/hotfix and major releases must be requested explicitly. Never push a
release commit directly to `master`, and never create the tag before the version-bump PR is merged.

The workflow builds `prodRelease`, uploads the APK and SHA-256 checksum as a workflow artifact, and attaches both files
to the GitHub Release for the tag.

GitHub Actions also supports a recovery/manual run through `workflow_dispatch`. Use `release_tag=vX.Y.Z` only for a tag
that already names the intended merged release commit; this does not replace the version PR, tag approval, monitoring, or
final release-note verification.

Required GitHub Secrets:

```text
RELEASE_KEYSTORE_BASE64
STOREPASS
KEYALIAS
PUBER_CLIENT_SECRET
TMDB_READ_ACCESS_TOKEN
```

Optional GitHub Secret:

```text
KEYPASS
```

When `KEYPASS` is not set, Gradle uses `STOREPASS` as the key password.

## Local Release Build

Run:

```bash
./gradlew :app:assembleProdRelease
```

Baseline profile generation is intentionally disabled during release builds:

```kotlin
baselineProfile {
    automaticGenerationDuringBuild = false
}
```

Release builds package the checked-in profiles from:

```text
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

## Refresh Baseline Profiles

Use the helper script:

```bash
./tools/generate-baseline-profile.sh
```

On the first run, if no tokens are provided and `.todo/baseline-profile-auth.env` does not exist, the script:

1. Installs `devNonMinifiedRelease`.
2. Starts the app with the normal OAuth device-code flow.
3. Waits while you enter the code manually.
4. Exports tokens through a provider that is registered only for `devNonMinifiedRelease` and `prodNonMinifiedRelease`.
5. Stores tokens locally in `.todo/baseline-profile-auth.env` with file mode `600`.
6. Runs `./gradlew :app:generateBaselineProfile`.

On later runs, the script reuses `.todo/baseline-profile-auth.env`, so you do not need to authorize again while the
refresh token remains valid.

You can also bypass the cached file by providing tokens explicitly:

```bash
PUBER_BASELINE_ACCESS_TOKEN="..." \
PUBER_BASELINE_REFRESH_TOKEN="..." \
./tools/generate-baseline-profile.sh
```

Optional values:

```bash
PUBER_BASELINE_USERNAME="..." \
PUBER_BASELINE_API_DOMAIN="..." \
./tools/generate-baseline-profile.sh
```

The helper ultimately passes these values to the profile instrumentation tests:

```bash
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.accessToken="$PUBER_BASELINE_ACCESS_TOKEN" \
  -Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.refreshToken="$PUBER_BASELINE_REFRESH_TOKEN"
```

The profile auth receiver/provider are not packaged in `prodRelease`.

## Verification

Run:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:testProdDebugUnitTest
./gradlew :app:assembleProdRelease
```

To sanity-check that the generated profile is available to the benchmark APK on an emulator, run:

```bash
./gradlew :baselineprofile:connectedDevBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kino.puber.baselineprofile.StartupBenchmarks#startupWithProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Use this only as a packaging/install check. Startup timing from an emulator is not representative; use a physical device
for meaningful benchmark numbers.
