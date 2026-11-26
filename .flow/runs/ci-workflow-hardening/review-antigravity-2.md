# Review antigravity, round 2 (7a836616..cfa72862)

ANTIGRAVITY: ran
CONFIRMED:
- [minor] tools/verify-apk-signer.sh:54 - The loosened regex matches any `certificate SHA-256 digest:` line, not only signer lines. If a release APK ever carried a Source Stamp signed by a different key, the extra digest would fail a correctly signed release. The failure is closed and spurious. No stamp is configured in `app/build.gradle.kts` or `gradle.properties` today, so it cannot happen now. The triage-1 #6 trade-off was to fail on any extra signer, and this is a known cost of it. No action needed.
REJECTED:
- The second sparse checkout of `github.sha` into `signer-verifier` is broken or unsafe - no defect: `persist-credentials: false`, same pinned action, `sparse-checkout-cone-mode: false`, separate `path` that does not touch `app/build`; the test accepts the verifier checkout only with `ref` = `${{ github.sha }}`.
- The new tests cannot fail - none found. AC-7 test ties APK dir, pin variable and value to the packaging step; later steps and job checked for `if`/`continue-on-error`.
- The fixes broke something in `app/` - incremental diff touches only `.github`, `tools`, `docs`.
VERDICT: clean
