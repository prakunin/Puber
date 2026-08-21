# Puber Repository Guide

Puber is an Android TV KinoPub client. Runtime code is in `:app`; baseline
profile generation is in `:baselineprofile`. Package: `com.kino.puber`.

## Context Index

- Kent lifecycle and delivery: `.kent/project-contract.md`.
- Node-specific reading budgets: `.kent/context/*.md`.
- Workflow and architecture index:
  `.kent/skills/puber-android-workflow/SKILL.md`.
- Step-specific recipes:
  `.kent/skills/puber-android-workflow/references/recipes/`.
- Workflow, MCP, Serena, routing, and feature-target rules:
  `.kent/skills/puber-android-workflow/references/rules/`.

Read the active node's manifest first and load only the recipes required by the
current step or finding.

## Build And Worktrees

- Flavors: `dev` and `prod`.
- Main compile task: `:app:compileDevDebugKotlin`.
- Main checkout may use `./gradlew`.
- Any worktree - Kent-managed, Orca-managed, or project-local - uses
  `./tools/agentw <task>`, never `./gradlew`.
- Dependency versions come from `gradle/libs.versions.toml`; do not hardcode
  them.
- Detekt configuration lives under `config/detekt/`.
- Worktrees have three legal owners. Kent-managed worktrees remain Kent-owned
  and must not be moved. Project-local worktrees live under `.kent/worktrees/`.
  Orca-managed worktrees are created from the Orca app or
  `orca worktree create` and live in Orca's managed directory. Do not add a
  worktree any other way, and do not create sibling worktrees by hand.
- Worktree SDK setup may write only `sdk.dir` to `local.properties`. Never copy
  KinoPub/TMDB credentials into task worktrees.
- Orca runs `scripts.setup` from `orca.yaml` on worktree creation; it calls the
  same `tools/configure-worktree-sdk` entry point Kent uses. `.worktreeinclude`
  lists the gitignored files Orca copies into a worktree, and credentials never
  belong there.
- A worktree has no `.env`, so `CLIENT_SECRET`, the TMDB token, and the API
  domain fall back to `System.getenv` and are normally empty. Such a build
  cannot complete a fresh login; it stays usable because smoke installs
  preserve app data through
  `.kent/adapters/mobile/android-apk-install-preserve`. Smoke still builds and
  installs the worktree's own APK - never substitute a main-checkout build for
  a worktree change.
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
- UI uses Compose TV Material3. Load the matching navigation, DI, ViewModel,
  Compose, filtering, paging, API, or testing recipe before editing.
- API access is centralized in `KinoPubApiClient`; API models are used directly
  by project domain/UI mapping.
- User-visible strings live in `res/values/strings.xml`; non-composable code
  uses `ResourceProvider`.

## Runtime And External Systems

- Smoke follows `.kent/commands/smoke-test.md` and
  `.kent/context/smoke.md`.
- Acquire a TV emulator lease before install, launch, input, logs, or MCP
  targeting. Use the exact serial for every adb and target-specific MCP call.
- Devices are shared with every parallel agent on this host, and
  `connectedAndroidTest` runs on every connected device regardless of the
  serial it is given. Do not start instrumented tests while another device is
  attached, and never detach a device whose lease you do not hold; ask for
  explicit authorization instead.
- Install the freshly built dev APK; do not use an implicit Gradle install
  target.
- Use `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`, never raw `mcporter`.
- Keep credentials, broad UI dumps/logs, playback/account mutations, and raw
  authenticated responses out of Git and workflow evidence unless an explicit
  task authorization permits the exact action.

## Repository Hygiene

- Generated/modified file content and commit messages are English unless the
  user explicitly requests otherwise.
- Do not create implicit `.todo/.current` pointers.
- Kent task state owns lifecycle; `plan.md` tracks writer-owned steps and
  `meta.json` stores identity/source metadata only.
