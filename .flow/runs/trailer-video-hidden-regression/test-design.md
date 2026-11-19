# Test design: trailer-video-hidden-regression

Base 3ec85287. ACs: `brief.md#Acceptance criteria`; fix T and risks: `design-architect.md`. Levels from `.flow/roles/test-designer.md`. No Compose UI harness exists, so the only unit seam is `surfaceNeedsRedraw` (`design-architect.md#Decisions`). Whether the listener sits on the real SurfaceView and the surface gets created can only be seen on a device.

New test file: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt`. It uses plain JUnit 5 like `VideoDetailsPosterStateTest.kt` and needs no dispatcher extension.

## Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| The wrong APK is checked: emulator-5554 still has the diagnostic build | brief Risks | 3 | 3 | 9 | D0 |
| Vendor HWUI on the owner's TV still skips surface creation | design Risks: rollout | 2 | 3 | 6 | T1, T2; fallback B (state.md Q2) |
| The listener is attached to a view other than the SurfaceView, or `videoSurfaceView` is null, so no traversal reaches `updateSurface()` | design Decisions | 2 | 3 | 6 | D1 (the device is the level where this fails) |
| Headless screencap renders no video layer, so the evidence cannot show video directly | task note | 3 | 2 | 6 | D1 uses SurfaceFlinger layer + `--latency` frames + still absence; T1 shows video |
| Predicate fires for 0x0 or misses a resize (`scaleTrailerToFit` toggle) | design Risks: correctness | 2 | 2 | 4 | unit rows AC-6 |
| Fix regresses the source run's poster behaviour (flash, skeleton, fade) | AC-5 | 1 | 3 | 3 | P0 guard unit rows; D4; T3 |
| The first trailer after opening a screen regresses | AC-2 | 1 | 2 | 2 | D2 |
| An invalidate loop redraws every frame (power) | design Risks: correctness | 1 | 2 | 2 | Accepted: `invalidate()` changes no size, so it causes no layout callback; review item |
| Listener left on a released view | design Risks: lifecycle | 1 | 1 | 1 | Accepted: stateless listener; review checks that add and remove use the same val |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` | Fails if the predicate misses the late first layout, which is the bug path (0x0 → 1350x759) |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_false_whenSizeUnchanged` | Fails if the predicate drops the "different from old" check and redraws on every layout pass |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_true_whenSizeChangesBetweenNonZeroSizes` | Fails if the predicate fires only for 0 → non-zero, so a resize (fit/zoom toggle) gets no traversal |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_false_whenSizeBecomesZero` | Fails if a collapse to 0x0 asks for a redraw that cannot create a surface |
| AC-6 | unit | P1 | `surfaceNeedsRedraw_false_whenOnlyOneDimensionIsZero` | Fails if "non-zero" is read as `width > 0 \|\| height > 0` (e.g. 1350x0) |
| AC-5 | unit | P0 guard | `VideoDetailsPosterStateTest` and `SkeletonAsyncImageVisibilityTest`, all tests unchanged | Fail if the fix touches the 1.12.1 poster seams. `git diff 3ec85287 -- app/src/test/kotlin/com/kino/puber/core/ui/uikit/component` shows only the new file |
| AC-7 | unit | P0 guard | `check.full` in the worktree | Fails on any compile, test, detekt or lint regression (e.g. unused lambda parameters not named `_`, a missing `@UnstableApi` opt-in) |
| AC-1 | device | P0 red | D1 | Fails if the SurfaceView of trailers 2-4 gets no layer or no frames. On base, diagnosis.md: Evidence row 2 shows this failure, and qa does not re-run base |
| AC-2 | device | P1 | D2 | First trailer after screen open loses its video or fade |
| AC-4 | device | P0 red | D3 | Fails if the details hero or favourites keeps the poster opaque over an audible trailer. On base this is the `diagnosis.md#Evidence` row 2 symptom on the shared player call (`VideoItemGridDetails.kt:483`) |
| AC-5 | device | P0 guard | D4 | Fails if the source run's poster behaviour breaks: skeleton or black frame before the first frame, no fade, poster not back at trailer end, poster pixels around a fitted trailer |
| AC-3 | manual (owner TV) | P0 red | T1 | Fails if video is still hidden on the TV's HWUI; on 1.12.1 the TV shows the `diagnosis.md#Evidence` row 2 symptom |
| AC-4 | manual (owner TV) | P0 red | T2 | Fails if the hero or favourites still hide video on the TV; on 1.12.1 they show the row 2 symptom |
| AC-5 | manual (owner TV) | P1 | T3 | Smoothness regression the emulator cannot show |

## Failing-first test

`surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` is written first. On base the seam does not exist (`git show 3ec85287:app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt` has no `surfaceNeedsRedraw`; the factory at `:126-132` adds no listener), so the test does not compile on base. The bug itself shows only on device (D1).

## Device checks (qa, emulator)

Package `com.kino.puber.stage` (dev flavor). Auto-trailer on. Keys go through `adb -s <serial> shell input keyevent` (`DPAD_RIGHT`, `DPAD_LEFT`, `DPAD_DOWN`, `DPAD_CENTER`, `BACK`). qa reads the probes below and changes no committed code: no temporary logging is built into the APK under test. `onRenderedFirstFrame` is proven indirectly. It is the only setter of `trailerRendered` (`VideoItemGridDetails.kt:469`), and `trailerRendered` is the only thing that drops the still to alpha 0 (`:506`). So a still that is gone after the fade, plus frames on the SurfaceView layer, proves the first-frame callback fired.

Probes, run about 3 s after a trailer starts:

| Probe | Command | Pass |
|---|---|---|
| Audio | `dumpsys audio \| grep -A2 USAGE_MEDIA` | `state:started` (a trailer is running) |
| Surface layer | `dumpsys SurfaceFlinger --list \| grep SurfaceView \| grep com.kino.puber.stage` | One SurfaceView layer for the app's window |
| Frames | `dumpsys SurfaceFlinger --latency '<exact layer name from --list>'` | Non-zero timestamp rows, growing between two reads 1 s apart |
| Still | `screencap -p` pulled after the 300 ms fade | Media area shows no poster pixels (headless: black/empty hole) |

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| D0 | 1. Lease a device with `tools/emulator-resource-lock.sh`. 2. Install this worktree's dev APK with `tools/android-apk-install-preserve`. 3. Compare the on-device `base.apk` sha256 with the built APK. | Hashes match; the app keeps its login | Uninstall, data clear, the diagnostic build still installed |
| D1 | 1. Open the catalogue grid and let the first trailer start (dwell over 2 s). 2. `DPAD_RIGHT` to a card with a trailer, dwell, run all probes. 3. Repeat step 2 twice more. | All probes pass for each of the three trailers | Audio `started` with no SurfaceView layer, no latency rows, or the poster in the screencap |
| D2 | 1. `BACK` out to home and open the catalogue again. 2. Dwell on the first card with a trailer and run the probes. | All probes pass, as on 1.12.1 | No layer or the still opaque on the first trailer |
| D3 | 1. Open a details screen with a trailer and wait for the hero trailer. Run the probes. 2. `BACK`, open favourites, change cards three times with dwells as in D1 and run the probes each time. | All probes pass on the hero and on every favourites trailer | Opaque still over audible playback; focus loss on `BACK` |
| D4 | 1. Run the source run's M1, M3 and M5 (`.flow/runs/detail-poster-trailer-flicker/test-design.md#Manual checks (device)`) on this build. 2. Run its M2: rest on a card with a trailer for over 2 s and take a screencap burst from URL arrival (audio `started`) through the first frame and the fade, about every 50 ms. | Step 1: results as recorded for 1.12.1. Step 2: poster at full opacity until the first frame, then fading out over about 300 ms to the blank hole | Skeleton or empty frame between posters, poster not back at trailer end, poster pixels around a fitted trailer; in step 2 a skeleton, black or empty frame before the fade starts, or a hard cut instead of a fade |

Headless screencap draws no video layer, so D4 step 2 sees the still and its fade over a blank layer: it can catch a skeleton or black frame and a missing or abrupt fade, not a flash between the poster and real video pixels; T3 covers that.

Release the lease after D4. The evidence directory passes `tools/mobile-evidence-audit.sh` and holds no raw logcat or trailer URLs.

## Manual checks (owner, TV)

The conductor builds the APK and the owner installs and watches. Agents never touch the TV (state.md: Decisions).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| T1 | 1. Open the catalogue and let a trailer play. 2. Move to another card with a trailer, wait. Repeat at least three times. | Every trailer shows video; the poster fades out over it in about 300 ms | Audio with the poster still fully visible (the 1.12.1 symptom) |
| T2 | 1. Repeat T1 on the favourites panel. 2. Open a details screen with a trailer. | Same as T1 on both | Same as T1 |
| T3 | Watch trailer start, trailer end and fast card changes in the catalogue | No skeleton or black flash, poster back at once at trailer end, smooth crossfades | Any flash or hard cut absent on 1.12.1 |

If T1 or T2 fails, the run switches to fallback B (`design-architect.md#Fallback B`). The AC-6 unit rows are then removed with the seam, and D1-D4 and T1-T3 run again.

## perf track

N/A: perf track off.

## UNTESTABLE

None.
