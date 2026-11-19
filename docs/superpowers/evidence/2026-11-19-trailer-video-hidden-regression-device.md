# Device evidence: trailer-video-hidden-regression

Date 2026-11-19. Head fc5faf69, base 3ec85287. Device emulator-5554 (AVD puber_tv_36, API 36, headless), package `com.kino.puber.stage`. Leased through `tools/emulator-resource-lock.sh`, released at the end.

## Probes

| Probe | Command (adb -s emulator-5554) | Reads |
|---|---|---|
| Audio | `shell dumpsys audio`, the `AudioTrack` line with `USAGE_MEDIA` | `state:started` while a trailer plays |
| Layer | `shell dumpsys SurfaceFlinger`, the `SurfaceView[com.kino.puber.stage/...](BLAST)#id` layer | present only when the SurfaceView got a surface |
| Frames | `shell dumpsys SurfaceFlinger --latency '<hash> SurfaceView[...](BLAST)#id'` twice, 1 s apart | the newest present timestamp grew |
| Pixels | `screencap -p`, and `screenrecord --size 960x540` (10 fps frames cut with ffmpeg) | the media area shows video frames, poster, or neither |

On this emulator both `screencap` and `screenrecord` do show the video layer, so video was seen directly. A `screencap` takes about 1.4 s, so the 300 ms fade was read from `screenrecord`.

## D0: install

| Step | Observed | Result |
|---|---|---|
| `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug` | BUILD SUCCESSFUL, APK sha256 `e75b006c29edac6b5ad4a93f9db05ac0b1edc14ca5f7ffb6b29344b18aeb9303` | PASS |
| `tools/android-apk-install-preserve install-preserve ...` | `outcome: installed`, `compatible_replace`, `destructive_action: false`, same signer, version 1.12.1-dev | PASS |
| on-device `base.apk` sha256 | equals the built APK sha256 above | PASS |
| App login after install | app launched into the home screen with content, no login screen | PASS |

## D1: catalogue (Movies), trailers after a card change

Each row is one trailer, probes read 6 to 7 s after the card got focus.

| Trailer | Audio | SurfaceView layer | Frames grew | Pixels | Result |
|---|---|---|---|---|---|
| card 2 (Who's Coming for Christmas) | started | present (#261) | yes (89 rows) | video frame in the media area | PASS |
| card 3 (Guarding Stars) | started | present (#264) | yes (62 rows) | video frame (helicopter), no poster | PASS |
| card 4 ('Tis the Season for Setups) | started | present (#267) | yes (69 rows) | video frame, no poster | PASS |
| card 3 again | started | present (#270) | yes (113 rows) | | PASS |

Card 1 of the grid showed no audio and no layer 8 s after the screen opened: it is the first card of the first open after the install, and a second open of the screen started its trailer (D2). Not compared with base.

## D2: first trailer after reopening the catalogue

BACK to the menu, Home, then Movies again. The screen reopened on the previously focused card. Probe at 3 s: audio idle, layer present (surface created before playback). At 6 s and 10 s: audio started, layer present, frames grew (101 and 127 rows), the screencap shows video. PASS.

## D3: details hero and favourites

| Where | Observed | Result |
|---|---|---|
| Details hero, series (Carrie) | audio started, layer present, frames grew, screencap shows video behind the text | PASS |
| Details hero, movie (Who's Coming for Christmas) | audio started, layer present (#276), frames grew, screencap shows video; BACK returned to the catalogue with the card focused and the poster back | PASS |
| Favourites panel ("Watching"), cards 1 to 4, 7 s each | no audio, no layer | NOT CHECKED: the screen never plays a trailer. `FavoriteScreenContent.kt` passes no `trailerUrl` to `VideoItemGridDetails` (0 occurrences), and the KDoc of `VideoItemGridDetails.kt:222` says only the catalogue and details panels play trailers |

## D4: poster behaviour of detail-poster-trailer-flicker

| Check | Steps | Observed | Result |
|---|---|---|---|
| M1 | `screenrecord` while 5 DPAD_RIGHT presses 0.7 s apart on a catalogue row | no frame with the skeleton colour `#625B71` and no flat black frame in the media area; the info text showed its own placeholder rows while the media area kept the previous picture | PASS |
| M2 | `screenrecord`, DPAD_RIGHT to a card, rest | poster crossfaded in at 1.7 s and stayed unchanged for 2.7 s (frame diff 0); video began at 4.5 s through intermediate frames (media-area mean 109 to 92 to 72) over about 0.3 s; no black, skeleton or hard cut | PASS |
| M3 | let one trailer play to its end (audio stopped about 64 s) | the last frames are video, then one frame with the video layer skewed, then the poster; no skeleton or black frame. A card change during a trailer (M1, M2) crossfaded poster to poster | PASS, with the skewed frame noted below |
| M5 | fitted (letterboxed) trailer | no trailer with a fitted layout was found on the catalogue or the two details screens opened | NOT CHECKED |

Skewed frame: one `screenrecord` frame at the trailer end shows the video layer rotated and scaled while the poster comes back. It is probably the SurfaceView teardown seen by the recorder. The base was not recorded, so whether 1.12.1 shows it too is unknown.
