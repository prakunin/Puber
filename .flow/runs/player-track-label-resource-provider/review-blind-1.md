LENS: blind  ROUND: 1  BASE: 8db929b5

No findings.

I read the whole diff and also checked how `ResourceProvider` is wired. All five `context.getString` calls in `PlaybackController` now go through `resources`. The Koin binding in `PlayerScreen.kt` passes five `get()` arguments, matching the new constructor. `ResourceProvider` is registered as a `single` in `PuberApp.kt`. The harness is the only other constructor caller, and it was updated. The remaining `context` uses in the controller are for ExoPlayer, the track selector and buffer config, which is correct.

The one behaviour change is that these strings now follow `AppLocale` through `AndroidResourceProvider` instead of the process-start context. That fits the change's purpose.

I did not build or run anything, so the new instrumented test is unrun. It rebuilds the harness mid-test with `harness.close()` and then reassigns `harness`. That only works if `harness` is a `var` that the test's teardown closes. The diff does not show that part.

### Unproven
- `PlaybackControllerNetworkTracksTest.kt` (`unlabelledAudio_usesInjectedResourceProviderForFallbackLabel`): the old harness is closed and replaced inside the test. If `harness` is a `lateinit var` closed in `@After`, this is fine. If teardown tracks a different reference, the replacement harness leaks. I would need to see the test's setup and teardown to know.
