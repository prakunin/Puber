# Test design: ci-workflow-hardening

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

## Risk profile

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

## Traceability

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

## Bugs

N/A: improvement, no defect on the base commit.

## Manual checks

| # | Step | Expected | Must not happen |
|---|---|---|---|
| M1 | After merge, on the next tag push, open the Release run's job "Build & Publish" | "Verify release signer" logs the matching digest and exits 0; "Create GitHub Release" publishes `puber-<tag>.apk` and its `.sha256` | 403 or "Resource not accessible by integration" at "Create GitHub Release"; upload or publish running after a failed verify; an "unable to resolve action" error |
| M2 | On the next PR (release version PR), open the "PR Checks" run | Detekt, Android Lint, Unit Tests, Build jobs green with pinned actions resolved | A job failing at checkout or action download; a token write permission shown in "Set up job" |

The qa report states M1 and M2 as the remaining proof; they cannot run before merge.

## Perf

N/A: perf track off.

## UNTESTABLE

none
