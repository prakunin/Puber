# Gate - release-startup-compilation - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** fbe81235   **Base:** e4c3b5aa

## Done as asked

`git diff e4c3b5aa...HEAD` matches the spec (`docs/superpowers/specs/2026-11-23-release-startup-compilation.md`, AC-1..AC-8 and spec deltas 4-7):

- the generator has rail, Details and player journeys and checks the pairing;
- generation goes through the `make baseline-profile` private adb server with emulator, API 34+ and one-serial refusals; the token seeding, cache and exported receiver are gone;
- both profiles are regenerated, with every method rule `H` (fix round 1);
- the `startup` line carries `prof` from `ProfileVerifier` (`ProfileStatusSource.kt`, `StartupMarks.kt`, `TelemetryRuntime.kt:265`);
- `docs/release.md` has the refresh rule and one command;
- `BaselineProfileContentTest` guards the profile content.

Nothing from Out of scope is in the diff: R8 rules, coil-video/appcompat and `AppUpdateInstaller` are untouched. The one-line `tools/android-apk-install-preserve` regex change is a recorded plan delta (state.md, 2026-11-23).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | M4 | `baseline.md` S0/S2/S1, 15 starts each, same day; S1 p95 941 ms | PASS |
| AC-2 | M1, M8 | qa: NOT CHECKED; implementer record only (plan, Task 5b and phase B). The current Makefile strip/restore recipe (65d7e35a) was never run end to end; run 5 used a manual swap | NOT CHECKED (manual, section below) |
| AC-3 | `BaselineProfileContentTest` (4 tests) | re-grepped at fbe81235: 0 method rules without `H`; details 432, player 1102, PlayerVM 131, DetailsScreen 298, TelemetryRuntime 32, RevealGate 20, favorites 109, history 317 lines; 0 hits for the 4 stale prefixes; startup-prof has no details/player rule | PASS |
| AC-4 | M5 | evidence Round 4: 45 S1 starts, p95 90 ms (rank 43), gate 199. Counted over 45 starts under the owner's rule (state.md, 2026-11-23) | PASS (accepted risk, see Concerns) |
| AC-5 | `TelemetryRuntimeTest`, `StartupMarksTest`, `TelemetryEncoderTest` rows; M6 | check.full green (below); box: `prof` on every `startup` line in rounds 3-4 | PASS |
| AC-6 | M2 | judge rebuild at fbe81235: `:app:assembleProdRelease` BUILD SUCCESSFUL, `app-prod-release.apk` lists `assets/dexopt/baseline.prof` 29425 B and `baseline.profm` 1646 B; devBenchmarkRelease 29426/1646 B (qa round 3) | PASS |
| AC-7 | M3 | `docs/release.md:99` refresh rule, `:108` single command, `:119` account write; `rg 'connected.*AndroidTest' docs/release.md` empty | PASS |
| AC-8 | M7 | evidence round 3: `prof` 2 on first start, `bg-dexopt-job` `[reason=bg-dexopt]`, next start `prof` 1 | PASS |

