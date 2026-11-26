# Harden the CI workflows and pin the release signer

Status: approved
Date: 2026-11-26
Slug: ci-workflow-hardening
Kind: improvement
Size: M
Tracks: none

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Goal

Both GitHub workflows run pinned, least-privilege actions, and the release job fails unless the built prod APK is signed by the release certificate `7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6` (state.md Q1).

### Why M

One goal and four files, but it changes the security posture of the release path (token scope, credentials, signer gate), and the release behaviour is proven only by the next real tag run. No production code, no device.

### Code touched

| File | Today | Change |
|------|-------|--------|
| `.github/workflows/release.yml:14-15` | top-level `contents: write` | `contents: read`; job `release` (`:42`) gets `contents: write` |
| `release.yml:24,50,29,55,35,61,112,119` | floating `@v4/@v5/@v2` | SHA pins with version comments (`research.md: 5`, identical to `2a4bc402`); `persist-credentials: false` on both checkouts |
| `release.yml:94-96` | build, then `find ... \| head -n 1` | new step "Verify release signer" between "Build release APK" and "Prepare release artifacts" |
| `.github/workflows/pr-checks.yml` | no `permissions:`; 4 checkouts (`:16,36,63,90`), `upload-artifact@v4` (`:54,81`) | top-level `contents: read`, pins, `persist-credentials: false` |
| `tools/verify-apk-signer.sh` (new) | none | `<apk-dir-or-file> <sha256>`: exactly one APK, newest `build-tools/*/apksigner`, digest regex as `tools/android-apk-install-preserve:18-21`, lower-case, colons stripped, exits non-zero with expected and actual digests |
| `docs/release.md:45-47` | no signer check | one sentence on the signer pin and where it lives |

The pinning half is commit `2a4bc402` on branch `upstream-sync-triage`; master's `.github/` is unchanged since its merge base `358b0ad6`, so it cherry-picks cleanly (checked: `git show 2a4bc402`). That commit also touches `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md`, which does not exist on master: take only the `.github/` hunks.

Out of scope by request: `app/build.gradle.kts:138-180` signing fallback to `debug.jks` (the signer pin is what turns that fallback into a failed release).

### Risks (analyst)

- The signer step meets the real keystore only at the next tag run; a wrong path or regex then fails a release after the tag is pushed. AC-4 on the real published APK is the mitigation; the qa report should state the first release is the remaining proof.
- `persist-credentials: false` in job `release`: `action-gh-release` uses its `token` input (default `github.token`), and `release.yml` runs no `git push`; proven only by the next release.
- Pinned actions on Node 20 keep the deprecation annotations seen on run 45638029261 (`research.md: 3`); Node 20 is already removed from runners and runs still pass under forced Node 24.
- `pr-checks.yml` runs only on a pull request; delivery is a local merge, so the pinned PR workflow is first exercised by the next PR (the release version PR).

## Acceptance criteria

