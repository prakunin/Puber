# Readiness 1: ci-workflow-hardening

Verdict: **READY**

Spec and plan: `/home/dev/orca/workspaces/Puber/ci-workflow-hardening/docs/superpowers/specs/2026-11-26-ci-workflow-hardening.md`, `.../plans/2026-11-26-ci-workflow-hardening.md`. Base `90e69aff`.

## AC coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | 1 | `test_every_uses_is_sha_pinned` |
| AC-2 | 1 | `test_checkouts_drop_credentials_and_keep_release_ref` |
| AC-3 | 1 | `test_permissions_read_top_level_write_only_on_release_job` |
| AC-4 | 2 | `test_published_release_apk_passes` (+ P1/P2 rows) |
| AC-5 | 2 | `test_debug_signed_apk_fails_with_both_digests`, `test_mixed_signers_fail` |
| AC-6 | 2 | `test_empty_dir_fails`, `test_two_apks_fail`, `test_unsigned_apk_fails` |
| AC-7 | 3 | `test_signer_step_gates_upload_and_publish` |
| AC-8 | 1 | `test_release_contract_unchanged` |
| AC-9 | 1, 2 | `test_workflows_parse`, `test_script_syntax` |

## Citations sampled

| Citation | Resolves to |
|----------|-------------|
| `release.yml:14-15` | top-level `permissions: contents: write` |
| `release.yml:24,50` | both `actions/checkout@v4` with `ref: ${{ env.RELEASE_REF }}` |
| `release.yml:42`, `:65-84`, `:86-94`, `:96`, `:99`, `:111`, `:118`, `:123` | job `release`, Validate secrets, Build release APK, Prepare artifacts, `find ... \| head -n 1`, Upload, Create Release, `generate_release_notes: true` |
| `pr-checks.yml:16,36,63,90`, `:54,81` | four checkouts, two `upload-artifact@v4` |
| `2a4bc402:release.yml:96-104`, `:106` | Build release APK, Prepare release artifacts (Task 3 anchors) |
| `2a4bc402` SHAs | five pins with `# vX.Y.Z` comments, six checkouts with `persist-credentials: false`, `contents: write` on job `release` only |
| `git diff 2a4bc402~1 90e69aff -- .github` | empty: the checkout yields exactly the reviewed diff |
| `tools/android-apk-install-preserve:18-21`, `:82-85` | `CERT_DIGEST` regex (IGNORECASE); newest `build-tools/*/apksigner` |
| `app/build.gradle.kts:175-178` | `debug.jks`, `android`, `androiddebugkey`, `android` |
| `tools/release.sh:132` | `gh run list --workflow release.yml` |
| `docs/release.md:46-47` | "The workflow builds `prodRelease`..." paragraph |

Environment the plan relies on: `/opt/android-sdk/build-tools/36.0.0`, `/opt/java/openjdk/bin/keytool`, pyyaml 6.0.1, `gh`, `app/debug.jks` present; release v1.12.7 has asset `puber-v1.12.7.apk`; `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md` absent on master.

## Build, order, scope

- No task touches `app/`; the fast gate stays green after each task. Order 1 -> 2 -> 3 follows the dependencies (Task 3 needs Task 1's `release.yml` and Task 2's script).
- Every P0 row names the mutation it catches, is `red` or `guard`, and names its test in backticks; red runs are scheduled in each task.
- `app/build.gradle.kts` fallback stays untouched (Out of scope). Q1-Q5 answered; none open.

## Notes (non-blocking)

1. Spec Traceability row AC-8 still says `git diff 90e69aff -- app/ docs/superpowers/plans/` is empty; Q5 replaced that with the absence of the upstream triage plan file, as Task 1 implements. Align the spec row when the spec is next touched.
2. AC-8 "KEYPASS optional" lives in the "Build release APK" env, which `test_release_contract_unchanged` does not compare; the step content comes verbatim from `2a4bc402`, so review can confirm it by diff.
