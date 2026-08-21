# Release And Baseline Profiles

## GitHub Release

Releases are published by `.github/workflows/release.yml`.

## The release procedure

The default bump is the next **minor** version: `X.Y.Z` -> `X.(Y+1).0`. A patch
(`X.Y.(Z+1)`) or a major (`(X+1).0.0`) happens only when the task says so
explicitly.

1. Fetch `origin/master` and tags, and read `currentVersion` from
   `app/build.gradle.kts` on that base.
2. Create or reuse `release/<version>` from the fetched base.
3. Update `currentVersion`, commit as `Bump version to <version>`, and verify
   with compile checks. Missing local signing secrets are not a reason to hold
   the version-bump PR - report that production packaging was not proven
   locally and continue.
4. Open or update the PR for `release/<version>` and watch its checks:
   `gh pr checks <pr> --watch --interval 30`. Queued or running checks are not
   a blocker; wait for the terminal state and then re-read the status.
5. Wait until GitHub reports the PR merged. Neither an agent nor this workflow
   merges it.
6. Prepare concise user-facing release notes in Russian for the range between
   the previous tag and the merged commit. Keep them under an ignored path such
   as `.todo/<task>/release-notes-ru.md`. Drop release-only chores and rewrite
   technical commit titles into user-visible changes.
7. Ask for publication approval, and only then create `v<version>` on the
   merged `master` commit and push the tag. The merge is not the approval:
   tagging is what publishes, so it needs its own explicit go-ahead.
8. Watch the release automation to its terminal state:
   `gh run watch <run-id> --exit-status --interval 30`.
9. Apply the prepared notes with
   `gh release edit <tag> --notes-file <path>` and verify the published body
   before cleaning up.

Hard rules: never push a release commit directly to `master`, never merge the
version PR yourself, and never create or push the tag before the version bump is
on `origin/master`. If a tag already exists and points somewhere other than the
intended commit, stop and say so.

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

Regenerating profiles installs a build on a real device, drives it through the
OAuth device-code flow, and rewrites files that are checked in - ask before
starting it, and never fold it into an unrelated change.

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
