# Harden the CI workflows and pin the release signer - plan

**Spec:** `docs/superpowers/specs/2026-11-26-ci-workflow-hardening.md`
**Goal:** Both GitHub workflows run SHA-pinned, least-privilege actions, and the release job fails unless the built prod APK's signer SHA-256 is `7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run inside the worktree `/home/dev/orca/workspaces/Puber/ci-workflow-hardening`; never `./gradlew` there.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Base `90e69aff`. Nothing under `app/` changes (spec Out of scope: `app/build.gradle.kts` signing fallback). No device.
- Never call the KinoPub API. The only network use is `gh release download v1.12.7 -p '*.apk' -D <scratch>` (public GitHub asset `puber-v1.12.7.apk`), once.
- Scratch files (downloaded APK, throwaway keys) live under `mktemp -d` or the session scratch dir, never in the worktree.
- Test runners (spec Test design, Levels):
  - static: `python3 tools/tests/test_ci_workflows.py` from the worktree root; pyyaml 6.0.1 is installed; `yaml.safe_load` reads `on:` as key `True`.
  - script: `bash tools/tests/test-verify-apk-signer.sh <path-to-puber-v1.12.7.apk>`; uses `/opt/android-sdk/build-tools/36.0.0/apksigner` and `/opt/java/openjdk/bin/keytool` on the dev box.
- Both runners: one `test_*` function per spec Traceability row, each prints `PASS|FAIL <name>`, the runner exits non-zero if any fails.

---

## Tasks

### Task 1: Pin workflow actions, drop persisted credentials, narrow permissions

**Covers:** AC-1, AC-2, AC-3, AC-8, AC-9 (parse)
**Files:**
- Create: `tools/tests/test_ci_workflows.py` - static tests below; `test_signer_step_gates_upload_and_publish` is added in Task 3.
- Modify: `.github/workflows/release.yml`, `.github/workflows/pr-checks.yml` - replaced by their content at commit `2a4bc402`: `git checkout 2a4bc402 -- .github/workflows`. Do not take that commit's `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md` hunk. `git diff 2a4bc402~1 master -- .github` is empty (merge base `358b0ad6`), so the result is exactly the reviewed diff.

**Consumes:** `2a4bc402` as shown by `git show 2a4bc402 -- .github/`: pins checkout v4.4.0 `11d5960a326750d5838078e36cf38b85af677262`, setup-java v4.9.1 `cf277c60eb25467037889841efdb72551f06f6c3`, setup-gradle v5.0.2 `0723195856401067f7a2779048b490ace7a47d7c`, upload-artifact v4.6.2 `ea165f8d65b6e75b540449e92b4886f43607fa02`, action-gh-release v2.6.2 `3bb12739c298aeb8a4eeaf626c5b8d85266b0e65`; `persist-credentials: false` on all six checkouts; top-level `contents: read` in both files; `contents: write` on job `release`; an "Assert GitHub-hosted runner" step after each checkout.
Base anchors: `release.yml:14-15` top-level `permissions`, `:24` and `:50` checkouts with `ref: ${{ env.RELEASE_REF }}`, `:42` job `release`, `:65-84` "Validate release secrets", `:7-12` `workflow_dispatch.inputs.release_tag`, `:123` `generate_release_notes: true`; `pr-checks.yml:32` job `lint`; `tools/release.sh:132` watches `--workflow release.yml`.
**Produces:** `release.yml` at `2a4bc402` content, where "Build release APK" is at `:96-104` and "Prepare release artifacts" at `:106` (Task 3 inserts between them).

Tests (spec Traceability, level static):

| Test | AC | Asserts |
|---|---|---|
| `test_every_uses_is_sha_pinned` | AC-1 | Every line containing `uses:` in both files, value after `uses:` stripped, matches `^[\w.-]+/[\w.-]+(/[\w./-]+)?@[0-9a-f]{40} # v\d+\.\d+\.\d+$`. Read raw lines, since YAML drops comments |
| `test_checkouts_drop_credentials_and_keep_release_ref` | AC-2 | Every step whose `uses` starts with `actions/checkout@` has `with['persist-credentials'] is False`; six such steps in total; both in `release.yml` (`jobs.test`, `jobs.release`) have `with.ref == '${{ env.RELEASE_REF }}'` |
| `test_permissions_read_top_level_write_only_on_release_job` | AC-3 | `doc['permissions'] == {'contents': 'read'}` in each file; `release.yml` `jobs.release.permissions == {'contents': 'write'}`; no other job in either file has `permissions` |
| `test_release_contract_unchanged` | AC-8 | Against `git show 90e69aff:<file>` parsed the same way: `pr-checks.yml` `jobs.lint` step `name` and `run` lists equal after removing the step named "Assert GitHub-hosted runner" (added to every job by Q4); `release.yml` `doc[True]['workflow_dispatch']['inputs']['release_tag']` equal; step "Validate release secrets" `run` equal and `env` key sets equal; step "Create GitHub Release" `with.generate_release_notes is True`; `.github/workflows/release.yml` exists; `git diff --quiet 90e69aff -- app/` exits 0; `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md` does not exist (the triage-plan hunk of `2a4bc402` was not taken; this plan file itself lives in that directory, so the directory-wide diff in the spec row is read as this path) |
| `test_workflows_parse` | AC-9 | Both files load with `yaml.safe_load` to a dict with key `jobs` |

