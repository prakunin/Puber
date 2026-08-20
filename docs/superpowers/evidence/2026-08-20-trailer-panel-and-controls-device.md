# Trailer work on the television — 2026-08-20

Everything below was measured on `192.168.1.121:5555` (KM6, 1920×1080 at density 320, so 960×540 dp)
against the dev build. Nothing here is inferred.

## Reading the television when video is on screen

`adb exec-out screencap` returns a blank 8159-byte PNG whenever a video surface is being rendered,
and `screenrecord` blanks the same way — so a capture that *fails* is itself the evidence that video
is playing. Whether a capture blanks depends on the content: the trailer for «Алхимия душ» captured
normally, the one for «Мятеж» did not. Where a picture was needed, that title supplied it.

Two probes carried the rest:

- `adb shell dumpsys audio | sed -n '/players:/,/^$/p'` — the panel preview sets
  `CONTENT_TYPE_MOVIE`, the full-screen overlay leaves `CONTENT_TYPE_UNKNOWN`, so the two are told
  apart by their attributes, and `state:started` / `state:paused` says which is running.
- `puber://content/items/<id>` opens an item directly. Blind key sequences drifted onto the wrong
  title repeatedly; the deep link is what made the bare-path case reproducible.

## The panel

The picture occupies the right three quarters of the panel and the description's last third lies on
top of it. Confirmed by screenshot on both the catalogue and the details screen: the picture's left
edge falls at x≈480 of 1920 (0.25) and the text ends at x≈750 (0.39).

## The trailer on the details screen

| Behaviour | Evidence |
| --- | --- |
| Starts two seconds after the item renders | capture blanks at +2 s, not before |
| Scrolling to the info page stops it | capture succeeds again, no player left |
| The Trailer button does not leave two playing | exactly one active `AudioTrack` in `dumpsys audio` |
| Returning from the full-screen trailer does not restart it | capture succeeds, zero active players |

## The full-screen trailer's controls

| Behaviour | Evidence |
| --- | --- |
| Title, elapsed and remaining time, progress bar | screenshot: «Алхимия душ», `0:14` / `-0:28` |
| LEFT and RIGHT seek ten seconds | `seek 10000 from 4844 to 14844`, `+10000 → 25648`, `-10000 → 16493` |
| OK pauses and resumes | `state:started` ↔ `state:paused` |
| Controls fade after 4.5 s | screenshot at +5 s shows video and no controls |
| Back closes and returns focus | screenshot: details screen, Trailer button focused, zero players |

Before the fix the remote did nothing at all: no key event ever reached the overlay, because nothing
in it held focus. That was measured, not guessed — a temporary log on the overlay's key handler
stayed silent for every press until focus was moved onto the progress bar.

## Why most trailers never played

The item payload gives the trailer as a bare storage path for most titles:

    "trailer":{"id":126301,"file":"/trailers/d/02/d88196ed87903a217508d759ad85a6a4.mp4"}

and as a signed CDN link for a few:

    "trailer":{"id":119632,"url":"https://86feaca6-….ams-static-01.cdntogo.net/hls/…"}

Counted in the app's own cache (`databases/puber.db`, table `cached_payload`, pulled with
`run-as … cat`): of 192 cached items, 168 carry a trailer, and of those **159 give only a path
against 9 that give a link**. media3 resolves a path against the filesystem and fails with `ENOENT`,
which is why both the button and the preview came up empty on all but a twentieth of the catalogue.

`items/trailer` returns the signed link, but only under the right parameter name. Probed on the
television against item 126301:

| Request | Answer |
| --- | --- |
| `items/trailer?sid=126301` | `{"status":404,"error":"Requested trailer not found."}` |
| `items/trailer?id=126301` | `{"status":200,"trailer":[{"id":126301,"url":"https://…cdntogo.net/hls/…"}]}` |
| `items/media-links?id=126301` | `{"status":400,"error":"Отсутствуют обязательные параметры: mid"}` |

`sid` is what the client's unused `getTrailerUrl` had always passed. Note also that `trailer` comes
back as a **list** here, while the item payload holds a single object.

After the fix, item 126301 — deep-linked to directly — plays its trailer in the panel
(`CONTENT_TYPE_MOVIE`, `state:started`) and from the button (`CONTENT_TYPE_UNKNOWN`, `state:started`),
with zero `FileNotFoundException` in logcat where before there was one within 150 ms of every
attempt.
