# Readiness 2: release-startup-compilation

Verdict: **READY**

Spec and plan at wt HEAD 35c38efa (base e4c3b5aa). Coverage and the citation sample of `readiness-1.md` still hold; no code changed since (`git diff 3788b2af 35c38efa --stat`: the spec only).

## Readiness-1 fix list

| # | Fix | Status | Evidence |
|---|---|---|---|
| 1 | One Test design and one Open questions | done | spec headings: `## Test design` :132, `## Open questions` :242, each once; the design has no `### Open questions`; S0/S1 rows :214-215 use the install-preserve reinstall; P2 row :181 reads `runScheduled`; M1 :220 is the implementer's |
| 2 | AC-3 checkable against a full stale list | done | spec :187-198 (and `test-design.md:56`) list 6 classes; an independent scan of the base `baseline-prof.txt` against `app/src` finds the same 6 missing, with the same entry counts (23, 8, 1, 2, 3, 1); the four prefixes of row :169 cover all 6 |
| 3 | Device and manual P0 rows marked | done | :165, :166, :171, :182, :183, :185 read `P0 guard` |

## Citations re-sampled

| Citation | Resolves |
|---|---|
| `DetailsVM.kt:536` (D7, Play) | yes: `router.navigateForResult` inside `openPlayer` (:532) |
| `app/build.gradle.kts:436` | yes: `baselineProfile(project(":baselineprofile"))` |
| `Makefile:123-124` | yes: `ITEST_ADB_PORT`, `ITEST_ADB` |
| `RevealGate.kt:9`, `PlayerScreen.kt:72` | yes |
| `StartupMarks.kt:10`, `:94-100` | yes: `LINE_WAIT_AFTER_REVEAL_MS`, `emitWhenComplete` |
| `ProguardRulesTest.kt:10-12` | yes: working-dir fallback |

## Non-blocking

- Spec :189 calls the 385 profile classes "outer classes"; 385 counts every `com/kino/puber` class including nested and lambda classes (115 outer). The 6 stale outer classes and their 38 entries are right.