- [x] Write `tools/tests/test_ci_workflows.py` with the five tests; a `main` runs every `test_*` in the module, catches `AssertionError`, prints `PASS|FAIL <name>: <message>`, exits 1 on any failure. Paths are relative to the worktree root.
- [x] Red run on the unchanged workflows: `python3 tools/tests/test_ci_workflows.py`; expect FAIL on `test_every_uses_is_sha_pinned`, `test_checkouts_drop_credentials_and_keep_release_ref`, `test_permissions_read_top_level_write_only_on_release_job`, PASS on the two guards. Record in the Implementation record.
- [x] `git checkout 2a4bc402 -- .github/workflows`; `git status --short` lists only the two workflow files and the new test file.
- [x] Run the static tests (all PASS) and the fast gate; commit.

**Done when:** `python3 tools/tests/test_ci_workflows.py` exits 0, `git diff 2a4bc402 -- .github/` is empty, and `./tools/agentw :app:compileDevDebugKotlin` passes.

---

### Task 2: Add the release signer check script

**Covers:** AC-4, AC-5, AC-6, AC-9 (`bash -n`)
**Files:**
- Create: `tools/verify-apk-signer.sh` (mode 755).
- Create: `tools/tests/test-verify-apk-signer.sh`.

**Consumes:** digest pattern `tools/android-apk-install-preserve:18-21` (`(?:Signer #\d+:?|V\d+ Signer:) certificate SHA-256 digest:\s*([0-9a-f:]+)`, case-insensitive); newest-apksigner lookup `tools/android-apk-install-preserve:82-85`; debug keystore values `app/build.gradle.kts:175-178` (`debug.jks`, store and key password `android`, alias `androiddebugkey`).
**Produces:** `tools/verify-apk-signer.sh <apk-dir-or-file> <sha256>`:

| Behaviour | Exit | stderr names |
|---|---|---|
| arg count not 2; pin not `^[0-9a-f]{64}$` after lower-casing and stripping `:` | 2 | usage / invalid pin |
| path neither a file nor a directory | 2 | the path |
| directory: `find <dir> -maxdepth 1 -type f -name '*.apk'` yields zero | 2 | `no APK` in the dir |
| same, more than one | 2 | `more than one APK` and the list |
| no `${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/*/apksigner` executable; newest chosen by `sort -V` | 2 | `apksigner` not found |
| `apksigner verify --print-certs <apk>` exits non-zero (stdout+stderr captured) | 1 | `verify failed` plus apksigner output |
| zero digests parsed from that output | 1 | `no signer digest` plus apksigner output |
| more than one distinct digest (lower-cased, colons stripped, `sort -u`) | 1 | `expected: <pin>`, `actual: <each digest>` |
| one digest, not equal to the pin | 1 | `expected: <pin>`, `actual: <digest>` |
| one digest equal to the pin | 0 | stdout: `signer OK: <digest> <apk>` |

Bash with `set -euo pipefail`; extract with `grep -oiE '(Signer #[0-9]+:?|V[0-9]+ Signer:) certificate SHA-256 digest:[[:space:]]*[0-9a-fA-F:]+'` then strip through `digest:`; guard the grep so no match does not abort under `pipefail` before the "no signer digest" message.

Tests (spec Traceability, level script). The runner takes the published APK path as `$1`, resolves the script as `<repo-root>/tools/verify-apk-signer.sh`, runs each test in a fresh `mktemp -d`, and for real-apksigner tests sets `ANDROID_HOME=/opt/android-sdk` unless already set. Fixtures as spec Test design, Fixtures table:

