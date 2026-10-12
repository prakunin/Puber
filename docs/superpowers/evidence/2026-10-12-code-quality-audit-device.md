# Device verification: code-quality-audit (2026-10-12)

Worktree HEAD `abe63f00`. Device `emulator-5554` (`puber_tv_36`, Android 16 TV).
No account data, posters or raw logs are included here.

## Run 1: blocked

`tools/android-apk-install-preserve install-preserve` returned `outcome: blocked`,
`classification: signer_unknown` (installed signer not matchable to this build's
signer). Nothing was uninstalled, cleared or replaced. QA did not retry.

## Run 3: D1-D4 on the installed worktree build

Install: performed by the conductor, not by QA, under the user's direct
authorization given in the main session as
«Разрешаю install -r на emulator-5554, без uninstall/clear/downgrade».
Command recorded by the conductor (preceded by `make DEVICE=emulator-5554 auth-save`,
1721344 bytes saved):
`adb -s emulator-5554 install -r <wt>/app/build/outputs/apk/dev/debug/app-dev-debug.apk`
-> "Success". No uninstall, no `pm clear`, no `-d`, no signer swap.

QA checks before the run (own lease, released at the end):
- Installed `base.apk` sha256 equals the worktree APK sha256
  `f0a3fe2fd64412236fa5e7f67e5dcf71224ee7e62df215977909a9d40fb16901`.
- versionName `1.9.69-dev`, lastUpdateTime 2026-10-12 08:50:12.
- App still logged in after the install (Home with account rows rendered); `auth-restore` not needed.
- Remote only: `adb -s emulator-5554 shell input keyevent ...`; screenshots kept out of Git.

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| D1 launch (F1, F2) | force-stop, `am start MainActivity`, wait 12 s; D-pad around Home | Home renders, focus moves, no crash | Home rendered with rows and menu; an in-app "new version available" dialog (1.9.70) appeared and was dismissed with BACK (Update never pressed); D-pad moved focus between cards and the menu; `logcat -b crash` empty; process alive at the end. Episode schedule screen not exercised (no TMDB token in a worktree build). | PASS |
| D2 API-domain dialog (F3) | menu -> Settings -> Network and device -> "Change KinoPub mirror", DPAD_CENTER, then BACK | dialog shows the same current domain and prefill as the row; closes on BACK; focus back on the item | Row showed `service-kp.com`; dialog showed "Currently using: service-kp.com", empty input with the placeholder example, no spinner; BACK closed it and focus returned to the "Change KinoPub mirror" row. Nothing saved. Only the no-custom-domain shape was reachable; the with-custom shape, the Auth path and the Home error-state path are covered by unit tests (T4-T7), not by the device. No master build was installed to compare, so "as on master" rests on the row/dialog agreement. | PASS (no custom domain only) |
| D3 player clock (F4) | open a series title, play, DPAD_CENTER to show controls; later controls hidden, DPAD_RIGHT | `m:ss` elapsed, `-h:mm:ss`/`-m:ss` remaining, seek indicator `m:ss` | Controls: `0:13` elapsed and `-1:01:26` remaining; after seeking `22:40` and `-38:59`; seek indicator after one DPAD_RIGHT: `+10s` with `0:37`. No zero-padded first field, minus present. The `-0:00` when duration is 0 case was not reachable. | PASS (zero-duration case NOT CHECKED) |
| D4 resume dialog (F4) | play, seek forward (~22 min, and ~2 min with a 45 s wait), BACK, reopen the title with DPAD_CENTER | resume dialog with the stopped position | No resume dialog appeared on either attempt; playback restarted at `0:02` to `0:04`, and the title's detail page showed no saved progress ("Up next - season 1, episode 1"). The resume dialog was therefore never rendered, and its time string was not read. No crash. Whether master saves progress in this environment is unknown, so this is not recorded as a regression. | NOT CHECKED |

Crash check at the end: `logcat -b crash` empty, no `AndroidRuntime` entry for the package.
Playback progress was written to the test account by D3/D4 (spec-approved paths); nothing else was mutated.

## Static gate (QA, `./tools/agentw`, `AGENT_GRADLE_SERIAL=1`)

compile and `testDevDebugUnitTest` green (1427 tests, 0 failed/errors/skipped, including
`PlaybackClockTest` and `ApiDomainDialogStateTest`); `detektAll` 0 findings in `devDebug`
and `devDebugUnitTest`; `lintDevDebug` and `assembleDevDebug` successful. Compile, test
and detekt were reported UP-TO-DATE from the earlier run on identical inputs.
Detekt prints compiler-error notes (83 and 7) but 0 findings.

## AC-10

F1 and F2 (no runtime reach): covered by D1, PASS. F3: D2 PASS for the no-custom shape. F4: D3 PASS;
the resume-dialog string (D4) is NOT CHECKED. AC-10 is therefore only partially verified.
