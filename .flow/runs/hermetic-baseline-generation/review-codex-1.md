# Review codex, round 1

CODEX: ran: review, adversarial

CONFIRMED:
- [minor] docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-device.md:47-50 - AC-9 asks for a worktree dev APK with the regenerated profile embedded, installed and launched on the box; the evidence launched a dev debug APK, which does not embed the profile. The shipped-profile effect on launch is unobserved (disclosed). Either run a dev release APK carrying assets/dexopt/baseline.prof (signer-compatible, via install-preserve), or have the owner waive AC-9 as written.

REJECTED:
- P2: API 34 guard missing from BaselineProfileGenerator - deliberate D17; the script and Makefile still refuse below API 34 before any install; a direct Gradle run could uninstall only the throwaway instrumentation package.
- P2 + adversarial medium: JourneyCheck accepts result=-1 anywhere in the shell output - needs a non-loopback URL whose path contains result=-1; nothing in the app does that; fails closed on broadcast error or missing receiver. Unproven (parsing the field would be nicer hardening).
- P2: a missing seg*.ts counted as a route hit before the asset read throws - playlist is a committed fixture listing seg0..seg5, all present. Unproven.
- Adversarial high: the ProxySelector blocker misses direct sockets, explicit proxies, native clients - no proxy( override; OkHttp, Ktor, Coil, Media3, DoH all use the JVM selector; network security config forbids cleartext and trusts only the mock CA. Unproven.
- Adversarial medium: a violation after the last verify lost to the next journal reset or teardown - journal only detects, the dead proxy stops egress; no such late path named. Unproven.
- Committed key material - only leaf-test-only.pem (test leaf key; root trusted solely in app/src/instrumentation/res/raw); no CA private key; dev/prod release APKs 0 matches for instrumentation classes; R8 mapping 0 lines for com.kino.puber.profile / BaselineProfileApp; profiles 0 profile/ rules. app/debug.jks pre-existing.
- Known drift D14 and D7 - no objection; consistent with evidence; neither reaches a shipped artifact.

VERDICT: changes needed (the AC-9 minor only; a documented owner waiver would make it clean)
