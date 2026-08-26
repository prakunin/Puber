---
name: designing-a-screen
description: Use when reworking how a Puber TV screen looks — choosing the next screen to redesign, judging sizes and spacing against the real thing, or porting an agreed design into Compose.
---

# Designing a Screen

A television screen cannot be judged in a browser at browser scale. Take ground truth off the
device, rebuild it as a full-size emulator artifact, let the user dial the sizes, then port the
agreed numbers into Compose. Offer the screens in `ui/feature/*` rather than asking which one.

Not for a one-line style fix, and not for behaviour with no visual question.

## Defaults this repo has settled on

Start here, not from Material defaults, and let the user push back up.

- **Smaller than feels right.** Reworking the content screen moved every size down and none up:
  buttons 40 → 24 dp, episode card 150 → 70 dp, rail 210 → 116 dp, caption 14 → 11 sp, still
  75 % → 56 % of the width.
- **Closer to the edge.** Page peek 16 dp, side margin 8 dp, everything on one left edge —
  deliberately inside Google's 48 dp TV safe zone. Say so once; do not quietly revert it.
- **Pinned, not flowed.** Title anchored top, actions bottom, captions one line with an ellipsis.
  An element must not move because its content changed length.
- **A slider, not a number.** Anything you would otherwise guess goes on a slider. "Зафиксируй"
  means: make the dialled values the defaults.
- **Never hide data to protect a layout.** Show the whole text and scroll it. Truncating on your
  own judgment gets overruled, and rightly.

## 1. Ground truth, never memory

```bash
adb shell service call SurfaceFlinger 1008 i32 1     # disable HW overlays
adb exec-out screencap -p > shot.png
adb shell service call SurfaceFlinger 1008 i32 0     # ALWAYS restore
```

The address comes from `DEVICE` in `.env`; never hardcode one.

**A trailer leaves a white hole in `screencap`** — a `SurfaceView` is invisible to the capture
until overlays are off, and the device composites in software until you restore them.

Measure in pixels (`ffmpeg -vf crop=… -f rawvideo -pix_fmt rgb24`, scan rows), don't eyeball.
**dp = px / 2**: a 1080p television is density 2.0.

## 2. The app's own content

Placeholders make a mock nobody can judge. Both sources sit on the device, and a deep link
`puber://content/items/<id>` fills them:

- Artwork — `run-as com.kino.puber.stage cat cache/image_cache/<hash>.1`
- Cast, director, quality — `databases/puber.db`, `cached_payload`, `content:v1:item:<id>`

## 3. The emulator artifact

**REQUIRED:** load `artifact-design` first.

- A real `1920 × 1080` stage, `transform: scale()` to fit, a readout naming the scale
- `PuberTheme` colours and type verbatim; Roboto in the screen, monospace for the chrome
- One `geom()` owning every size, with clamping; a slider shows the value that *applied*
- Guides overlay labelling edges and fractions; arrow keys move focus, so focus order is visible
  before it is written

**The artifact is the spec.** When the code must deviate, change the artifact in the same breath
and republish, or it starts lying about what was agreed.

**It lives in the repo, not on claude.ai.** Artifacts get deleted and the page goes with them.
Author it as `docs/design/<screen>/index.src.html`, build with `python3 docs/design/build.py
<screen>`, and publish the built `index.html` — passing the existing artifact URL, so the link
survives. Record the agreed numbers and what is still open in `docs/design/<screen>/README.md`.
See `docs/design/README.md`.

## 4. Porting into Compose

- **Check whether a constant is shared.** `MediaWidthFraction` and the scrim live in
  `VideoItemGridDetails`, which the catalogue also draws. Parameterise; never retune in place.
- **Pin vertical focus with `onPreviewKeyEvent`.** Geometric search walks past a short chip row to
  the cards below, and the row becomes unreachable by remote.
- **Never tint a raster icon.** `ic_kinopub` is a webp; a solid tint fills its silhouette.
- **Snap a scrolling text block to whole lines**, or it ends mid-letter and reads as breakage.
- Comments and docs here are English, whatever language the conversation is in.

## 5. Verify

```bash
make check              # unit tests + detekt
make itest TESTS=<FQCN> # instrumented, keeps the login
```

Never `connectedAndroidTest` directly: it targets every attached television and uninstalls the
app, taking the KinoPub pairing with it.

Drive the remote, not the mouse. `performClick()` does nothing to a tv-material3 `Surface` — it
listens for key events. Focus a node, then `pressKey`.

## Common Mistakes

| Mistake | What happens |
|---|---|
| Judging the design in the artifact only | Focus paths and overscan show up only on the television |
| Leaving HW overlays disabled | The device composites in software from then on |
| A slider that clamps silently | The readout disagrees with the screen and wrong numbers get recorded |
| A choice built on an unchecked claim | The user decides on a false premise. Verify every option before offering it |
| Deciding what the viewer need not see | Data the API already returns stops reaching the screen |
