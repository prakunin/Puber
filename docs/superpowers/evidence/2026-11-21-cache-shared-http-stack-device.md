# Device evidence: cache-shared-http-stack (round 1)

Date: 2026-11-21. Head faa99d8e. Dev debug APK sha256 78ff16da1406b01b78c5bd5894557f70235dc5cd2f16498af5022793b4049d74 (on-device base.apk sha256 matches).
Device: emulator-5554 (AVD puber_tv_36, API 36 TV, booted headless, data not wiped), lease held and released.
Install: tools/android-apk-install-preserve, outcome installed, classification compatible_replace, destructive_action false.
Counts only: NetStats counters (cumulative line at session end). No raw logcat, tokens or account data kept; screenshots were looked at and not stored.
Host load average was about 23 during the later timing runs (another worktree builds), so TotalTime is noisy.

## M1 launch on the shared stack
Steps: force-stop, launch, wait 12 s. Observed: Home renders (hero, Continue watching, My bookmarks rows with posters), still logged in, no crash. Status: PASS.

## M2 posters on Home and Details (AC-4)
Steps: Home, RIGHT/DOWN x2/RIGHT, CENTER into a series Details. Observed: Home posters and backdrop, Details backdrop and episode stills all rendered. In a deeper-scroll session (4K hub, 12 RIGHT, 12 DOWN) the image label appeared with call=23, dns=4, connect=4 and the screens showed images. Status: PASS.

## NetStats labels on one stack
Final line of the deeper-scroll session: app[call=32 dns=3 connect=3 hit=1 cond=1 miss=30] | doh[call=6 dns=2 connect=2] | image[call=23 dns=4 connect=4]. The three labels app, doh and image are all present in one process, and the image label's dns events resolve through DoH (doh call count rose by the image host's A and AAAA lookups, 4 to 8 in the M4 session). Sharing of the ConnectionPool instance itself is proven by the unit test apiClients_shareOneConnectionPoolAndOneDns, not by counters.

## M3 KinoPub HTTP cache (AC-5)
Cache kept (baseline verdict KEEP). On the final build app hit=1 in every run and cond=1 in 4 of 9 runs, so the cache still serves. Drop branch tests and M6 do not apply.

## M4 domain switch (AC-3, device part)
Steps: Settings, Network and device, Change KinoPub mirror. Reset (was already service-kp.com): no new DoH call, Home loaded. Save api.alador.space: first requests logged two doh.callStart (A and AAAA) for the new host, doh call 4 to 6, Home loaded. Reset back to service-kp.com about 3.5 min after that host was first resolved in the process: two more doh.callStart (doh call 8 to 10) before the first requests, Home loaded. Final state: default domain restored.
Reading: consistent with the evict (a host resolved earlier was looked up again right after the switch), but it cannot tell an evict from a record TTL that expired, so the proof of AC-3 is the unit tests. Status: PASS for Home on each domain, evict itself NOT proven on device.

## M5 perf (AC-7), same session as baseline.md
Session: force-stop, launch, 12 s Home, RIGHT/DOWN x2/RIGHT, CENTER, BACK, LEFT x3, DOWN x4, CENTER, DOWN x5. Warm-up run uncounted.

| run | doh.call | image.dns | resolver | connect app/doh/image | connect sum | app hit/cond/miss |
|---|---|---|---|---|---|---|
| warm-up | 2 | 0 | 2 | 3/2/0 | 5 | 1/0/21 |
| 1 | 4 | 0 | 4 | 3/2/0 | 5 | 1/1/12 |
| 2 | 2 | 0 | 2 | 3/2/0 | 5 | 1/0/11 |
| 3 | 4 | 0 | 4 | 3/2/0 | 5 | 1/1/11 |
| extra 4 | 4 | 0 | 4 | 3/2/0 | 5 | 1/1/25 |
| extra 5 | 2 | 0 | 2 | 3/2/0 | 5 | 1/0/11 |
| extra 6 | 4 | 0 | 4 | 3/2/0 | 5 | 1/1/12 |

