# session-revoked-pairing: device check, round 1

Build: worktree dev debug APK (1.12.7-dev, sha256 86ed3ccd16535ec8c57e3daeca47b01a0101213f0b43e7b7688091a6449dde8e), installed with install-preserve (outcome installed, compatible_replace, no data loss).
Device: TV box 192.168.1.110:5555, com.kino.puber.stage only. auth-save before, auth-restore after (restored OK).

## M-1 (AC-5, AC-6, AC-8): FAIL

Steps: force-stop, launch the stage app, screenshot at intervals, status lines of API responses read from the app's own log (no body, no tokens).

Observed:
- Warm-up at process start received two 403 answers (items/hot, watching/serials) before any screen subscribed.
- The next Home section calls then went out without a bearer (tokens already cleared by the new path): 401, refresh POST answered 400 (worktree build has no client secret), seven times in 3 s, then calls stopped.
- The app stayed on the loading spinner with the mirror hint ("Вижу проблемы с загрузкой ... зеркала") for the whole 110 s watched. Auth was never shown: expected Auth with the blocked line. Screenshot: 2026-11-26-session-revoked-pairing-stuck-spinner-70s.png
- Second launch: tokens were gone, LauncherVM opened Auth without the blocked line (reason is lost with the dropped event); the device-code request answered 400 (no client secret in the worktree build). Screenshot: 2026-11-26-session-revoked-pairing-relaunch.png

Reading: the session was ended by apiCall (tokens cleared) but the SessionEvent was emitted when the app-level handler was not collecting (SessionEventBus has no replay), so no re-root to Auth happened and Home stays loading. The accepted risk "event dropped with no subscriber" in the design is hit on the very first cold start of the banned box.

AC-10 not judgeable: the blocked line was never displayed.