| Test | AC | Setup and assert |
|---|---|---|
| `test_published_release_apk_passes` | AC-4 | Dir holding a copy of the published APK, pin: exit 0 |
| `test_debug_signed_apk_fails_with_both_digests` | AC-5 | `apksigner sign --ks app/debug.jks --ks-pass pass:android --ks-key-alias androiddebugkey --out <tmp>/debug.apk <published>`; expected digest from `keytool -list -v -keystore app/debug.jks -storepass android -alias androiddebugkey` SHA256 line, lower-cased, colons stripped; exit 1, output holds the pin and that digest |
| `test_empty_dir_fails` | AC-6 | Empty dir: exit 2, output matches `no APK` |
| `test_two_apks_fail` | AC-6 | Published APK copied twice under two names: exit 2, output matches `more than one APK` |
| `test_unsigned_apk_fails` | AC-6 | `python3 -c` with `zipfile` copies every entry except `META-INF/*` into `<tmp>/unsigned.apk`: exit 1, output matches `verify failed` |
| `test_script_syntax` | AC-9 | `bash -n tools/verify-apk-signer.sh` exits 0 and `test -x` holds |
| `test_apk_file_argument_accepted` | AC-4 | Path to the APK file itself: exit 0 |
| `test_pin_is_normalised` | AC-4 | Pin upper-cased with `:` every two hex chars: exit 0 |
| `test_unparsed_output_fails_closed` | AC-4 | Fake SDK `<tmp>/sdk/build-tools/36.0.0/apksigner` exits 0 printing `Verifies` only; `ANDROID_HOME=<tmp>/sdk`, `ANDROID_SDK_ROOT` unset: exit 1 |
| `test_bad_pin_rejected` | AC-6 | 63-hex pin: exit 2; one argument only: exit 2 |
| `test_missing_apksigner_fails` | AC-6 | `ANDROID_HOME` an empty dir, `ANDROID_SDK_ROOT` unset: exit 2, output matches `apksigner` |
| `test_newest_build_tools_chosen` | AC-4 | Fake `9.0.0/apksigner` prints `Signer #1 certificate SHA-256 digest: <64 zeros>`, fake `10.0.0/apksigner` prints the same line with the pin: exit 0 |
| `test_mixed_signers_fail` | AC-5 | Fake apksigner prints `Signer #1 ... digest: <pin>` and `Signer #2 ... digest: <64 ones>`: exit 1 |

- [x] Download the fixture once: `gh release download v1.12.7 -p '*.apk' -D <scratch>`.
- [x] Write `tools/tests/test-verify-apk-signer.sh` with the tests above.
- [x] Red run before the script exists: `bash tools/tests/test-verify-apk-signer.sh <scratch>/puber-v1.12.7.apk`; every test FAILs (script absent). Record in the Implementation record.
- [x] Write `tools/verify-apk-signer.sh`, `chmod 755`, so the mode is committed.
- [x] Run the script tests (all PASS), the static tests (still PASS) and the fast gate; commit.

**Done when:** `bash tools/tests/test-verify-apk-signer.sh <scratch>/puber-v1.12.7.apk` exits 0 and `git ls-files -s tools/verify-apk-signer.sh` shows mode `100755`.

---

### Task 3: Gate the release job on the signer and document the pin

**Covers:** AC-7 (AC-1..AC-3, AC-8, AC-9 re-checked)
**Files:**
- Modify: `.github/workflows/release.yml` - new step between "Build release APK" (`:96-104` after Task 1) and "Prepare release artifacts" (`:106`):
  ```yaml
      - name: Verify release signer
        env:
          RELEASE_CERT_SHA256: 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6
        run: bash tools/verify-apk-signer.sh app/build/outputs/apk/prod/release "$RELEASE_CERT_SHA256"
  ```
  No `if:`, no `continue-on-error`. "Prepare release artifacts", "Upload APK artifact", "Create GitHub Release" stay as they are.
- Modify: `docs/release.md` - one sentence after `:46-47` (the paragraph "The workflow builds `prodRelease`..."): the release job fails unless the APK signer SHA-256 equals the pin in `.github/workflows/release.yml` (step "Verify release signer"), checked by `tools/verify-apk-signer.sh`. The digest value is not repeated in the doc.
- Modify: `tools/tests/test_ci_workflows.py` - add the test below.

**Consumes:** Task 2 script contract; Task 1 `release.yml`.
**Produces:** none.

| Test | AC | Asserts |
|---|---|---|
| `test_signer_step_gates_upload_and_publish` | AC-7 | In `jobs.release.steps` the index of "Verify release signer" is greater than "Build release APK" and less than each of "Prepare release artifacts", "Upload APK artifact", "Create GitHub Release"; its `run` contains `tools/verify-apk-signer.sh`; `yaml.safe_dump` of the step contains `7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6`; the step has neither `if` nor `continue-on-error` |

