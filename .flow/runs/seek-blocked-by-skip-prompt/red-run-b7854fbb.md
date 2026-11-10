# Red run b7854fbb

Worktree `/home/user1/orca/workspaces/Puber/seek-red-b7854fbb` (production prompt UI as on base). Command: `AGENT_GRADLE_SERIAL=1 make DEVICE=emulator-5554 itest GRADLE=./tools/agentw TESTS=com.kino.puber.ui.feature.player.component.PlayerPromptFocusAndKeysTest`. Device `emulator-5554` (puber_tv_36), leased and released. Exit 2, 5 PASS / 6 FAIL (11 tests). Nothing uninstalled or cleared.

Test file: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/component/PlayerPromptFocusAndKeysTest.kt`

| Test | Result | Failing line | Cause |
|------|--------|--------------|-------|
| skipPrompt_appearsWithActionFocused_okSkips | FAIL | :150 `action(SKIP_LABEL).assertIsFocused()` | bug: chip found (`Skip intro`), Focused=false |
| promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks | FAIL | :180 `assertTrue(SeekForward in actions)` | bug: Right does not seek |
| controlsHidingWhilePromptIsUp_focusesThePromptAction | FAIL | :194 `assertIsFocused()` | bug: chip found, Focused=false |
| skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction | PASS | :143 | not red: expected fail, passes on base |
| skipPrompt_okUpWithoutDownDoesNotSkip | FAIL | :211 `assertIsFocused()` | bug: chip found, Focused=false (precondition focus assertion, not the OK-up check itself) |
| skipPrompt_leftAndRightSeekAndFocusStaysOnAction | FAIL | :92 `assertTrue(SeekBackward in actions)` | bug: seek blocked |
| skipPrompt_heldRightSeeksOnEveryRepeat | FAIL | :103 `count { SeekForward } > 1` | bug: seek blocked |
| nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction | FAIL | :113 `SeekBackward in actions` | bug: seek blocked |
| skipPrompt_backCancels | PASS | | |
| skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel | PASS | | |
| nextEpisodePromptHidden_nextRightSeeks | PASS | | |
| skipPromptHidden_nextRightSeeks | PASS | | |

No failure is a lookup failure: the chip node resolves (`Text = '[Skip intro ]'`, selector matched one node) and the focus assertions report `Focused = 'false'`.

Open: `skipPromptReplacedByNextEpisode_focusMovesToNextEpisodeAction` is green on base, so it does not prove the fix. Likely the next-episode chip already gets focus when it replaces the skip prompt; the test needs a precondition or a stronger assertion (for example assert the skip chip was focused first) to be red.
