# session-revoked-pairing: device check, round 4

Build: worktree dev debug APK at 672d3618 (1.12.7-dev, sha256 776218d886a27b0caf278e29fba3f49a44725e713498c25568b6dfae521e394c), install-preserve outcome installed, compatible_replace, destructive_action false.
Device: TV box 192.168.1.110:5555, com.kino.puber.stage only. The banned session snapshot was restored (make auth-restore) before the run and again after it. A worktree build has no client secret, so no device code is shown; Auth stays in its loading state.
Scope: fix round 2 moved the session-end clears into SessionEndCleanup (claim released on cancel) and changed a preview. Runtime path: App.kt handler, AC-5, AC-7 (handler), AC-8.

## M-1 (AC-5, AC-8): PASS

Cold start after install (am force-stop, then am start), screenshots at about 12, 30, 44 and 54 s.

| Step | Expected | Observed | Result |
|---|---|---|---|
| cold start | Auth with the blocked line | 12 s: Auth with the blocked line over the spinner (r4-12s.png); no Home spinner | PASS |
| mirror hint | hint after about 20 s, blocked line kept | 30 s, 44 s, 54 s: hint at the bottom, blocked line kept (r4-30s.png, r4-54s.png) | PASS |
| stability | Auth reached once, stays | four screenshots over 54 s show one stable Auth screen; no Home, no crash (judged from screenshots, no log count) | PASS |

Then force-stop and make auth-restore (exit 0); lease released.

## Not re-run

Mirror dialog and DeviceSettingsContentTest: untouched by this round; round 2 results stand.
