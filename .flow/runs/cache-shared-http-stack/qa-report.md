Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks were UP-TO-DATE from an earlier green run at head faa99d8e, results read from build/test-results: 2175 tests, 0 skipped, 0 failures)
DEVICE: emulator-5554 (puber_tv_36, booted headless without wiping data; lease taken and released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | ApiModuleHttpStackTest 2/2 green; device: NetStats labels app, doh, image all present in one process, all lookups through DoH |
| AC-2 | PASS | DnsOverHttpsFactoryTest 3/3 green (repeat within TTL, after TTL, after evictAll) |
| AC-3 | PASS (unit) | ApiDomainInteractorTest 20/20 and ApiDomainInteractorWiringTest green; device M4: Home loads on api.alador.space and after reset, a host resolved earlier was re-queried (2 doh.callStart) right after the switch back; this cannot separate evict from TTL expiry, so the device part does not prove it |
| AC-4 | PASS | HttpsEnforcingInterceptorTest 2/2, ImageHttpClientTest 1/1; device: posters, backdrops and episode stills render on Home, Details and a 4K hub grid on the shared stack |
| AC-5 | PASS (keep branch) | Cache kept: app hit=1 in every run, cond=1 in 4 of 9; no drop, so the delete tests and M6 do not apply |
| AC-6 | PASS | KinoPubConfigTest 7/7 green |
| AC-7 | PASS (after the round-1 addendum) | Host lookups (app.dns + image.dns) median of 3 counted runs 3 before (dd19f70d: 4, 3, 3), 3 after (faa99d8e: 3, 3, 3); new connections 5 to 5; doh.call (secondary) 4 to 4, identical runs 4, 2, 4 |
| AC-8 | PASS | check.full green; git diff 88fa7303..HEAD on app/src/test is 610 insertions, 0 deletions: no existing assertion changed |
Secondary (R13, TotalTime): NOT CHECKED reliably. Counted runs 2427, 1898, 1971 ms (median 1971 vs 1845, +6.8%); later runs 3.2-5.9 s under host load average 23 (another worktree building), not comparable.
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md (committed in wt; audit passed)
DEFECTS: none (the round-1 AC-7 failure came from the metric unit; see the addendum)
UNAVAILABLE: none
MANUAL: none. Notes: (1) a player started once by my navigation (series Details, episode 8, buffering at 1:10, a few seconds), left with BACK; watch progress for that title may have been written. (2) The mirror was switched to api.alador.space and reset to the default service-kp.com (final state default). (3) The emulator is left running.

## Round 1 addendum: AC-7 in host lookups

Per the conductor decision, resolver queries are counted as host lookups: dnsStart summed over the app and image labels; doh.call is secondary; doh's own dnsStart (2 in every run) is the dns.google bootstrap lookup. baseline.md lacks app.dns, so I measured both builds again in one sitting: the dd19f70d APK (built from a git archive in a temp directory, since removed; sha256 6dea4345...2f7, matches on device) then the head APK (78ff16da...49d74). Both Room v4 and the same version code, installed with install-preserve (installed, compatible_replace, no downgrade). One uncounted warm-up, then 3 counted runs per build, same session as baseline.md.

| metric (median of 3) | before dd19f70d | after faa99d8e |
|---|---|---|
| host lookups (app.dns + image.dns) | 3 (runs 4, 3, 3) | 3 (runs 3, 3, 3) |
| new connections (app+doh+image) | 5 (6, 5, 5) | 5 (5, 5, 5) |
| doh.call, secondary | 4 (4, 2, 4) | 4 (4, 2, 4) |
| doh.dns, bootstrap | 2 | 2 |

image.dns is 0 on both sides (no image fetch reached the network). No playback in these runs. AC-7: PASS. Evidence: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md, committed as 780aa9ca; the audit passed. Lease released; the emulator is left running; the head APK is installed.

---

Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at head e3a8cdb3; test, detekt and lint tasks UP-TO-DATE from the implementer's green run at this head, results read from build/test-results: 2176 tests, 0 skipped, 0 failures, 0 errors; lintDevDebug re-executed)
DEVICE: emulator-5554 (puber_tv_36, booted headless without wiping data; lease taken and released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | device smoke at head: labels app, doh, image in one process, image lookups resolve through DoH (image.dns 3 + app.dns 3 = doh.call 6/2 hosts); pool sharing proven by the unit test (ApiModuleHttpStackTest, green in the 2176) |
| AC-2 | PASS | unit tests green; device smoke: 6 host lookups cost 7 doh calls where uncached would be 12 |
| AC-4 | PASS | posters, backdrops and hub rows rendered on head in runs with image.call 7 to 27 (fetched over the network, never loaded before) |
| AC-7 | PASS (with image fetches on the network) | median of 3 counted runs per build, dd19f70d to e3a8cdb3: Q4 metric (doh.call + image.dns before, doh.call after) 8 to 6; host lookups (app.dns + image.dns) 7 to 6; new connections (app+doh+image) 9 to 7 |
Counted runs. Before dd19f70d (fresh hubs: Concerts, Documentary series, TV shows): host lookups 7, 8, 7; Q4 6, 8, 8; connections 9, 10, 9; image.call 21, 16, 15; image.dns 4 each. After e3a8cdb3 (Movies hub 25 RIGHT, Home scrolled, Series hub 20 RIGHT): host lookups 6, 5, 6; Q4 7, 6, 6; connections 7, 7, 8; image.call 27, 7, 19; image.dns 2, 2, 3. The sides fetched different posters (a poster is fresh once), so image.call is listed to normalise; the run with the most images after (27) still had 6 lookups against 7 to 8 before.
Derived clients: on dd19f70d every client built from the shared base counts under app (KinoPub, IntroDb, TheIntroDb, TMDB, probe, diagnostics, player); on e3a8cdb3 the same, except the player carries the debug "player" label. No playback in these runs, so no player calls on either side; image is Coil's own listener on both, and its lookups are system DNS before, DoH after (hence they appear in doh.call after).
Not counted: dd19f70d Documentary movies (no image event, posters already cached) and two head attempts that wandered into Details.
Not shown on device: no resolver failure was provoked, so "failed lookups not cached", the DoH Dispatcher and the Koin-single Coil client rest on the unit tests (DnsOverHttpsFactoryTest, ApiModuleHttpStackTest, ImageHttpClientTest).
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md (appended, committed in wt: 63dc038e Record the round 2 device evidence of the shared HTTP stack with fresh posters
/home/user1/.puber/device-locks/mobile-emulator-5554.lock; audit passed, 55 files)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: no playback was started this round. The head APK is installed; the emulator is left running; the dd19f70d APK was built from a temp git archive, now removed.


---

Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at head 04bb948c; 2236 tests, 0 skipped, 0 failures, 0 errors read from build/test-results; test and detekt tasks UP-TO-DATE from the implementer's run at this head, lintDevDebug re-executed)
DEVICE: emulator-5554 (puber_tv_36, already running; lease waited for, acquired, released). Head APK sha256 40133f05...cd40 installed with install-preserve (installed, compatible_replace, no destructive action), on-device hash matches.
ACS (integration after the rebase onto master 7bcbfd83):
| AC / row | Status | Evidence |
|----|--------|----------|
| AC-1 (R15 rerun) | PASS | ApiModuleHttpStackTest green in the 2236; M1 cold start renders Home with posters, no Koin error or crash |
| AC-3 (R15 rerun) | PASS (unit, as the AC states) | ApiDomainInteractorTest and ApiDomainInteractorWiringTest green; M4 style switch: Home loads on api.alador.space and after Reset; doh.call 6 to 8 across the Reset (consistent with the evict, not proof) |
| AC-4 / M2 | PASS | Home rows, hero and series Details show images; NetStats image label present (call=9) |
| M1 | PASS | splash, then Home with rows and posters; 0 FATAL, 0 NoDefinitionFound, 0 ANR |
| Startup splash smoke | PASS | five telemetry startup events: revealed 3981 to 4337 ms after process start (host load about 14); Home with posters each time, no hang |
| Domain switch smoke | PASS | Home loads on each domain; final state default service-kp.com |
Other ACs (AC-2, AC-5, AC-6, AC-7, AC-8): unchanged since round 2, covered by the same green suite (2236 tests); no device re-measure was asked.
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md (appended "Round 3"; committed in wt: d376f671 Record the round 3 device evidence of the shared HTTP stack after the rebase onto master; audit passed, 56 files)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: (1) revealed 4.0 to 4.3 s is above the splash evidence's quiet-host medians (2.0 to 2.7 s) because the host load was about 14 and reveal follows first_content; the cap constant is 2.5 s from begin(), but the splash gate also waits for content, so a loaded host shows longer. Not an AC of this task; the owner may want a quiet-host re-measure of the splash on the merged head. (2) No playback was started. (3) The emulator is left running; the head APK is installed.

## Round 3b

Question: does the branch slow the startup reveal compared with master? Answer: no slowdown seen; the difference is inside the spread, so no speed-up is proven either. Builds: master 7bcbfd83 (git archive in a temp directory, removed; APK sha256 6b08476d...c572a) against head 04bb948c (APK sha256 40133f05...2cd40). Both installed with install-preserve each time (installed, compatible_replace; two apkanalyzer 60 s timeouts under load, repeated). Device emulator-5554, interleaved master/head, force-stop then launch, one warm-up per build, host load recorded per start.

| metric (counted starts) | master (n=6) | head (n=5) |
|---|---|---|
| revealed median / range | 4640 / 3639 to 5180 ms | 3922 / 3714 to 4499 ms |
| first_content median / range | 4079 / 3119 to 10954 ms | 3711 / 3213 to 4840 ms |
| app_composed median | 2327 ms | 2040 ms |
| host load at start | 6 to 17 | 6 to 16 |

Head revealed lower than master's in 4 of 5 same-round pairs, but the ranges overlap and master's slowest starts had the higher load, so the difference does not exceed the spread. Image fetches: master has no NetStats listener (tag empty, cannot be counted); on the head the image label was absent on all 5 counted starts (posters from Coil's disk cache, no image request on the network) and showed image call=1 on one warm-up.

Caveats: (1) the emulator died twice under host load 70 to 77 and was cold-booted without wipe, so the data is two segments (A: 2 master and 1 head counted; B: 4 master and 4 head counted) with a warm-up pair after the reboot; (2) the lease likely expired about 20 minutes in (the final release was refused with a token mismatch, another agent took the lock right after my last start); no overlap with another leasing agent is known, but segment B strictly ran on an expired lease; (3) one start per install, so every start follows an install-preserve, for both builds alike; (4) the host was loaded, absolute numbers are not quiet-host numbers.

Evidence: /home/user1/orca/workspaces/Puber/cache-shared-http-stack/docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md (section Round 3b), committed; mobile-evidence-audit passed (56 files scanned).
