# Round 1

CHECK: check.full from .flow/config.yaml in the worktree (HEAD 7248c54c) -> pass (BUILD SUCCESSFUL, 117 tasks, 3 executed and 114 up-to-date from a run at the same inputs; baseline-profile tooling tests all PASS)
DEVICE: 192.168.1.110:5555 (owner's box, com.kino.puber.stage, manual check 1); emulator host.docker.internal:5555 for AC-6 itest (owner decision), stopped and lease released afterwards

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlayerAudioTracksTest.buildAudioRows_undeterminedLanguage_usesFallbackString and SubtitleTrackIdentityTest.knownLanguageOrNull_* (20 tests in class, 0 failures; 4/4 in PlayerAudioTracksTest) |
| AC-2 | PASS | PlayerAudioTracksTest.buildAudioRows_labelledUndeterminedLanguage_keepsLabelWithoutLanguage |
| AC-3 | PASS | PlayerVMTrackSelectionTest.selectAudioTrack_undeterminedRowFromPlayer_keepsStoredAudioPreference and selectSubtitle_whileAudioRowIsUndetermined_keepsStoredAudioPreference (15/15) |
| AC-4 | PASS | SubtitleLabelerTest.apply_undeterminedLanguageWithoutLabel_readsAsUnknownOrdinal, PlayerTextTracksTest.manifestRow_undeterminedFormatLanguage_takesRenditionLanguage and toPlayerTextTracks_undeterminedLanguage_isNull |
| AC-5 | PASS | SubtitleTrackIdentityTest.sameSubtitleLanguage_rejectsUndetermined, SubtitleTrackSelectorTest.select_doesNotMatchByLanguage_whenBothAreUndetermined |
| AC-6 | PASS | Re-run at HEAD (itest-2.xml predated the last production commit 7248c54c): make DEVICE=host.docker.internal:5555 FLAVOR=instrumentation itest TESTS=...PlaybackControllerNetworkTracksTest -> 13 tests, 0 failures, 0 errors, 0 skipped, including progressiveUndeterminedAudio_reportsLocalizedFallbackRow. Result XML: app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation/ |
| AC-7 | PASS for audio, NOT CHECKED for subtitle rows | Box, worktree dev APK sha256 verified: Audio rows "01. Многоголосый. MUZOBOZ (RUS)" and "02. Оригинал (FRE)", no "und", no "Track N", no focus loss or crash; picked dubbing restored after leaving and reopening. The movie has no subtitle tracks (only "Выкл."), so named subtitle rows and manual step 3 were not seen on a device; they rest on the unit tests of AC-4/AC-5 |
| AC-8 | PASS | check.full above |

TESTS ADDED: none (the plan's tests are already in HEAD and cover every testable AC)
EVIDENCE: /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md
(full path: /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md, plus two screenshots next to it; mobile-evidence-audit: passed). Not committed, per the brief.
DEFECTS: none
UNAVAILABLE: none (subtitle rows on the box: see MANUAL)
MANUAL: optional, owner: on the box with the worktree build, open an item that has several subtitle tracks and check the Subtitles list reads language names, not "UND" (manual check step 3).

Notes:
- First itest attempt ran without FLAVOR=instrumentation, so the dev flavor failed with ClassNotFoundException (initializationError, wrong flavor, not a code defect); it also left the dev-flavor com.kino.puber.stage test APKs on the emulator only. The repeat with FLAVOR=instrumentation is the evidence above.
- Box restored: master dev APK reinstalled (sha256 4354d24d...c128, matches the pre-test on-device hash), audio choice re-set to 01 MUZOBOZ, auth snapshot in /home/dev/orca/workspaces/Puber/player-audio-und-label/.auth/com.kino.puber.stage.tar (gitignored, holds tokens). The movie now has a ~5 min resume position (side effect of playing it).
- Leases released for both devices; emulator stopped.

# Round 1b

Scope: AC-7 subtitle rows only (manual check step 3), box `192.168.1.110:5555`.

CHECK: not re-run (no code or test change since round 1; production sources identical to 7248c54c)
DEVICE: 192.168.1.110:5555 (owner's box, com.kino.puber.stage); worktree dev APK sha256 d5dbf429...55a9 installed and verified on device; master dev APK (4354d24d...c128) restored and verified; lease released.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 (subtitle rows, manual step 3) | PASS | "Квазимодо, душа Парижа" (4 audio, 11 subtitles): Subtitles list on Auto and after a manual 720p pick shows Выкл. plus 11 language-named rows (Украинский, Русский, Французский, Английский with "вариант N"/"частичные"); no "UND", no "Субтитры N"; audio rows "01. Дубляж (RUS)", "02. Дубляж (UKR)", "03. Оригинал (FRE)", "04. Дубляж (RUS) AC3". No crash or focus loss |
| AC-7 (audio) | PASS | Unchanged from round 1; reconfirmed on this item |

DEFECTS: none
UNAVAILABLE: none
MANUAL: none
RESTORED: quality Авто, subtitles Выкл., audio 01. Дубляж (RUS); the movie's resume position moved to about 19-20 min. Auth snapshot refreshed in .auth/ (gitignored). Evidence appended to /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md (two screenshots, audit below). Not committed.

# Round 2

Scope: AC-4, AC-5, AC-7 subtitle selection after fix commit d1ed06ad (SubtitleTrackSelector, PlaybackController.applySubtitleTrackSelection). Head 2d52a21e.

CHECK: check.full from .flow/config.yaml in the worktree (HEAD 2d52a21e) -> pass (exit 0, baseline-profile tooling tests all PASS)
DEVICE: 192.168.1.110:5555 (owner's box, com.kino.puber.stage); worktree dev APK sha256 b43f040d...920d installed with install-preserve and verified on device; master dev APK 4354d24d...c128 restored and verified; lease released.

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 | PASS | Unit tests from round 1 still in HEAD (check.full green); on device "Квазимодо, душа Парижа" lists 11 subtitle rows named by language with "вариант N"/"частичные", no UND |
| AC-5 | PASS | Unit tests (check.full green); on device the picked "Русский · вариант 1" row stays selected and cues keep rendering after a manual switch Авто to 720p; picking "Английский · вариант 1" changes the cue language to English |
| AC-7 (subtitles) | PASS | Auto: picked Русский · вариант 1, Russian cues rendered; 720p (1280x534): same row checked, Russian cues rendered; Английский · вариант 1: English cues rendered. No crash or focus loss |

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-audio-und-label/docs/superpowers/evidence/2026-11-27-player-audio-und-label-device.md (Round 2 section appended; audit passed, 423 files scanned; no screenshots kept, film frames). Not committed, per the brief.
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
RESTORED: subtitles Выкл., quality Авто, audio 01. Дубляж (RUS); movie resume position now about 22-23 min. Auth snapshot refreshed in .auth/ (gitignored). Lease released (status after: no leases listed).
