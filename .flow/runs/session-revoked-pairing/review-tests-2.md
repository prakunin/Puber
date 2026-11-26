LENS: tests  ROUND: 2  BASE: b53bf631

The seven test classes this diff touches pass in the worktree: DefaultErrorHandlerTest, SessionEventBusTest, KinoPubApiClientSessionEndTest, KinoPubRepositoryAuthStateTest, DeviceSettingsVMErrorTest, HomeVMTest and LauncherVMTest. That is 86 tests, none skipped and none failing, run with `./tools/agentw :app:testDevDebugUnitTest --tests …`. The Fix round 1 record in the plan lists a red run on the unchanged code for each new test of triage items 1-5. Item 6 (`theEndIsPendingBeforeTheTokenIsCleared`) was green on base and is marked as a guard. The AC-4 row in test-design.md was corrected as item 7 asked.

I tried each new test against the change it is meant to catch:
- **`aPairingCallBeforeTheNewTokensAreSavedDoesNotPinAnEmptyBearer`:** `getDeviceCode` goes to the OAuth host, where `sendWithoutRequest` is true. On the old code that call loads `("","")` into the token cache, so the header check fails.
- **`start_opensPlainAuth_whenThePersistedReasonIsUnknown`:** the screens mock is relaxed, so `auth(Blocked)` would return a different mock and the `verify` would fail.
- **HomeVM:** reverting the `endsSession` check at both catch sites makes `allSectionsRefusedWithoutEndingTheSession_keepMirrorAction` fail. Reverting only one site leaves fewer than `TOTAL_SECTIONS` failures counted, so the mirror action stays and the behaviour is still correct. There is no gap in behaviour here.

### F1 [minor] The once-per-end clear in the App session handler has no test; only the bus primitive does
- Where: /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:97-112
- Problem: Triage item 4 asked for a test where an end has already been handled, a link is then accepted with `accept(uri)`, and the target survives. No such test exists. `SessionEventBusTest` covers `claimPendingEnd()` returning true only once, but nothing checks that `SessionExpiredHandler` uses it. The plan says so too ("the App handler itself has no unit test (composable)").
- Failure scenario: Someone removes the `if (sessionEventBus.claimPendingEnd())` guard, or moves `clearSession()` out of it. The handler then reruns every clear when it collects the replayed end again (for example on a cold start or a recomposition). That clears a deep-link target accepted after the end, which is the triage-4 bug, and the whole suite stays green.
- Fix: Move the clear block into a small non-composable function that the handler calls, for example `SessionEndClears.onEnd(bus)`, which runs the clears only when the claim succeeds. Then add a unit test: emit an end, call it once, accept a link, call it again, and assert that the coordinator still holds the target. A Compose test of `SessionExpiredHandler` would also work.

### Unproven
- /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/data/repository/CryptoPreferenceRepository.kt:63-67 The real save/get/clear of the session end reason (an empty value read back as null) has no test. It only matters if a stored `""` changed routing, and LauncherVM already maps an unknown name to plain `auth()`.
- /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:278-279 The `runCatching` that lets the tokens be cleared even when saving the reason throws has no test. It would matter only if `saveSessionEndReason` failed on a real device, for example a keystore error.
