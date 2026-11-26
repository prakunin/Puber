# Triage round 1: hermetic-baseline-generation

Reviewed head: 7d639a9b (base 5cd3e37e). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (ran, clean). qa round 1: qa-report.md.
Each finding was checked against the code by the conductor (rule counts in blind F1 re-counted below).

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Regenerated startup profile loses code every prod cold start runs: token decryption (CryptoPreferenceRepository), DoH resolver, endpoint probe, AppUpdate, tvprovider channel publishing | blind F1 | major | fix | Design-level: the instrumentation app swaps the token store, pins the endpoint (DoH off, probes skipped) and serves empty watching fixtures. Architect design delta first (options: real CryptoPreferenceRepository with seeded synthetic tokens; DoH served by the mock or another way to keep DoH classes hot; non-empty watching/serials and an AppUpdate route), plus BaselineProfileContentTest guards for these startup paths. Owner decides if an AC changes |
| 2 | No test fails if the egress blocker is never installed (empty journal proves nothing); NSC system anchors unchecked | tests F1 | minor | fix | Cheap JVM test via configure(journalDir); NSC parse test |
| 3 | Late requests after the last iteration of each journey are never checked | edge F1 | minor | fix | Check VERIFY and unknownRequests before resetting in @Before and in @AfterClass |
| 4 | AC-9 given (profile embedded) not met: debug APK launched, no master screenshot | codex, tests F2 | minor | fix | AC wording: owner decides between amending AC-9 to what is provable on the box and installing a release-type dev build on the box |
| 5 | AC-1 says both profile files all-H; startup-prof.txt keeps S/P flags (as at base) | tests F3 | minor | fix | AC wording: amend AC-1 to baseline-prof.txt (behaviour at base); owner approves the AC change |
| 6 | Spec D14 (genres) and D7/R15 (25 years) no longer match the code | tests F4, edge F2, codex | minor | fix | Architect updates D7, D14, R15 in the design (drift is correct on merit) |
| 7 | New JVM tests run in no CI gate | blind F2 | - | reject | Q3 decision: they run in check.full (extended at ship, D18); PR checks unchanged by owner decision |
| 8 | StartupBenchmarks targets an app with no backend; stale KDoc | blind F3 | - | reject | Q4: startup-benchmark-entry owns it; this run notes it in docs/release.md |
| 9 | JourneyCheck substring match on result=-1; missing seg counted before throw; ProxySelector bypass; late violation lost | codex rejected, blind/edge unproven | - | reject | Unproven per codex-reviewer and edge; NSC is the second guard; #3 covers the late-request window |
| 10 | journal.record throwing; @AfterClass noise; CLEAR keeps prefs/filesDir; API34 reason text | edge, blind unproven | - | reject | Hypothetical; no path shown |

Deferred: none. Accepted: none.
