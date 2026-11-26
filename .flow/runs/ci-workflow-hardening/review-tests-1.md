# Review tests, round 1 (7a836616)

LENS: tests  ROUND: 1  BASE: 90e69aff

Suite state at head 7a836616: test_ci_workflows.py 6 PASS; test-verify-apk-signer.sh 13 PASS (v1.12.7 APK via gh release download); check.full BUILD SUCCESSFUL. Every P0 row has a test under its own name; red runs recorded for every red row; the two base-green rows are guards.

### F1 [minor] AC-7 test does not check which APK directory or pin the signer step actually passes to the script
- Where: tools/tests/test_ci_workflows.py:103-116 (`test_signer_step_gates_upload_and_publish`)
- Problem: only checks that `run` contains `tools/verify-apk-signer.sh` and the pin appears in the step dump; not the directory "Prepare release artifacts" packages (`app/build/outputs/apk/prod/release`), nor that `run` reads the env var holding the pin.
- Failure scenario: a typo in the dir or in `"$RELEASE_CERT_SHA256"` passes the test and fails only at the next tag push.
- Fix: assert `run` contains the dir plus `"$RELEASE_CERT_SHA256"` and `step["env"]["RELEASE_CERT_SHA256"] == PIN`; better, read the `find <dir>` path from "Prepare release artifacts" and assert both use the same dir.

### F2 [minor] AC-7 "no if: that lets later steps run after it fails" is only checked on the gate step
- Where: tools/tests/test_ci_workflows.py:115
- Failure scenario: `if: always()` on "Create GitHub Release" or `continue-on-error: true` on job `release` publishes a wrong-signed APK and the test passes.
- Fix: assert the later steps have no `if` with `always()`/`failure()`/`cancelled()` and no `continue-on-error`; and none on `jobs.release`.

### F3 [minor] `test_release_contract_unchanged` compares against a hard-coded base, so it will fail after the next ordinary change
- Where: tools/tests/test_ci_workflows.py:10, 99-100
- Problem: tied to this run; nothing runs tools/tests automatically.
- Fix: keep only durable AC-8 parts (release_tag input, 5 secrets without KEYPASS, generate_release_notes, file name); move base-diff checks to the run's verification notes.

### Unproven
- test-verify-apk-signer.sh:17 KEYTOOL default path; matters only off the puber-mac box.
- verify-apk-signer.sh:34-36 exit-2 "not a file or directory" branch is untested.
