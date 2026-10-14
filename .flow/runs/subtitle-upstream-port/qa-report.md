# QA report: subtitle-upstream-port

## Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 1460 tests on disk, 0 failures, 0 errors, 0 skipped; tasks up to date for HEAD 0127f0c3, so detekt's "compiler errors found during analysis" line was not printed this run; it is recorded on master in docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md and plans/2026-10-12-code-quality-audit.md (83 / 7 errors, exit 0), so it is pre-existing; master be8395a4 not built)
DEVICE: emulator-5554 (puber_tv_36, API 36), leased and released; install-preserve outcome `installed` (compatible_replace, same signer); on-device base.apk sha256 3a38a332...7376 matches the built APK

ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 Off by default | PASS (HLS DEFAULT/FORCED manifest NOT CHECKED, accepted) | Round 3 device: Slow Horses S4E6 from start (no rows) `Off`; Psyren S1E1 (9 rows, in-app Russian) `Выкл.` checked, no text. Pick `Русский` on S1E1, Next to S1E2: `Выкл.`, no leak. Outlander S2E3 with `AI-generated`, Next to S2E4: `Off`, no text. Unit tests as before. |
| AC-2 Off survives rebuild | NOT CHECKED | Unchanged; no unit means, optional smoke not run. |
| AC-3 explicit and restored choice | PASS | Round 3 device: Outlander S2E3 saved `AI-generated` renders from the first seconds (resume prompt, after Continue, and after Previous from S2E4); explicit `Off` hides text, picking `AI-generated` again brings it back; Psyren S1E1 saved `Русский` restored and rendered Russian text. |
| AC-4 no positional match | NOT CHECKED | Unchanged. Psyren `Русский` rendered text on its HLS stream, but that is not the test (no positional-match fixture). |
| AC-5 language names | PASS | Round 3 device, in-app Русский: `Выкл.`, `Созданные ИИ`, nine Psyren language names, `Аа Размер Средний`, root labels in Russian. Restored to `Same as the device`. Bibliographic-code mapping (`SubtitleLabels`) not observable (no such title); unit `SubtitleLabelsTest`. |
| AC-6 forced | PASS (unit only) | Unchanged. Device NOT CHECKED (no title). |
| AC-7 collisions | PASS (unit only) | Unchanged. Device NOT CHECKED (no title). |
| AC-8 Back on fresh media | PASS | Round 3 device: Psyren S1E2 started via Next with controls up, one Back at about 3 s hid controls, video kept advancing, still MainActivity. Resume prompt on Outlander S2E3 seen again (`Продолжить` focused, alone). |
| AC-9 regressions | PASS | Gate above (1460 tests, detekt, lint); no crash-buffer entries after the device pass. |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-player-fixes/docs/superpowers/evidence/2026-10-14-subtitle-upstream-port-device.md (Round 3 section appended, audit passed, committed 0973d92b in the worktree)

DEFECTS: none
Observations, not defects: Slow Horses S5E1 opened to a black picture stuck at 0:00 (stream or emulator issue, no subtitle involvement; S4E6 used instead). Back from the player after closing panels does not always match the fresh-media path (same as earlier rounds).

UNAVAILABLE: none

MANUAL:
- Forced and duplicate-language labels (`частичные`, `вариант N`) and bibliographic ISO 639-2 codes (e.g. `fre`, `ger`, `chi`) on a real title: open Settings > Subtitles, confirm one-line labels, panel at most 420 dp.
- AC-4: on an HLS title with spoken dialogue and an API subtitle row, pick the row and confirm text shows (accepted until subtitle-sideload-merge).
- AC-2 (optional): with Off selected, change Settings > Advanced buffer preset during playback; text stays off.
- Account state left behind: Slow Horses S4E6 was started from the beginning (saved position 50:19 may be replaced); Psyren S1E1 now holds an explicit `Off` (was unset); Outlander left on `AI-generated` as found.

## Round 2

Superseded by Round 3 for AC-1, AC-3, AC-5, AC-8; kept for history in the evidence file (Round 2 section). All ACs passed or were carried as above; defect 1 of round 1 was fixed by 0bf26ee5.
