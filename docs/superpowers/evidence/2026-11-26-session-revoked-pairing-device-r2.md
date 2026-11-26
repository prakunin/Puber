# session-revoked-pairing: device check, round 2

Build: worktree dev debug APK after fixes 5ad691e2, e679abbf, a823c57c (1.12.7-dev, sha256 4cd9420030bfed67ba72b53a193040870964008b60bfa73d2dcb08a50fafb99c), install-preserve outcome installed, compatible_replace, destructive_action false.
Device: TV box 192.168.1.110:5555, com.kino.puber.stage only. The banned session snapshot was restored (make auth-restore) before the run and again after it. A worktree build has no client secret, so no device code is shown; Auth stays in its loading state.

## M-1 (AC-5, AC-6, AC-8): PASS

Steps: force-stop, cold start, screenshots at intervals (about 12, 30, 44 and 65 s after launch), long OK, Back, Back.

| Step | Expected | Observed | Result |
|---|---|---|---|
| 4 cold start | Auth loading state with the blocked line | 12 s: splash; 30 s: Auth with the blocked line above the spinner, wrapped over two lines, not truncated (2026-11-26-session-revoked-pairing-r2-auth-30s.png) | PASS |
| 4 mirror hint | hint appears after about 20 s | 44 s and 65 s: the mirror hint at the bottom, blocked line kept (r2-auth-44s.png, r2-auth-65s.png) | PASS |
| 5 watch 30 s | Auth reached once, no flashing | Four screenshots over 53 s show one stable Auth screen; no Home spinner, no Home rows, no crash. Re-root count is judged from screenshots only (no logcat kept) | PASS |
| 6 long OK | mirror dialog opens | Dialog opened (r2-mirror-dialog.png). The first Back left the dialog open with focus moved off the first button (r2-after-dialog-back.png); the second Back closed it and returned to Auth with the blocked line (r2-after-second-back.png) | PASS, note below |
| 7 Back on Auth | base exit behaviour | The app left to the system launcher, not to Home | PASS |
| 8 | no new pairing | none done | PASS |
| 9 | auth-restore | exit 0 | PASS |

Note: the dialog needing two Back presses was not compared with base; it looks like the existing dialog focus handling, not part of this change. Reported to the owner as a manual check.

Round 1 defect 1 (event lost on a cold start, Home spinner for 110 s) does not reproduce: Auth with the blocked line appears within 30 s of the cold start.

## AC-7: instrumented DeviceSettingsContentTest

make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.device.settings.DeviceSettingsContentTest: BUILD SUCCESSFUL, 2 tests, 0 failures, 0 errors, 0 skipped (device KM6 - 10). Data kept; auth-restore run afterwards.

## AC-10

The blocked line text and layout are visible in the screenshots; the wording and look are the owner's judgement.