check.full: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at fbe81235 gave EXIT 0. The unit, detekt and lint tasks were up to date from the green run at 2d1c7395, and only an evidence doc changed after it.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Rail journey lost its place in Search | edge-1 F1 (major) | fixed | cdc7d97e, regenerated 540c4ee4; favorites/history/contentlist rules present |
| Playback writes a watch position, undocumented | blind-1 F1 (major) | fixed (docs; behaviour accepted by the owner) | 171382fe; `docs/release.md:119`, generator KDoc |
| triage-1 #3-#5 (preflight order, reachability, trap) | codex, blind, edge | fixed | 171382fe |
| triage-1 #6-#8 (token presence, profile bound, executor test) | blind, tests | fixed (#6 did not hold, see triage-2 #1) | dba016e9 |
| triage-1 #9-#10 (test-design rows) | tests-1 | fixed in the run folder | `test-design.md:42-43` |
| triage-2 #1-#5 (presence check, rail check, backup/HUP, offline preflight, 5038 kill) | edge-2 | deferred | new run `baseline-generation-hardening`; its state.md lists all five |
| triage-2 #6-#8 (two test-design rows, assert message) | tests-2 | accepted minor | bookkeeping/readability only |
| Codex: separate authorization for a physical TV | codex-1 | rejected; I accept the reason | AGENTS.md gives standing authorization for the box; the real defect, the ordering, was fixed as #3, and the script now refuses non-emulators before any install |
| Codex: adversarial telemetry-scope remark | codex-1 | rejected; I accept the reason | it is an observation with no defect scenario |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | S1 first-key p95 went from 941 to 90 ms (45 starts). Costs: the forced or bg-dexopt compile grows from 4.3 s to about 16 s (`Compiler stats` 4320 -> 16289), `baseline.prof` from 21 to 29 KB, and compiled code size grows; odex size was not measured. The `startup` line now waits for `prof` up to reveal + 7 s (`StartupMarks.kt:159`); this is telemetry only |
| Reliability | `ProfileVerifierStatusSource` catches `ExecutionException`. With no status, the 7 s bound still writes the line (`boundEmitsWithoutProfileStatusWhenAwaited`). Recording off never queries the verifier (`offStartNeverAsksProfileStatus`). The generation route's deferred gaps are in `baseline-generation-hardening` |
| Maintainability | The dead receiver and its manifest entries were removed. The `configurations.configureEach` hook limits generation to `devReleaseBaselineProfile` (`app/build.gradle.kts:84-88`). `PlayerScreen` gains a production `testTagsAsResourceId` modifier for the generator |
| Security / privacy | The diff holds no secrets; the worktree evidence audit passed (82 files). The run folder fails the audit: `generation-run4-auth-logcat.txt` is a raw logcat (`unscoped_logcat_content`), and `e2-s1-ref.txt` (4.3 MB profile dump) sits next to it. Neither is gitignored, and run folders are committed by "Record the ... run". The exported token provider in nonMinifiedRelease existed at base; it is followed up as `baseline-auth-provider-presence` |

## Manual checks for the owner

1. **AC-2, generation through the committed recipe.** It needs the Mac-host emulator, your pairing and the live account, and writes up to 15 watch positions.
   1. `ssh mac-emu start`, then `adb connect host.docker.internal:5555`, and take the emulator lease.
   2. In the worktree, run `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 PUBER_CLIENT_SECRET=... PUBER_API_DOMAIN=... TMDB_READ_ACCESS_TOKEN=... ./tools/generate-baseline-profile.sh`, with the values from the main `.env` set in that command's environment only. Pair when it asks.
   3. Expect:
      - the line after `baseline profile run sees:` lists only `host.docker.internal:5555`;
      - exit 0 and "Pairing on host.docker.internal:5555 is intact";
      - `git diff --stat` lists both profile files and no `baseline-prof.txt.bak` is left;
      - `grep -- '->' app/src/main/generated/baselineProfiles/baseline-prof.txt | grep -vc '^H'` prints 0.
   4. `git checkout` the two profile files unless you want to keep the new ones, then release the lease and run `ssh mac-emu stop`.

## Must fix

none

## Concerns for the owner

1. **AC-4 passes only under the 45-start rule.** It was set after the first 15 starts failed (p95 215). Two of 45 starts are over 199 ms (215 and 292, both reveal cause `cap`); a third would put rank 43 over the gate. 42 of 45 values are the 50 ms floor.
2. **Compile cost grows from about 4.3 s to 16 s on the box** with the all-`H` profile, on a forced compile and on the bg-dexopt job. The odex size was not measured.
3. **AC-2 rests on the implementer's record.** The final Makefile strip, generate and restore path (65d7e35a) has only a dry run on a copy; the one successful regeneration used a manual swap. It is carried as the manual check above.
4. **45d5c3d5 carries a `Co-Authored-By` line**, against `commit_style`. Remove it in the pre-ship squash.
5. **The run folder holds a raw logcat** (`generation-run4-auth-logcat.txt`, fails `tools/mobile-evidence-audit.sh`) and a 4.3 MB dump (`e2-s1-ref.txt`). Drop or gitignore both before the run folder is recorded.
6. **Player BACK count differs between rounds** (M7: 3 presses to leave the player in rounds 1 and 3, 1 press in round 2), never compared with the base. The diff does not touch player input; this is a candidate idea run.
