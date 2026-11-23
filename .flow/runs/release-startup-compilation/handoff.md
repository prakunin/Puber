# Handoff: release-startup-compilation (2026-11-23)

Stopped by the owner to move development to a more powerful machine (Mac mini). This file is the complete restart point; state.md, brief.md, research.md, design-architect.md, test-design.md, baseline.md and readiness-*.md in this folder hold the detail.

## Where the run is

- Stage: impl, Task 5 of 5 (regenerate both profiles) not done. Tasks 1-4 committed.
- Branch: `prakunin/release-startup-compilation` (local only, not pushed), base `e4c3b5aa`. Orca worktree on the old host: `/home/user1/orca/workspaces/Puber/release-startup-compilation`.
- Spec: `docs/superpowers/specs/2026-11-23-release-startup-compilation.md` (approved, gate 1). Plan: `docs/superpowers/plans/2026-11-23-release-startup-compilation.md` (READY, readiness-2).
- Commits on the branch (oldest last):
```
a0d21716 Add the baseline profile content test that fails until the profiles are regenerated
0945400f Seed tokens only into an empty app and log auth events of generation runs outside git
3c994639 Keep tokens out of the generation log, wait for the device, and leave the emulator paired after a run
6c88cb2a Seed the baseline profile journeys with the tokens the app holds now, not the rotated-out ones
ea78e231 Say when to refresh baseline profiles and the one command that does it
3711812a Generate baseline profiles for the dev flavor on one named device through a make target
dbb2d28b Drive the baseline profile journeys through the side rail, details and the player
64fd004e Record the profile compilation status on the telemetry startup line
f3eada5d Record plan readiness for release-startup-compilation
35c38efa Apply the readiness-1 fixes to the release-startup-compilation spec
3788b2af Add the release-startup-compilation plan and apply the no-reset spec delta
cbcc7dba Add the release-startup-compilation spec
```
- No Co-Authored-By lines. check.full not run yet. No review, codex or judge yet.

## Results so far

Baseline on the box 192.168.1.110:5555, base build devBenchmarkRelease, 15 cold starts per state, first key median / p95 (baseline.md):

| State | Median | p95 |
|---|---|---|
| S0 as installed | 2578 ms | 3105 ms |
| S1 old profile only, compiled before launch | 520 ms | 941 ms |
| S2 compiled after use (JIT samples) | <= 50 ms | 120 ms |

AC-1 PASS. The old profile covers little of the startup path; the target is S1 p95 <= 199 ms with the new profile (AC-4).

Facts learned:
- `cmd package compile --reset` is refused for shell on the box (API 29). A reinstall with `tools/android-apk-install-preserve` empties cur and ref profiles (check with `cmd package dump-profiles com.kino.puber.stage`); S1 = reinstall, ProfileInstaller INSTALL_PROFILE broadcast, force-stop, `compile -m speed-profile -f`, then launch.
- install-preserve needs `ANDROID_HOME=~/Android/Sdk` (apkanalyzer).
- qa subagents refuse relayed owner consent: the conductor runs compile-state, bg-dexopt and other authorised device commands itself.

## Why generation is blocked

Four generation runs on emulator-5554 (API 36 TV AVD `puber_tv_36`) failed:
1. androidx.benchmark 1.5.0 `CompilationMode.resetAndCompile` on a non-rooted device runs `pm uninstall` + reinstall before every journey (implementer, from library bytecode). App data and the AndroidKeyStore key go with it, so only generator-seeded tokens survive. The emulator image is a production build: `adb root` is refused.
2. In run 4 the auth event log (`generation-run4-auth-logcat.txt` in this folder, long strings redacted) shows: after a reinstall, seeded tokens of length 1118 and 3304 while real KinoPub tokens are 32 chars; then 401 and `Refresh token failed with HTTP 400`, then logout. Leading hypothesis (unverified): the auth provider exports the *sealed* (encrypted) token values, which the reinstalled app cannot use with its new keystore key. Next step: export and seed plaintext tokens (nonMinifiedRelease-only provider), or generate on a rooted image (`google_apis` userdebug AVD, `adb root` works) so the library skips the reinstall.
3. The old host ran out of memory (21/23 GB, swap full): qemu crashed once with exit 139.
4. Fixed: the make recipe printed tokens to stdout (now `@`-silent, 3c994639).

Each failed run costs the owner a re-pair of the emulator (device code at https://kino.watch/device).

## Owner decisions in force

Q1-Q5, Q-A1/2, Q-D1..3 and the box/emulator authorisations are in state.md Decisions. Standing: answers per recommendation, codex required on every round, round caps lifted, device evidence on the box only, never push or tag.

## Left to do

1. Fix seeding (item 2 above) or use a rooted generation AVD; re-pair; run `make baseline-profile` through `tools/generate-baseline-profile.sh` (DEVICE=<emulator serial>; secrets from the main checkout's .env passed in the command env only).
2. Implementer finishes Task 5: BaselineProfileContentTest green, M2 (`assembleProdRelease` lists assets/dexopt/baseline.prof and .profm), check.full.
3. qa-device verify on the box: new build S0/S1 (AC-4), telemetry `prof` (AC-5), update path over the base build + `bg-dexopt-job` (AC-8, conductor runs it).
4. Review (blind, edge, tests, codex, state-path if it applies), judge, gate 2, squash to <5 commits, ship.

## Continue on the new machine

See also `.flow/handoff/2026-11-23-README.md` (both stopped runs, one carry-over recipe).


The branch exists only on the old host. Either push it from the old host (owner's call):
```
git -C /home/user1/orca/workspaces/Puber/release-startup-compilation push -u origin prakunin/release-startup-compilation
```
or copy the bundle `.todo/release-startup-compilation.bundle` (base e4c3b5aa must be in the target clone) and run `git fetch <path-to>/release-startup-compilation.bundle prakunin/release-startup-compilation:prakunin/release-startup-compilation`.
The `.flow/runs/` folders are uncommitted in the main checkout: commit them or copy `.flow/` across. Then, in the main checkout on the new machine, create the Orca worktree for the branch and run:
```
/task:run release-startup-compilation
```
