# Review codex, round 1 (7a836616)

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] .github/workflows/release.yml:53,106-109 - A manual `workflow_dispatch` with `release_tag` set to a tag that predates this change (for example v1.12.7) runs this workflow but checks out that tag. `tools/verify-apk-signer.sh` is absent there, so the "Verify release signer" step fails and nothing is published, even if the APK is signed correctly. It fails closed, and the spec already lists it as design risk 1. It is worth stating in `docs/release.md` that re-publishing an old tag through dispatch no longer works. Optionally, take the script from the workflow revision with a second sparse checkout.

REJECTED:
- A modified verifier on the release ref can return success without checking the APK (adversarial, "high") - a tag push already runs the workflow file from the tag itself; whoever controls the tag controls the workflow, the pin and the build. The signer pin guards against a wrong key, not a malicious tag author. Rejected as unproven.
- The verifier should run against the exact staged APK passed to upload - the script requires exactly one `*.apk`; "Prepare release artifacts" picks from the same directory. No scenario gives a different file.
- [P3] `test_ci_workflows.py` should assert downstream steps keep the default success condition - hypothetical future regression, no defect today.

VERDICT: clean
