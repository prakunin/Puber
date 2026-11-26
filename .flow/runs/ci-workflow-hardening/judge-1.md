# Gate - ci-workflow-hardening - round 1

**Decision:** PASS
**Head:** cfa72862   **Base:** 90e69aff

## Done as asked

The diff (`git diff 90e69aff...cfa72862`) pins every action by SHA, sets `persist-credentials: false` on every checkout, narrows the token to `contents: read` with `contents: write` on job `release` only, and adds "Verify release signer" (`.github/workflows/release.yml:115-118`) between "Build release APK" and "Prepare release artifacts". The verifier is taken from `github.sha` by a sparse checkout (`release.yml:106-113`, triage-1 #1). `app/` diff is empty; the `build.gradle.kts` signing fallback is untouched (Out of scope). The five pinned SHAs match their tags (`git ls-remote`, re-run here).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `test_every_uses_is_sha_pinned` | re-run here: PASS; ls-remote of all 5 tags matches | PASS |
| AC-2 | `test_checkouts_drop_credentials_and_keep_release_ref` | re-run here: PASS | PASS |
| AC-3 | `test_permissions_read_top_level_write_only_on_release_job` | re-run here: PASS | PASS |
| AC-4 | `test_published_release_apk_passes` + 5 P1/P2 rows; M1 for the runner | re-run here on v1.12.7 APK: PASS | PASS |
| AC-5 | `test_debug_signed_apk_fails_with_both_digests`, `test_mixed_signers_fail` | re-run here: PASS | PASS |
| AC-6 | `test_empty_dir_fails`, `test_two_apks_fail`, `test_unsigned_apk_fails`, `test_bad_pin_rejected`, `test_missing_apksigner_fails`, `test_missing_path_fails` | re-run here: PASS | PASS |
| AC-7 | `test_signer_step_gates_upload_and_publish`; M1 | re-run here: PASS | PASS |
| AC-8 | `test_release_contract_unchanged` + diff read | Lint job and "Validate release secrets" hunks absent from the diff; `git diff 90e69aff...HEAD -- app/` empty | PASS |
| AC-9 | `test_workflows_parse`, `test_script_syntax` | re-run here: PASS | PASS |

check.full: `qa-report.md` (06:30) is newer than cfa72862 (06:29); qa reports BUILD SUCCESSFUL; not re-run.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| triage-1 #1 old-tag dispatch recovery lacks the script | blind F1, codex | fixed | c11d3e59; `release.yml:106-118`, `docs/release.md:47-49` |
| triage-1 #2 run-scoped checks in `test_ci_workflows.py` | blind F2, edge F1, tests F3 | fixed | c11d3e59; no `BASE`, no `git show` in the file |
| triage-1 #3 AC-7 test does not tie dir and pin | tests F1 | fixed | cfa72862; `test_ci_workflows.py` asserts dir, `"$RELEASE_CERT_SHA256"`, env value |
| triage-1 #4 later steps / job soft-fail unchecked | tests F2 | fixed | cfa72862; loop over later steps and `jobs.release` |
| triage-1 #5 hardcoded keytool / SDK paths | blind F3 | fixed | cfa72862; `command -v keytool`, `JAVA_HOME`, exit 2 with message |
| triage-1 #6 v3.1 rotated-signer lines missed | blind, edge (unproven) | fixed | cfa72862; regex on any `certificate SHA-256 digest:`; `test_rotated_signer_line_counts` |
| triage-1 #7 exit-2 missing-path branch untested | tests (unproven) | fixed | `test_missing_path_fails` |
| triage-1 #8 `RUNNER_ENVIRONMENT` unset passes | blind (unproven) | rejected | accepted: all jobs are `ubuntu-latest`; guard kept by Q4 |
| triage-1 #9 hostile verifier on the tag | codex adversarial | rejected | accepted: tag author controls workflow and build anyway; verifier now from `github.sha` |
| triage-1 #10 verify the exact staged APK | codex | rejected | accepted (spot-checked): script requires one APK in `app/build/outputs/apk/prod/release`, the dir "Prepare release artifacts" reads |
| triage-2 #1 no test requires the verifier checkout at `github.sha` | edge-2 F1, tests-2 F1 | accepted minor | workflow correct at `release.yml:106-118`; test-only gap |
| triage-2 #2 durable AC-8 test drops the 5-secret check | tests-2 F2 | accepted minor | step unchanged in the diff |
| triage-2 #3 regex also matches a Source Stamp digest | antigravity-2, edge-2, codex-2 | accepted minor | fails closed; no stamp configured |
| triage-2 #4 `total > 0` misses a dropped PR checkout | tests-2 (unproven) | rejected | accepted (spot-checked): AC-1 and the diff keep all 4 PR checkouts |
| triage-2 #5 release may publish unchecked APKs | codex-2 | rejected | same as triage-1 #10 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One extra shallow sparse checkout and one apksigner call per release; seconds |
| Reliability | Every script failure exits non-zero with a cause; zero or several digests fail closed; first real run is M1 |
| Maintainability | Spec Design still describes the step as `bash tools/verify-apk-signer.sh` and risk 1 as "fix only applies to a new tag"; the workflow and `docs/release.md` hold the current behaviour. No dead code |
| Security / privacy | Pin is a public certificate digest; `debug.jks` password is the public debug one; no secrets or account data added; token scope narrowed |

## Manual checks for the owner

| # | Steps | Expected |
|---|-------|----------|
| M1 | After merge, on the next tag push open GitHub Actions, run "Release", job "Build & Publish" | "Check out the signer verifier..." and "Verify release signer" green, log `signer OK: 7431d0e6...`; "Create GitHub Release" publishes `puber-<tag>.apk` and `.sha256`; no 403 / "Resource not accessible by integration", no "unable to resolve action" |
| M2 | On the next PR (release version PR) open "PR Checks" | Detekt, Android Lint, Unit Tests, Build green; "Set up job" lists `Contents: read` only |

## Must fix

none

## Concerns for the owner

none
