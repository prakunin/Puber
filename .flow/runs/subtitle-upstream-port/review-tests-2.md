LENS: tests  ROUND: 2  BASE: be8395a4

**Suite:** I ran `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree at HEAD 0127f0c3 and it passed. Gradle marked the tasks up to date, so it reused earlier results with the same inputs; the only commit after them is the plan update. The test XML shows 1460 tests with 0 failures, 0 errors and 0 skipped. Both new or renamed tests appear in it. Detekt and lint are green.

Checked and found sound (not findings):
- **Renamed T-M1 (`resumeDialog_noHideTimerRunsWhileItIsUp_andResumingLeavesControlsHiddenUntilShown`):** the three new checks run right after `ResumeFromPosition` and before `ShowControls`. If `resumeFromSavedPosition` (PlayerVM.kt:1305-1320) stopped clearing `resumeDialog`, `assertNull(resumeDialog)` would fail. If resume started showing the controls or setting focus, `assertFalse(controlsVisible)` or `assertNull(controlsFocusTarget)` would fail. The handler updates the state synchronously, so these checks read the state after resume. Round 1 F1 is resolved.
- **`bibliographicCodes_readAsLanguageNamesToo`:** without the `bibliographicToTerminology` lookup, `ger` stays `ger` and `iso3Locales` has no entry for it. `getDisplayLanguage` then returns "ger", which equals the code, so the result is null and the label falls back to "GER". The test fails both with the Russian locale and with `Locale.ENGLISH`. Only ger and fre are tested, but I checked all 20 pairs in the table by hand against ISO 639-2 B/T and they are correct. `canonicalSubtitleLanguage` is only used inside SubtitleLabels.kt, so the change does not affect track matching anywhere else.
- **Trimmed `initialize_dropsWhatThePreviousMediaLeft`:** the dead line `lastPanelOpener = Buttons` and the assertion that could not fail are both gone. The checks that remain each cover a reset `initialize` still performs. Round 1 F2 is resolved.
- **PlaybackController `pendingSubtitleTrack = null` in `prepare()`:** it has no automated test, as triage-1 #1 and #2 decided. That gap is deferred to run playback-controller-split, so I am not reporting it again.
- **Plan record (round 1 F3):** the plan was updated in 0127f0c3. It is not production code and is not a test concern.

No findings.
