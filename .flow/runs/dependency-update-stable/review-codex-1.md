CODEX: ran: review, adversarial
CONFIRMED:
- [major] /home/user1/orca/workspaces/Puber/dependency-update-stable/docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md:12 - AC-10 (P0) is recorded "NOT CHECKED (progress save)", so nothing shows that a new watch position survives a restart on Room 3.0.3, sqlite 2.7.1 and KSP 2.3.12.
  - Scenario: play a title, seek and stop, force-stop the app, relaunch. The row should show the new progress, but the app logged "Playback progress save failed" twice per playback, and no new progress appeared.
  - The same two lines appear on base 4db9c0d4, so the failure is not caused by this branch. But the failure and the Room work share the write path, and the cause is unknown. `PlayerProgressDiagnostics.kt` drops the throwable, so this is not provably a network or auth failure.
  - A Room 3 write regression could hide behind it. The evidence only shows that the DB opens, that existing Continue watching rows survive, and that the filtered log has no Room/SQLite exception.
  - Needed before ship, either one of:
    - Evidence that a new write persists. For example, the `WatchStateDao` and `CachedPayloadDao` instrumented suites via `make DEVICE=<serial> itest`, or a progress save that succeeds on the device.
    - An owner-approved revision of AC-10.
  - Not rated blocker because the failure also occurs on base.
REJECTED:
- Plain review found no regressions in the version bumps, the regenerated wrapper or the debug-signing fallback credentials - no defect claimed.
- Adversarial claim that the version changes need checking against the target table - I checked it. The diff matches the To column, except media3, which stays at 1.11.0. The spec's decision log (the Gate 1 delta) records this deliberately and names the follow-up run `media3-hagc-removal`.
- The explicit `storePassword`/`keyAlias`/`keyPassword` in the `app/build.gradle.kts` fallback is in scope and narrow. The spec's decision log records the reason (AGP 9.4+ rejects a signing config without credentials) and says the signature is unchanged.
VERDICT: changes needed
