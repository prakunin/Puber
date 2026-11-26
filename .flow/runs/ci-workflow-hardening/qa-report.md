Round 2 (after fix commits 7a836616..cfa72862)

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; app/ unchanged, so mostly UP-TO-DATE)
DEVICE: not needed: only .github/workflows, tools/ scripts and tests, docs change; git diff 90e69aff -- app/ is empty (0 bytes)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | test_every_uses_is_sha_pinned PASS |
| AC-2 | PASS | test_checkouts_drop_credentials_and_keep_release_ref PASS |
| AC-3 | PASS | test_permissions_read_top_level_write_only_on_release_job PASS |
| AC-4 | PASS | test_published_release_apk_passes, _apk_file_argument_accepted, _pin_is_normalised, _newest_build_tools_chosen, _unparsed_output_fails_closed, _rotated_signer_line_counts PASS (v1.12.7 APK) |
| AC-5 | PASS | test_debug_signed_apk_fails_with_both_digests, test_mixed_signers_fail PASS |
| AC-6 | PASS | test_empty_dir_fails, _two_apks_fail, _unsigned_apk_fails, _bad_pin_rejected, _missing_apksigner_fails, _missing_path_fails PASS |
| AC-7 | PASS | test_signer_step_gates_upload_and_publish PASS; real run is M1 |
| AC-8 | PASS | test_release_contract_unchanged PASS; app/ diff empty |
| AC-9 | PASS | test_workflows_parse, test_script_syntax PASS |
TESTS ADDED: none by me. Run's own tests: tools/tests/test_ci_workflows.py 6/6 PASS; tools/tests/test-verify-apk-signer.sh 15/15 PASS (round 1: 13; new: test_missing_path_fails, test_rotated_signer_line_counts)
EVIDENCE: n/a (no device needed)
DEFECTS: none
UNAVAILABLE: none
MANUAL:
- M1: after merge, on the next tag push open Release job "Build & Publish": "Verify release signer" logs the matching digest and exits 0; "Create GitHub Release" publishes puber-<tag>.apk and .sha256; no 403 / "Resource not accessible by integration", no "unable to resolve action". This first release is the remaining proof for the real keystore build, persist-credentials: false with action-gh-release, and build-tools 37 output format.
- M2: on the next PR (release version PR) open "PR Checks": Detekt, Android Lint, Unit Tests, Build green with pinned actions resolved; no write token permission in "Set up job".

Round-1 note: round 1 also passed, no defects; the fix commits added digest-line counting, running the verifier from the workflow revision, and two more script tests.
