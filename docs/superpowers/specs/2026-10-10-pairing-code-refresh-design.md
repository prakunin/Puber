# Pairing code renews when it expires

Status: draft
Date: 2026-10-10
Slug: pairing-code-refresh
Kind: bug
Size: S
Route: quick

<!--
Each section has one owner; nobody else edits it. The conductor commits this
file in the task's worktree after every phase.
  analyst        Problem, Acceptance criteria, Out of scope, Plan (quick route)
  architect      Design
  ux-designer    Visual
  test-designer  Test design
  anyone         Open questions (append only; the user answers)
Cite code as [Source: path:line]. A claim with no source is a guess.
-->

## Problem

<!-- analyst. Bug: Repro (steps, build, device), Observed, Expected,
Root cause (confirmed | plausible) with [Source: …]. Feature: who needs what
and why, and what exists today. -->

**Repro.** Dev build 1.9.69-dev on emulator-5554: pairing screen countdown reaches 00:00; the code on screen does not change until the app is restarted. Device repro skipped: the emulator is paired (login not renewable), so the app does not show the pairing screen. Analysis is from code.

**Observed.** Countdown stops at 00:00 and the old, dead code stays on screen. A "failed" snackbar is the only trace.

**Expected.** When the code runs out, the screen shows a fresh code and a restarted countdown, with no restart of the app.

**How the code models the flow.**
- The device-code response carries `expires_in` (code lifetime, seconds) and `interval` (seconds between polls) [Source: app/src/main/java/com/kino/puber/data/api/auth/OAuthModels.kt:11-12].
- `KinoPubRepository.getAuthState()` already has a renew loop: request a code, emit `AuthState.Code`, poll every `interval` seconds until `now + expiresIn` passes, then "loop restarts with new device code" [Source: app/src/main/java/com/kino/puber/data/repository/KinoPubRepository.kt:29-53].
- The countdown is a separate VM timer started per `AuthState.Code`; it counts `expiresIn` down to 0 and ends, changing nothing else [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:199-210]. A new `Code` would restart it and replace the shown code [Source: AuthVM.kt:118-128].
- Polling: `getDeviceToken` posts the device `code`; `getDeviceLoginStatus` emits nothing for `authorization_pending` and emits a failure for every other error [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1076-1086, 1118-1143]. `pollForToken` rethrows any `ApiError.OAuth` that is not `authorization_pending` [Source: KinoPubRepository.kt:65-74; app/src/main/java/com/kino/puber/core/error/ApiError.kt:50-58].

**Root cause (plausible, strongly supported by code; the server's exact expiry string is unverified).** The deadline loop is never reached cleanly. The poll is made `interval` seconds after the previous one and the deadline is checked only before the delay (`while (now < deadline) { delay(interval); poll }`), so the last poll can land up to `interval` seconds after the server has expired the code, and in any case server and client clocks disagree by the request latency [Source: KinoPubRepository.kt:34-39]. The server answers an expired code with an OAuth error that is not `authorization_pending`; `pollForToken` treats it as final and throws [Source: KinoPubRepository.kt:60-63, 70]. The throw ends the `channelFlow`, so the renew loop at line 52 is never taken. In `AuthVM`, the exception goes to `dispatchError`, which only shows a snackbar and returns in `LoginRequired` mode [Source: AuthVM.kt:159-163]. The Content state, with the dead code and the timer at 00:00, remains. A unit test pins this: an `expired_token` refusal must throw and not renew [Source: app/src/test/kotlin/com/kino/puber/data/repository/KinoPubRepositoryAuthStateTest.kt:29-38]. That test encodes the defect for the expiry case; it is right only for a refusal like user denial.

**Polling after expiry.** Polling does not continue against an expired code in the intended path (the loop exits at the deadline), but it can make one poll past the deadline (see above), and that poll is what kills the flow.

## Acceptance criteria

<!-- analyst. Numbered, each checkable by a test or an observation on the TV.
- **AC-1** Given …, when …, then …
-->

- **AC-1** Given a signed-out device showing code A, when the server answers a poll with the code-expired refusal, then the repository requests a new device code and emits a new `AuthState.Code` instead of throwing.
- **AC-2** Given the client deadline (`expiresIn`) passes with only `authorization_pending` answers, when the loop ends, then a new code is requested and emitted (existing behaviour, kept).
- **AC-3** Given a new `AuthState.Code` arrives, when `AuthVM` handles it, then the shown code is the new one and the countdown restarts from the new `expiresIn`.
- **AC-4** Given a poll refusal that means the user denied the code (not an expiry), when it arrives, then the flow still stops and the error is reported (not silently renewed).
- **AC-5** Given the new-code request itself fails (network or server), when renewing, then the failure surfaces through the existing error path and the loop does not spin; renewals are never closer together than `interval` seconds.
- **AC-6** Given a code is renewed, when the old code's polling is considered, then no further poll is made with the old `code`.
- **AC-7** Given the pairing screen on a TV with the countdown at 00:00, when a minute passes, then the screen shows a different code and a running countdown (device check; needs a signed-out device, so the user or a dedicated test device runs it) `(user judges)`.

## Out of scope

- The pairing screen layout, copy, and the API-domain dialog.
- Token refresh and any post-login flow.
- Changing the countdown format.
- Logging out or re-pairing the shared emulator.

## Design

<!-- architect. Decisions table, changes by layer, alternatives rejected,
risks. Not on the quick route. -->

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

<!-- test-designer.
### Risks
| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|

### Traceability
| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|

### Device checks
1. …

### Baseline (perf route)
-->

## Plan

<!-- analyst, quick route only; otherwise see docs/superpowers/plans/. -->

1. `ApiError.OAuth`: add `isCodeExpired` next to `isAuthorizationPending`, true for the server's expired-code error strings (see Open question 1). [Source: app/src/main/java/com/kino/puber/core/error/ApiError.kt:50-58]
2. `KinoPubRepository.pollForToken`: on an expired-code refusal return a signal that ends the inner loop (code expired) rather than throw; other non-pending OAuth refusals still throw. Make the inner loop stop and fall through to the existing "new code" iteration, keeping the `interval` delay so renewals are not tighter than `interval`. [Source: app/src/main/java/com/kino/puber/data/repository/KinoPubRepository.kt:29-74]
3. Tests: change `aTerminalOAuthRefusalStopsThePollInsteadOfRunningOutTheCode` to use a non-expiry refusal (e.g. `access_denied`); add a test that an expired refusal yields a second `AuthState.Code`, and one that a late poll past the deadline with expiry renews. [Source: app/src/test/kotlin/com/kino/puber/data/repository/KinoPubRepositoryAuthStateTest.kt:29-38]
4. `AuthVM` needs no change if AC-3 holds; add a VM test only if one exists for `AuthState.Code` handling. [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:118-128, 199-210]

## Open questions

1. Which error string does KinoPub return for an expired device code (`expired_token`, `authorization_expired`, `code_expired`, other)? It is not documented in the repo, and it cannot be observed on emulator-5554 without unpairing. Proposed default: renew on any of those three, and treat `access_denied` and anything else as terminal. Alternatively, invert it: renew on every non-pending refusal except `access_denied`. Which does the user prefer, or can the user capture the real string from a signed-out device (the user must supply that)?
