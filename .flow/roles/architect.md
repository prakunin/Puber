# Puber addendum: architect

Read the `docs/architecture/` documents your change touches before deciding.

## Layers for "Changes by layer"

API / repository, ViewModel state and actions, screen and DI module, Compose
content, resources.

## Conventions the design must respect

- DI is Koin DSL. Global modules are assembled in `PuberApp.kt`; screen
  dependencies live in the screen's `buildModule(scopeId, parentScope)`.
- Screens are parcelable `PuberScreen`s navigating through `AppRouter`.
  Parameterized screens override `key` with a stable navigation identity
  (computed parcelable keys use `@IgnoredOnParcel`) so Voyager state, TV focus
  and DI scopes do not collide.
- ViewModels extend `PuberVM<ViewState>` or `PagingVM<T, VS>`; screens resolve
  them with `puberViewModel<VM>()` inside `DIScope`, never bare
  `koinViewModel()`.
- Content composables are pure: state plus `onAction: (UIAction) -> Unit`.
- UI is Compose TV Material3 (`androidx.tv.material3`); lists use the standard
  Compose `Lazy*` APIs, not `androidx.tv.foundation`.
- API access only through `KinoPubApiClient`; API models are used directly by
  domain/UI mapping.
- Strings in `res/values/strings.xml` (non-composable code: `ResourceProvider`);
  dependency versions from `gradle/libs.versions.toml`.

## Risks to name, highest first

Focus and D-pad paths, Koin scope lifetime (a VM must not outlive its scope),
recomposition (unstable parameters, state read in composition instead of draw,
`Lazy*` items without keys), main-thread work, memory growth, cache
invalidation, baseline profile.

## Visual track

If the change is track `ui`, the `ux-designer` states the visual delta against
`docs/design/`; do not decide sizes yourself.
