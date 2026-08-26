# UI, Focus, And Compose

The toolkit is `androidx.tv.material3` for components - `Surface`, `Card`,
`Text`, `MaterialTheme` - wrapped by `PuberTheme`
(`core/ui/uikit/theme/`). Lists are the **standard** Compose ones:
`LazyColumn`, `LazyRow`, `LazyVerticalGrid`, `GridCells`. The app has no
dependency on `androidx.tv.foundation`; anything mentioning `TvLazyRow` or
`TvGridCells` predates that removal.

Images come from Coil 3 (`coil3.compose.AsyncImage`), usually through
`SkeletonAsyncImage` (`core/ui/uikit/component/SkeletonAsyncImage.kt`), which
pairs the image with the skeleton treatment.

## Shared components

`core/ui/uikit/component/`:

- `VideoItem(modifier, state: VideoItemUIState, onClick: () -> Unit, onContextMenu: (() -> Unit)?)`
  - the poster card. The context-menu callback is wired to
  `Modifier.onTvContextMenuKey`, the D-pad long-press equivalent for TV.
- `VideoGrid(modifier, state: VideoGridUIState, onItemClick, onItemFocused, onItemContextMenu, ...)`
  - the grid used by list screens; it also takes `initialFocusedItemId`,
  `rowsFillViewport`, `detailsPrefetchEnabled`, and `onDownFromLastRow`.
- `VideoItemGridDetails` with `VideoDetailsUIState` - the details panel.
- `FullScreenProgressIndicator()` (`component/Loading.kt`) - the shared loading
  composable, and `FullScreenError` / `ListItemError` (`component/Errors.kt`)
  for failures.
- `Rating` with `RatingUIState`: a sealed class over `value: String` (not
  `Double`) with an `isLoading` flag, variants such as `KP` and `IMDB`.

`VideoItemUIState` is `@Immutable` and holds `id: Int`, `title`, `imageUrl`,
`bigImageUrl`, `wideImageUrl`, `imageFallbackUrls`, watch progress and watched
flags, `ratings`, `isSaved`, season/episode numbers, and `year`. There is no
`posterUrl` field.

Some screens predate `Errors.kt` and keep a private `ErrorView` of their own
(`HomeScreenContent.kt`, `DeviceSettingsContent.kt`). Empty states have no
shared widget at all and are built per screen.

## Skeletons

`Modifier.placeholder(visible, color, shape, progressForMaxAlpha)` from
`core/ui/uikit/component/modifier/Placeholder.kt`. It wraps
`com.eygraber.compose.placeholder` and defaults its colour to
`LocalSkeletonColor`. Import the project modifier, not the library one, and not
Accompanist - the app does not depend on it.

## Focus

Focus is the navigation model on TV, so most screen bugs are focus bugs.

- `rememberFocusRequesterOnLaunch()`
  (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`) - initial focus
  for a screen. Its "already asked" flag is a `rememberSaveable` taking
  `LocalScreenKey` as an input, so it fires once per navigation entry rather
  than on every recomposition.
- `Modifier.focusGroup()` and `focusRestorer()` from Compose itself are used
  directly elsewhere.

## Performance rules

- `@Immutable` on the view-state sealed class and on every UI model.
- Stable action lambda: `val onAction = remember(vm) { vm::onAction }`, passed
  down; never `{ vm.onAction(it) }` inline.
- A stable `key = { it.id }` on `items(...)` whenever the list can change
  identity or order. Count-based placeholder lists (`items(SHIMMER_COUNT)`) take
  no key, and a list keyed on something other than an id is fine as long as the
  key is stable.
- `derivedStateOf` for values computed from scroll position.
- Fixed image sizes plus `contentScale`, so loading does not relayout.
- Lambda-based modifiers (`offset { }`, `graphicsLayer { }`, `drawBehind { }`)
  whenever the value being read is animated.
- Pass the fields a child needs, not the whole state, so a sibling's change does
  not recompose it.

`config/compose/compiler_config.conf` declares stable: `java.time.LocalDateTime`,
`kotlinx.datetime.*`, `kotlin.collections.*`, and
`androidx.compose.ui.graphics.painter.Painter`. `List`, `Map`, and `Set` are
therefore stable here and need no wrapper type.

## Previews

Screen previews target TV: `@Preview(device = Devices.TV_1080p, ...)`, wrapped
in `PuberTheme` rather than bare `MaterialTheme`. Previews of a single component
often skip the device and just size the box (see the previews at the bottom of
`VideoItemUIState.kt` and `RatingUIState.kt`). There is no screenshot testing in
this project.

`PreviewParameterProvider` is used in exactly one place today
(`ui/feature/history/component/preview/`). It is a good pattern for a screen
with many states - one provider listing loading, empty, error, and a couple of
content variants - but the repository does not have it everywhere, so do not
assume a provider exists for the screen you are editing.
