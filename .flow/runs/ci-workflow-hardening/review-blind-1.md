# Review blind, round 1 (7a836616)

LENS: blind  ROUND: 1  BASE: 90e69aff
### F1 [minor] A manual recovery run for a tag older than this change fails at the signer step
- Where: .github/workflows/release.yml:106-109
- Problem: The release job checks out `RELEASE_REF`, which for `workflow_dispatch` is `inputs.release_tag`, and then runs `bash tools/verify-apk-signer.sh` from that checked-out tree. The workflow file comes from the branch the run is started on (master), but the script comes from the tag. `docs/release.md` describes a recovery run with `release_tag=vX.Y.Z` for a tag that already exists. Any tag made before this change does not contain `tools/verify-apk-signer.sh`.
- Failure scenario: someone starts a recovery run from master with `release_tag=v1.12.7`. The build succeeds, then `bash tools/verify-apk-signer.sh ...` stops with "No such file or directory", and nothing is uploaded or published. The recovery path no longer works for every tag cut before this merge.
- Fix: say in `docs/release.md` that a recovery run only works for tags that contain `tools/verify-apk-signer.sh`. Or inline the check in the workflow step, or fetch the script from `github.sha` rather than from the tag tree.

### F2 [minor] `test_ci_workflows.py` checks things that are only true for this run, so it will fail after any later change
- Where: tools/tests/test_ci_workflows.py:10, 98-99, 62
- Problem: The file is committed as a permanent tool test, but it hardcodes `BASE = "90e69aff"` and asserts: `git diff --quiet 90e69aff -- app/`; a file under `docs/superpowers/plans/` that has nothing to do with CI does not exist; there are exactly 6 checkouts. It also reads the old workflows through `git show 90e69aff:...`, which fails in a shallow clone.
- Failure scenario: the next commit that touches `app/` makes `test_release_contract_unchanged` fail for good. Adding one more CI job breaks the checkout count.
- Fix: drop the checks against the base commit and the unrelated file. Keep the checks that hold for any version of the workflows.

### F3 [minor] The signer test script will not run outside the dev box
- Where: tools/tests/test-verify-apk-signer.sh:15-17
- Problem: falls back to `KEYTOOL=/opt/java/openjdk/bin/keytool` and `ANDROID_HOME=/opt/android-sdk`; an empty `APKSIGNER` gives an unclear failure.
- Fix: find `keytool` with `command -v keytool` (or through `JAVA_HOME`), and exit 2 with a clear message when `APKSIGNER` or `KEYTOOL` cannot be found.

### Unproven
- "Assert GitHub-hosted runner": `${RUNNER_ENVIRONMENT:-github-hosted}` passes when the variable is unset; a hole only on an old self-hosted runner.
- tools/verify-apk-signer.sh:55: the digest regex matches only `Signer #N` and `Vn Signer:` lines; a v3.1 rotated signer prints `Signer (minSdkVersion=...)` lines. Matters only if the release key is ever rotated.

I checked all five pinned action SHAs with `git ls-remote`, and each one matches the version tag in its comment.
