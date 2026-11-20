# Design (architect): update-dialog-focus-trap

Base 0bc3ab0e. Paths relative to `app/src/main/java/com/kino/puber/`. Implements brief.md option B with state.md Q1-Q3 as decided.

## Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Where does "prompt open" live? | `App()` holds `var updatePromptOpen by remember { mutableStateOf(false) }`; `UpdatePromptHost` reports `state != Hidden` through a new `onOpenChange: (Boolean) -> Unit` from a `SideEffect`. | The VM is resolved inside the root `DIScope` (`ui/feature/root/component/App.kt:130`), the navigator is built before `content()` (`core/ui/navigation/component/FlowComponent.kt:103-109`); the flag must cross from the second to the first. One boolean for this prompt only (Q3). |
| D2 | Where is the gate provided? | New `FlowComponent` parameter `screenFocusActive: Boolean = true`, passed to `FlowNavigator`, which adds both locals to its existing provider at `FlowComponent.kt:131-137` through a new `ContentFocusGate(active, content)` in `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`. The gate ANDs with the outer values: `LocalContentFocusActive provides (outer && active)`, `LocalAutoFocusOnLaunchEnabled provides (outer && active)`. | Gates the screens only; the prompt in `content()` stays outside. ANDing keeps the nested `FlowComponent` (`ui/feature/device/settings/flow/DeviceSettingsFlowScreen.kt:27`) and the default `true` a no-op. A named composable is the seam the instrumented tests drive. |
| D3 | Main overrides the outer value | `DrawerMainContent` reads `val outerActive = LocalContentFocusActive.current` and provides `outerActive && drawerState.currentValue != DrawerValue.Open` at `ui/feature/main/component/MainScreenComponent.kt:138`. | Same AND as `ui/feature/bookmarks/component/BookmarksScreenContent.kt:77`; without it the root gate is lost under Main (brief cause 3). |
| D4 | A launch request already scheduled when the gate closes | `rememberFocusRequesterOnLaunch` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:43-67`) re-reads `LocalAutoFocusOnLaunchEnabled` via `rememberUpdatedState` after the delay at `:58`; when false it does not request and does not set `isFocusRequested`, so it re-arms on the recomposition that lifts the gate. | The coroutine at `:57-63` checks only `isFocusRequested`; a request scheduled up to 100 ms before the prompt opens would still steal focus. |
| D5 | D-pad trap | `TvDialogOverlay` adds `.focusProperties { onExit = { if (requestedFocusDirection in TrapDirections) cancelFocusChange() } }` before `.focusGroup()` at `core/ui/uikit/component/TvDialogOverlay.kt:50`; `TrapDirections` = Up, Down, Left, Right, Next, Previous. | Q1: all dialogs, directional moves only. `Enter` (programmatic `requestFocus`) and `Exit` (`clearFocus`, IME Done in `core/ui/uikit/component/ApiDomainDialog.kt`) pass, so the trap's behaviour on programmatic requests (research.md: 5, unverified) is not relied on. Precedent: `ui/feature/device/settings/DeviceSettingsContent.kt:300-304`. |
| D6 | Initial claim | `UpdatePromptOverlay.kt:56-61` becomes the `TvContextMenu.kt:77-91` loop: delay 100 ms, then up to N attempts 50 ms apart, stop on the first `requestFocus()` that returns true. Constants private to the file; N chosen so the last attempt is under 1 s (AC-2). | Covers the request that lands before the button is attached. No re-claim loop: with D2-D4 nothing gated retries against it. |
| D7 | Back whatever the handler order | `RailClosedBackHandler` and `RailOpenBackHandler` (`MainScreenComponent.kt:160-197`) take `outerActive` (D3) and AND it into `enabled` at `:167` and `:186`. | The only callbacks added after the prompt in the launch race are Main's (`:108`, `:148`); `TabBackHandler` is disabled with one screen (`FlowComponent.kt:719`). Disabled callbacks are skipped by `OnBackPressedDispatcher`, so the overlay's `BackHandler` (`TvDialogOverlay.kt:43-45`) gets Back in both orders. |
| D8 | One owner of focus on dismiss | `FlowNavigator` runs `LaunchedEffect(screenFocusActive)`: on true->false it calls `contentFocusRequester.saveFocusedChild()` and keeps the result; on false->true, after one `withFrameNanos {}`, it calls `restoreFocusedChild()` only if the save returned true. No `requestFocus()` fallback. | Content held focus before the prompt (AC-2, AC-8): the saved child is restored, same API pair as `FlowComponent.kt:236` and `:658`. Launch race (AC-1): nothing was saved, and the re-armed launch request (D4) plus the hero claim (`ui/feature/home/component/HomeScreenContent.kt:194-203`) run as on a normal launch. A blind `requestFocus()` could enter the rail. |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | N/A: `UpdatePromptVM` unchanged; `UpdatePromptAction.UpdateClicked` already reaches `handleAction` (`ui/feature/update/vm/UpdatePromptVM.kt:61`). |
| Screen and DI module | `ui/feature/root/component/App.kt`: D1 state in `App()` (`:146`), `screenFocusActive = !updatePromptOpen` on `FlowComponent` (`:153`), `UpdatePromptHost` (`:129-143`) gains `onOpenChange`. `core/ui/navigation/component/FlowComponent.kt`: parameter on `FlowComponent` (`:82`) and `FlowNavigator` (`:113`), gate in the provider (`:131-137`), D8 effect next to `RootFlowReturnEffect` (`:166`). |
| Compose content | `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`: `ContentFocusGate` (D2), launch re-check (D4). `ui/feature/main/component/MainScreenComponent.kt`: D3 at `:138`, D7 at `:108`, `:148`, `:160-197`. `core/ui/uikit/component/TvDialogOverlay.kt`: D5. `ui/feature/update/component/UpdatePromptOverlay.kt`: D6, `FocusDelayMs` (`:43`) replaced by the retry constants. |
| Resources | N/A: no strings or assets. |

Test seams: `ContentFocusGate` + `UpdatePromptOverlay` with a late-claiming child (AC-1, AC-2, AC-3, AC-7); `TvDialogOverlay` users through `TvContextMenuLongPressTest`, `BookmarksScreenContentFocusTest` (AC-9). Host-with-gate precedent: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:243`.

