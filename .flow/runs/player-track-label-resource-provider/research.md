# Research: player-track-label-resource-provider

## Request
"The player fallback track label is read from the raw Context instead of ResourceProvider" (state.md title). Log: created from player-audio-unknown-language Q2: PlaybackController.kt:873-875 gets the "Track N" fallback label from Context.getString, not ResourceProvider (AGENTS.md: non-composable code uses ResourceProvider); it may keep the old language after an in-app language switch (unverified on a device).

## Understanding
`PlaybackController` resolves its user-visible strings (the "Track N" audio fallback and the "Playback error" messages) from the injected Koin `Context`, which is the `Application` wrapped once in the start-up language. After the user changes the interface language in Device Settings without restarting, every other string follows the new language (Compose through `AppLanguageProvider`, view models through `AndroidResourceProvider`), but these keep the language the process started in. The fix is to resolve them through `ResourceProvider`, as the AGENTS.md rule requires.

## Current state in code
Paths under `app/src/main/java/com/kino/puber/` unless stated.

- `ui/feature/player/vm/PlaybackController.kt:161-166`: constructor `(context: Context, okHttpClient, mediaCache, playerPreferencesRepository)`. `context` is also needed by Media3 (`DefaultBandwidthMeter` :205, `DefaultTrackSelector` :425, `ExoPlayer.Builder` :445/:720, `DeviceBufferConfig.resolve` :478, `applyVideoCap` :429/:545), so it stays.
- Strings read from `context` in that file:

| Line | String | Reaches the user as |
|------|--------|---------------------|
| 873-875 | `player_audio_track_fallback` ("Дорожка %1$d" / "Track %1$d") | audio picker row label, via `buildAudioRows` |
| 193 | `player_error_playback` | `onStreamFailure` message from `SeekStallRecovery`; PlayerVM passes it to `refreshStreamLinksOnce(message)` (`PlayerVM.kt:270-279`) |
| 266 | `player_error_playback` | fallback when `error.localizedMessage` is null |
| 634 | `player_error_playback` | `callback.onError` -> `PlayerViewState.Error(message)` (`PlayerVM.kt:282-284`), shown on screen |

- `ui/feature/player/vm/PlayerAudioTracks.kt:8-17`: `buildAudioRows(formats, fallbackLabel: (Int) -> String)` already takes the label as a lambda; `isFallbackLabel = name == null`. Pure, unit-tested.
- `ui/feature/player/component/PlayerScreen.kt:58`: `scoped<PlaybackControl> { PlaybackController(get(), get(), get(), get()) }` in the screen module; a new controller per PlayerScreen scope.
- `PuberApp.kt:54`: `single<ResourceProvider> { AndroidResourceProvider(get()) }` (global, resolvable from the screen scope). `PuberApp.kt:74-75`: `attachBaseContext(AppLocale.wrap(base))`; `:103` `androidContext(this@PuberApp)`, so `get<Context>()` is the Application with the start-up locale.
- `core/system/AppLocale.kt:33`: `current: StateFlow<AppLanguage>`; `:40-43` `store` writes prefs and publishes; `:46-53` `wrap` builds a configuration context. Nothing re-wraps the Application after a switch.
- `core/system/AndroidResourceProvider.kt:15-25`: re-wraps `appContext` whenever `AppLocale.current` changes, cached per language; `getString(resId, vararg)` at :30.
- `ui/feature/device/settings/vm/DeviceSettingsVM.kt:455-461`: `onChangeAppLanguage` -> `preferencesStore.setAppLanguage` -> `AppLanguageRepository.setLanguage` -> `AppLocale.store` (`data/preferences/AppLanguageRepository.kt:20`).
- `core/ui/AppLanguageProvider.kt:27-40`: composition re-wraps `LocalResources` off `AppLocale.current`; `LocalContext` deliberately untouched.
- `ui/feature/player/vm/PlayerVM.kt:75` already injects `resources: ResourceProvider` (used at :340, :605, :1122+). `PlayerVM.kt:1966`: a fallback-labelled row never overwrites the stored audio preference, so the localized label is not persisted.
- Tests:
  - `app/src/test/kotlin/.../player/vm/PlayerAudioTracksTest.kt:41-63`: `buildAudioRows` with `FakeResourceProvider`.
  - `app/src/androidTestInstrumentation/kotlin/.../player/vm/PlaybackControllerHarness.kt:150-155`: the only other constructor call, `PlaybackController(context = targetContext, ...)`; must gain the new argument.
  - `PlaybackControllerNetworkTracksTest.kt:160-188`: asserts the fallback row label against `targetContext.getString(R.string.player_audio_track_fallback, 1)`.
  - No test covers a language switch for any controller string.

