# Puber Repository Guide

Puber is an Android TV KinoPub client. Runtime code is in `:app`; baseline
profile generation is in `:baselineprofile`. Package: `com.kino.puber`.

## Context Index

- How the app is built: `docs/architecture/` - screens and DI, ViewModels,
  navigation, paging and filters, API, UI and Compose, testing. Written from the
  code, each claim naming the file it came from. Load the one document your step
  touches.
- Plans, specs, and evidence for in-flight work: `docs/superpowers/`.
- Release procedure: `docs/release.md`.
- Repository tooling lives in `tools/`: `agentw`, `configure-worktree-sdk`,
  `emulator-resource-lock.sh`, `android-apk-install-preserve`,
  `mobile-evidence-audit.sh`, `generate-baseline-profile.sh`.

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
- Screens resolve their ViewModel with `puberViewModel<VM>()` inside `DIScope`,
  not with bare `koinViewModel()`.
- Unit tests are JUnit 5: `@RegisterExtension` with `MainDispatcherExtension`,
  and `FakeResourceProvider` for anything that maps strings.
- API access is centralized in `KinoPubApiClient`; API models are used directly
  by project domain/UI mapping.
- User-visible strings live in `res/values/strings.xml`; non-composable code
  uses `ResourceProvider`.

## Runtime And External Systems

- Decide whether a change needs on-device verification before looking at what
  hardware is free. It needs it when the change touches TV UI, focus, D-pad
  input, navigation or overlays; ViewModel state, screen wiring, DI, startup or
  deep links; playback, networking, persistence, auth or permissions; packaged
  resources, manifest behaviour or build variants; or when a crash or
  acceptance criterion only shows up at runtime. Documentation, prompts, repo
  metadata, and tests that leave production sources alone do not. Compiling
  cleanly, or having no device at hand, is not evidence that a change is safe -
  when the device is unavailable, say so and ask, do not silently skip.
- Acquire a device lease before install, launch, input, or logs through
  `tools/emulator-resource-lock.sh`, and release it when done. Use the exact
  serial for every adb call.
- Devices are shared with every parallel agent on this host, and
  `connectedAndroidTest` runs on every connected device regardless of the
  serial it is given. Run instrumented tests through
  `make DEVICE=<serial> itest` (optionally `TESTS=<fully.qualified.Class>`),
  which is the only supported way: it drives a private adb server that knows
  only that serial, and leaves the APKs installed so the device keeps its
  KinoPub pairing. `adb disconnect` on the other devices is not a substitute -
  adb reconnects them by itself, well inside the length of a run. Never detach
  a device whose lease you do not hold; ask for explicit authorization instead.
- A device with no display cannot be re-paired by the user on their own, so its
  login is not a renewable resource. `make DEVICE=<serial> auth-save` stores the
  app's data under `.auth/` (gitignored, holds account tokens) and
  `make DEVICE=<serial> auth-restore` puts it back. It only helps while the app
  stays installed: the tokens are encrypted with an AndroidKeyStore key
  (`CryptoPreferenceRepository`), which an uninstall takes with it, and the
  restored prefs are then undecryptable. So not uninstalling is the protection;
  the snapshot only covers damage to app data.
- Install the freshly built dev APK; do not use an implicit Gradle install
  target. Preserve app data with `tools/android-apk-install-preserve`: a wiped
  app cannot log in again from a worktree build.
- A failed install blocks the change; it is never a reason to uninstall, clear
  package data, allow a downgrade, or replace a signer. Each of those needs
  separate explicit authorization, as does any use of a physical TV rather than
  an emulator.
- Keep credentials, broad UI dumps/logs, playback/account mutations, and raw
  authenticated responses out of Git and out of any evidence file unless an
  explicit task authorization permits the exact action.
  `tools/mobile-evidence-audit.sh <evidence-dir> [package]` scans a directory
  for secrets and raw logcat before it is shared.

## Repository Hygiene

- Generated/modified file content and commit messages are English unless the
  user explicitly requests otherwise.
- Plans, specs, and evidence belong in `docs/superpowers/`. `.todo/` is
  untracked scratch; nothing may depend on what is in it.
