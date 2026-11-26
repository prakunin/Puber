# Review edge, round 2 (7a836616..cfa72862)

LENS: edge  ROUND: 2  BASE: 90e69aff

No blocker or major findings. Fixes 7a836616..cfa72862 do not break the release path. test_ci_workflows.py 6/6 at cfa72862.

Handled: tag push (github.sha = tag commit); workflow_dispatch recovery of an old tag (verifier from the branch head, build from RELEASE_REF); verifier checkout shallow, sparse non-cone, no credentials, own folder signer-verifier/; wider regex does not match `public key SHA-256 digest` lines, extra signer lines fail closed (test_rotated_signer_line_counts); missing keytool/apksigner exit 2 with a message.

### F1 [minor] No test covers the round-1 fix #1 (verifier taken from the workflow revision)
- Where: tools/tests/test_ci_workflows.py:49-56 and :92
- Problem: the `ref == ${{ github.sha }}` check runs only when a `path: signer-verifier` checkout exists; nothing requires it. The signer step check is a substring test that `bash tools/verify-apk-signer.sh ...` also passes. Checkout count went from `== 6` to `> 0`.
- Failure scenario: someone deletes the verifier checkout and restores `bash tools/verify-apk-signer.sh ...`; all static tests stay green; old-tag recovery breaks again.
- Fix: require a checkout with `with.path == VERIFIER_PATH` and `with.ref == "${{ github.sha }}"`, placed after "Build release APK" and before the gate step; require `f"{VERIFIER_PATH}/tools/verify-apk-signer.sh"` in the gate step's run.

### Unproven
- tools/verify-apk-signer.sh:54 would also catch a Source Stamp signer line; only matters if the release APK ever gets a source stamp (AGP assembleProdRelease does not add one).
