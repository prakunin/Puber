Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; result XML: 2651 tests, 0 skipped, 0 failures, 0 errors). Also :app:assembleDevDebug :app:assembleProdRelease -> pass.
DEVICE: not used (owner decision: no device this run; nothing leased, connected or installed)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | All 8 unpinned/existing tests named in test-design (ApiDomainInteractorTest x8 incl. initialize/detectAndSave*/skipsTheCurrent, HttpStackTest.create_resolvesThroughDoh_byDefault) present in result XML, none failed/skipped; KinoPubConfigTest and ApiModuleHttpStackTest green |
| AC-2 | PASS | KinoPubConfigTest x3 pin tests and ApiDomainInteractorPinnedEndpointTest x8 (all seven mutators + no walk in flight) present, pass; each pinning class has clearPinnedEndpoint() in @AfterEach (checked by hand) |
| AC-3 | PASS | HttpStackTest.create_usesSystemDnsAndKeepsTheDnsCache_withoutDoh and ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh present, pass |
| AC-4 | PASS (diff review) | PuberApp.kt diff: open class; three protected open hooks with defaults true/Unit/emptyList(); onCreate order appCreateMs, super.onCreate(), gate return, TelemetryRuntime.install, StartupReveal.installFrameWait, configureRuntime(), initDi(), initLogger(); runtimeModules() appended last to the six modules; attachBaseContext/newImageLoader untouched. No JVM test, by design |
| AC-5 | PASS | rg setPinnedEndpoint app/src/main prints only the definition (KinoPubConfig.kt:81); both assembles, detekt, lint, tests exit 0 |
| AC-6 | NOT CHECKED | Owner decision: no device this run; dev APK launch on the box not performed |
TESTS ADDED: none (all 22 Class.method names from test-design.md exist in result XML and passed; automatic checker parsed 0 names, verified by hand)
EVIDENCE: n/a - device not used; evidence file not required, static results above
DEFECTS: none
UNAVAILABLE: device (owner decision) - AC-6 only
MANUAL: When the owner allows a device: install the worktree dev APK with tools/android-apk-install-preserve on 192.168.1.110:5555, launch com.kino.puber.stage, expect the same first screen as the master dev build and no crash (manual check 4).

---

Round 2 (AC-6 only, head 23fb886d)

CHECK: not re-run (no production change since round 1; :app:assembleDevDebug -> pass)
DEVICE: 192.168.1.110:5555 (owner's TV box), leased and released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 | PASS | install-preserve outcome installed; on-device base.apk sha256 = build sha256 (95a923cf...5c80); cold launch Status ok, Main with rail drawn, no pairing screen; HOME + relaunch ok; crash buffer 0 entries for the package. An app update dialog (1.12.8) overlays Main; it is not from this change. No master side-by-side screenshot. |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/hermetic-baseline-seams/docs/superpowers/evidence/2026-11-26-hermetic-baseline-seams-device.md (audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
