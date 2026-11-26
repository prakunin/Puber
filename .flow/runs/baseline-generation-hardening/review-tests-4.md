# Review tests, round 4

LENS: tests  ROUND: 4  BASE: 91a9b7ba

No findings. Shell suite exit 0, all PASS.
- #1: the fake merge emits mixed H and non-H rules; test_sigkill_after_merge_before_remark_restores_original fails on the old discriminator (red run recorded).
- #3: test_leftover_restore_failure_keeps_original_backup (mv fake fails for non-.bak destinations) asserts non-zero exit, no Gradle call, original backup intact (red run recorded).
- #5: stale test seeds the startup backup; absent-startup test checks exit code and notice (guards).
- #2: no test (signal between two rm calls cannot be aimed) - accepted; one trap line.
- ! grep -qE '^[SP]*L.*->' ignores H rules and class-only lines; restore || exit 1 runs before cp.
- #4 spec wording amended in the diff.
