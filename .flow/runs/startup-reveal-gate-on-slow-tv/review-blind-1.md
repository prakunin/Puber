LENS: blind  ROUND: 1  BASE: 9158cf82
### F1 [minor] `img_idle` can now be stale or missing on a READY reveal, and READY can come with `in_flight` above 0
- Where: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:98 and :123
- Problem: the reveal now waits only for `held`, so a READY reveal can happen while other requests are still in flight. `onImagesIdle()` is still reported only while `!revealedOnce`, so the idle edge that follows such a reveal is never recorded. `StartupMarks.markImagesIdle` keeps the last value it got, so the line carries an idle edge from before ready, or none. On base, a READY reveal meant `inFlight` was empty, so `img_idle` always came before the reveal and `in_flight` was 0. The diff changes what the telemetry line means and says nothing about it. The updated `readyMark_*` test simply drops the `"idle"` entry.
- Failure scenario: begin. Image A starts and ends (idle recorded at t1). The ready mark holds B. C starts after ready. B ends, which reveals READY with `in_flight=1`. C ends after the reveal and records nothing. The `startup` line says `cause=ready`, `in_flight=1`, `img_idle=t1`, and t1 is earlier than `ready`. Anyone reading `img_idle` as "images went idle before the reveal" gets a wrong number.
- Fix: either keep reporting `onImagesIdle()` after the reveal until the line is written (drop `!revealedOnce` from line 98, or fire it once after the reveal), or record the change in meaning of `img_idle` and `in_flight` for `cause=ready` in the telemetry docs.

### F2 [minor] No test covers the production grace wiring
- Where: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/StartupReveal.kt:60-63, :94-96
- Problem: the grace timer only works if `installFrameWait()` sets `graceHook = ::postGrace`. Before that, and in every JVM test, `graceHook` is a no-op. All grace tests build their own `RevealGate` and pass `scheduleGrace` directly, so none of them goes through `StartupReveal`.
- Failure scenario: someone deletes or reorders the `graceHook = ::postGrace` line. The grace never fires, slow images hold the splash until the 2.5 s cap again (the bug this run fixes), and the unit suite stays green.
- Fix: add a seam test on `StartupReveal`, or note in the device evidence that a `cause=grace` line was seen on the box. Either one shows the wiring can fail visibly.

### Unproven
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:82-84 After a READY reveal, the grace still fires and quietly sets `forcedCause = GRACE` (`revealedOnce` stops it from being reported). This only matters if some code reads `forcedCause` after the reveal and before the next `begin()`, and none does today.
