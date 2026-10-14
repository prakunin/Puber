LENS: tests  ROUND: 4  BASE: a97ee2b1  RANGE: 2903fc50..871b06a6

No findings.

- Fallback nulling the intent is caught by `rebuildFastDns_keepsIntent_whenOtherStreamLacksRow_thenRestoresItWhenRowReturns` (three assertions fail on the old fallback: picker row, exactly one `selectSubtitle(rus.vtt)`, audio pick saves `"rus"`).
- Removing the re-arm in `toggleFastDns` is caught by `rebuildFastDns_reSendsPick_onFirstReportWithText_andSavesNothing` (zero-call check after the empty report fails).
- Removing the re-arm in `selectBufferPreset` is caught by `rebuildBufferPreset_reSendsPick_onFirstReportWithText_andSavesNothing` the same way; the test cannot pass without a real rebuild.
- The one-line fallback that re-sends the displayed row stays covered by `tracksUpdated_nextMediaWithUnresolvedChoice_sendsOffRow`.
- No test or assertion removed or weakened; no mocks of the class under test; no timing dependence.
- check.full at 871b06a6: BUILD SUCCESSFUL, 1593 tests, 0 failures, 0 errors, 0 skipped.
