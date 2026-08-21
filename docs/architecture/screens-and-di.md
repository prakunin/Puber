# Screens And DI

A screen is two files plus a Koin scope. `FavoritesScreen` is the smallest
complete example:
`app/src/main/java/com/kino/puber/ui/feature/favorites/content/FavoritesScreen.kt`.

## Screen class

```kotlin
@Parcelize
internal class FavoritesScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::FavoriteItemUIMapper)
            scopedOf(::FavoritesInteractor)
            viewModelOf(::FavoriteVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<FavoriteVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        FavoriteScreenContent(state = state, onAction = onAction)
    }
}
```

`puberViewModel<VM>()` - not bare `koinViewModel()`. It lives in
`core/di/DIScope.kt` and resolves the VM from `LocalPuberKoinScope`, which
`DIScope` installs. Bare `koinViewModel()` resolves against the default scope
instead, so anything registered in `buildModule` is not found there.

`PuberScreen` (`core/ui/navigation/PuberScreen.kt`) is Voyager's `Screen` plus
`Parcelable`, with `key` defaulting to `javaClass.simpleName`. Two markers
extend it: `RootPuberScreen` and `FullscreenPuberScreen`.

## Keys for parameterized screens

A screen carrying params must give itself a distinct key, or Voyager state, TV
focus, and the DI scope of two instances collide. Compute it with
`@IgnoredOnParcel` so it stays out of the parcel:

```kotlin
@IgnoredOnParcel
override val key: ScreenKey = "ContentListScreen_${tabType.name}"
```

See `ContentListScreen.kt`, `ShowAllScreen.kt`, `HistoryScreen.kt`, and
`DetailsScreen.kt`, which builds its key from item id plus the initial episode.

## Content composable

The second file holds pure UI: `state` in, `onAction: (UIAction) -> Unit` out,
no DI, no mapping. Building label-value pairs from `stringResource()` inside a
composable is the anti-pattern this split exists to prevent - map in the
UIMapper through `ResourceProvider` (`core/system/ResourceProvider.kt`) and let
the composable render ready strings.

Shared states live in `core/ui/uikit/component/`: `FullScreenProgressIndicator()`
in `Loading.kt`, and `FullScreenError(...)` / `ListItemError(...)` in
`Errors.kt`. Some screens still render their own private `ErrorView` instead
(`HomeScreenContent.kt`, `DeviceSettingsContent.kt`); prefer the shared ones for
new code. There is no shared empty-state composable.

## DI rules

Koin 4.2.2, pure DSL - no `@InjectConstructor`, no annotation processing for DI.

- Global singletons are assembled in `PuberApp.kt`: `singleOf(::Impl)`, with
  `{ bind<Interface>() }` when an interface exists (`IAuthInteractor`,
  `ErrorHandler`).
- Screen dependencies go in the screen's own `buildModule(scopeId, parentScope)`
  under `scope(named(scopeId))`: `scopedOf(::Mapper)`, `scopedOf(::Interactor)`,
  `viewModelOf(::VM)`. Params are registered as an instance: `scoped { params }`.
- `DIScope(scopeName, moduleFactory, content)` owns the scope lifetime and
  closes it when the composable leaves composition.
- A flow of screens sharing dependencies uses `FlowComponent` (see
  [navigation.md](navigation.md)); it wraps its own `DIScope`, and child screens
  nest their scope names under it through `LocalPuberScopePrefix`.
