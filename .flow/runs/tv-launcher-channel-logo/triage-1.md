# Triage round 1: tv-launcher-channel-logo

Reviewed HEAD 9d80b868 against base e11d1b66. Inputs: review-blind-1.md, review-tests-1.md, review-codex-1.md (clean).

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| T1 | tests F1 | minor | The "keep a stored preview while the channel is down" arm of publish has no test | fix | Cheap, real gap on new behaviour; one JVM test with a stored preview and a failing publishChannel |
| B1 | blind F1 | minor | A failing logo may leave an orphan channel and retry on every reconcile | reject | research.md (tvprovider 1.1.0 bytecode) shows publishChannel deletes the inserted row before it throws, so no orphan row is left; the retry-per-reconcile is the existing behaviour on failure and is logged once per reconcile by design (AC-4). The logo is a raster mipmap (no mipmap-anydpi in res, brief), and the box accepted it (qa-report round 1). |
| T2 | tests F2 | minor | test-design labels cancellationFromChannelCreationPropagates P0 red, it was green on base | accept | Documented in implementation.md (kept as a guard); the test guards the new catch. Listed for the owner. |
| T3 | tests F3 | minor | AC-1..3 rest on stored IDs and the launcher row, not provider rows; logo look not proven | reject as a code finding; carried to the gate | Provider tables are not readable from adb on the box (qa-report UNAVAILABLE). Carried to the judge and gate 2 exactly as NOT CHECKED / indirect, with the manual "customize channels" check for the owner. |
| T4 | tests F4, blind F2 | minor / nit | Production gateway and store have no JVM test | accept | No Robolectric in the project; covered by the device run (stored IDs stable over two restarts). Listed for the owner. |

Fix list for the implementer: T1.
