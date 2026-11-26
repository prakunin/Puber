# Research: ci-workflow-hardening

## 1. Request

harden the GitHub Actions workflows (.github/workflows/release.yml, pr-checks.yml and any others): pin every action by full commit SHA (with a version comment), set persist-credentials: false on checkout, set top-level permissions: contents: read and grant write only to the job that needs it (the release publish job), and add a release-signer pin: after building the prod release APK, run apksigner verify --print-certs and fail the job unless the signer SHA-256 equals 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6 (owner confirmed the release key never changed; read from published v1.12.3 and v1.12.7 APKs). Keep the fork's Lint job, workflow_dispatch and secrets check. Do not take upstream's signing-mode logic in app/build.gradle.kts.

## 2. Understanding

Both workflows run third-party actions by floating major tags, keep the job token in the checked-out `.git/config`, and the release workflow grants `contents: write` to every job. The change pins each action to a commit, drops persisted credentials, narrows the token to read except for the publishing job, and makes the release job fail when the built prod APK is not signed by the owner's release certificate (today a missing or wrong keystore silently yields a debug-signed or foreign-signed APK). Workflow and docs only; no app code, no device. Almost all of it already exists as commit `2a4bc402` on branch `upstream-sync-triage`; only the signer pin was never written.

## 3. Current state in code

- `.github/workflows/` holds only `release.yml` and `pr-checks.yml`; no `dependabot.yml` in `.github/`.
- `.github/workflows/release.yml:14-15` top-level `permissions: contents: write`; jobs `test` (`:18`) and `release` (`:42`) have no own block.
- `release.yml:24`, `:50` `actions/checkout@v4` with `ref:` only, no `persist-credentials`.
- `release.yml:29,55` `setup-java@v4`; `:35,61` `gradle/actions/setup-gradle@v5`; `:112` `upload-artifact@v4`; `:119` `softprops/action-gh-release@v2` (`generate_release_notes: true`, `:123`).
- `release.yml:7-12` `workflow_dispatch` with `release_tag`; `:65-84` "Validate release secrets" (5 required, `KEYPASS` optional at `:91`).
- `release.yml:86-94` "Build release APK" (`assembleProdRelease`); `:96-109` "Prepare release artifacts" picks the first APK with `find ... | head -n 1` and writes the `.sha256`. The signer step goes between these two.
- `.github/workflows/pr-checks.yml` no `permissions:` block; four jobs (Detekt `:12`, Android Lint `:32`, Unit Tests `:59`, Build `:86`), four `checkout@v4` (`:16,36,63,90`), `upload-artifact@v4` at `:54,81`.
- `app/build.gradle.kts:141-180` release signing config: `keystore.properties`, then `RELEASE_KEYSTORE_BASE64`, then `STOREPASS` + existing `release.jks`, else falls back to `debug.jks` (`:172-178`). The signer pin is what turns that fallback, or a wrong keystore secret, into a failed release. Out of scope to change.
- `tools/android-apk-install-preserve:18-19` already parses apksigner output with `(?:Signer #\d+:?|V\d+ Signer:) certificate SHA-256 digest:\s*([0-9a-f:]+)`, and `:82-84` picks the newest `build-tools/*/apksigner`: the reference for format-tolerant parsing (apksigner 36 prints `Signer #1 certificate SHA-256 digest:` without a colon after `#1`, see run `release-startup-compilation`).
- `tools/release.sh:132` watches the run by `--workflow release.yml`; renaming the file or the workflow would break it.
- `docs/release.md` "GitHub Release" section describes the workflow and secrets; it does not mention a signer check.
- Last release run 45638029261 (v1.12.7, 2026-11-25, success) carries annotations: Node.js 20 actions forced onto Node 24 (`checkout@v4`, `setup-java@v4`, `upload-artifact@v4`, `action-gh-release@v2`) and "setup-java v4 is deprecated and will no longer receive updates".

## 4. Related work