## Related work
| Run / commit | Relation |
|--------------|----------|
| player-audio-unknown-language (done) | Source; Q2 deferred this; its research option C was this change (`research.md:45,75,90`) |
| audio-preference-locale-independent-label (done, 7905b9d9) | Stored audio preference matched by title across locales; confirms localized labels are not used as persistent keys |
| manual-quality-hls4 (done, d736f9b9) | Added `player_audio_track_fallback`, `buildAudioRows` and the `isFallbackLabel` guard |
| 0bf26ee5 (subtitle-upstream-port) | Moved player labels in `PlayerUIMapper` to ResourceProvider; left the controller on Context |
| afd66b77 | In-app language switch without restart (AppLocale, AndroidResourceProvider re-wrap, AppLanguageProvider) |
| playback-controller-split (idea, M) | Refactors the same class; no overlap with string resolution |

Duplicate: none.

## External findings
External findings: off (local research only).

## Options
| | Change | Trade-offs | Size |
|-|--------|-----------|------|
| A (recommended) | Add `resources: ResourceProvider` to the controller constructor; route all four strings (:193, :266, :634, :874) through it; DI `PlaybackController(get(), get(), get(), get(), get())` at `PlayerScreen.kt:58`; harness passes `AndroidResourceProvider(targetContext)` | One root cause fixed for every controller string; one more constructor parameter; touches DI and screen wiring, so device check needed | S |
| B | Same as A but only line 874 | Narrowest diff; leaves the error messages in the start-up language, the same bug the request names | S |
| C | Controller reports rows with no label; PlayerVM fills the fallback through its own `resources` | No new controller dependency; changes the `PlaybackControl.Callback` contract and VM track logic, larger test churn, errors still unfixed | M |

## Risks
- A locale-switch instrumented test must change `AppLocale` (prefs `language_preferences` and the static `current`) on the device; it has to restore the previous value in teardown or it leaves the stage app in another language.
- `PlaybackControllerNetworkTracksTest` assertions read `targetContext` strings; with `AppLocale.current` = System these match today, but they should assert through the same provider so a stale language in a test process cannot mask or fake a result.
- Device evidence needs content with an unlabelled audio track, or the error path, after a language switch on the box; finding such content may be hard. The instrumented hermetic fixture `audio_english.m3u8` already yields the fallback row.

## Open questions
| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Include the three `player_error_playback` reads (:193, :266, :634), not only the fallback label? | Yes (option A): same file, same cause, same fix |
| Q2 | How to prove the switch: instrumented test on the emulator or device run on the box? | Instrumented test in `PlaybackControllerNetworkTracksTest` that sets English, then Russian, via `AppLocale.store` and checks the fallback label, restoring the language after; plus a box smoke run (install-preserve, play one item) for the DI wiring |

## Sources
- Files: `PlaybackController.kt`, `PlayerAudioTracks.kt`, `PlayerVM.kt`, `PlayerScreen.kt`, `PuberApp.kt`, `AppLocale.kt`, `AndroidResourceProvider.kt`, `ResourceProvider.kt`, `AppLanguageProvider.kt`, `AppLanguageRepository.kt`, `DeviceSettingsVM.kt`, `res/values/player.xml:48,99`, `res/values-en/strings.xml:302,343`, `PlaybackControllerHarness.kt`, `PlaybackControllerNetworkTracksTest.kt`, `PlaybackControllerResourcesTest.kt`, `PlayerAudioTracksTest.kt`, `docs/testing/player-playback.md`, `docs/architecture/screens-and-di.md:62`
- Commits: 0bf26ee5, afd66b77, d736f9b9, 7905b9d9
- Runs: player-audio-unknown-language, audio-preference-locale-independent-label, manual-quality-hls4, playback-controller-split
