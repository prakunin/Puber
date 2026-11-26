# Release And Baseline Profiles

## GitHub Release

Releases are published by `.github/workflows/release.yml`.

## The release procedure

The `release` skill (`.claude/skills/release/`) runs this procedure; the
mechanical steps are `tools/release.sh prepare | checks | range | status |
publish`.

The default bump is the next **patch** version: `X.Y.Z` -> `X.Y.(Z+1)`. A minor
(`X.(Y+1).0`) or a major (`(X+1).0.0`) happens only when the owner asks for it.

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
to the GitHub Release for the tag. The release job fails unless the APK signer SHA-256 equals the pin in
`.github/workflows/release.yml` (step "Verify release signer"), checked by `tools/verify-apk-signer.sh`. The script is taken from the workflow's own revision (`github.sha`), so a
`workflow_dispatch` recovery of a tag cut before the script existed still runs the check.

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

Every non-debuggable variant, `prodRelease` included, packages them as `assets/dexopt/baseline.prof` and
`assets/dexopt/baseline.profm`, so an app update carries the profile to the device.

## Refresh Baseline Profiles

Refresh before every release whose diff touches startup, Home, Details or the player, and at least every minor
release. Regenerating installs a build on a device, drives it, and rewrites files that are checked in - ask before
starting it, and never fold it into an unrelated change.

One command, on one named emulator. It needs no secrets, no `.env`, no KinoPub account and no login on the device:

```bash
DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh
```

It runs `make baseline-profile`, which talks to a private adb server that sees only `$DEVICE`, so no other device is
touched. The device must be an emulator with API 34 or newer: there the benchmark library resets the app in place, and
the make target refuses anything else. The script first checks that `$DEVICE` is reachable (`adb connect` for a
host:port serial, `get-state` is `device`), is an emulator and runs API 34 or newer; otherwise it exits 2 before
installing anything.

Generation drives the `instrumentation` flavor (`com.kino.puber.instrumentation`, the only variant that generates). It
runs against an HTTPS mock the generator hosts on `127.0.0.1:18765` (`puber.baselineMockPort` in `gradle.properties`),
with seeded synthetic tokens in the real token store and a network config that trusts only the mock's test root. The
app resolves `api.baseline.test` through DNS-over-HTTPS served by the mock (`dns.baseline.test`, bootstrap `127.0.0.1`),
probes the pinned endpoint, runs the update check against the mock's `/github` route and publishes one TV home program,
so the production cold-start paths stay in the profile. An egress blocker allows the mock by IP and those two test hosts
on the mock port and refuses every other host, journaling the attempt, so nothing reaches KinoPub, TMDB or any other
server. Before each iteration the target is killed and its databases and caches are dropped; after it, the generator
checks that the target's egress journal is empty, that the mock saw no unknown route and that every required route was
hit (the per-iteration `PuberGen` lines in the logcat file the script prints). A late check (`PuberGen` `late after=`
lines) repeats the journal and unknown-route test before each generator test after the first and after the last one.
Any violation fails the run. A route the app now requests and the mock lacks is added to the route table in
`BaselineMockBackend.kt`; the check is never loosened.

The refresh marks every method rule in `baseline-prof.txt` hot (`H`): on API 29 the `speed-profile` compile
builds only hot methods, so rules left as `S` or `P` do not shorten startup.

Generation runs against an `S`/`P`-only copy of `baseline-prof.txt`: the make target strips the `H` first, because
the all-`H` profile embedded in the target APK coincided with `Failed to compile` flakes between journeys. A failed
run restores the checked-in file; a successful one marks the new file `H`.

The mock's TLS material and media are committed output of two recipes; rerun them only to replace the files:

| Recipe | Writes | Expiry |
|---|---|---|
| `tools/make-baseline-mock-cert.sh` | `app/src/instrumentation/res/raw/baseline_mock_ca.pem`, `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem` (leaf SANs `IP:127.0.0.1`, `DNS:api.baseline.test`, `DNS:dns.baseline.test`) | both valid 2020-01-01 to 2051-01-01 (notBefore far in the past: the emulator clock can lag the host) |
| `tools/make-baseline-mock-media.sh <dir>` (needs ffmpeg) | `baselineprofile/src/main/assets/baseline/media/` | none |

`StartupBenchmarks` targets `com.kino.puber.instrumentation` without a mock; the run `startup-benchmark-entry` owns it.

## Verification

Run:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:testProdDebugUnitTest
./gradlew :app:assembleProdRelease
```
