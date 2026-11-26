# Triage round 2: hermetic-baseline-generation

Reviewed: 7d639a9b..3e79f660 (incremental, fix round Tasks 9-17). Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran, clean), review-antigravity-2.md (ran, clean); qa round 2 (qa-report.md): AC-1..AC-11 PASS, evidence 811fde6b.
Round 2 rule: fix blockers and majors only. None found; unpinned prod behaviour unchanged per edge, codex, antigravity and tests.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Late check runs before the resets, so one failed journey cascades into every later test and @AfterClass | edge F1 | minor | accept | Real; affects only diagnosis of an already failed generation run (try/finally around checkLate, or check only after a passed test). Listed for the owner |
| 2 | pinnedEndpointWithResolver_buildsTheAppClientWithDoh asserts only "is DoH", not the resolver URL | tests F1 | minor | accept | Real test gap; prod default is Google and generation would fail visibly on a device. Listed |
| 3 | BaselineInstrumentationEnvironmentTest hardcodes port 18765 | codex | minor | accept | Fails only with a non-default puber.baselineMockPort. Listed |
| 4 | dnsResponse throws on a truncated DoH question | antigravity, edge/codex unproven | minor | accept | Test-only robustness; only OkHttp DoH posts there. Listed |
| 5 | okhttp3/internal/http1 51 -> 0 and kotlinx.serialization/phosphoricons drops >20 % in the regenerated profile | qa round 2 (AC-5 evidence) | - | defer | Not one of AC-5's guarded packages; likely HTTP/2 mock vs HTTP/1.1 API and thin fixtures. New run baseline-mock-protocol-parity |
| 6 | Truncated DoH counted as hit; late-violation window; two volatiles; launcher preview program; seed overwriting a paired token | codex, edge unproven | - | reject | Unproven; generation runs only on the emulator in a separate package |

Deferred: #5 -> baseline-mock-protocol-parity. Accepted: #1-#4.
