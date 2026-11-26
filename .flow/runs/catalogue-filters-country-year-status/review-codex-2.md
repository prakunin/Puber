CODEX: ran: review, adversarial
CONFIRMED:
(none)
REJECTED:
- [P3] `ShowAllFilterFocusTest.kt:567-570`: `awaitFocusOnRow` does not assert `assertIsSelected()` on the off-list year row - this is a test-strength wish with no scenario that occurs. The product code sets `selected = true` unconditionally on that row (`ShowAllFilterPanel.kt` YearPage), and the test's purpose is focus on entry. Marked `unproven`.
- [medium, adversarial] `ShowAllFilterPanel.kt:256-272`: picking a preset from an off-list year removes the conditional row, and unkeyed preset rows may be reused by position, so focus moves or is lost - The `if (chosen != null && chosen !in yearOptions)` block is its own Compose group. The `forEach` preset rows sit in a separate group, matched by position inside that loop. Removing the earlier `if` group does not shift the loop's slots, so the focused preset keeps its node and its focus. YearPage is a plain composable with no `items` or lazy keys, so there is no list-index reuse either. Codex gave no concrete mechanism for the focus loss and said it ran no device check, so I rejected it as `unproven`. The conductor's on-device run should still cover the off-list-year-then-preset transition, since AGENTS.md requires device evidence for focus changes.
- Codex note: the new year-row focus behaviour needs device verification, and the patch records compilation only - this is process, not a code defect. The qa step already owns it.
- The `ContentListInteractor` cache-key change (`browseFilterKeyParts`) got no finding from either pass.

VERDICT: clean
