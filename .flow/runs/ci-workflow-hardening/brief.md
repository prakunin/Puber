# Brief: ci-workflow-hardening

Kind: improvement. Size: M. Tracks: none. Device verification: no (workflows, a repo script and docs; no production source changes).
Builds on `research.md` (sections 3-6); option A there is the scope below.

## Goal

Both GitHub workflows run pinned, least-privilege actions, and the release job fails unless the built prod APK is signed by the release certificate `7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6` (state.md Q1).

## Why M

One goal and four files, but it changes the security posture of the release path (token scope, credentials, signer gate), and the release behaviour is proven only by the next real tag run. No production code, no device.

## Code touched

| File | Today | Change |
|------|-------|--------|
| `.github/workflows/release.yml:14-15` | top-level `contents: write` | `contents: read`; job `release` (`:42`) gets `contents: write` |
| `release.yml:24,50,29,55,35,61,112,119` | floating `@v4/@v5/@v2` | SHA pins with version comments (`research.md: 5`, identical to `2a4bc402`); `persist-credentials: false` on both checkouts |
| `release.yml:94-96` | build, then `find ... \| head -n 1` | new step "Verify release signer" between "Build release APK" and "Prepare release artifacts" |
| `.github/workflows/pr-checks.yml` | no `permissions:`; 4 checkouts (`:16,36,63,90`), `upload-artifact@v4` (`:54,81`) | top-level `contents: read`, pins, `persist-credentials: false` |
| `tools/verify-apk-signer.sh` (new) | none | `<apk-dir-or-file> <sha256>`: exactly one APK, newest `build-tools/*/apksigner`, digest regex as `tools/android-apk-install-preserve:18-21`, lower-case, colons stripped, exits non-zero with expected and actual digests |
| `docs/release.md:45-47` | no signer check | one sentence on the signer pin and where it lives |

The pinning half is commit `2a4bc402` on branch `upstream-sync-triage`; master's `.github/` is unchanged since its merge base `358b0ad6`, so it cherry-picks cleanly (checked: `git show 2a4bc402`). That commit also touches `docs/superpowers/plans/2026-10-11-upstream-sync-triage.md`, which does not exist on master: take only the `.github/` hunks.

Out of scope by request: `app/build.gradle.kts:138-180` signing fallback to `debug.jks` (the signer pin is what turns that fallback into a failed release).

## Acceptance criteria

- AC-1. Given both workflow files, When every `uses:` line is listed, Then each reads `<owner>/<repo>[/<path>]@<40 hex> # v<X.Y.Z>` and no floating tag remains (grep).
- AC-2. Given both workflow files, When every `actions/checkout` step is read, Then each has `persist-credentials: false`, and the two release checkouts keep `ref: ${{ env.RELEASE_REF }}`.
- AC-3. Given both workflow files, When `permissions` blocks are read, Then top level is `contents: read` in each, job `release` alone has `contents: write`, and no other job declares permissions.
- AC-4. Given the published v1.12.7 release APK (`gh release download v1.12.7`, public GitHub asset, not the KinoPub API) and the pin, When `tools/verify-apk-signer.sh` runs with apksigner 36.0.0, Then it exits 0.
- AC-5. Given an APK signed with a throwaway key generated in the scratch dir (or the repo's `app/debug.jks`), When the script runs with the pin, Then it exits non-zero and prints the expected and the actual digest.
- AC-6. Given a directory with no APK, with two APKs, or an unsigned APK, When the script runs, Then it exits non-zero with a message naming the cause.
- AC-7. Given `release.yml`, When the steps of job `release` are read, Then "Verify release signer" runs after "Build release APK" and before "Prepare release artifacts", "Upload APK artifact" and "Create GitHub Release", calls the script with the literal pin, and has no `if:`/`continue-on-error` that lets later steps run after it fails.
- AC-8. Given the changed workflows, Then the Android Lint job, `workflow_dispatch` with `release_tag`, "Validate release secrets" (5 required, `KEYPASS` optional), `generate_release_notes: true` and the file name `release.yml` (`tools/release.sh:132`) are unchanged, and `git diff master -- app/` is empty.
- AC-9. Given both workflow files, When parsed with `python3 -c 'import yaml'` (pyyaml present; actionlint is not installed and is not installed for this run), Then both parse and `bash -n tools/verify-apk-signer.sh` passes.

## Risks

- The signer step meets the real keystore only at the next tag run; a wrong path or regex then fails a release after the tag is pushed. AC-4 on the real published APK is the mitigation; the qa report should state the first release is the remaining proof.
- `persist-credentials: false` in job `release`: `action-gh-release` uses its `token` input (default `github.token`), and `release.yml` runs no `git push`; proven only by the next release.
- Pinned actions on Node 20 keep the deprecation annotations seen on run 45638029261 (`research.md: 3`); Node 20 is already removed from runners and runs still pass under forced Node 24.
- `pr-checks.yml` runs only on a pull request; delivery is a local merge, so the pinned PR workflow is first exercised by the next PR (the release version PR).

## Open questions

1. Stay on the current majors (pins of v4/v5/v2) rather than moving to the Node 24 majors now? Recommended: yes, current majors; a separate run bumps majors and is checked on a PR before a release depends on it. Blocks: no.
2. Add upstream's keytool pre-check of the decoded keystore before the build? Recommended: no; the APK check named in the request covers the same failure and keeps keystore handling in Gradle. Blocks: no.
3. Keep the "Assert GitHub-hosted runner" step that `2a4bc402` adds to every job? Recommended: yes; one line, no effect on hosted runners, already reviewed in `upstream-sync-triage`. Blocks: no.
