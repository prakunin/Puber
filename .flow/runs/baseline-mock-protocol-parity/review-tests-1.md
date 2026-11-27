# Review tests, round 1

LENS: tests  ROUND: 1  BASE: 08f50b81

Suite: check.full exit 0 at f04f6e36 (test tasks UP-TO-DATE from the 05:28 run): BaselineFixtureContentTest 14/14, BaselineInstrumentationEnvironmentTest 3/3, BaselineNetworkBlockerTest 10/10; tooling suite all PASS; recount http1 54, internal 235, descriptors 203, phosphoricons 57, http2 284; all seven AC-6 prefixes present; AC-8 list empty. BaselineMockSelfTest device-only, not run.

### F1 [major] AC-5 and AC-7 have no qa evidence file; counts and the route-journal result exist only as the implementer's notes
- Where: docs/superpowers/evidence/ (none for this run); run folder has no verification output for device-only ACs
- Failure scenario: after merge nobody can re-check AC-5/AC-7 from the repo; the /tmp logcat is gone after a reboot.
- Fix: qa-device writes docs/superpowers/evidence/2026-11-26-baseline-mock-protocol-parity-device.md with Manual 1-3 and the Perf table, audited.

### F2 [minor] everyResponse_carriesDateHeader never checks a binary response
- Where: BaselineMockSelfTest.kt:137; BaselineMockBackend.kt:261
- Fix: add $origin/media/poster.jpg; also assert Date on one DoH response via the existing interceptor.

### F3 [minor] Seven P0 red self-tests have no red run in the Implementation record
- Where: BaselineMockSelfTest.kt:110, :125, :137, :154, :204, :221, close_stopsBothServers :164
- Problem: red by construction (don't compile at base / fail on base by reading), but none shown failing on its named mutation.
- Fix: on the next device run apply each row's mutation once and record the failing assertion, or re-tag the rows as guard with the reasoning.

### Unproven
- BaselineMockBackend.kt:70 h2 bind failure branch untested.
- BaselineProfileTestControl.kt:22 missing baselineMockH2Port error path untested.