## Alternatives rejected

- Window-level `Dialog` (option C): changes the visual stand and bypasses `AppRemoteHotkeyHandler` (`FlowComponent.kt:141-147`); out of scope.
- Wrapping the whole `FlowComponent` call in `App` with the locals: the prompt and root handlers would sit under the gate too.
- Consuming `KEYCODE_BACK` in the overlay's key handler: depends on Back reaching the view tree, which predictive back may bypass; D7 needs no platform assumption.
- Overlay re-claim on focus loss: two retrying claimants can fight; D2-D4 stop the sources instead.

## Risks

| # | Risk | Area | Code | Guard |
|---|---|---|---|---|
| R1 | An ungated focus request under the gate still steals focus (the drawer's `LaunchedEffect`, a tab handoff) and D6 does not re-claim. | focus | `core/ui/uikit/component/drawer/ModalNavigationDrawer.kt:198`, `FlowComponent.kt:640-645` | AC-1 device run in the launch-race order; add a re-claim only on evidence. |
| R2 | On dismiss after AC-2, D8's restore and Home's row effects (gated by `LocalContentFocusActive`, `core/ui/uikit/component/moviesList/FocusTargetState.kt:118-137`) both request focus; a different card wins or Home scrolls. | focus | `FlowComponent.kt` D8 effect, `FocusTargetState.kt:75` | AC-6 device observation; both target the remembered card. |
| R3 | The trap in a shared component changes `TvContextMenuDialog` and `ApiDomainDialog`: a dialog whose content relied on D-pad leaving it now cannot. | focus, lifecycle | `TvDialogOverlay.kt:46-54`, `TvContextMenu.kt:95`, `ApiDomainDialog.kt:100` | AC-9. |
| R4 | Flipping two `staticCompositionLocalOf` locals recomposes the whole root navigator subtree twice per prompt; remembered state survives (no structural change) but every reader re-runs. | recomposition | `FocusOnLaunchRequester.kt:23`, `:30` | Flips only on prompt open/close; AC-5, AC-6. |
| R5 | D4 changes every `rememberFocusRequesterOnLaunch` caller: one scheduled while the local is true and flipped false before 100 ms no longer fires. No production code provides `false` today besides D2. | focus | `FocusOnLaunchRequester.kt:55-63` | `HistoryScreenContentTest`, `ShowAllRailCloseFocusTest` stay green (check.full does not run them; device suite). |
| R6 | D7 disables Back for the rail while the gate is closed; if the prompt state stuck non-Hidden without the overlay composed, Back on Main would do nothing. | lifecycle | `App.kt` D1, `UpdatePromptOverlay.kt:51-53` | Gate derives from the same `state` that composes the overlay. |
| R7 | `saveFocusedChild` is called when focus may already be in transit (prompt opened mid-navigation), saving nothing; dismiss then leaves focus on no node. | focus | `FlowComponent.kt` D8 effect | AC-6, AC-8 device observation. |

## Open questions

1. During `Checking` (manual check) the prompt has no focusable node, so focus stays on the settings screen under the scrim and D-pad still moves there. Leave as is? Recommended: yes, AC-8 starts at the result state. Blocks: no.
