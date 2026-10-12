# Device verification: code-quality-audit (2026-10-12)

Status: BLOCKED at install. No AC-10 path was exercised.

- Worktree HEAD: `abe63f00`; APK built by `./tools/agentw :app:assembleDevDebug`
  (version 1.9.69-dev, code 2090000000, sha256 `f0a3fe2f...6901`).
- Device: `emulator-5554` (`puber_tv_36`), lease acquired and released.
- `tools/android-apk-install-preserve install-preserve` returned
  `outcome: blocked`, `classification: signer_unknown`: the installed
  `com.kino.puber.stage` (same version code) has a signer the tool cannot match to
  this build's signer (`6b291812...a83e`). Per AGENTS.md this is not worked around:
  no uninstall, no clear, no signer swap.

| Check | Result |
|---|---|
| D1 launch (F1, F2) | NOT CHECKED (install blocked) |
| D2 API-domain dialog (F3) | NOT CHECKED (install blocked) |
| D3 player clock (F4) | NOT CHECKED (install blocked) |
| D4 resume dialog (F4) | NOT CHECKED (install blocked) |

Static gate on `abe63f00` (through `./tools/agentw`, `AGENT_GRADLE_SERIAL=1`):
compile and `testDevDebugUnitTest` green (1427 tests, 0 failures, 0 errors, 0 skipped,
including `PlaybackClockTest` and `ApiDomainDialogStateTest`); `detektAll` 0 findings in
`devDebug` and `devDebugUnitTest`; `lintDevDebug` and `assembleDevDebug` successful.
Tasks were reported UP-TO-DATE from the implementer's earlier run on identical inputs.
