# QA report

## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh -> pass (BUILD SUCCESSFUL, baseline-profile tooling tests all PASS; most tasks were up-to-date from the implementer's run)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | unit test mapAudioTracks_nullLanguageUsesLocalizedUnknownWithAuthor, green; implementer's red run on the old mapper recorded |
| AC-2 | PASS | mapAudioTracks_blankOrUndLanguageIsTreatedAsUnknown, green; red on base |
| AC-3 | PASS | mapAudioTracks_nullLanguageFallsBackToTypeTitle, green; red on base |
| AC-4 | PASS | mapAudioTracks_noLanguageAndNoTitlesIsFallbackLabel, green; red on base |
| AC-5 | PASS | mapAudioTracks_keepsKnownLanguageAndAuthor, green guard |
| AC-6 | PASS | grep '"Unknown"' in PlayerUIMapper.kt: no match; string present in values/player.xml ("Язык не указан") and values-en/strings.xml ("Unknown language"); check.full green |
| AC-7 | PASS | device regression look, see evidence: Audio list shows 4 named rows, focus moves, playback continues, no "Unknown", no crash |

TESTS ADDED: none (implementer's five mapAudioTracks tests cover AC-1..AC-5)
EVIDENCE: /home/dev/orca/workspaces/Puber/player-audio-unknown-language/docs/superpowers/evidence/2026-11-27-player-audio-unknown-language-device.md  (audit: evidence_audit_status=passed evidence_dir=/home/dev/orca/workspaces/Puber/player-audio-unknown-language/docs/superpowers/evidence files_scanned=425)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

Notes: install-preserve of the worktree APK returned outcome "installed"; on-device sha256 matched the worktree APK. Master dev APK was put back the same way (sha256 matches). auth-save was taken before the install. A no-language API row is not reachable on device without probing, so AC-1..AC-4 are unit-only. Nothing committed.
