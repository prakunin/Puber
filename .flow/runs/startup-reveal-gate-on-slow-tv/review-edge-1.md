LENS: edge  ROUND: 1  BASE: 9158cf82

No findings.

### Unproven
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:82-84 Grace timer firing after the held images have already revealed the splash with cause `ready`. `onGrace` still sets `forcedCause = GRACE` because the cause is still null. Nothing is reported, `covered` does not change, and the next `begin()` clears the cause, so today it has no visible effect. It becomes real if any reader of `forcedCause` other than `update`, or anything that skips `revealedOnce`, is added. Fix: also check `!revealedOnce` in `onGrace`.
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/StartupReveal.kt:93-95 The grace timer runs even when the app goes to the background during the splash. The splash would then lift with cause `grace` while nobody sees it. It becomes real if the `startup` line from a backgrounded start is counted in the AC-6 or AC-7 tables; no evidence of such a start exists.
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:127 On a `grace` reveal, `onRevealed` reports every request in flight, including ones started after ready. It does not report only the held ones. The spec chose this (design row "`inFlight` on `onRevealed`: Unchanged"). It becomes real only if the evidence tables read `in_flight` as "held images left undecoded".
