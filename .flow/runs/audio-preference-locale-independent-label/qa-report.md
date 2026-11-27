Round 1

CHECK: check.full (./tools/agentw testDevDebugUnitTest ... && tooling shell tests) -> pass (exit 0, tooling tests all PASS). AudioTrackPreferenceResolverTest: 40 tests, 0 failures, 0 skipped.
DEVICE: 192.168.1.110:5555 (owner's TV box), com.kino.puber.stage only
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unit test findAudioTrackIndex_unknownLanguageLabelStoredUnderEnglish_matchesRussianRow; red on base (-1), green now |
| AC-2 | PASS | unit test ...StoredUnderRussian_matchesEnglishRow; red on base, green now |
| AC-3 | PASS | unit tests legacyUnknownLabelWithNullLang / legacyUndLabelWithUndLang; red on base, green now |
| AC-4 | PASS | unit test knownStoredLanguage_titleMatchDoesNotApply; green on base and now |
| AC-5 | PASS | unit tests noSeparator / emptyTitle / titleAbsentFromEmptyLanguageRows -> NO_MATCH |
| AC-6 | PASS | four existing-matcher guard tests green on base and now; check.full green; M1 on box: UKR dubbing chosen, player left and reopened, Audio row showed "02. Дубляж. LeDoyen (UKR)"; RUS then re-picked |
TESTS ADDED: none by QA (implementer's 40-test suite reviewed as sufficient)
EVIDENCE: /home/dev/orca/workspaces/Puber/audio-preference-locale-independent-label/docs/superpowers/evidence/2026-11-27-audio-preference-locale-independent-label-device.md (mobile-evidence-audit passed; not committed, as instructed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

Notes: master dev APK (sha256 4354d24d...) was on the box first, auth-save taken, worktree APK (2e3fe677...) installed with install-preserve and hash-checked, master APK put back with install-preserve (hash matches). Lease released. AC-1..AC-5 are unit-test only by design (API-rows window not reachable on device). Original audio choice (01 RUS) restored. Watch position of the item advanced to about 29 min from the test playback.
