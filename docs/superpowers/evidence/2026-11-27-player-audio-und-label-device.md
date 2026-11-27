# player-audio-und-label: device evidence (round 1)

Date: 2026-11-27. Slug: player-audio-und-label.

## Build and device

- Device: owner's TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease.
- Candidate: this worktree's dev APK at HEAD `7248c54c`, sha256 `d5dbf429f4dff1b9940ad166ae58dc483931fe4027765788c466e872569c55a9`, installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, same signer, no destructive action). On-device `base.apk` sha256 checked equal to the candidate.
- Before: the box held the master dev APK (sha256 `4354d24d...c128`), logged in. `make DEVICE=192.168.1.110:5555 auth-save` was run first.
- After: the master dev APK was put back with install-preserve (outcome `installed`); on-device sha256 is again `4354d24dda6ce6a7034f5dde1fdb9e2b3a03730ea2c1decdfe8a15c6f632c128`. Lease released.
- One movie (HLS, two dubbings) played at human pace; no scripted API requests. Side effect: a resume position of about 5 minutes now exists for that movie.

## AC-7 (manual check 1)

| Step | Expected | Observed | Result |
|---|---|---|---|
| 1. Open a movie with named dubbings, start playback | Plays | Details screen showed "Дорожек: 2"; playback started and resume prompt appeared on reopen | PASS |
| 2. Controls, Settings, row "Аудио" | Rows read manifest names; no "und"; no "Track N" in place of a name; no focus loss or crash | Settings row read "01. Многоголосый. MUZOBOZ (RUS)"; Audio list rows "01. Многоголосый. MUZOBOZ (RUS)" and "02. Оригинал (FRE)", focus on the selected row; no crash (screenshot `2026-11-27-player-audio-und-label-audio-list.png`) | PASS |
| 3. Subtitles list | Language names, Off row present | This movie carries no subtitle tracks: the list showed only "Выкл." and "Размер". Named subtitle rows were not exercised on the box | NOT CHECKED (subtitle rows) |
| 4. Pick the other audio row, leave the player, reopen | Picked dubbing restored | Picked "02. Оригинал (FRE)", left the player, reopened with "Продолжить"; Settings row read "02. Оригинал (FRE)" (screenshot `2026-11-27-player-audio-und-label-settings.png`) | PASS |
| Restore | Original choice back | Re-picked "01. Многоголосый. MUZOBOZ (RUS)"; list showed it checked | restored |

Screenshots contain only a title row and film frames; no account data.

## Round 1b: AC-7 subtitle rows (manual check step 3)

Date: 2026-11-27. Device: owner's TV box `192.168.1.110:5555`, `com.kino.puber.stage` only, under a lease.

- Before: box held the master dev APK (sha256 `4354d24d...c128`), logged in; launched once, `make DEVICE=192.168.1.110:5555 auth-save` run, then this worktree's dev APK (sha256 `d5dbf429f4dff1b9940ad166ae58dc483931fe4027765788c466e872569c55a9`, HEAD production sources unchanged since `7248c54c`) installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`); on-device base.apk sha256 equal to the candidate.
- After: master dev APK put back with install-preserve (outcome `installed`); on-device sha256 `4354d24dda6ce6a7034f5dde1fdb9e2b3a03730ea2c1decdfe8a15c6f632c128`. Lease released.
- Item: "Квазимодо, душа Парижа" (HLS, details screen "Дорожек: 4 · Субтитры: 11"), opened from History, resumed at 17:58, human-paced remote input, no scripted API requests. Side effect: resume position moved to about 19-20 min.

| Step | Expected | Observed | Result |
|---|---|---|---|
| Settings on Auto quality | Audio row reads a manifest name | Row "Аудио" = "01. Дубляж (RUS)", Subtitles "Выкл.", Quality "Авто" | PASS |
| Subtitles list on Auto | Language names, Off row present, no "UND" | 12 rows: "Выкл." (checked), Украинский вариант 1/2/частичные, Русский вариант 1/2/частичные, Французский вариант 1/2, Английский вариант 1/2/3 (all 11 tracks, scrolled to the end); no "UND", no "Субтитры N" | PASS |
| Manual quality pick | 720p applies | Quality list: 720p checked, Stream row changed 1920x800 to 1280x534 | PASS |
| Audio list after the pick | Manifest names, no "und" | "01. Дубляж (RUS)" (checked), "02. Дубляж (UKR)", "03. Оригинал (FRE)", "04. Дубляж (RUS) AC3"; screenshot `2026-11-27-player-audio-und-label-round1b-audio-720p.png` | PASS |
| Subtitles list after the pick | Language names, Off row present, no "UND" | Same 11 language-named rows plus "Выкл." (checked); screenshot `2026-11-27-player-audio-und-label-round1b-subtitles-720p.png` | PASS |
| Restore | Original state | Quality back to "Авто" (Stream 1920x800), Subtitles "Выкл.", Audio "01. Дубляж (RUS)" untouched; left the player | restored |

No crash, no focus loss seen. Not done: picking a subtitle row (no subtitle was turned on, to avoid account-side state), and manual step 4 (covered in round 1 on another movie). Other screenshots from this pass were deleted because film frames in them were unsuitable for the repo.

## Round 2: subtitle selection after fix d1ed06ad (AC-4, AC-5, AC-7)

- Build: worktree dev APK at 2d52a21e, sha256 b43f040d...920d, verified on device after install-preserve (outcome "installed", compatible_replace). Before: master dev APK (4354d24d...c128) was installed and launched once, `make auth-save` run.
- Device: owner's box 192.168.1.110:5555, com.kino.puber.stage only, human-paced remote input, no scripted API requests.
- Item: "Квазимодо, душа Парижа" (HLS, 4 audio, 11 subtitles), opened from History, resumed at about 18 min. Side effect: resume position moved to about 22-23 min.
- No screenshots are kept in the repo: every player frame here shows film content.

| Step | Expected | Observed | Result |
|---|---|---|---|
| Quality Авто, Settings > Субтитры | Rows named by language, no "UND" | Выкл. plus 11 rows: Украинский вариант 1, 2, частичные; Русский вариант 1, 2, частичные; Французский вариант 1, 2; Английский вариант 1, 2, 3 | PASS |
| Pick "Русский · вариант 1" on Auto | Row checked, Settings row shows it, cues render | Check mark on the row, Settings row "Субтитры: Русский · вариант 1", Russian cues on screen in several frames (e.g. two-line cue, single-line cue) | PASS |
| Switch quality to 720p (stream 1280x534) | Same subtitle row stays selected, cues keep rendering | Settings row still "Русский · вариант 1", Subtitles list checks the same row, Russian cues rendered at 720p in four frames | PASS |
| Pick "Английский · вариант 1" at 720p | Selection moves, cue language changes | Check mark on "Английский · вариант 1", English cues on screen ("I'm not going down there." / "We should go.") instead of Russian | PASS |
| Restore | Subtitles Выкл., quality Авто, audio 01 | Settings read: Аудио "01. Дубляж (RUS)", Субтитры "Выкл.", Качество "Авто", Поток 1920x800 | PASS |

No crash, no focus loss, no "UND" or "Субтитры N" row. Master dev APK (4354d24d...c128) put back with install-preserve, sha256 verified on device; lease released.
