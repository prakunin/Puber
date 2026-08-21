# Navigation

Voyager underneath, wrapped by `AppRouter` (`core/ui/navigation/AppRouter.kt`).
A ViewModel never touches the Voyager navigator directly - it gets `router` as
the first `PuberVM` constructor parameter.

## AppRouter

```kotlin
class AppRouter(val screens: Screens, val coroutineScope: CoroutineScope)
```

| Method | Effect |
|---|---|
| `navigateTo(screen)` | push |
| `navigateForResult<T>(screen, requestCode, listener)` | push and register a one-shot result listener |
| `setOnceResultListener<T>(resultCode, listener)` | register a listener without navigating |
| `replaceScreen(screen)` | replace the current screen |
| `newRootScreen(vararg screen)` / `newRootScreens(list)` | replace the whole stack |
| `back(resultCode = null, result = null)` | pop, optionally delivering a result |
| `backTo(screen)` | pop back to a screen |
| `closeRootFlow()` | close the current flow |
| `addBackDispatcher` / `removeBackDispatcher` / `dispatchBackPressed` / `hasBackDispatchers` | let a component intercept Back |
| `events()` | the command flow the navigator host collects |
| `clearPendingCommands()` | drop queued commands |

There is no `showOver` and no `hideBottomSheet`, and the app has no bottom sheet
container at all. An overlay is built as a normal screen or as content inside
the current screen.

## Screens factory

`Screens` (`core/ui/navigation/Screens.kt`) is the shared factory for
cross-feature destinations; the single implementation is
`internal object ScreensImpl : Screens` in `ui/ScreensImpl.kt`. Reach another
feature through it - `router.screens.details(itemId)` - rather than importing
its screen class. A VM may construct a screen of its own feature directly, as
`ContentListVM` does with `ShowAllScreen(config)` and `DeviceSettingsFlowVM`
with `DeviceSettingsScreen()`.

Note the types: `details(itemId: Int)`, with an overload taking
`DetailsEpisodeTarget`, and `player(itemId, seasonNumber, episodeNumber,
videoNumber, startMode)`. Item ids are `Int` throughout the API models.

## Results

Result codes are shared constants, not generated per call. Content mutations use
`RESULT_CONTENT_CHANGED` (`core/ui/navigation/ContentChangeResultCodes.kt`) and
carry a `ContentChangeSet`:

```kotlin
router.navigateForResult<ContentChangeSet>(
    screen = router.screens.details(itemId = state.id),
    requestCode = RESULT_CONTENT_CHANGED,
    listener = ::onReturnedContentChanges,
)
```

The listener fires once. The screen returning the result calls
`router.back(resultCode = RESULT_CONTENT_CHANGED, result = changes)`.
`IdGenerator.generateId()` (`core/system/IdGenerator.kt`) exists but is not the
navigation-result convention.

## Flows

`FlowComponent` (`core/ui/navigation/component/FlowComponent.kt`) hosts a nested
navigation stack with its own Koin scope:

```kotlin
fun FlowComponent(
    scopeName: String,
    screen: PuberScreen = LoadingScreen,
    composableScope: CoroutineScope = rememberCoroutineScope(),
    moduleFactory: (scopeId: ScopeID, parentScope: Scope) -> Module = { _, _ -> module {} },
    remoteKeyHandler: ((KeyEvent, AppRouter, PuberScreen) -> Boolean)? = null,
    content: @Composable () -> Unit = {},
)
```

It provides the flow's `AppRouter` to everything inside, publishes
`LocalScreenKey`, and prefixes child scope names through
`LocalPuberScopePrefix`. Dependencies shared by the flow's screens are
registered in its `moduleFactory`.

## Tabs

`PuberTab` (`core/ui/navigation/PuberTab.kt`) is a `@Parcelize data class`
wrapping a screen plus a `tag`, and is itself both a `PuberScreen` and a Voyager
`Tab`. Its key is `"Tab:${screen.key}"` - tab identity comes from the wrapped screen
alone. `instanceKey` feeds only `contentInstanceKey`, which separates two
content instances behind the same tab slot.

Switching tabs goes through `TabRouter.openTab(tab)`
(`core/ui/navigation/TabRouter.kt`), which emits `TabCommand.Open(tab)` on a
`SharedFlow` that `TabComponent` collects.
