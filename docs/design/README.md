# Screen stands

A stand is the full-size emulator a screen was designed on: a real 1920 × 1080 stage, frames
taken off the television, and a slider for every size that would otherwise be guessed. It is the
spec — when the Compose code deviates, the stand is changed in the same breath, or it starts
lying about what was agreed.

Stands used to live only as Artifacts on claude.ai. Those get deleted and the page goes with
them, so the copy here is the one that counts. Publishing to claude.ai stays useful for dialling
the sliders together in a browser; it is a copy, not the source.

## Opening one

Open `<stand>/index.html` in a browser — double-click is enough. Every asset is inlined, so the
file works from disk, from a USB stick, from anywhere.

## Layout

```
docs/design/
  build.py                     inlines the assets
  <stand>/
    README.md                  what was measured, what was agreed, where it landed in code
    index.src.html             the source — edit this one
    index.html                 built, self-contained — never edit by hand
    assets/                    frames off the television, icons, anything the page loads
```

## Changing one

Edit `index.src.html`, then rebuild:

```bash
python3 docs/design/build.py <stand>   # or with no argument for all of them
```

The source refers to an asset by the token `__ASSET:name.ext__`; the build replaces it with a
`data:` URI. An unresolved token or a missing file fails the build rather than shipping a page
with a hole in it.

To put it back on claude.ai, publish the built `index.html` — and pass the existing artifact URL
so the link stays the same instead of spawning a second copy.

## Starting a new one

Copy an existing stand's folder, empty `assets/`, and follow the `designing-a-screen` skill:
ground truth off the device first, then the stage, then the sliders. Frames come from the
television at density 2.0 — 1 dp is 2 px there, and every number in a stand is stated in dp.
