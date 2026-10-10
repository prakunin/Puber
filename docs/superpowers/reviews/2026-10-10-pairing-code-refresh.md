# Pairing code renews when it expires — review

**Spec:** `docs/superpowers/specs/2026-10-10-pairing-code-refresh-design.md`
**Plan:** the `## Plan` section of the spec (quick route)
**Evidence:** `docs/superpowers/evidence/2026-10-10-pairing-code-refresh-device.md`
**Branch:** `pairing-code-refresh` at `6617ae99`

<!-- The conductor writes the rounds from the reviewers' returns; the
final-judge writes the Gate. Each finding gets a disposition: fixed (<sha>),
rejected (<why>), or deferred (<board row>). Nothing stays open silently. -->

## Round 1

### code-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | `KinoPubRepository.kt:54,63` | `interval` is not floored: `interval = 0` with an immediate expiry renews and polls with `delay(0)`, hammering the API | fixed (22f475aa) |
| 2 | minor | `KinoPubRepository.kt:30-31` | A failed renewal request ends the flow; in `LoginRequired` the VM only shows a snackbar, so one network blip at renewal leaves the dead code at 00:00 — the original symptom. AC-5 chose this | deferred: board row `pairing-renewal-retry` |
| 3 | minor | `KinoPubRepositoryAuthStateTest.kt:78` | `aPollPastTheDeadline…` never polls past the deadline (wall-clock deadline, virtual `delay`) | duplicate of test #2 |

### test-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | major | `KinoPubRepositoryAuthStateTest.kt` | No test that an unlisted OAuth string (e.g. `invalid_grant`) still throws; the user's whitelist is unguarded | fixed (625008e1) |
| 2 | major | `KinoPubRepositoryAuthStateTest.kt` `aPollPastTheDeadlineThatIsRefusedAsExpiredRenews` | Does not simulate a late poll; the AC-2 root-cause path is untested. Same clock mix costs ~1 s real time in `codeRunningOutOnPendingAnswersRequestsANewCode` | fixed (625008e1) |
| 3 | minor | `pollUntilExpired` guard | The `expiresIn <= 0` delay guard has no test | fixed (625008e1) |
| 4 | minor | `renewalsAreNeverCloserThanTheInterval` | No assertion on `requestTimes.size` | fixed (625008e1) |
| 5 | minor | `everyExpiredStringRenewsWithTheNewCode` | Case-insensitive match untested | fixed (625008e1) |
| 6 | minor | `AuthVMTest.kt:~33` | Import order | fixed (22f475aa) |

### codex-reviewer

Ran: review (`gpt-6-astra`; first attempt failed: the global default `gpt-6.1-sol` is refused for a ChatGPT login). No findings.

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|

Rejected Codex claims: none.

### ux-designer (visual changes)

Not run: no layout change.

## Gate

<!-- final-judge, gate mode. -->

**Decision:** PASS | CONCERNS | FAIL | WAIVED (user only)

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|

### Non-functional

| Area | Assessment |
|---|---|
| Performance | |
| Reliability | |
| Maintainability | |
| Security / privacy | |
| TV (focus, D-pad, Back) | |

### Must fix

### Concerns for the user