Medians of the 3 counted runs: resolver queries 4 (before 3), new connections 5 (before 5). Over 6 runs: resolver median 4, range 2-4 (before range 2-4); connections 5 in every run (before 5-6).
Image fetches never reached the network after the warm-up (Coil disk cache warm), so the image label is absent in these sessions.
Each DoH host costs two doh calls (A and AAAA); a run that touches one extra host shows 4 instead of 2. Before the change the same hosts cost 1 system lookup each on the image label, so the Q4 sum is not like for like.
Result in the unit of round 1 (doh.call + image.dns): resolver 4 vs 3. Superseded by the conductor decision below.

## Secondary: am start -W TotalTime
Counted runs 2427, 1898, 1971 ms (median 1971, before 1845, +6.8%). Later runs 3172, 3370, 4846 and a 5-run batch 5917, 5393, 4421, 4854, 3895 ms under load average about 23: not comparable. Status: NOT CHECKED reliably.

## Mistake note
During navigation the app reached a player once (series Details, episode 8 resume, buffering at 1:10, a few seconds of playback). It was left with BACK. Watch progress for that title may have been written by the app.

## M5 addendum: AC-7 re-measured in host lookups (conductor decision)

Unit: host lookups = dnsStart summed over the app and image labels. doh.call is kept as a secondary number; doh's own dnsStart (2 in every run, the bootstrap lookup of dns.google) is listed separately. The baseline.md table has no app.dns, so both builds were measured again in one sitting with the same session and method: install, one uncounted warm-up, 3 counted runs.
Before: task-1 commit dd19f70d built from a git archive in a temp directory (APK sha256 6dea4345b477ad1a07abfb3243f4b78bd0868c64be2a9024530afd3c31dfe2f7, matches on-device base.apk; differs from baseline.md's hash only because of the build path). After: head faa99d8e (sha256 78ff16da...49d74, matches on device). Both Room v4, same version code 2090000000, install outcome installed / compatible_replace, no downgrade.

| build | run | app.dns | image.dns | host lookups | doh.call (secondary) | doh.dns (bootstrap) | connect app+doh+image |
|---|---|---|---|---|---|---|---|
| dd19f70d | warm-up | 3 | 0 | 3 | 4 | 2 | 5 |
| dd19f70d | 1 | 4 | 0 | 4 | 4 | 2 | 6 |
| dd19f70d | 2 | 3 | 0 | 3 | 2 | 2 | 5 |
| dd19f70d | 3 | 3 | 0 | 3 | 4 | 2 | 5 |
| faa99d8e | warm-up | 3 | 0 | 3 | 2 | 2 | 5 |
| faa99d8e | 1 | 3 | 0 | 3 | 4 | 2 | 5 |
| faa99d8e | 2 | 3 | 0 | 3 | 2 | 2 | 5 |
| faa99d8e | 3 | 3 | 0 | 3 | 4 | 2 | 5 |

Medians of 3 counted runs, before to after: host lookups 3 to 3, new connections 5 to 5, doh.call 4 to 4 (secondary, identical runs 4, 2, 4). No image events in any run (Coil disk cache warm), so image.dns is 0 on both sides. Threshold after <= before holds for both metrics: AC-7 PASS. The round-1 FAIL came from comparing doh.call (two calls per host) with system lookups (one per host).
No playback started in these runs (media_session shows none).

## Round 2 (head e3a8cdb3): fresh posters, AC-1, AC-2 smoke, AC-4, AC-7 (M5)

Date: 2026-11-21. Builds: after = head e3a8cdb3, dev debug APK sha256 141dc52351566045f585525934a43806e892d467aab4ec09e433cb54ac51590f; before = task-1 commit dd19f70d from a git archive in a temp directory (since removed), APK sha256 6dea4345b477ad1a07abfb3243f4b78bd0868c64be2a9024530afd3c31dfe2f7 (same hash as round 1). Both installed with install-preserve (outcome installed, compatible_replace, destructive_action false), no downgrade, no data clear. Device emulator-5554 (puber_tv_36, booted headless without wipe), lease held and released. The head APK is left installed.
Counts only (last NetStats line of each session); no raw logcat kept. No playback was started; the hub backdrop on Series/Movies shows a trailer preview, not the player, and the debug "player" label never appeared.

### Method
Round-1 runs had no image fetch reaching the network (Coil disk cache warm). Here each counted run uses posters that no earlier run loaded: hubs and Home rows not opened before in this process or the earlier sessions, confirmed by a non-zero image label (image.call > 0) in the run. Session: force-stop, launch, 12 s on Home, then open a hub (LEFT, DOWN to the entry, CENTER, 8 s) and move RIGHT through its row, or scroll Home down and right. No cache clear. Order: three counted runs on dd19f70d, then three on head. Runs that showed no image events (Documentary movies on dd19f70d) are listed and not counted; two head attempts that wandered into Details screens are not counted either.
The hubs are different between sides, since a poster can be fresh only once, so image counts differ; the table lists image.call so the reader can normalise.

### Which clients count under app
dd19f70d: the "app" listener sits on the shared base client, so every client derived from it counts under app: KinoPub API, IntroDb, TheIntroDb, TMDB, the domain probe, diagnostics and the player (none of the last three made calls here). image is Coil's own client with its own listener and system DNS; doh is the DoH bootstrap client.
e3a8cdb3: same, except the player's builder carries the debug "player" label (absent in these runs, no playback) and DoH has its own Dispatcher. image is the Coil client taken from a Koin single, built on the shared client: its lookups resolve through the shared DoH resolver, so they show up in doh.call and its own dns events.

### Counted runs
| build | run (posters) | app.dns | image.dns | host lookups (app+image) | doh.call | Q4 (before: doh.call + image.dns; after: doh.call) | connect app/doh/image | image.call |
|---|---|---|---|---|---|---|---|---|
| dd19f70d | 1 Concerts hub | 3 | 4 | 7 | 2 | 6 | 3/2/4 (9) | 21 |
| dd19f70d | 2 Documentary series hub | 4 | 4 | 8 | 4 | 8 | 4/2/4 (10) | 16 |
| dd19f70d | 3 TV shows hub | 3 | 4 | 7 | 4 | 8 | 3/2/4 (9) | 15 |
| e3a8cdb3 | 1 Movies hub, 25 RIGHT | 4 | 2 | 6 | 7 | 7 | 3/2/2 (7) | 27 |
| e3a8cdb3 | 2 Home scrolled down and right | 3 | 2 | 5 | 6 | 6 | 3/2/2 (7) | 7 |
| e3a8cdb3 | 3 Series hub, 20 RIGHT | 3 | 3 | 6 | 6 | 6 | 3/2/3 (8) | 19 |
Not counted: dd19f70d Documentary movies hub (no image events: posters already cached; app.dns 3, doh.call 4).

Medians of 3, before to after: Q4 8 to 6 (doh.call + image.dns before, doh.call after); host lookups (app.dns + image.dns) 7 to 6; new connections (app+doh+image) 9 to 7. After <= before on all three; AC-7 PASS with image fetches reaching the network. The after side fetched 7 to 27 images against 15 to 21 before, so the comparison is not matched on image count; in the run with the most images after (27) the lookups were still 6 against 7 to 8 before. doh.dns (bootstrap lookup of dns.google) is 2 in every run on both sides.

### AC-1 smoke
One process on head shows the three labels app, doh and image together; image.dns events resolve through DoH: in the Series run image.dns 3 plus app.dns 3 give 6 lookups and doh.call is 6 (two per resolved host), the doh label rising with the image hosts. Pool sharing itself stays proven by the unit test apiClients_shareOneConnectionPoolAndOneDns, not by counters. Status: PASS.

### AC-2 smoke
In the Movies run 6 host lookups (4 app, 2 image) cost 7 doh calls; with no cache each lookup would cost two (A and AAAA), i.e. 12. Repeated lookups of a host within the TTL did not reach the DoH server. Status: PASS (smoke; the TTL/evict behaviour is proven by DnsOverHttpsFactoryTest).

### AC-4 posters load
On head posters, backdrops and hub grids rendered in every run (screenshots looked at: Series, Movies, Home Collections and Trending rows with posters that were fetched in the run, image.call 7 to 27). Status: PASS.

### Not shown on device
Failed DNS lookups no longer cached (no resolver failure was provoked), the DoH Dispatcher isolation and the Koin-single Coil client are covered by the unit tests, not by these counters.


## Round 3 (head 04bb948c, integration after the rebase onto master 7bcbfd83)

Date: 2026-11-21. Dev debug APK sha256 40133f054bbae370a04ccd95114f28512b402a4c9b1e24fb610d5af2f2e2cd40 (on-device base.apk sha256 matches; assembleDevDebug UP-TO-DATE at the head). Installed with tools/android-apk-install-preserve: outcome installed, classification compatible_replace, destructive_action false, same signer, no downgrade, no data clear. Device emulator-5554 (puber_tv_36, already running), lease acquired after waiting for another agent and released at the end. Host load average about 14. Counts and screenshots only; no raw logcat kept (logcat was only counted for FATAL EXCEPTION, NoDefinitionFoundException and ANR: 0 each, three checks).

### M1 launch (R2, R15)
Steps: am force-stop, am start, screenshots at 1, 3, 6 and 12 s. Observed: splash (KINOPUB logo) at 1 and 3 s, then Home with hero, Continue watching and My bookmarks rows with posters at 6 s and 12 s; logged in; no crash, no Koin error. Status: PASS.

### Startup splash reveal from master (smoke)
Telemetry startup events of five cold starts (force-stop, start): revealed 4337, 3981, 4258, 4123, 4329 ms after process start; first_content 4343, 3644, 4255, 4122, 4057; app_composed 2.1 to 2.6 s. Screenshot polling (3 cold starts, one more with timestamps): launcher, then splash for about 4 s, then Home with posters, every time; no run hung past the reveal. These numbers are slower than the 2.0 to 2.7 s medians in the startup-splash-preload evidence because the host load here was about 14 (that evidence reports 4.6 s revealed under load as well); reveal here follows first_content, which is bound by the network and the loaded host. This is a smoke, not a re-measurement of the splash target. Status: PASS (reveals Home with posters, no hang).

### M2 posters on Home and Details (AC-4)
Steps: Home, RIGHT, DOWN x2, RIGHT x2 (focus on a My bookmarks card), CENTER, wait 5 s, BACK. Observed: every Home row card and the hero backdrop show an image; the series Details screen shows backdrop and episode stills; no blank placeholders; focus stayed on the card after BACK. NetStats line at this point: app[call=11 dns=2 connect=2 hit=1 cond=1 miss=9] | doh[call=7 dns=2 connect=2] | image[call=9 dns=2 connect=1]. The image label is present, so Coil fetches through the shared stack (its host lookups go through DoH). Status: PASS.

### M4 style domain switch (AC-3 device part)
Steps: Settings, Network and device, Change KinoPub mirror, typed api.alador.space, Save; Home; Settings again, mirror dialog, Reset; Home. Observed: after Save the setting shows api.alador.space and Home loads with rows and posters (NetStats app[call=18 ...] | doh[call=6] | image[call=25]); after Reset the setting shows service-kp.com and Home loads (app[call=32 ...] | doh[call=8 dns=2 connect=2] | image[call=27]). doh.call rose by 2 (one A and one AAAA lookup) across the Reset, for a host resolved earlier in the process: consistent with the evict, and as in round 1 it cannot tell an evict from an expired TTL, so the proof of the evict is the unit tests. Final state: default domain service-kp.com. Status: PASS for Home on each domain.

### Static gate
check.full at 04bb948c: BUILD SUCCESSFUL, 2236 tests, 0 skipped, 0 failures, 0 errors (results read from build/test-results; test and detekt tasks UP-TO-DATE from the implementer's run at this head, lintDevDebug re-executed). The suite includes the AC-1 and AC-3 tests (ApiModuleHttpStackTest, ApiDomainInteractorTest, ApiDomainInteractorWiringTest) that R15 asks to re-run after the rebase.

## Round 3b: startup reveal, master 7bcbfd83 against head 04bb948c (perf A/B)

Date: 2026-11-21. Question: does this branch slow the startup reveal compared with master? Builds: master 7bcbfd83 from a git archive in a temp directory (since removed), dev debug APK sha256 6b08476dde337ec456de201ce30a7b0ad1560359b5a30b4927223d425c9c572a; head 04bb948c, dev debug APK sha256 40133f054bbae370a04ccd95114f28512b402a4c9b1e24fb610d5af2f2e2cd40 (the evidence commit on top changes no source). Both Room v4, versionCode 2090000000, same signer. Device emulator-5554 (puber_tv_36), one lease for the whole sitting, released at the end. No playback, no uninstall, no data clear, no downgrade.

Method: interleaved master, head, master, head; before every start the build under test was installed with tools/android-apk-install-preserve (every call: outcome installed, classification compatible_replace; two calls hit an apkanalyzer 60 s timeout on the loaded host and were repeated, see below). Each start: am force-stop, logcat -c, am start of MainActivity, 22 s wait, then the `startup` telemetry line of the newest session file (revealed, first_content, app_composed; ms after process start) and the last NetStats line. Host load average (1 min) read just before each start. Counts and numbers only; no raw logcat kept (logcat was also counted for FATAL EXCEPTION and ANR: 0 on every start).

Interruption: the emulator process died twice during the sitting (host load 70 to 77 from other agents; once after my first loop was cut, once with a segfault in the emulator, exit 139). I cold-booted it again without wipe (same AVD, app data kept, still logged in) after the load dropped below 20, re-ran a warm-up pair, and continued. So the data is two segments: A (round 0 warm-up, counted rounds 1 and 2, before the crash) and B (after the reboot: warm-up pair, then counted rounds 11 to 14). One head install at round 2 failed on the apkanalyzer timeout (60 s) and was repeated after the reboot; it is not a device install failure.

| segment | build | warm-up (revealed ms, load) | counted revealed ms | load at start |
|---|---|---|---|---|
| A | master | 4477 (15.5) | 5180, 5131 | 15.6, 16.8 |
| A | head | 4143 (19.7) | 3951 | 15.3 |
| B | master | 7343 (18.8) | 3914, 4830, 4449, 3639 | 15.3, 10.4, 6.2, 8.0 |
| B | head | 3779 (18.1) | 4499, 3922, 3714, 3849 | 15.7, 8.9, 6.3, 7.0 |

| metric (counted starts) | master (n=6) | head (n=5) |
|---|---|---|
| revealed median | 4640 ms | 3922 ms |
| revealed range | 3639 to 5180 | 3714 to 4499 |
| first_content median | 4079 ms (range 3119 to 10954) | 3711 ms (range 3213 to 4840) |
| app_composed median | 2327 ms | 2040 ms |
| revealed, pairs of the same round (master vs head) | 5180/3951, 3914/4499, 4830/3922, 4449/3714, 3639/3849 | head lower in 4 of 5 pairs, higher in 1 |

Reading: the head medians are lower than master's (revealed 3922 against 4640 ms), but the ranges overlap (head 3714 to 4499 lies inside master 3639 to 5180) and the master starts at the higher host load (about 16) are its slowest, so the gap is within the spread and part of it is load. The test does not show a slowdown: no head start was slower than master's range, and the head's worst reveal (4499) is below master's median. It also does not prove a speed-up. Status: no regression seen; difference NOT outside the spread.

Image fetches on the network (NetStats image label): master has no NetStats listener at all (the logcat tag is empty on every master start; the label does not exist before this task), so master's image traffic cannot be counted. On the head the image label was absent from the NetStats lines on all 5 counted starts and on 1 of 2 warm-ups: Home posters came from Coil's disk cache and no image request went over the network. The one head warm-up in segment A showed image[call=1 dns=1 connect=1 hit=0 cond=0 miss=0], one fetched image. So image fetches were not what separated the head starts.

Lease: acquired at the start (wait 600 s, default TTL); by the time segment B began (about 20 minutes later) the lease had probably expired, since the final release was refused with a token mismatch and the lock was already held by another agent, created about 6 s after my last start ended. No other agent's lock was observed during segment B (status showed mine at 1150 s of age just before the reboot), so no overlap with another leasing agent is known, but strictly segment B ran on an expired lease and I did not renew it. Nothing of mine was left to release.

Cleanup: the master archive and its APK copy were removed; the head APK (04bb948c) is the one installed on the device after the last start, and the emulator is left running.
