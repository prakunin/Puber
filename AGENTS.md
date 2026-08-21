# Puber Repository Guide

Puber is an Android TV KinoPub client. Runtime code is in `:app`; baseline
profile generation is in `:baselineprofile`. Package: `com.kino.puber`.

## Context Index

- Plans, specs, and evidence for in-flight work: `docs/superpowers/`.
- Architecture recipes verified against the code on 2026-08-21, under
  `.kent/skills/puber-android-workflow/references/recipes/`: `viewmodel.md`,
  `di-setup.md`, `compose-screen.md`, and `compose-performance.md`. In the last
  one, ignore the list examples: they use the removed `androidx.tv.foundation`
  API, while this project uses the standard `LazyRow`/`LazyColumn`/
  `LazyVerticalGrid`.
- Every other document under `.kent/` is unverified legacy from a workflow
  runner that is no longer installed here. Parts of it are accurate and parts
  prescribe APIs, screens, and test infrastructure that do not exist, so treat
  all of it as untrusted: check a claim against the code before acting on it,
  and never cite it as the reason for a change.
- Two scripts are the exception and still run standalone:
  `.kent/adapters/mobile/emulator-resource-lock.sh` and
  `.kent/adapters/mobile/android-apk-install-preserve`, both named below.

## Build And Worktrees

- Flavors: `dev` and `prod`.
- Main compile task: `:app:compileDevDebugKotlin`.
- Main checkout may use `./gradlew`.
- Any worktree uses `./tools/agentw <task>`, never `./gradlew`.
- Dependency versions come from `gradle/libs.versions.toml`; do not hardcode
  them.
- Detekt configuration lives under `config/detekt/`.
- Worktrees are Orca-managed: create them from the Orca app or with
  `orca worktree create`. Do not add one by hand with `git worktree add`.
- Orca runs `scripts.setup` from `orca.yaml` on worktree creation, which calls
  `tools/configure-worktree-sdk`. That may write only `sdk.dir` to
  `local.properties`. `.worktreeinclude` lists the gitignored files Orca copies
  into a worktree; KinoPub and TMDB credentials never belong in either.
- A worktree has no `.env`, so `CLIENT_SECRET`, the TMDB token, and the API
  domain fall back to `System.getenv` and are normally empty. Such a build
  cannot complete a fresh login, which is why installs preserve app data. Build
  and install the worktree's own APK - never substitute a main-checkout build
  for a worktree change.
- Parallel worktree builds share `GRADLE_USER_HOME=~/.gradle-agents`. Set
  `AGENT_GRADLE_SERIAL=1` whenever another worktree may build at the same time.

## Architecture Gotchas

- DI uses Koin DSL. Global modules are assembled in `PuberApp.kt`; screen
  dependencies live in the screen's `buildModule(scopeId, parentScope)`.
- Screens implement parcelable `PuberScreen` and navigate through `AppRouter`.
- Parameterized screens must override `key` with stable navigation identity so
  Voyager state, TV focus, and DI scopes do not collide. Computed parcelable
  keys use `@IgnoredOnParcel`.
- ViewModels extend `PuberVM<ViewState>` or `PagingVM<T, VS>`.
- Content composables are pure: state plus
  `onAction: (UIAction) -> Unit`.
- UI uses Compose TV Material3 (`androidx.tv.material3`); lists use the
  standard Compose `Lazy*` APIs, not `androidx.tv.foundation`.
- Unit tests are JUnit 5: `@RegisterExtension` with `MainDispatcherExtension`,
  and `FakeResourceProvider` for anything that maps strings.
- API access is centralized in `KinoPubApiClient`; API models are used directly
  by project domain/UI mapping.
- User-visible strings live in `res/values/strings.xml`; non-composable code
  uses `ResourceProvider`.

## Runtime And External Systems

- Acquire a device lease before install, launch, input, or logs through
  `.kent/adapters/mobile/emulator-resource-lock.sh`, and release it when done.
  Use the exact serial for every adb call.
- Devices are shared with every parallel agent on this host, and
  `connectedAndroidTest` runs on every connected device regardless of the
  serial it is given. Do not start instrumented tests while another device is
  attached, and never detach a device whose lease you do not hold; ask for
  explicit authorization instead.
- Install the freshly built dev APK; do not use an implicit Gradle install
  target. Preserve app data with
  `.kent/adapters/mobile/android-apk-install-preserve`: a wiped app cannot log
  in again from a worktree build.
- A failed install blocks the change; it is never a reason to uninstall, clear
  package data, allow a downgrade, or replace a signer. Each of those needs
  separate explicit authorization, as does any use of a physical TV rather than
  an emulator.
- Keep credentials, broad UI dumps/logs, playback/account mutations, and raw
  authenticated responses out of Git and out of any evidence file unless an
  explicit task authorization permits the exact action.

## Repository Hygiene

- Generated/modified file content and commit messages are English unless the
  user explicitly requests otherwise.
- Plans, specs, and evidence belong in `docs/superpowers/`. `.todo/` is
  untracked scratch; nothing may depend on what is in it.