- [x] Add the test; red run `python3 tools/tests/test_ci_workflows.py`: only `test_signer_step_gates_upload_and_publish` FAILs. Record it.
- [x] Insert the step and the doc sentence.
- [x] Run the static tests (all PASS), `bash tools/tests/test-verify-apk-signer.sh <scratch>/puber-v1.12.7.apk` (all PASS), `git diff --stat 90e69aff -- app/` (empty), and the full gate `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; commit.

**Done when:** both test runners exit 0, `grep -n 'Verify release signer' .github/workflows/release.yml` shows one line between the "Build release APK" and "Prepare release artifacts" lines, and the full gate passes.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- Task 1: Pin workflow actions to SHAs, drop persisted checkout credentials and narrow token permissions
- Task 2: Add a script that fails unless the release APK is signed by the pinned certificate
- Task 3: Gate the release job on the signer check and document the pin (sha in git log)

**Red run** (P0 `red` tests on the unchanged code):
- red: test_every_uses_is_sha_pinned - release.yml: unpinned 'actions/checkout@v4'
- red: test_checkouts_drop_credentials_and_keep_release_ref - release.yml test: credentials persisted
- red: test_permissions_read_top_level_write_only_on_release_job - release.yml: top-level permissions
- green on base (guards, as designed): test_release_contract_unchanged, test_workflows_parse
- red: all 13 script tests (published_release_apk_passes, debug_signed_apk_fails_with_both_digests, empty_dir_fails, two_apks_fail, unsigned_apk_fails, script_syntax, and the P1/P2 rows) - tools/verify-apk-signer.sh: No such file or directory (exit 127)
- red: test_signer_step_gates_upload_and_publish - signer step missing

**Deviations from the plan:** none. Readiness note folded in: `test_release_contract_unchanged` also asserts KEYPASS is absent from "Validate release secrets" and still present in "Build release APK".

**Files touched outside the plan:** none.

**For the reviewers:** `tools/verify-apk-signer.sh` parsing and exit codes; the fake-apksigner tests (apksigner 36 output format only checked against the real v1.12.7 APK). Check: static 6 PASS, script 13 PASS, `check.full` BUILD SUCCESSFUL (1m 47s). M1 and M2 remain post-merge.

**Fix round 1** (triage-1 #1-#7):
- #1 second checkout of `github.sha` (sparse, no credentials, path `signer-verifier`) feeds the verifier step; documented in `docs/release.md`.
- #2-#4 `test_ci_workflows.py`: run-scoped checks removed (base diff, upstream plan file, checkout count, `git show`); AC-7 test ties the APK dir and pin to the packaging step and checks later steps and the job for `if`/`continue-on-error`. One-off check at fix time: `git diff 90e69aff -- app/` empty.
- #5 test script resolves keytool via PATH, `JAVA_HOME` or `KEYTOOL`; exit 2 with a message when a tool is missing.
- #6 digest regex matches any `certificate SHA-256 digest:` line; new `test_rotated_signer_line_counts`.
- #7 new `test_missing_path_fails`.
- Checks: static 6 PASS, script 15 PASS, fast gate see the return block.

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec and plan: `/home/dev/orca/workspaces/Puber/ci-workflow-hardening/docs/superpowers/specs/2026-11-26-ci-workflow-hardening.md`, `.../plans/2026-11-26-ci-workflow-hardening.md`. Base `90e69aff`.

### AC coverage

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

### Citations sampled

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

### Build, order, scope

- No task touches `app/`; the fast gate stays green after each task. Order 1 -> 2 -> 3 follows the dependencies (Task 3 needs Task 1's `release.yml` and Task 2's script).
- Every P0 row names the mutation it catches, is `red` or `guard`, and names its test in backticks; red runs are scheduled in each task.
- `app/build.gradle.kts` fallback stays untouched (Out of scope). Q1-Q5 answered; none open.

### Notes (non-blocking)

1. Spec Traceability row AC-8 still says `git diff 90e69aff -- app/ docs/superpowers/plans/` is empty; Q5 replaced that with the absence of the upstream triage plan file, as Task 1 implements. Align the spec row when the spec is next touched.
2. AC-8 "KEYPASS optional" lives in the "Build release APK" env, which `test_release_contract_unchanged` does not compare; the step content comes verbatim from `2a4bc402`, so review can confirm it by diff.

