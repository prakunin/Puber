# gradle-build-cache device-free evidence (round 1)

Build: 47efa4dc. No device needed: build configuration and AGENTS.md only.

| Check | Outcome |
|---|---|
| check.full (warm, UP-TO-DATE: 117 tasks, 3 executed) plus both tooling scripts | exit 0 |
| AC-2 copy of HEAD at another path (git archive, not a worktree), same shared GRADLE_USER_HOME | :app:kspDevDebugKotlin FROM-CACHE, :app:compileDevDebugKotlin FROM-CACHE, 12 tasks: 2 executed, 10 from cache |
| AC-5 lintDevDebug loaded from cache in the copy | lintAnalyze* FROM-CACHE; reports contain the copy's own path, the original worktree path is absent |
| AC-4 update_file_paths.xml edit | testDevDebugUnitTest executed; revert FROM-CACHE |
| AC-4 generated baselineProfiles file edit | executed; revert FROM-CACHE |
| AC-4 src/main/java/.../PuberApp.kt edit | executed; revert FROM-CACHE |
| Reverts | git status clean after all edits |

Same-name worktree player-dpad-seek-coalesce was not usable: its outputs were already built (UP-TO-DATE), and cleaning it would disturb another run.

## Review round 1 fixes

Test input is now `proguard-rules.pro` plus the whole `app/src/main` directory (covers `values/player.xml`, `values-en/strings.xml`, res, java, generated baseline profiles).

| Check (`:app:testDevDebugUnitTest`) | Edit | Revert |
|---|---|---|
| strings.xml | executed | FROM-CACHE |
| values/player.xml | executed | FROM-CACHE |
| update_file_paths.xml | executed | FROM-CACHE |
| generated baselineProfiles | executed | FROM-CACHE |
| proguard-rules.pro | executed | FROM-CACHE |

| Check | Outcome |
|---|---|
| check.full after `clean` (cache warm from earlier runs) | gradle exit 0, 117 actionable tasks: 42 executed, 68 from cache, 7 up-to-date; both tooling scripts exit 0 |
| Warm `testDevDebugUnitTest` before the edits | one run failed once, the immediate rerun passed with no input change (known flake, `unit-suite-flaky`) |

## Judge round 1 fix

A grep of every JVM test source set (`app/src/test`, `app/src/testInstrumentation`, `player-test-fixtures/src/test`; `:baselineprofile` has none) for `File(`, `Paths.get`, `Path.of`, `readText`, `src/`, `assets/` found two more project-file reads, both in `app/src/testInstrumentation`. They are declared as Test inputs with relative path sensitivity: `app/src/instrumentation` and `baselineprofile/src/main/assets/baseline`. The other `File(` hits are fake paths, temp dirs or classpath resources.

Red run before the declaration (`:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`, cache on, task warm):

| File edited | Result after edit |
|---|---|
| `app/src/instrumentation/res/xml/network_security_config.xml` | UP-TO-DATE (stale) |
| `baselineprofile/src/main/assets/baseline/ok.json` | UP-TO-DATE (stale) |

After the declaration:

| File | Edit | Revert |
|---|---|---|
| `app/src/instrumentation/res/xml/network_security_config.xml` | executed | FROM-CACHE |
| `baselineprofile/src/main/assets/baseline/ok.json` | executed | FROM-CACHE |

Probe edits reverted, git status clean.
