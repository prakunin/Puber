# Triage round 1: ci-workflow-hardening

Reviewed head: 7a836616 (base 90e69aff). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (ran, clean). state-path and ux-designer not triggered (no ViewModel, focus or Compose change).
Each finding was checked against the code in the worktree by the conductor.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | workflow_dispatch recovery for a tag cut before this merge fails: the tag tree has no tools/verify-apk-signer.sh (release.yml:53,106-109) | blind F1, codex, edge unproven | minor | fix | Fails closed, but breaks the documented recovery path (docs/release.md) for every existing tag. Run the verifier from the workflow's own revision: a second checkout of `github.sha` limited to the script (sparse, persist-credentials: false, same pinned action) into a separate directory, and call it from there; for a tag push github.sha is the tag commit, so nothing changes there. Document it in docs/release.md |
| 2 | test_ci_workflows.py holds run-scoped checks (BASE 90e69aff app/ diff, absent upstream plan file, exact checkout count, git show of base) that go red after ordinary later work | blind F2, edge F1, tests F3 | minor | fix | Keep only durable workflow-contract assertions; move the base-diff checks into the plan's verification record; count checkouts as "every checkout" |
| 3 | AC-7 test does not tie the step's APK dir and pin env to the packaging step | tests F1 | minor | fix | Assert the dir matches the "Prepare release artifacts" find path, `"$RELEASE_CERT_SHA256"` in run, env value == pin |
| 4 | AC-7 test does not check later steps / the job for always()/continue-on-error | tests F2 | minor | fix | Cheap assertions on the three later steps and jobs.release |
| 5 | test-verify-apk-signer.sh hardcodes keytool and SDK paths; empty APKSIGNER gives an unclear failure | blind F3, edge/tests unproven | minor | fix | command -v keytool / JAVA_HOME, exit 2 with a clear message when a tool is missing |
| 6 | Digest regex misses v3.1 rotated-signer lines ("Signer (minSdkVersion=...)") | blind unproven, edge unproven | minor | fix | Cheap: collect every "certificate SHA-256 digest:" line whatever its prefix, so any extra signer fails the pin; add a fake-SDK test |
| 7 | Untested exit-2 "not a file or directory" branch (verify-apk-signer.sh:34-36) | tests unproven | minor | fix | One-line test, cheap |
| 8 | Assert GitHub-hosted runner passes when RUNNER_ENVIRONMENT is unset | blind unproven | - | reject | Every job runs on ubuntu-latest, which always sets RUNNER_ENVIRONMENT; the step is a guard copied from upstream (Q4) |
| 9 | A modified verifier on the release ref can return success (adversarial) | codex rejected | - | reject | Whoever controls the tag controls the workflow and the build; the pin guards against a wrong key, not a hostile tag author. After fix #1 the verifier also comes from github.sha |
| 10 | Verifier should check the exact staged APK | codex rejected | - | reject | Script requires exactly one APK in the directory "Prepare release artifacts" reads |

Deferred: none. Accepted: none.
