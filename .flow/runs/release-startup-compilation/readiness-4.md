# Readiness 4: release-startup-compilation

Verdict: **READY**

Scope: `git show 43453de1` (plan only) against `readiness-3.md` Fix list and non-blocking notes.

## Readiness-3 items

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | 5a `rg` check cannot pass while the old profiles hold the receiver | done | 5a check now has `-g '!app/src/main/generated/**'`; run at 43453de1 it prints only non-generated hits (manifests, generator `:267`, receiver), which 5a deletes. 5b adds `rg -n BaselineProfileAuthReceiver app/src/main/generated` prints nothing |
| 2 | Delta 4 and the route paragraph cite empty Open questions | done | both now cite state.md Decisions 2026-11-23; delta 4 tells the conductor to put `host.docker.internal:5555` into AC-2, M1, M3, the Files table row and D13 |
| NB1 | qemu property unverified on the Mac arm64 AVD | done | refusal 3 also accepts `ro.hardware` = `ranchu`; 5b M1 step 1 checks the three values before any install and reports Blocked if none holds |
| NB2 | Script refuses the secret even when `.env` holds it | done | the check also greps `$ROOT_DIR/.env` and `$ROOT_DIR/local.properties`, the files `envOrNull` reads (`app/build.gradle.kts:21-41`, `rootProject.file`). The worktree has neither line (no `.env`; `local.properties` has no `PUBER_CLIENT_SECRET`), so the M8 secret case still exits 2 |

## Newly broken

None found.

## Non-blocking

- `^PUBER_CLIENT_SECRET=.` misses a properties line written as `PUBER_CLIENT_SECRET = …`; the script then refuses and names the variable, so nothing on the device is touched. Spec delta 7 still says "refuses an empty `PUBER_CLIENT_SECRET`" without the file fallback.
