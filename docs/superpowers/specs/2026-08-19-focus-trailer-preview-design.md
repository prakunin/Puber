# Focus trailer preview, and the side rail by default

Status: approved design, pending implementation
Date: 2026-08-19
Scope: `ContentListScreen` detail panel, plus the app-wide navigation-mode default.

## Problem

With the side rail selected, moving focus onto a catalogue card fills the panel above the
rows with that title's description and a still (`ContentListScreenContent.kt:122` →
`VideoItemGridDetails`). The still never changes while focus stays put.

Prime Video does one thing more: linger on a card and the still gives way to the trailer.
Puber already has everything that needs — `VideoDetailsUIState.trailerUrl` is mapped from
`item.trailer?.url` (`VideoItemUIMapper.kt:91`) and thrown away unused, and the details
screen plays trailers through ExoPlayer today (`TrailerOverlay.kt`).

Separately, the panel only exists in `NavigationMode.SideDrawer`, and the default is
`NavigationMode.TopTabs` (`NavigationPreferencesRepository.kt:56`). Most users therefore
never see the panel at all.

## Decisions

| Question | Decision |
| --- | --- |
| Which screens | `ContentListScreen` only |
| Delay before the trailer starts | 2s from the moment focus lands |
| Audio | On, with system audio focus |
| When the trailer ends | Back to the still; no replay until focus leaves and returns |
| User control | On/off switch in device settings, default on |
| Default navigation mode | `SideDrawer` |

## Design

### 1. Preferences

`NavigationPreferencesRepository`:

- `getNavigationMode()` (`:56`) defaults to `SideDrawer` instead of `TopTabs`. The key is
  written only on an explicit choice, so anyone who picked top tabs themselves keeps them;
  only users who never touched the setting move over.
- New `getAutoTrailerEnabled()` / `setAutoTrailerEnabled()`, default `true`. It belongs here
  rather than in `PlayerPreferencesRepository` because it governs the catalogue, and
  `ContentListVM` already injects `navPrefs`.

`DeviceSettingsPreferencesStore` gains the pass-through pair, `DeviceSettingsVM` gains
`toggleAutoTrailer()` built on the existing `updatePreference` helper (the shape of
`toggleWatchedIndicators`, `DeviceSettingsVM.kt:331`), and `DeviceSettingsContent` gains a
switch next to the watched-indicator one. Strings go in `res/values/strings.xml` and
`res/values-en/strings.xml`. `DeviceSettingsViewState:36` starts from `SideDrawer`.

### 2. Screen state

`ContentListViewState` gains `previewTrailerUrl: String? = null`.

It is non-null only when the panel is shown, the setting is on, focus has rested on a card
for 2s, and that title has a trailer. One field, one source of truth — the UI decides
nothing on its own.

### 3. Timing in `ContentListVM.onItemFocused`

The 2s countdown runs *alongside* the details request, not after it. Sequencing them would
mean 2s plus network time on a cold cache.

```kotlin
focusedItemJob?.cancel()
updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
focusedItemJob = launch {
    val trailerGate = async { delay(TRAILER_PREVIEW_DELAY_MS) }   // 2000, from focus
    delay(FOCUS_DETAILS_DEBOUNCE_MS)                              // 150, unchanged
    updateViewState<ContentListViewState> { copy(selectedItem = VideoDetailsUIState.Loading) }
    val details = interactor.getItemDetails(item.id)
    updateViewState<ContentListViewState> { copy(selectedItem = mapper.mapDetailedItem(details)) }

    if (!navPrefs.getAutoTrailerEnabled()) return@launch
    val url = details.trailer?.url ?: details.trailer?.file ?: return@launch
    trailerGate.await()
    updateViewState<ContentListViewState> { copy(previewTrailerUrl = url) }
}
```

What that shape buys:

- **Fast scrolling costs nothing.** The `focusedItemJob?.cancel()` already at the top of
  `onItemFocused` kills the gate along with the request, so no trailer starts.
- **The previous trailer dies immediately**, because the `null` write is synchronous and
  precedes every delay.
- **Virtual time works.** `ContentListVMTest` already advances the scheduler by hand
  (`:261`); no ExoPlayer is needed to test the timing.

`onItemSelected` and `onItemPlayed` clear `previewTrailerUrl` and cancel the job. The VM
outlives a trip to the details screen, and without this the trailer would be playing the
instant the user came back, with no pause at all.

The early `if (!stateValue.showDetailPanel) return` stays, so `previewTrailerUrl` is always
null under `TopTabs`.

### 4. `TrailerPreviewPlayer`

New composable at `core/ui/uikit/component/details/TrailerPreviewPlayer.kt`, beside
`VideoItemGridDetails` because it lives inside that panel. `TrailerOverlay` is left alone:
it is full-screen, button-triggered, and owns its own Back behaviour.

- ExoPlayer with `setAudioAttributes(..., handleAudioFocus = true)`, matching
  `PlaybackController.kt:298-312`. Audio plays, and the system can take it away.
- `PlayerView(useController = false)`, `resizeMode = RESIZE_MODE_ZOOM`, so a 16:9 trailer
  fills the still's box without letterboxing.
- `Player.Listener`: `STATE_ENDED` → `onFinished()`; `onPlayerError` → `onFinished()`.
- `LifecycleAction(Lifecycle.Event.ON_STOP)` → `onFinished()`. Coming back from the
  background into a running trailer is not what the user was looking at.
- `onDispose` → `stop()`, `release()`.

`onFinished` is a callback rather than local state. A local flag would leave the UI showing
the still while `previewTrailerUrl` was still set in the VM, and the next recomposition
would bring the player back.

### 5. Wiring into the panel

`VideoItemGridDetails` takes two new parameters with defaults — `trailerUrl: String? = null`
and `onTrailerFinished: () -> Unit = {}` — so Favourites and the previews are untouched.
Inside `VideoDetailsPoster`, an `AnimatedVisibility(trailerUrl != null, fadeIn/fadeOut)`
holding the player sits above the `AsyncImage`. The left-edge gradient
(`VideoItemGridDetails.kt:196+`) stays on top, so the seam with the description survives.

`ContentListScreenContent.kt:122` passes `state.previewTrailerUrl` and
`{ onAction(ContentListAction.TrailerPreviewFinished) }`.

`ContentListAction` gains `TrailerPreviewFinished`, handled in `ContentListVM.onAction` by
clearing the field.

### 6. Failure handling

No trailer, blank URL, player error, dead network — every one of them ends at the still,
silently. No toast, no retry. A decorative feature must not make noise.

### 7. Tests

**Unit, `ContentListVMTest`** — where the value is:

- 2s of focus on a card with a trailer → `previewTrailerUrl` set;
- focus moves at 1.5s → the new item's field is null and the old one never appears;
- setting off → stays null even though details loaded;
- `trailer == null` → null; `trailer.url == null` with a `file` present → `file` is used;
- `TrailerPreviewFinished` clears the field; `ItemSelected` clears the field;
- `showDetailPanel == false` → always null.

**Unit, `NavigationPreferencesRepository`** — empty prefs give `SideDrawer`; a stored
`TopTabs` is honoured; `autoTrailerEnabled` defaults to true.

**Compose/instrumented** — only the no-trailer branch (`trailerUrl == null` shows the
still). ExoPlayer cannot be instantiated in the test renderer, as already recorded at
`DetailsScreenPreview.kt:253`. Real playback is covered by the manual smoke run in
`.kent/commands/smoke-test.md`.

## Out of scope

Auto-trailer in `HeroCarousel`, in Favourites, or on any other screen; a muted/unmuted
choice in settings; prefetching trailers for neighbouring cards.
