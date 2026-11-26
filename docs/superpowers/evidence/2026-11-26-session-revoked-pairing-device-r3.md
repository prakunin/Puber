# session-revoked-pairing: device check, round 3

Build: worktree dev debug APK at 3968eeaa (1.12.7-dev, sha256 7b34a88b4f9200ae0bbaf0a4b4acae8b20ea7462e0dc15fac3429f97fe824691), install-preserve outcome installed, compatible_replace, destructive_action false.
Device: TV box 192.168.1.110:5555, com.kino.puber.stage only. The banned session snapshot was restored (make auth-restore) before the run and again after it. A worktree build has no client secret, so no device code is shown; Auth stays in its loading state.

## M-1 (AC-5, AC-6, AC-8): PASS

Cold start after install (am force-stop, then am start), screenshots at about 12, 30, 44 and 64 s.

| Step | Expected | Observed | Result |
|---|---|---|---|
| 4 cold start | Auth loading with the blocked line | 12 s: splash; 30 s: Auth with the blocked line over the spinner, two lines, not truncated (2026-11-26-session-revoked-pairing-r3-30s.png) | PASS |
| 4 mirror hint | hint after about 20 s | 44 s and 64 s: mirror hint at the bottom, blocked line kept (r3-64s.png) | PASS |
| 5 watch 30 s | Auth reached once, stable | screenshots over 52 s show one stable Auth screen; no Home spinner, rows or crash (judged from screenshots, no log count) | PASS |

## Persisted reason (AC-6, triage-1 #3): PASS

am force-stop com.kino.puber.stage (ordinary process kill, no data clear), relaunch, no auth-restore in between. 14 s: Auth with the blocked line and a snackbar "could not sign in, request a new code" (r3-persisted-14s.png); 30 s and 45 s: blocked line, then the mirror hint (r3-persisted-30s.png). Round 1's relaunch lost the line; it is now kept.

Observation: the snackbar appeared only on this second launch. A worktree build has no client secret, so the device-code request fails; it was not seen in round 2 screenshots at 30 s and later. Not part of this change.

Then force-stop and make auth-restore (exit 0); lease released.

## Not re-run this round

M-1 steps 6-7 (mirror dialog, Back) and DeviceSettingsContentTest: the runtime changes of the fix round do not touch them; round 2 results stand.
