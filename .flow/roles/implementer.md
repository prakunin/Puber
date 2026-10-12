# Puber addendum: implementer

## Builds

Builds go through `./tools/agentw`, never `./gradlew` (the guard hook refuses
it in a worktree). Set `AGENT_GRADLE_SERIAL=1` unless you know no other
worktree is building: parallel worktree builds share `~/.gradle-agents`.

```bash
cd {wt} && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin
cd {wt} && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests "<FQCN>"
```

A worktree has no `.env`: do not add one, and never write KinoPub or TMDB
credentials anywhere. Never touch a device: no install, adb or emulator lease;
that is qa's job.

## Conventions (AGENTS.md, Architecture Gotchas)

Koin DSL with `buildModule(scopeId, parentScope)`; `puberViewModel<VM>()` in
`DIScope`; pure content composables; Compose TV Material3; strings in
`res/values/strings.xml`; versions from `gradle/libs.versions.toml`.

## Definition of done

- every plan checkbox ticked or explained;
- P0 tests from the spec exist and pass;
- `check.full` green (paste the final lines);
- no hardcoded user-visible strings, no hardcoded dependency versions;
- no debug logging, no commented-out code, no TODO without a tracked run
  (`/task:new`).
