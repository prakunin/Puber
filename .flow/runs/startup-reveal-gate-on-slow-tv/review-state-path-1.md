LENS: state-path  ROUND: 1  BASE: 9158cf82

No findings.

I followed each order of events through the changed gate and found nothing that undoes the reveal or leaves the splash up. The files I read:

- `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt`
- `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/StartupReveal.kt`
- `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/StartupImageActivity.kt`
- `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/telemetry/StartupMarks.kt`
- The callers in `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/MainActivity.kt` and `core/ui/PuberVM.kt:94`

The sequences I checked and ruled out:

| Sequence | Why it holds |
|---|---|
| `markReady` arms the grace timer, then the held requests finish before it fires | The splash opens with cause `ready`. When the timer then sets cause `grace`, `revealedOnce` blocks a second report and the splash does not cover again. |
| A timer from an earlier start fires after `begin()` or `reset()` | It fails the `generation == armedIn` check in `onGrace`. |
| The cap or a forced reveal (`revealNow`) comes before ready, or before the timer | Ready does not start a timer once a cause is set (`forcedCause == null` check). A late timer cannot change a cause already set. |
| Home sets its state several times, so `markReady` runs again | The `!ready` check means only the first one starts a timer. |
| A request that started after ready, or before `begin()`, finishes | It can only leave `held` if it was in flight when ready took effect, so it cannot hold the splash or release another request. |
| The grace timer on the main thread and a Coil thread finishing a request at the same moment | Both change state inside `update` under `lock`; whichever runs first decides the cause. |
| The timer is scheduled while the gate holds its lock | `postDelayed` only queues the task, so it never runs inside the lock. |
| The `startup` telemetry line after a `grace` reveal | It waits until the held requests finish, or 5 s at most, the same as after the cap. |
| Keys and focus once the splash is gone | The splash still opens only after Home's ready, so the launch focus claim is unchanged. Only images can still be loading, and they do not move focus. |
