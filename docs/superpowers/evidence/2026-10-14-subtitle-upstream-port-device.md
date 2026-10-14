# subtitle-upstream-port: device smoke on the rebased build

Date: 2026-10-14. Device: emulator-5554, AVD `puber_tv_36` (Android TV, Android 16 / API 36, x86_64,
1920x1080). Build: this worktree (branch `subtitle-player-fixes`, HEAD a4f0aeeb on base be8395a4),
`app-dev-debug.apk` `com.kino.puber.stage` 1.9.70-dev, sha256 `73d8f349c2c2100d4df7135ee19efb3972f1524647c76da01b4081ebd9fd9165`.
Installed with `tools/android-apk-install-preserve` (`compatible_replace`, same signer, no data lost);
the on-device `base.apk` has the same sha256 as the built file. Input: remote key events only, device
leased and released. No screenshots are kept (the home and detail screens carry account data).

Static gate before this: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`
passed (1458 tests, 0 failures, 0 skipped; Gradle reported the three tasks up to date for this exact
tree), no crash-buffer entries for the package after the pass.

Scope follows the owner's decision: a short smoke. The DEFAULT/FORCED manifest case, Off across a
player rebuild, and forced and variant labels on a real title are not covered here.

## Checks

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| M1 Off by default with a row available | Fresh movie "Compostelle" (Not started, API lists 1 subtitle). Start, open Settings, Subtitles page | Door reads `Off`, page first row `Off` focused and checked, `AI-generated` row unchecked, no text | Door `Off`; page rows `Off` (checked, focused), `AI-generated`, `Aa Size`; no subtitle text in the opening frames | PASS |
| M1 Off by default, movie with no API rows | Fresh movie "Moon le Panda", Settings, Subtitles | `Off` checked, no text | `Off` checked, only `Off` and `Aa Size` rows, no text | PASS |
| AC-3 restored choice on next media | Outlander S2E3 with a choice saved on the earlier pass (`AI-generated`) | Saved subtitle rendered, door and page mark it | Russian text rendered from the first frames; door `AI-generated`; page `AI-generated` checked | PASS |
| AC-3 explicit pick | On S2E3 pick `Off`, then `AI-generated` again | Off hides text; picking the row shows text again | After `Off` the page checked `Off`, no text on speaking frames; after `AI-generated` Russian text rendered | PASS |
| Off saved then next media | Episode restarted after `Off` was saved | Picker `Off`, no text | Door `Off`, no text while a character spoke | PASS |
| M4 Back on fresh media, no prompt | Fresh movie, controls up with play/pause focused; press Back once within ~2 s | Controls hide, playback continues, still in the player | Controls hid, video kept running (progress advanced), activity unchanged; a later Back left the player | PASS |
| M4 note | Back pressed after the 6 s auto-hide had already hidden the controls | Leaves the player (controls already hidden) | Left to the detail page | as designed |
| M7 resume prompt | Outlander S2E3 (saved progress 15:10) | Prompt alone, no controls over it, focus on `Continue` | Prompt at bottom-left, `Continue` focused, no control pills; only the dimmed buffering/seek strip at the bottom edge while buffering, gone once buffering ended | PASS (note 1) |
| M7 after the prompt | `Continue` and `From beginning` | Playback; controls follow normal behaviour | `Continue` resumed at 15:10 with controls visible on Buttons; `From beginning` started with controls hidden and the skip-recap chip, Back then left the player | observation (note 2) |
| M8 Episodes switch | Controls up, open the Episodes panel (focus on current episode), move to episode 4, OK | Panel closed, controls visible, focus on the buttons row, no panel over new media | Switched to S2E4; panel gone; controls visible; play/pause focused; no resume prompt (from 0:00) | PASS |
| M5 panel width, no ellipsis | Settings root and Subtitles page, English | Panel at most 420 dp, no ellipsis | Settings panel about 345 dp wide (690 px at 1080p), Subtitles page about 240 dp; every label on one line | PASS for the rows shown (`Off`, `AI-generated`); forced and variant rows not measured |
| AC-5 Russian labels, in-app language Russian on an English device | Interface language set to Русский in Settings; reopen media and the picker (also tried the OS per-app locale) | `Выкл.`, `Созданные ИИ` | UI chrome Russian (`Субтитры`, `Аа Размер`, `Средний`) but the picker rows and the door value stay `Off` and `AI-generated` | FAIL (see defect 1) |

Not checked: forced (`forced` / `частичные`) and duplicate-language (`variant N` / `вариант N`) labels
on a real title (no title named; unit tests only); Off across a rebuild (M6); what a picked API row does
on an HLS title (AC-4: the only subtitle-carrying titles opened had no dialogue in the frames I could
catch); the Russian device-locale case (device locale not changed); ISO-3 map cost on TV.

## Notes

1. On the resume prompt, a faint "Buffering... 0:00 / -54:27" strip shows at the bottom edge while the
   media is still buffering. It fades with the controls; it is not a focusable control.
2. After the resume prompt is answered the controls are hidden for `From beginning` (a skip-recap chip is
   up), so the first Back leaves the player. After `Continue` the controls were visible. This is the path
   the owner decision calls "normal behaviour"; recorded as observed, not as a pass or fail.
3. Settings changed on the emulator during the pass and restored: interface language back to "Same as the
   device"; OS per-app locale cleared; saved subtitle choice left on `AI-generated` as found.
4. Account mutations caused by watching: about 40 s of S2E3 and S2E4 and short plays of two movies; no
   other writes.

## Round 2: in-app language fix (commit 0bf26ee5)

Date: 2026-10-14. Device: emulator-5554 (`puber_tv_36`, English device locale), leased and released.
Build: HEAD 0bf26ee5, `app-dev-debug.apk` sha256 `d10a2dfdca71c47bbf25eb77aecbf84f784f76a16f759a741d4597bac5d8ce3a`,
installed with `tools/android-apk-install-preserve` (`installed`, `compatible_replace`, same signer, no data lost);
the on-device `base.apk` has the same sha256. Input: remote key events only. No screenshots kept.

Static gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL
(1459 tests, 0 failures; Gradle reported the tasks up to date for this exact tree). No crash-buffer entries for the package afterwards.

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| AC-5 door and rows, in-app Russian, no saved choice | Settings > General > Interface language > Русский. Fresh series "Psyren" (API lists 9 subtitle languages), player Settings, Subtitles | Door `Выкл.`; first row `Выкл.` checked; language rows in Russian | Door `Выкл.`; page `Выкл.` (checked), then `Китайский`, `Русский`, `Португальский`, `Польский`, `Итальянский`, `Французский`, `Испанский`, `Немецкий`, `Английский`, `Аа Размер Средний` | PASS |
| AC-5 AI row, in-app Russian | Outlander S2E3 (saved choice `AI-generated`), Continue, Settings, Subtitles | Door and row `Созданные ИИ` | Door `Созданные ИИ`; page `Выкл.`, `Созданные ИИ` (checked); Russian text rendered | PASS |
| AC-5 other mapper labels, Russian | Settings > Дополнительно | Buffer preset names in Russian | `БУФЕР`: `Авто`, `Малый (16 МБ / 60с)`, `Средний (32 МБ / 120с)`, `Большой (64 МБ / 180с)`, `Макс (128 МБ / 300с)`; `СЕТЬ`: `Быстрый DNS`, `Вкл.`; root rows `Аудио`, `Качество`, `Скорость`, `Поток` | PASS (aspect-ratio row not reached; no such row on the root page) |
| AC-5 "Same as the device", English | Interface language back to `Same as the device`; reopen Outlander S2E3, Settings, Subtitles | `Off`, `AI-generated`, `Aa Size Medium`, English root labels | Door `AI-generated`; page `Off`, `AI-generated` (checked), `Aa Size Medium`; root `Audio`, `Subtitles`, `Quality`, `Speed`, `Advanced`, `Stream` | PASS |
| AC-1 Off default (regression) | Fresh series "Psyren" ep 1 and "Nia Liston" ep 2, no saved choice, in Russian | Door `Выкл.`, `Выкл.` checked, no text | Psyren: `Выкл.` checked, no text over the picture (the Japanese captions are burned into the video). Nia Liston ep 2: `Выкл.` checked, no API rows | PASS |
| AC-8 Back on fresh media (regression) | Fresh episode starts with controls up; one Back about 4 s in | Controls hide, video continues, not left | Controls hid, picture kept playing (progress advanced on the next key press), still in the player | PASS |
| AC-8 resume prompt (regression) | Outlander S2E3 with saved progress | Prompt alone, `Continue` focused, no controls over it | Prompt `Продолжить просмотр с 15:44?` with `Продолжить` focused and no control pills; saved AI subtitle rendered | PASS |

Notes:
1. Interface language was found as `Same as the device` and is restored to it; the final check ran in that state. Saved subtitle choice left on `AI-generated` as found.
2. Back pressed after the controls had been shown by closing a settings panel left the player (observed twice, about 1 minute into playback); that is not the fresh-media path of AC-8 and matches the round 1 note. Recorded as observed.
3. Account mutations caused by watching: a few seconds to about a minute of Psyren ep 1, Nia Liston ep 1 and 2, and Outlander S2E3 (played twice); no other writes.
4. Not covered: forced and duplicate-language labels on a real title, Off across a rebuild, HLS DEFAULT/FORCED manifest, Russian OS locale.
