# Review tests, round 1

LENS: tests  ROUND: 1  BASE: 5cd3e37e

Suite state: extended check.full exits 0 at 7d639a9b (test tasks up-to-date from the 14:37-14:38 run: BaselineNetworkBlockerTest 6/0, BaselineFixtureContentTest 3/0, BaselineProfileContentTest 6/0). check.full does not compile :baselineprofile (QA recorded its compile OK).

### F1 [minor] No test fails if the egress blocker is never installed, so an empty journal proves nothing
- Where: app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:29; app/src/instrumentation/res/xml/network_security_config.xml
- Problem: BaselineNetworkBlockerTest calls BaselineNetworkBlocker.selector(...) directly; nothing checks that configureRuntime() -> configure() calls BaselineNetworkBlocker.install(...), nor that the NSC leaves out system trust anchors. JourneyCheck passes on an empty journal, which is also empty with no blocker.
- Failure scenario: a refactor drops install(...) or a later ProxySelector.setDefault overrides it; a non-loopback request goes out; journal empty; all journeys and unit tests green.
- Fix: split configure(context) to delegate to configure(journalDir: File); JVM test in testInstrumentation: after configure(tempDir), ProxySelector.getDefault().select(URI("https://example.org/")) returns the dead proxy and journals the request, and KinoPubConfig is pinned to the mock origin. Optionally parse the NSC XML and assert no certificates src="system".

### F2 [minor] AC-9's given (new profile embedded) is not met and its comparison step was skipped
- Where: evidence M6; spec M6 step 4
- Problem: M6 installed the dev debug APK, which does not package baseline.prof; the master screenshot was not taken; QA marked AC-9 PASS.
- Fix: amend AC-9/M6 to what was proven (flavor change does not break the dev launch; embedded-profile launch left to the release procedure), or run M6 with a release-type dev build whose signer matches the box; record the missing master screenshot.

### F3 [minor] AC-1 says every method rule in both files is H; startup-prof.txt has 23546 rules without H
- Where: spec AC-1, M1 step 4, BaselineProfileContentTest.baselineProfileMethodRulesAreHot (reads only baseline)
- Problem: startup-prof.txt keeps its S/P flags (23546 non-H now, 24172 at base) because the Makefile re-marks only the baseline file.
- Fix: amend AC-1 to baseline-prof.txt only (the behaviour at base). No code change.

### F4 [minor] The spec still states D14 (genres required on rail) and D7 (25-year leaf), which the code no longer follows
- Where: spec D14, D7, R15; BaselineMockBackend.kt (/v1/genres not required); tools/make-baseline-mock-cert.sh:5,20
- Problem: drifts recorded only in the plan's Implementation record; AC-2 traceability and M1 step 3 point at a route table the generator does not enforce. Both drifts are sound on merit (no rail screen requests genres; route still served; fixed validity handles emulator clock lag, documented in docs/release.md:137).
- Fix: amend spec D14, D7 and R15.

### Unproven
- profilesHaveNoInstrumentationOrGeneratorClasses: the com/kino/puber/baselineprofile/ half cannot fail (generator code runs in the test APK).
- AC-2 traceability "fails if the per-iteration reset (D11) is removed": no run with clearTarget removed.
- BaselineMockSelfTest: no red run on unchanged code (spec accepts red-by-absence); each test reads as able to fail.
- BaselineFixtureContentTest checks JSON only, not media/stream.m3u8 (relative URIs today).
