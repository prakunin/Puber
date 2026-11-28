LENS: edge  ROUND: 1  BASE: 8c3117ea

No findings.

### Unproven
- /home/dev/orca/workspaces/Puber/trailer-preview-load-control/app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt:193 A trailer above about 10 Mbit/s reaches the 12 MiB `targetBufferBytes` before the 10 s `minBufferMs`. With `prioritizeTimeOverSize = false`, `DefaultLoadControl` stops loading once it holds 12 MiB, though it still starts playing at that point, so this is a stall risk and not a deadlock. The device run covered one trailer, "Shaun the Sheep". It becomes real only if a trailer URL that `TrailerLinkInteractor` returns turns out to be a progressive stream at that bitrate, or the box shows a stall partway through a high-bitrate trailer.
