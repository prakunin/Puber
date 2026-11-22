LENS: tests  ROUND: 1  BASE: 2fd851a4

No findings.

Suite state: I ran `check.full` (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) in `/home/user1/orca/workspaces/Puber/resume-progress-anomaly` at HEAD f2e0f25f. It exited 0 with BUILD SUCCESSFUL. Gradle reused the earlier results for the unit tests and detekt because nothing had changed since the implementer's green run. It ran only `lintDevDebug` again, and lint passed.

What I checked:
- **AC coverage:**
  - AC-1 to AC-4 each have a unit test in `KinoPubApiClientFailureTest` or `PlayerVMPlaybackFlowTest`.
  - AC-5 to AC-7 have the two rounds of device checks, with round 2 passing, plus the P0 rows for `DetailsScreenUIMapperTest` added in design delta 2.
  - AC-8 rests on the existing `PlayerVMInvalidationTest` and `PlayerInteractorTest`, and on `check.full`.
  - No AC is left without a test or a documented check.
- **Red on base:** I compared each new test against the base code.
  - The `{"status":200}` and `{}` tests fail on base, because `WatchingStatus.id` was required there.
  - The `Serialization` check in `failedFinalProgressSave_emitsIdentityFreeDiagnostic` fails on base, because base logged only the constant.
  - The five delta-2 tests that should fail on base do fail there. Base `timeLeft()` needed `watching.duration`, and base read `item.watching` first.
  - The implementation record in the plan shows a failing run on unchanged code for every P0 test marked red.
  - The tests that pass on base are the extra-fields answer, the HTTP 500 answer and "not started". Each is marked `guard` or P1.
- **Weakened assertions:** none. The only assertion this diff removes is the message check in `failedFinalProgressSave_emitsIdentityFreeDiagnostic`. It is replaced by the `cause.message` check plus new checks for "Malformed response" and for `Serialization`, so the test is stricter than before.
- **Test quality:** the API tests run the real client against a local server, so nothing is mocked. The mapper tests compare against strings built from the same resource id with `FakeResourceProvider`. No test sleeps or depends on order.
- **"Prefer the video's position to the item's":** the test with an empty item-level `WatchingInfo()` would fail if that order were reversed again.

### Unproven
- `DetailsScreenUIMapper.kt:404` (`length`): when the position does carry `watching.duration > 0`, it should win over the media's own length, but no test covers that. Every new fixture leaves it at 0. It would only matter if an item payload sent `watching.duration`, and the API docs cited in the spec say item payloads do not.
- `DetailsScreenUIMapper.kt:140` (`progressFraction`): a position of 0 with a known length now gives `null` instead of `0f`, and no test covers it. It would only matter if a Details payload carried `watching.duration > 0` together with `time == 0`. Base already gave `null` for item payloads without that field.