- AC-1. Given both workflow files, When every `uses:` line is listed, Then each reads `<owner>/<repo>[/<path>]@<40 hex> # v<X.Y.Z>` and no floating tag remains (grep).
- AC-2. Given both workflow files, When every `actions/checkout` step is read, Then each has `persist-credentials: false`, and the two release checkouts keep `ref: ${{ env.RELEASE_REF }}`.
- AC-3. Given both workflow files, When `permissions` blocks are read, Then top level is `contents: read` in each, job `release` alone has `contents: write`, and no other job declares permissions.
- AC-4. Given the published v1.12.7 release APK (`gh release download v1.12.7`, public GitHub asset, not the KinoPub API) and the pin, When `tools/verify-apk-signer.sh` runs with apksigner 36.0.0, Then it exits 0.
- AC-5. Given an APK signed with a throwaway key generated in the scratch dir (or the repo's `app/debug.jks`), When the script runs with the pin, Then it exits non-zero and prints the expected and the actual digest.
- AC-6. Given a directory with no APK, with two APKs, or an unsigned APK, When the script runs, Then it exits non-zero with a message naming the cause.
- AC-7. Given `release.yml`, When the steps of job `release` are read, Then "Verify release signer" runs after "Build release APK" and before "Prepare release artifacts", "Upload APK artifact" and "Create GitHub Release", calls the script with the literal pin, and has no `if:`/`continue-on-error` that lets later steps run after it fails.
- AC-8. Given the changed workflows, Then the Android Lint job, `workflow_dispatch` with `release_tag`, "Validate release secrets" (5 required, `KEYPASS` optional), `generate_release_notes: true` and the file name `release.yml` (`tools/release.sh:132`) are unchanged, and `git diff master -- app/` is empty.
- AC-9. Given both workflow files, When parsed with `python3 -c 'import yaml'` (pyyaml present; actionlint is not installed and is not installed for this run), Then both parse and `bash -n tools/verify-apk-signer.sh` passes.

## Out of scope

Out of scope by request: `app/build.gradle.kts:138-180` signing fallback to `debug.jks` (the signer pin is what turns that fallback into a failed release).

## Design

Brief: `brief.md` (AC-1..AC-9). Base `90e69aff`. Pin: state.md Q1.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

- Inline the apksigner check in the workflow `run:` block: not runnable locally, so AC-4..AC-6 could not be proven before a tag.
- Python script reusing `android-apk-install-preserve` code: that tool is built around adb and a serial; a second entry point couples release CI to a device tool.
- Upstream `7bd28c11` keytool pre-check of the decoded keystore: Q3 answered no.
- Pin in a repository variable (`vars.RELEASE_CERT_SHA256`): changeable without a reviewed diff.
- Node 24 majors now: Q2 answered no.

### Risks

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

### Open questions

none

## Test design

Inputs: `brief.md` (AC-1..AC-9), `design-architect.md` (risks 1-7). Base `90e69aff`. Pin: `state.md` Q1.

No JUnit or device level applies: nothing under `app/` changes. Levels used here:

| Level | Form | Run |
|---|---|---|
| `static` | `tools/tests/test_ci_workflows.py`: one `test_*` function per row, parses both workflows with `yaml.safe_load`, exits non-zero if any fails | `python3 tools/tests/test_ci_workflows.py` from the worktree root |
| `script` | `tools/tests/test-verify-apk-signer.sh`: one `test_*` bash function per row, each in a fresh `mktemp -d`, exits non-zero if any fails | `bash tools/tests/test-verify-apk-signer.sh <published-v1.12.7.apk>`; the APK comes from `gh release download v1.12.7 -p '*.apk' -D <scratch>` (public GitHub asset, never the KinoPub API) |
| `manual` | Observation of the first real runs after merge | qa report |

Fixtures for `script`, built inside the test from the published APK:

| Fixture | How |
|---|---|
| debug-signed APK | `apksigner sign --ks app/debug.jks --ks-pass pass:android --ks-key-alias androiddebugkey --out <tmp>/debug.apk <published.apk>` (values from `app/build.gradle.kts:175-178`) |
| expected debug digest | `keytool -list -v -keystore app/debug.jks -storepass android -alias androiddebugkey`, SHA256 line, lower-cased, colons stripped; computed independently of the script under test |
| unsigned APK | `python3 -c` with `zipfile`: copy every entry except `META-INF/*` into a new zip (no v1 files, no signing block) |
| fake SDK | `<tmp>/sdk/build-tools/<ver>/apksigner` stub scripts printing fixed text; `ANDROID_HOME=<tmp>/sdk`, `ANDROID_SDK_ROOT` unset. A fake of apksigner, never of the script under test |

pyyaml reads the key `on:` as boolean `True`; the static tests look up `doc[True]`.

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Signer step meets the real keystore only at the next tag (design 1) | 2 | 3 | 6 | `test_published_release_apk_passes`; M1 |
| R2 | Build-tools 37 on the runner prints another format, zero digests parsed (design 2) | 1 | 3 | 3 | `test_unparsed_output_fails_closed`, `test_newest_build_tools_chosen` |
| R3 | `contents: write` lost from job `release` or granted elsewhere (design 3) | 2 | 3 | 6 | `test_permissions_read_top_level_write_only_on_release_job` |
| R4 | `persist-credentials: false` breaks `action-gh-release` (design 4) | 1 | 3 | 3 | M1 |
| R5 | Several APKs in the output dir, an arbitrary one shipped (design 5) | 1 | 2 | 2 | `test_two_apks_fail` |
| R6 | Stale pins, Node 20 annotations (design 6) | 3 | 1 | 3 | Accepted: separate major-bump run (Q2) |
| R7 | `pr-checks.yml` first runs on the next PR (design 7) | 1 | 2 | 2 | `test_workflows_parse`; M2 |
| R8 | Signer step soft-fails (`if:`, `continue-on-error`, wrong order) and publish still runs | 1 | 3 | 3 | `test_signer_step_gates_upload_and_publish` |
| R9 | Pin case or colons differ from apksigner output, a good APK is rejected | 2 | 2 | 4 | `test_pin_is_normalised` |
| R10 | The `2a4bc402` checkout drags the plan file or touches `app/` or the release contract | 1 | 2 | 2 | `test_release_contract_unchanged` |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | static | P0 red | `test_every_uses_is_sha_pinned` | Every `uses:` in both files matches `^[\w.-]+/[\w.-]+(/[\w./-]+)?@[0-9a-f]{40} # v\d+\.\d+\.\d+$`; fails on any floating `@v4` left or a pin without its version comment |
| AC-2 | static | P0 red | `test_checkouts_drop_credentials_and_keep_release_ref` | All six `actions/checkout` steps have `persist-credentials: false` (boolean); both release checkouts keep `ref: ${{ env.RELEASE_REF }}`; fails if one checkout is missed or a `ref` is dropped |
| AC-3 | static | P0 red | `test_permissions_read_top_level_write_only_on_release_job` | Top level of each file is exactly `{contents: read}`; `jobs.release.permissions == {contents: write}`; no other job has `permissions`; fails if write stays top-level, is lost from `release` or appears on `test` |
| AC-4 | script | P0 red | `test_published_release_apk_passes` | Script on the published v1.12.7 APK with the pin, real apksigner 36.0.0, exits 0; fails if the regex misses apksigner 36 output or the APK lookup is wrong |
| AC-5 | script | P0 red | `test_debug_signed_apk_fails_with_both_digests` | Exit 1; output contains the pin and the independently computed debug digest; fails if the comparison is skipped or only one digest is printed |
| AC-6 | script | P0 red | `test_empty_dir_fails` | Empty dir: exit 2, message names "no APK"; fails if an empty dir passes |
| AC-6 | script | P0 red | `test_two_apks_fail` | Two APKs (published copied twice): exit 2, message names more than one APK; fails if the script picks one like `head -n 1` |
| AC-6 | script | P0 red | `test_unsigned_apk_fails` | Unsigned zip: exit 1, message names the verify failure; fails if apksigner's exit code is ignored |
| AC-7 | static | P0 red | `test_signer_step_gates_upload_and_publish` | In `jobs.release.steps`, "Verify release signer" index is after "Build release APK" and before "Prepare release artifacts", "Upload APK artifact", "Create GitHub Release"; `run` calls `tools/verify-apk-signer.sh`; the step's text holds the literal pin; no `if` or `continue-on-error` key on it; fails if the step moves, is softened or loses the pin |
| AC-8 | static | P0 guard | `test_release_contract_unchanged` | Against `git show 90e69aff:<file>`: `jobs.lint` step names and `run` lines equal; `doc[True].workflow_dispatch.inputs.release_tag` equal; "Validate release secrets" `run` and `env` keys equal; `generate_release_notes: true`; `.github/workflows/release.yml` exists (`tools/release.sh:132`); `git diff 90e69aff -- app/ docs/superpowers/plans/` empty |
| AC-9 | static | P0 guard | `test_workflows_parse` | Both files load with `yaml.safe_load` to a dict with `jobs` |
| AC-9 | script | P0 red | `test_script_syntax` | `bash -n tools/verify-apk-signer.sh` exits 0 and the file is executable |
| AC-4 | script | P1 | `test_apk_file_argument_accepted` | Path to the APK file instead of its dir exits 0; fails if only directories are handled |
| AC-4 | script | P1 | `test_pin_is_normalised` | Pin given upper-case with colons every two hex chars exits 0 on the published APK; fails if normalisation is dropped (R9) |
| AC-4 | script | P1 | `test_unparsed_output_fails_closed` | Fake SDK whose apksigner exits 0 and prints no digest line: exit 1; fails if zero digests counts as a match (R2) |
| AC-6 | script | P2 | `test_bad_pin_rejected` | 63-hex pin and wrong arg count: exit 2; fails if a malformed pin reaches the comparison |
| AC-6 | script | P2 | `test_missing_apksigner_fails` | `ANDROID_HOME` set to an empty dir: exit 2 naming apksigner; fails if the script falls through to a missing binary with a misleading message |
| AC-4 | script | P2 | `test_newest_build_tools_chosen` | Fake SDK with `9.0.0` (prints a wrong digest) and `10.0.0` (prints the pin): exit 0; fails on lexical instead of `sort -V` ordering |
| AC-5 | script | P2 | `test_mixed_signers_fail` | Fake apksigner printing two different `Signer #n` digests, one equal to the pin: exit 1; fails if any-match is accepted instead of a single distinct digest |

Every `red` row is run first against the base tree (workflows at `90e69aff`, no script): static rows fail on floating tags and the top-level write, script rows fail because `tools/verify-apk-signer.sh` is absent.

### Bugs

N/A: improvement, no defect on the base commit.

### Manual checks

| # | Step | Expected | Must not happen |
|---|---|---|---|
| M1 | After merge, on the next tag push, open the Release run's job "Build & Publish" | "Verify release signer" logs the matching digest and exits 0; "Create GitHub Release" publishes `puber-<tag>.apk` and its `.sha256` | 403 or "Resource not accessible by integration" at "Create GitHub Release"; upload or publish running after a failed verify; an "unable to resolve action" error |
| M2 | On the next PR (release version PR), open the "PR Checks" run | Detekt, Android Lint, Unit Tests, Build jobs green with pinned actions resolved | A job failing at checkout or action download; a token write permission shown in "Set up job" |

The qa report states M1 and M2 as the remaining proof; they cannot run before merge.

### Perf

N/A: perf track off.

### UNTESTABLE

none

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which release certificate SHA-256 does the signer pin check? | yes | answered | 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6; the key never changed (owner) |
| Q2 | Stay on current action majors (pinned by SHA) instead of the Node 24 majors now? | no | answered | Yes; bump majors in a separate run |
| Q3 | Add upstream's keytool pre-check of the decoded keystore? | no | answered | No; the APK signer check covers it |
| Q4 | Keep the "Assert GitHub-hosted runner" step from 2a4bc402? | no | answered | Yes |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Release signer SHA-256 7431d0e6...f2c6 is the pin; the key never changed | owner |
| 2026-11-26 | Q2-Q4 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved | owner (standing instruction: recommended) |
| 2026-11-26 | Q5 (plan): the AC-8 test checks that docs/superpowers/plans/2026-10-11-upstream-sync-triage.md does not exist, instead of an empty diff of docs/superpowers/plans/ (this run adds its own plan there) | owner (standing instruction: recommended) |
