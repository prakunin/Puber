# Readiness 3: release-startup-compilation

Verdict: **NOT READY**

Scope: rewritten Task 5 (5a, 5b), "Device verification after Task 5", "Spec deltas for the conductor" at wt 8d95e4f1 (`git show 8d95e4f1`, plan only). Line numbers checked at a0d21716 (8d95e4f1 touches only the plan).

## Fix list

| # | Where | Fix |
|---|---|---|
| 1 | Task 5a, the `rg -n 'seedAuthState\|...\|BaselineProfileAuthReceiver\|...' ... app/src ...` check | It cannot print nothing at 5a: `app/src/main/generated/baselineProfiles/baseline-prof.txt:14154-14168` and `startup-prof.txt:13952-13966` hold `BaselineProfileAuthReceiver` rules until 5b regenerates them. Exclude `app/src/main/generated` in 5a (for example `-g '!app/src/main/generated/**'`), and in 5b add that the same `rg` over the generated files prints nothing |
| 2 | Spec delta 4 ("The AC-2 wording waits on the owner (state.md Open questions, AC-2 serial)") and the Task 5 route paragraph ("state.md Open questions, AVD userdata") | Both are answered: state.md Decisions 2026-11-23 rows "AC-2 is read with DEVICE=host.docker.internal:5555" and "pairing and generation run in one emulator session"; the Open questions table is empty. Delta 4 must tell the conductor to apply the owner's AC-2 reading to AC-2 and M1/M3 wording, citing the Decision, and both references must point to Decisions |

## Checklist

| Check | Result |
|---|---|
| AC coverage of Task 5 | AC-2: 5b M1 steps 1-7 (Test design M1, P0 guard) and M8 (P1); AC-3: `BaselineProfileContentTest` green in 5b (P0 red rows, red run recorded at a0d21716); AC-6: 5b M2 and full gate (M2, P0 guard). AC-1/4/5/8 box steps: Device verification table, unchanged in substance |
| Order and build | 5a has no device step and leaves the compile green; the only red test is `BaselineProfileContentTest`, committed red at a0d21716 and turned green by 5b. 5b consumes 5a's pairing check and refusals |
| P0 rows | unchanged; each names its defect, `red` or `guard`, and its test or M-check |
| Out of scope | held: no R8, `coil-video`/`appcompat` or `.dm` work. Receiver deletion is recorded as spec delta 6 |
| Blocking questions | none open (state.md Decisions 2026-11-23: AC-2 serial, playback CUJ, one emulator session) |
| Tasks 1-4 | not contradicted; Task 3's export/cache path is replaced and recorded as deltas 5-6 |

## Citations sampled

| Citation | Resolves |
|---|---|
| benchmark-macro 1.5.0 `CompilationMode.kt:86-109` | yes: API >= 34 runs `cmdPackageCompile(..., "verify")` + `pm art clear-app-profiles`; `reinstallPackage` only in the pre-34 non-rooted branch (:105-108). No other uninstall or `pm clear` path in the jar's sources |
| run-4 log `:9-11,287-289,416-418`; `:20,136,180,224,268,378,428`; `:31-39` | yes: "Resetting profiles", no "Reinstall" line in 438 lines; seeded lengths 34, 42, 68, 148, 390, 1118, 3304; HTTP 400 then Unauthorized |
| `BaselineProfileGenerator.kt:157-188,190-201,203-215,217-244,246-256,260,267-278,3-4`; calls `:34,47,90,130` and `:40,83,123,154` | yes, every span and call |
| `CryptoPreferenceRepository.kt:41,47,63-67`; `AuthEventLog.kt:28`; `BaselineProfileAuthProvider.kt:29-33` | yes: clear writes `encrypt("")`, log tests `contains`, provider skips blank values |
| `KtorPlugins.kt:22,53`; `app/build.gradle.kts:37-41,58,438-439` | yes: `BuildConfig.CLIENT_SECRET` from `envOrNull("PUBER_CLIENT_SECRET")`; comment names the receiver |
| `tools/generate-baseline-profile.sh:11,15,18-23,25-35,37-50,52-92,94-130,110-114,121,132-174,163,167-172,176-177` | yes |
| `Makefile:139-156` (comment `:139-140`, wait `:145-150`, "sees:" `:151`, comment `:152`) | yes |
| `{dev,prod}NonMinifiedRelease/AndroidManifest.xml:5-12`; `docs/release.md:103-124,110,112-121` | yes |
| `tools/emulator-resource-lock.sh acquire <resource>`, `status`; `ssh mac-emu` | yes: usage lines 20, 25; lock dir `$HOME/.puber/device-locks` shared by main and worktree; `Host mac-emu` in `~/.ssh/config` |

## Non-blocking

- Make refusal (3) relies on `ro.boot.qemu`/`ro.kernel.qemu` reading `1` on the Mac arm64 AVD; not verified here. A miss blocks generation and does not touch data.
- The script refuses an empty `PUBER_CLIENT_SECRET` even in the main checkout, where `envOrNull` would also read `.env`; `docs/release.md` tells the user to pass it.
