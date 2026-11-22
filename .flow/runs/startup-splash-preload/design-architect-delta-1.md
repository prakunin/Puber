# Design delta 1 (architect): images decoded under the splash

Base: spec `docs/superpowers/specs/2026-11-21-startup-splash-preload.md` (Design, Architect). Problem and measurements: `qa-report.md` Round 1b (trace) and Round 1c (fixed anchor). Paths are relative to `app/src/main/java/com/kino/puber/`. Coil facts are from the coil-core-android 3.6.3 bytecode in `~/.gradle-agents/caches` (`javap`).

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the post-reveal burst comes from | The requests the composed screen itself issues: Home is composed and laid out under the splash (`ui/feature/root/component/StartupSplashHost.kt:30`), so hero (`core/ui/uikit/component/HeroCarousel.kt:225-232`) and cards (`core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:96-103`) already start their loads before the reveal; the reveal rule just does not wait for them | Round 1b: 3.5 s of decode CPU falls between `revealed` and key 1 |
| Fix | Readiness gains a second input: no image request that has resolved its size is in flight. `covered = begun && !forced && !revealedOnce && !(ready && !imagesBusy)`; the cap (2.5 s from `onCreate`, `core/startup/RevealGate.kt:7`) is unchanged | Option (c) generalised: the landing screen's own visible requests (hero backdrop, first-row cards, LazyRow and pager prefetch) are decoded while covered; image shaders compile under the splash with them |
| How "images in flight" is known | New `core/startup/StartupImageActivity` (a Coil `EventListener`): a request joins a synchronized identity set at `resolveSizeEnd`, leaves it at `onSuccess`/`onError`/`onCancel`; it calls `onBusyChanged(Boolean)` only on empty/non-empty edges. Installed once with `.eventListener(...)` in `PuberApp.newImageLoader` (`PuberApp.kt:100-125`) | One listener sees every request of the singleton loader; no composable changes. Counting from `resolveSizeEnd` skips requests that never get measured (a size that never resolves would otherwise hold the splash to the cap) |
| Once revealed, stays revealed | `RevealGate` latches `revealedOnce` on the first uncover; only `begin()` (`RevealGate.kt:21`) clears it, together with `ready` and `forced`; `imagesBusy` is process state and survives `begin()` | Without the latch, a card load after the reveal would put the splash back and drop keys (`MainActivity.kt:62`) |
| Update prompt opening under the splash | `App.kt:185` calls new `StartupReveal.revealNow()` (sets `forced`) instead of `markReady()` | The prompt needs the screen now (AC-12); it must not wait for Home's images |
| Thread safety | `RevealGate` recomputes `covered` under `synchronized`; Coil callbacks may arrive off main (`onCancel`) | `MutableStateFlow` writes from two threads would otherwise race the latch |
| Prefetch, option (a) | Not added | The same requests with the same keys and sizes already run under the splash; a second request path duplicates size derivation, and Coil reuses a sampled bitmap only when it is at least the requested size (`MemoryCacheService.isCacheValueValidForSize`; size enters the key only with transformations), so a mismatch decodes twice |
| Decode parallelism, option (b) | Not in this delta; fallback (Open question 2) | `bitmapFactoryMaxParallelism` (default 4, one semaphore shared by `StaticImageDecoder` and `BitmapFactoryDecoder`) at 2 halves the decode rate under the splash, which is exactly the work the new rule waits for |
| Smaller server renditions | Not available | `Posters` has `small`, `medium`, `big`, `wide` only (`data/api/models/Models.kt:198-203`); 16:9 cards and the hero need `wide` (`core/ui/model/VideoItemUIMapper.kt:32`, `:58-64`) |

## Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `PuberApp.kt:100-125` | `.eventListener(StartupReveal.imageActivity)` on the `ImageLoader.Builder` |
| ViewModel | N/A | `HomeVM.kt:202` and `core/ui/PuberVM.kt:94` keep calling `markReady()`; its meaning becomes "content ready" |
| Screen and DI | `core/startup/StartupImageActivity.kt` (new) | `EventListener` subclass with the in-flight set and `onBusyChanged: (Boolean) -> Unit` |
| Screen and DI | `core/startup/RevealGate.kt:21-43` | Inputs `ready`, `forced`, `imagesBusy`, latch `revealedOnce`; `onImagesBusy(Boolean)`, `revealNow()`; `onCap()` sets `forced`; `reset()` clears all |
| Screen and DI | `core/startup/StartupReveal.kt:7-23` | `val imageActivity = StartupImageActivity(gate::onImagesBusy)`, `fun revealNow()` |
| Compose content | `ui/feature/root/component/App.kt:185` | `revealNow()` replaces `markReady()` |
| Compose content | `MainActivity.kt:76-84` | None: `begin()` and the cap effect stay |
| Resources | N/A | No strings or dimensions |

## Alternatives rejected

