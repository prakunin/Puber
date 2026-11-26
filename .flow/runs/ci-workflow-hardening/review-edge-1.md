# Review edge, round 1 (7a836616)

LENS: edge  ROUND: 1  BASE: 90e69aff

### F1 [minor] Two checks in `test_release_contract_unchanged` only hold until the next merge, so the committed test will go red after ordinary later work
- Where: tools/tests/test_ci_workflows.py:96-97 (and `BASE = "90e69aff"` at :10)
- Problem: `git diff --quiet 90e69aff -- app/` passes only while nothing under `app/` has changed since this run's base; `not Path("docs/superpowers/plans/2026-10-11-upstream-sync-triage.md").exists()` assumes that plan never lands on master (branch upstream-sync-triage touches it in fc3c33f1).
- Failure scenario: the next merged run that touches `app/`, or the merge of upstream-sync-triage, makes the test fail on master even though neither workflow changed. Also fails in a shallow clone.
- Fix: keep the run-scoped checks in the plan's verification record, not in the committed test; leave the workflow-contract assertions.

### Unproven
- release.yml:106-109 workflow_dispatch for a pre-merge tag: no `tools/verify-apk-signer.sh` there, step fails, nothing published. Real only if a pre-merge tag must be re-published.
- tools/verify-apk-signer.sh:55 key rotation (v3.1 / lineage) lines like "Signer (minSdkVersion=...)" do not match the regex. Real only if the key is rotated (Q1: never changed).
- tools/tests/test-verify-apk-signer.sh:17 `KEYTOOL` defaults to `/opt/java/openjdk/bin/keytool`; matters only off the puber-mac box.

Checked and found handled: action SHAs match tags; exactly one APK; zero digests fail closed; mixed digests; missing build-tools -> exit 2; debug-signed fallback rejected; abiFilters does not split the APK; `contents: write` only on `release`; `test` read-only; PR jobs need no write scope.
