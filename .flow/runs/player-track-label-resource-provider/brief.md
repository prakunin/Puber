# Brief: player-track-label-resource-provider

Kind: bug   Size: S   Tracks: none   Base: 8db929b5
Device verification: yes (DI and screen wiring of the player scope, playback strings).
Research: `research.md` (option A, scope and call sites; not repeated here).

## Problem
After an in-app language switch (Device Settings, no restart) the player's "Track N" audio fallback label and its "Playback error" messages stay in the language the process started in, while the rest of the UI follows the new language.

## Root cause (confirmed by code trace; not reproduced on a device)
`PlaybackController` reads its strings from the injected Koin `Context` (`app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:162`), which is the Application wrapped once in `attachBaseContext` (`PuberApp.kt:74-75`, `:103`). Nothing re-wraps it after `AppLocale.store` (`core/system/AppLocale.kt:40`). `AndroidResourceProvider` re-wraps per `AppLocale.current` (`core/system/AndroidResourceProvider.kt:20-25`), so strings through `ResourceProvider` follow the switch; these four do not:

| Line | String |
|------|--------|
| `PlaybackController.kt:193` | `player_error_playback` (stream failure message) |
| `PlaybackController.kt:266` | `player_error_playback` (fallback for a null `localizedMessage`) |
| `PlaybackController.kt:634` | `player_error_playback` (`callback.onError`, shown on screen) |
| `PlaybackController.kt:874` | `player_audio_track_fallback` (audio picker row) |

## Acceptance criteria
- **AC-1** Given a `PlaybackController` built with a `ResourceProvider` whose `player_audio_track_fallback` returns a marker text, When it plays the hermetic `/media/hls/audio_english.m3u8` fixture, Then the reported audio row label equals the marker and `isFallbackLabel` is true (instrumented test on the emulator).
- **AC-2** Given the change, When `PlaybackController.kt` is searched, Then no `context.getString` remains; all four strings in the Root cause table resolve through the injected `ResourceProvider`.
- **AC-3** Given the existing `PlaybackControllerNetworkTracksTest` fallback tests (`:166`, `:182`), When they run, Then they pass, asserting against the harness's `ResourceProvider` instead of `targetContext`.
- **AC-4** Given the dev APK installed with install-preserve on the box `192.168.1.110:5555`, When an item is opened and played, Then the player starts with no Koin resolution crash (logcat checked for `NoBeanDefFoundException`/`InstanceCreationException` only).

## Plan
1. `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:161-166`: add `private val resources: ResourceProvider` to the constructor; replace `context.getString` at :193, :266, :634, :874 with `resources.getString`. `context` stays for Media3.
2. `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreen.kt:58`: pass a fifth `get()` (global `single<ResourceProvider>`, `PuberApp.kt:54`).
3. `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerHarness.kt:150-155`: take a `resources: ResourceProvider = AndroidResourceProvider(targetContext)` harness parameter, expose it, pass it to the controller.
4. `.../PlaybackControllerNetworkTracksTest.kt:166,182`: assert against `harness.resources.getString(...)`; add the AC-1 test with a delegating stub provider that overrides only `player_audio_track_fallback`.
5. Run `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest` under a lease; box smoke run for AC-4.

## Risks
- Touches the playback class and the player scope wiring (project risk areas); behaviour change is limited to string resolution, which AC-1 and AC-4 cover.
- A test that drives a real switch through `AppLocale.store` would change the stage app's stored language on a shared device; the stub provider in AC-1 avoids writing `AppLocale` at all.

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Include the three `player_error_playback` reads, not only the fallback label? | Yes: same file and cause (research.md: Options A) | no |
| Q2 | Prove the source with a stub `ResourceProvider` instead of a real language switch on the device? | Yes: the re-wrap itself belongs to `AndroidResourceProvider`; the stub isolates the controller and leaves device prefs untouched | no |
