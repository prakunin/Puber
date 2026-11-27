LENS: tests  ROUND: 1  BASE: 6ccf2ebf

I ran `AudioTrackPreferenceResolverTest` in the worktree with `./tools/agentw testDevDebugUnitTest --tests '...AudioTrackPreferenceResolverTest'`. It passes (BUILD SUCCESSFUL). I did not re-run `check.full`. I relied on the implementer's record of a green run (117 tasks, tooling shell tests passing) and on qa-report round 1.

### F1 [minor] `exactLabel_wins` does not isolate `exactLabelMatch`
- Where: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/AudioTrackPreferenceResolverTest.kt`, `findAudioTrackIndex_exactLabel_wins`.
- Problem: The rows are `A` and `B` with stored label `B`. `normalizedLabelMatch` strips only an `NN. ` prefix, so it also returns 1 for this input. The test design promised inputs where only the guarded matcher can hit.
- Failure scenario: Someone deletes or breaks `exactLabelMatch`. The normalized matcher takes over and the test stays green, so this guard cannot fail for its own matcher.
- Fix: Use a fixture where the normalized form is ambiguous, so only exact picks the right row. For example, rows `[01. X, 02. X]` with stored `02. X` should return 1. Exact returns 1, while normalized alone returns 0.

All other ACs and P0 rows are covered by a test that fails when the behaviour breaks:
- **AC-1 to AC-3:** the five red tests are recorded as failing on base (got -1), as the Implementation record shows.
- **AC-4 and AC-5:** each guard fails if its condition is removed. Dropping the known-language check returns 0 instead of 1. Dropping the empty-language filter returns 1 instead of -1. Dropping the empty-title guard matches `"" == ""`.
- **AC-6:** device check M1 is recorded in the evidence file. It covers only the Media3 restore path.

The red-run record meets the P0 rule. Each new P0 test is either recorded red on base or marked as a guard that was green on base. There are no P0 tests recorded green on base without being marked as guards.

### Unproven
- `AudioTrackPreferenceResolverTest.kt` `numberedPrefixLabel` and `voiceTypeAndLanguage` guards: the same overlap with other matchers may exist. They have no evidence of it. A mutation run (removing each matcher in turn) would confirm.
