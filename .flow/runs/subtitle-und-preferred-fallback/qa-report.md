Round 1

CHECK: check.full from .flow/config.yaml in the worktree -> pass (exit 0; unit, player-test-fixtures, detekt, lint, instrumentation unit and baseline-profile tooling tests all PASS; most Gradle tasks were UP-TO-DATE from the implementer's run, same sources)
DEVICE: 192.168.1.110:5555 (owner's TV box, com.kino.puber.stage only)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | preferredTextLanguage_isEmpty_forUndeterminedAndBlank in SubtitleTrackIdentityTest.kt (und, UND, und-Latn, empty, blank, null all ""), green in check.full; red on base per implementation.md (compile failure, helper absent) |
| AC-2 | PASS | preferredTextLanguage_keepsKnownLanguage (ru, rus, en-US, ai unchanged), green in check.full |
| AC-3 | PASS | diff read: the only setPreferredTextLanguage in app/src/main (PlaybackController.kt:907) receives preferredTextLanguage(track.language); import swapped, comment reworded |
| AC-4 | PASS (neighbouring path only) | device: Russian row showed a Russian cue, Off showed none, no crash or focus loss. No "und" row exists in this title, so the changed branch itself was not reached on the device |
| AC-5 | PASS | check.full exit 0 incl. existing SubtitleTrackSelectorTest and knownLanguageOrNull_* tests |
TESTS ADDED: none (implementer's two tests cover AC-1/AC-2)
EVIDENCE: /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback/docs/superpowers/evidence/2026-11-27-subtitle-und-preferred-fallback-device.md (audit passed; uncommitted, as instructed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Optional: the "und" row path (AC-3's runtime effect) needs a title whose API list has lang "und"; none known.

Device notes: master dev APK (sha 4354d24d...) was on the box, auth-save done, worktree APK installed with install-preserve (on-device sha equals 572a53d9...), M1 run, master APK put back (on-device sha 4354d24d... again, app launches), lease released. State left: quality Auto, subtitles Off, audio 01. The title was found by typing a Latin query in the app's search (one query, debounced). Playback advanced about 1.5 min in that title. Seen on the box: from the search field LEFT/DOWN did not reach the rail reliably (focus bounced between field and rail); not related to this diff, not investigated.