- Separate prefetch of hero and first-row images during the splash: duplicates requests the composition already makes (Decisions, option (a)).
- Wait for the hero image only: leaves the 15 card decodes (3.2 s CPU, Round 1b) after the reveal.
- Per-composable "visible image" tracking through a `CompositionLocal`: touches `HeroCarousel`, both card types and `SkeletonAsyncImage` for the same signal one listener gives.
- Background-priority decoder dispatcher (`decoderCoroutineContext`): same trade-off as option (b), plus hardware-bitmap upload on a low-priority thread.
- `crossfade(true)` removal (`VideoItemHorizontal.kt:100`, `HeroCarousel.kt:229`): visual change; memory-cache hits after the reveal skip the crossfade anyway.

## Risks

| Risk | Where | Guard |
|---|---|---|
| Splash covers again after the reveal when a new image load starts, dropping D-pad keys | `RevealGate.kt`, `MainActivity.kt:62` | `revealedOnce` latch; `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain` |
| Reveal moves later: a cold disk cache, new items or a slow image CDN hold the splash to the cap, and AC-13's `revealed` median (<= 2502 ms) can fail | `RevealGate`, `StartupImageActivity` | Cap unchanged; AC-13 and AC-15 measured together; Open question 1 |
| Unbalanced callbacks (a request with `resolveSizeEnd` and no terminal event) keep `imagesBusy` true for the process: every later warm start waits for the cap | `StartupImageActivity` | Identity set, not a counter, so a double terminal event cannot go negative; `StartupImageActivityTest`; cap |
| Cross-thread writes race the gate (`onCancel` off main against `begin()` on main) | `RevealGate` | `synchronized` recompute; `covered` stays a `StateFlow` read by `MainActivity.kt:80` |
| More keys dropped under the splash at the fixed anchor (2.0 and 2.5 s may fall before the reveal) | `MainActivity.kt:62` | By design (Q3); AC-15 counts them separately |
| Post-reveal cost not removed: hero page changes on D-pad decode a 4K source each (`HeroCarousel.kt:125`), JIT and stale baseline profile remain | Coil, ART | AC-15 measures; Open questions 2 and 3 |
| `StartupReveal` global state leaks between unit tests | `RevealGateTest`, `PuberVMRevealTest` | `resetForTests()` also clears `imagesBusy` and empties the in-flight set |

## AC delta (gate 1)

| AC | Change |
|---|---|
| Q2 (owner decision) | Ready additionally requires that no image request of the loader is in flight (size resolved, no success, error or cancel yet); the cap and the Home and other-landing rules stay; an update prompt opening reveals at once |
| AC-4 | Add: and the hero backdrop and the card images on screen are drawn, not skeletons, in the first frame after the fade, unless the cap fired (device screenrecord, frame by frame) |
| AC-6 | "publishes its first `StartupContentState`" becomes "publishes its first `StartupContentState` and its image requests have settled" |
| AC-12 | Add: the prompt opening reveals the splash without waiting for content or images |
| AC-15 (new) | Given devBenchmarkRelease on the emulator, 3 interleaved base (a22c03bc) and head sets of 4 cold starts per build, host load <= 9, the six keys 22, 21, 22, 21, 22, 21 at 2.0, 2.5, 3.0, 3.5, 4.0 and 4.5 s after `am start` (qa-report Round 1c, fixed anchor), Then over head keys sent after `revealed` the `key_lat` p95 is <= the base p95 over all six keys of the same session, and the share of those head keys at >= 199 ms is <= the base share; the evidence lists head keys dropped under the splash and the `ImageDecoder#decodeBitmap` slices between `revealed` and the first delivered key from one trace |

## Tests to add

| Test | Level | AC |
|---|---|---|
| `RevealGateTest.readyWaitsForImagesInFlight` | unit | AC-4, AC-6 |
| `RevealGateTest.imagesSettlingAfterReadyUncovers` | unit | AC-4, AC-6 |
| `RevealGateTest.capUncoversWhileImagesInFlight` | unit | AC-5 |
| `RevealGateTest.revealNowIgnoresContentAndImages` | unit | AC-12 |
| `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain` | unit | AC-4, AC-7 |
| `RevealGateTest.beginKeepsImagesBusyFromBeforeIt` | unit | AC-10 |
| `StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess` | unit (`mockk` `ImageRequest`) | AC-4 |
| `StartupImageActivityTest.errorAndCancelEndARequest` | unit | AC-4, AC-5 |
| `StartupImageActivityTest.cancelWithoutResolvedSizeLeavesIdle` | unit | AC-5 |
| `StartupImageActivityTest.busyIsReportedOnlyOnEdges` | unit | AC-4 |
| Existing `RevealGateTest.markReady_uncoversImmediately` | unit | AC-6: still passes with no images in flight |
| Fixed-anchor A/B and one trace (AC-15) | device | AC-15, AC-13 |

## Open questions

1. Accept the Q2 delta (reveal waits for in-flight images within the unchanged cap) and keep AC-13's `revealed` target (<= 2502 ms median) unchanged alongside AC-15? Recommended: yes to both; if both cannot hold, report and ask. Blocks: yes.
2. If AC-15 fails with images drained, add `bitmapFactoryMaxParallelism(2)` in `PuberApp.newImageLoader` and measure again? Recommended: yes, as the next fix round. Blocks: no.
3. Record an idea run for a card-size image tier (downscaled bitmaps on disk, so a cold start never decodes 4K `wide` sources into 533x300 cards)? Recommended: yes. Blocks: no.