| Item | Relation |
|------|----------|
| run `upstream-sync-triage` (spec row T14, port P1, D1-D4, AC-5..AC-7) | Source of this run. Its plan Task 1 is done as commit `2a4bc402` "Pin workflow actions by commit and narrow their permissions" on that branch (never merged). Task 2 "Pin the release signer" was blocked on the digest and is unwritten; its plan specifies `tools/verify-apk-signer.sh <apk> <sha>` plus a "Verify release signer" step, and test T-4 with a throwaway key. |
| `2a4bc402` vs master | master's `.github/` is unchanged since the merge base `358b0ad6` (no diff), so the commit cherry-picks without conflict. It pins checkout v4.4.0, setup-java v4.9.1, setup-gradle v5.0.2, upload-artifact v4.6.2, action-gh-release v2.6.2; adds `persist-credentials: false` on all 6 checkouts, top-level `contents: read` in both files, `contents: write` on job `release` only, and an "Assert GitHub-hosted runner" step per job. Keeps Lint, `workflow_dispatch`, the secrets check, `KEYPASS`, `generate_release_notes`. |
| upstream `7bd28c11` (rovkinmax/Puber) | Reference for the signer step shape (`release.yml:66-76` at that commit): keytool pre-check of the decoded keystore against the pin, `apksigner verify --print-certs`, exactly-one-APK check, sed extraction. Its pin `3e0ddb2c...` is upstream's key, not ours. Its Gradle signing-mode logic and `gh release create` empty body are excluded by the request. |
| run `release-startup-compilation` | Found the apksigner 36 output-format quirk and fixed the regex in `tools/android-apk-install-preserve`. |
| run `hermetic-baseline-profiles` (P5) | Will later edit `release.yml` / `pr-checks.yml` run lines (triage plan Task 13); ordering only. |

Duplicate: none. `2a4bc402` covers the pinning half but lives on an unmerged branch that the triage run now uses only as a cherry-pick source.

## 5. External findings

| Finding | Source |
|---------|--------|
| Current pins of the majors in use (GitHub API, 2026-11-26): checkout `v4`=v4.4.0 `11d5960a326750d5838078e36cf38b85af677262`; setup-java `v4`=v4.9.1 `cf277c60eb25467037889841efdb72551f06f6c3`; gradle/actions `v5` (annotated tag, dereferenced)=v5.0.2 `0723195856401067f7a2779048b490ace7a47d7c`; upload-artifact `v4`=v4.6.2 `ea165f8d65b6e75b540449e92b4886f43607fa02`; action-gh-release `v2`=v2.6.2 `3bb12739c298aeb8a4eeaf626c5b8d85266b0e65`. Identical to `2a4bc402`. | `gh api repos/<owner>/<repo>/git/ref/tags/<major>`, `.../tags` |
| Runtimes at those pins: checkout, setup-java, upload-artifact, action-gh-release declare `node20`; setup-gradle v5.0.2 declares `node24`. | `action.yml` at each SHA via `gh api .../contents` |
| Node 24 default on runners from 2026-06-16; Node 20 removed 2026-09-23; `ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION` opt-out only until removal. Our runs still succeed with a warning (section 3). | https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/ |
| Newest releases (node24): checkout v7.0.1 `3d3c42e5aac5ba805825da76410c181273ba90b1`; setup-java v6.3.0 `d1374f8ac74474200d16ef8ecee22c7ae7a51880`; upload-artifact v7.0.2 `cf430e030ddbb5b0abf93d22962f4752f3646cd9`; action-gh-release v3.0.4 `8a0cd651e5b2365bd640854a3fa5dfc70e4875f3`; gradle/actions v6.4.1 `8da46b539cd8fe6ab27ca75128919a18d757604c`. | `gh api .../releases/latest`, `.../tags` |
| Major-bump notes: checkout v5 = node24, v6 persists creds to a separate file, v7 blocks fork-PR checkout under `pull_request_target`/`workflow_run` (not used here); setup-java v5 = node24, v6 Zulu API/ESM changes (we use temurin); upload-artifact v6 = node24, v7 ESM + opt-in `archive: false`; action-gh-release v3.0.0 = runtime move to node24 only. | release pages of each repo (`gh api .../releases/tags/<tag>`) |
| gradle/actions v6 moves caching into a closed-source `gradle-actions-caching` component under Gradle Terms of Use; upgrading accepts them. v5.0.2 already runs on node24, so there is no runtime reason to go to v6. | https://github.com/gradle/actions/releases/tag/v6.0.0 |
| `ubuntu-latest` (24.04) image ships Android SDK build-tools 34.0.0 to 37.0.0 at `ANDROID_HOME=/usr/local/lib/android/sdk`, so `apksigner` is present without extra setup. | https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md (fetched 2026-11-26) |

