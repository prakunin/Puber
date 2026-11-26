# Design (architect): ci-workflow-hardening

Brief: `brief.md` (AC-1..AC-9). Base `90e69aff`. Pin: state.md Q1.

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| How the pinning half lands | `git checkout 2a4bc402 -- .github/workflows` in the worktree, committed as this run's own commit; the plan-file hunk of `2a4bc402` is not taken | master's `.github/` equals the merge base `358b0ad6`, so the checkout yields exactly the reviewed diff; no cherry-pick of a file absent on master |
| Action versions | Current majors pinned: checkout v4.4.0, setup-java v4.9.1, setup-gradle v5.0.2, upload-artifact v4.6.2, action-gh-release v2.6.2 (SHAs in `research.md` section 5) | Q2 answered; same SHAs as `2a4bc402` |
| "Assert GitHub-hosted runner" step | Kept in every job, as in `2a4bc402` | Q4 answered |
| Where the signer check lives | New `tools/verify-apk-signer.sh`, called by one workflow step | Runs locally against real and throwaway APKs (AC-4..AC-6); the workflow step stays one line |
| Script language | Bash, `set -euo pipefail` | AC-9 `bash -n`; runner and dev box both have bash, grep, sort |
| Script contract | `verify-apk-signer.sh <apk-dir-or-file> <sha256>`; exit 0 on match, 1 on any signer failure (verify failed, no digest parsed, more than one distinct digest, mismatch), 2 on input errors (wrong arg count, pin not 64 hex after normalising, path missing, zero or several APKs) | Every failure path names its cause on stderr (AC-5, AC-6) |
| APK selection | A directory: `find <dir> -maxdepth 1 -type f -name '*.apk'`, exactly one required; a file: used as is | Same glob as `release.yml:99`; a second APK (future splits) fails instead of shipping an arbitrary one |
| apksigner location | Newest `${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/*/apksigner` by `sort -V`; none found is exit 2 | Mirrors `tools/android-apk-install-preserve:82-85`; `ubuntu-latest` sets `ANDROID_HOME` and ships build-tools 34..37 (`research.md` section 5); the dev box has `/opt/android-sdk/build-tools/36.0.0` |
| Digest parsing | `apksigner verify --print-certs`, its exit code checked first; digests extracted case-insensitively with the pattern of `tools/android-apk-install-preserve:18-21`, lower-cased, colons stripped, de-duplicated | apksigner 36 prints `Signer #1 certificate` without a colon; the existing regex already covers both forms |
| Pin location | Literal in the step: `env: RELEASE_CERT_SHA256: 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6`, passed as the script's second argument | AC-7 asks for the literal; a repo variable could change without a reviewed diff |
| Step placement | "Verify release signer" between "Build release APK" (`release.yml:86-94`) and "Prepare release artifacts" (`:96`), no `if:` or `continue-on-error`, argument `app/build/outputs/apk/prod/release` | Default step semantics stop the job, so upload (`:111`) and publish (`:118`) never run on a failure (AC-7) |
| "Prepare release artifacts" | Unchanged | The script already guarantees exactly one APK before it runs |
| Docs | One sentence after `docs/release.md:46-47`: the release job fails unless the APK signer SHA-256 equals the pin in `release.yml`, checked by `tools/verify-apk-signer.sh` | Single place for the pin's value stays the workflow |

## Changes by layer

App layers (API / repository, ViewModel, screen and DI, Compose, resources): N/A, no file under `app/` changes (AC-8).

| Layer | File | Change |
|-------|------|--------|
| CI | `.github/workflows/release.yml:14-15` | `contents: write` -> `contents: read` |
| CI | `release.yml:42` (job `release`) | job-level `permissions: contents: write` |
| CI | `release.yml:24,50` | pinned checkout, `persist-credentials: false`, `ref: ${{ env.RELEASE_REF }}` kept; runner-assert step after each |
| CI | `release.yml:29,35,55,61,112,119` | SHA pins with `# vX.Y.Z` comments |
| CI | `release.yml` after `:94` | new step "Verify release signer": `run: bash tools/verify-apk-signer.sh app/build/outputs/apk/prod/release "$RELEASE_CERT_SHA256"` |
| CI | `.github/workflows/pr-checks.yml` after `:5` | top-level `permissions: contents: read` |
| CI | `pr-checks.yml:16,36,63,90`, `:19,39,66,93`, `:25,45,72,99`, `:54,81` | pins, `persist-credentials: false` on the four checkouts, runner-assert step per job |
| Tooling | `tools/verify-apk-signer.sh` (new, mode 755) | contract in Decisions; prints `expected: <pin>` and `actual: <digest(s)>` on mismatch |
| Docs | `docs/release.md:46-47` | one sentence (Decisions) |

Invoking the script through `bash` in the step keeps it working if the executable bit is lost; the bit is still set for local use.

## Alternatives rejected

- Inline the apksigner check in the workflow `run:` block: not runnable locally, so AC-4..AC-6 could not be proven before a tag.
- Python script reusing `android-apk-install-preserve` code: that tool is built around adb and a serial; a second entry point couples release CI to a device tool.
- Upstream `7bd28c11` keytool pre-check of the decoded keystore: Q3 answered no.
- Pin in a repository variable (`vars.RELEASE_CERT_SHA256`): changeable without a reviewed diff.
- Node 24 majors now: Q2 answered no.

## Risks

| # | Kind | Risk | Lives in | Mitigation |
|---|------|------|----------|------------|
| 1 | Rollout | The step meets the real keystore build only at the next tag push; a fault fails a release after the tag exists | `release.yml` new step, `tools/verify-apk-signer.sh` | AC-4 on the published v1.12.7 APK; recovery is a fix merged to master plus `workflow_dispatch` with `release_tag` (`release.yml:7-12`), but that run checks out the tag, so the fix only applies to a new tag. qa report states the first release is the remaining proof |
| 2 | Correctness | The runner picks build-tools 37 (newest), the local proof uses 36.0.0; a changed output format would parse zero digests | `tools/verify-apk-signer.sh` digest extraction | Fail closed: zero parsed digests is exit 1 with apksigner's output; regex accepts both known forms |
| 3 | Security | Job-level `permissions` replace the top-level block; dropping `contents: write` from job `release` breaks publishing, adding it elsewhere widens the token | `release.yml:14-15`, job `release` at `:42` | AC-3 grep; `upload-artifact` uses the runtime token, not `contents` |
| 4 | Security | `persist-credentials: false` in job `release`: `action-gh-release` authenticates via its `token` input (default `github.token`); nothing runs `git push` | `release.yml:50`, `:118-124` | Proven only by the next release (same run as risk 1) |
| 5 | Data shape | An ABI split or a second variant output would put several APKs in `app/build/outputs/apk/prod/release` | script APK selection; `release.yml:99` `head -n 1` | Script fails on more than one APK (AC-6), so `head -n 1` never chooses |
| 6 | Lifecycle | Pins go stale; Node 20 annotations and the setup-java v4 notice remain on every run | both workflows | Separate major-bump run (Q2) |
| 7 | Rollout | `pr-checks.yml` runs only on a PR; delivery is a local merge, so its first run is the next release version PR | `pr-checks.yml` | AC-9 parse; the version PR shows failures before a tag |

Concurrency, performance: N/A, one sequential step of a few seconds.

## Open questions

none
