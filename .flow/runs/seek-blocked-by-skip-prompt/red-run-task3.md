# Red run, Task 3 (`PlayerPromptFocusAndKeysTest`)

Worktree HEAD `82909d99` (production code unchanged since base). Device `emulator-5554` (puber_tv_36), lease taken and released, app data kept.
Command: `AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 itest GRADLE=./tools/agentw TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest` -> exit 2, 12 ran, 9 failed, 3 passed.
Failing line is in `app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt`. Expectation is from `test-design.md` (AC rows).

| Test | Result | Failing line | Expected | Match |
|---|---|---|---|---|
| `skipPrompt_leftAndRightSeekAndFocusStaysOnAction` | FAIL | :91 `assertTrue(SeekBackward in actions)` | red | yes |
| `skipPrompt_heldRightSeeksOnEveryRepeat` | FAIL | :102 `assertTrue(count{SeekForward} > 1)` | red | yes |
| `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction` | FAIL | :112 `assertTrue(SeekBackward in actions)` | red | yes |
| `skipPromptHidden_nextRightSeeks` | PASS | - | red, guard if it passes | yes: passes on base, root-cause step 4 refuted, keep as guard |
| `nextEpisodePromptHidden_nextRightSeeks` | PASS | - | red, guard if it passes | yes: same as above |
| `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` | FAIL | :142 `action(nextEpisodeLabel).assertIsFocused()` | no row in test-design | n/a |
| `skipPrompt_appearsWithActionFocused_okSkips` | FAIL | :149 `action(SKIP_LABEL).assertIsFocused()` | red | yes |
| `skipPrompt_backCancels` | PASS | - | guard | yes |
| `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` | FAIL | :176 `action(SKIP_LABEL).assertIsNotFocused()` | red | yes |
| `controlsHidingWhilePromptIsUp_focusesThePromptAction` | FAIL | :193 `action(SKIP_LABEL).assertIsFocused()` | red | yes |
| `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel` | PASS | - | guard | yes |
| `skipPrompt_okUpWithoutDownDoesNotSkip` | FAIL | :210 `action(SKIP_LABEL).assertIsFocused()` (precondition) | no row in test-design | n/a |

Notes:
- **The `action(label)` matcher finds no node** (`Expected exactly '1' node but could not find any node` for `hasClickAction() and (hasText(label) or hasAnyDescendant(hasText(label)))`) in five tests: `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction`, `skipPrompt_appearsWithActionFocused_okSkips`, `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks`, `controlsHidingWhilePromptIsUp_focusesThePromptAction`, `skipPrompt_okUpWithoutDownDoesNotSkip`. Their red is a lookup failure, not proof of the focus bug, and they would stay red after Tasks 4-5 until the matcher is fixed. The "matches" in the table for `:149`, `:176`, `:193` hold only for the outcome, not the cause.
- The same lookup runs before the key press in the `*Hidden_nextRightSeeks`, `leftAndRight` and `heldRight` tests only if they call `action()`; those three `leftAndRight`/`heldRight` failures found their node and fail on the recorded actions (`:91`, `:102`, `:112`), so they are real red.
- `skipPromptHidden_nextRightSeeks` and `nextEpisodePromptHidden_nextRightSeeks` pass on base: root-cause step 4 is refuted; keep as guard.
- `skipPrompt_okUpWithoutDownDoesNotSkip` fails on its focus precondition, so the Task 4 `ACTION_UP` guard question stays open.