## 6. Options

| Option | What | Trade-offs | Size |
|--------|------|-----------|------|
| A (recommended) | Cherry-pick `2a4bc402`; add the signer pin as `tools/verify-apk-signer.sh <apk> <sha256>` (newest `build-tools/*/apksigner`, regex tolerant like `android-apk-install-preserve:19`, exactly one APK, lower-case, colons stripped) called from a "Verify release signer" step after `release.yml:94` with the literal pin in the step `env`; one line in `docs/release.md`. | Reuses reviewed work; the script is testable locally (throwaway key for the mismatch, the public v1.12.7 release APK for a real match, both with no secrets). Node 20 deprecation warnings and the setup-java v4 notice remain. | S |
| B | A, plus pin the node24 majors (checkout v7.0.1, setup-java v6.3.0, upload-artifact v7.0.2, action-gh-release v3.0.4; setup-gradle stays v5.0.2). | Clears the warnings, but four major bumps land in the release path at once and are proven only by the next real release; mixes upgrade with hardening. | S |
| C | A, plus `.github/dependabot.yml` (`package-ecosystem: github-actions`) so pins get update PRs. | Keeps pins from going stale; adds PR noise and a new config file. Dependabot's handling of SHA pins with version comments is `unverified` here. | S |

## 7. Risks

- **Signer step untested on the real key in CI until the next release.** A wrong regex or a multi-APK output fails the release after tagging. Mitigate by running the script on the published v1.12.7 APK (`gh release download v1.12.7`, public) as the true positive.
- **`persist-credentials: false` in the release job.** `action-gh-release` authenticates with its `token` input (default `github.token`), not the git credential (triage D3); nothing in `release.yml` runs `git push`. Proven only by the next release.
- **Job-level `permissions` replace, not merge.** Job `release` must keep `contents: write`; `upload-artifact` needs no extra scope (`unverified` beyond the triage branch being unrun).
- **Pins go stale.** Without option C, security fixes in pinned actions need a manual bump.
- **Workflow name/file.** `tools/release.sh:132` filters by `release.yml`; keep the file name.

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q2 | Stay on current majors (A) or move to node24 majors now (B)? | Stay on current majors; open a separate run for the major bump, which can be checked on a PR before a release depends on it. |
| Q3 | Take upstream's keytool pre-check of the decoded keystore (fail before the build) in addition to the APK check? | No: the APK check is the one the request names and covers the same failure; the pre-check needs the keystore on disk in the workflow, which the fork leaves to Gradle. |
| Q4 | Keep the "Assert GitHub-hosted runner" step that `2a4bc402` adds? | Yes: one line, no behaviour change on hosted runners, already reviewed in the triage run. |
| Q5 | Add `dependabot.yml` (option C)? | No, not in this run. |

## 9. Sources

- Files: `.github/workflows/release.yml`, `.github/workflows/pr-checks.yml`, `app/build.gradle.kts:136-186`, `tools/android-apk-install-preserve`, `tools/release.sh`, `docs/release.md`, `AGENTS.md`, `.flow/config.yaml`.
- Commits: `2a4bc402`, `358b0ad6` (merge base), `9930b952`, upstream `7bd28c11` and `upstream/master:.github/workflows/pr-checks.yml`.
- Branch docs: `upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` (T14, P1, AC-5..7, D1-D4), `upstream-sync-triage:docs/superpowers/plans/2026-10-11-upstream-sync-triage.md` (Tasks 1-2), `upstream-sync-triage:docs/superpowers/reviews/2026-10-11-upstream-sync-triage.md`.
- Runs: `ci-workflow-hardening`, `upstream-sync-triage`, `release-startup-compilation`.
- GitHub run 45638029261 annotations (`gh api .../check-runs/<id>/annotations`).
- URLs: https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/ ; https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md ; https://github.com/gradle/actions/releases/tag/v6.0.0 ; https://github.com/actions/checkout/releases ; https://github.com/actions/setup-java/releases ; https://github.com/actions/upload-artifact/releases ; https://github.com/softprops/action-gh-release/releases/tag/v3.0.0 ; GitHub REST tag/ref/contents endpoints for the five action repos.
