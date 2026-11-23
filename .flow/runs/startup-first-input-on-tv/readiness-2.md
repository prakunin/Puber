# Readiness 2: startup-first-input-on-tv

Verdict: **READY**

Base c506d0bd. Spec and plan as committed in the worktree at 740d780f.

## Readiness 1 fix list

| # | Item | Result |
|---|---|---|
| 1 | Spec rows for the grace cause | Resolved. Design Decisions "New `startup` fields" lists `grace` added in phase B; "Cause when the grace ends" reports `cause: grace` (`RevealCause.GRACE`); Open questions 1 and 2 marked answered |
| 2 | `KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` red on assertion | Resolved. Plan Task 4 asserts `assertEquals(1, lats.size)` and `latMs` 201; step 1 adds the `dropped` parameter with no behaviour so the red run fails on the assertion (two `KeyLat`, 301 and 201), not on compilation. Test design row and section Bug say the same |

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass, unchanged from readiness-1 |
| Citations resolve at c506d0bd | Pass, re-sampled: `KeyProbe.kt:108-119` (`dispatchWithProbe`), `KeyProbe.kt:126-132` (`KeyProbe.onKey`, arms on DOWN repeat 0), `MainActivity.kt:59-71` (drop inside `superDispatch`), `MainActivity.kt:44` (`coveredKeys`), `RevealGate.kt:45-50,52,55,57,65-73,78-79`, `TelemetryEvent.kt:207-215` (`KeyLat.latMs`), tests `KeyProbeGateTest.kt:29-30` (`key` helper), `DispatchWithProbeTest.kt:38` (`aKeyDroppedBySuperIsStillProbed`), `KeyProbeOffTest.kt:21-35` |
| No task leaves the build red; dependency order | Pass. Task 4 consumes Tasks 2 and 3 and updates every `dispatchWithProbe` / `onKey` caller (`MainActivity`, `KeyProbeGateTest`, `DispatchWithProbeTest`, `KeyProbeOffTest`) |
| P0 rows: defect named, red/guard, backticked test | Pass |
| Out of scope respected; blocking questions answered | Pass |

## Notes for implementation

- Readiness-1 note on Task 6 step 5 still applies: the dev flavor of 8428c946 and base share `Versions.DebugVersionCode`, so the reference APK install is not a downgrade.
