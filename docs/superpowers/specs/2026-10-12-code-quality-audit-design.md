# Code quality audit

Status: draft | approved
Date: 2026-10-12
Slug: code-quality-audit
Kind: refactor
Size: L
Route: standard

<!--
Each section has one owner; nobody else edits it. The conductor commits this
file in the task's worktree after every phase.
  analyst        Problem, Acceptance criteria, Out of scope, Plan (quick route)
  architect      Design
  ux-designer    Visual
  test-designer  Test design
  anyone         Open questions (append only; the user answers)
Cite code as [Source: path:line]. A claim with no source is a guess.
-->

## Problem

The user asked for an analysis of the code and its structure: places where
hand-written code duplicates a ready library or platform solution, SOLID,
best practices, and performance. It is a broad request, not a defect.

Survey (triage only, not findings):
- `:app` has 386 Kotlin files, about 51k lines, in `core/` (cross-cutting),
  `data/` (api, cache, db, preferences, repository), `domain/` and
  `ui/feature/` [Source: app/src/main/java/com/kino/puber].
- Largest files, the likely SOLID hotspots: `PlayerVM.kt` 1859 lines,
  `KinoPubApiClient.kt` 1195, `PlaybackController.kt` 990,
  `DetailsScreenContent.kt` 945, `DeviceSettingsContent.kt` 816, `HistoryVM.kt`
  805, `FlowComponent.kt` 785, `HomeVM.kt` 705 [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1].
- Hand-rolled candidates to check against libraries: `Paginator.kt` (604 lines)
  with no `androidx.paging` in the version catalogue
  [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:1;
  gradle/libs.versions.toml]; `TypedTtlCache.kt` (325),
  `CachedFeed.kt` (607), `ContentCacheRepository.kt` (634)
  [Source: app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:1];
  `ContentUriCodec.kt` (178); `IdGenerator.kt`; `SessionEventBus.kt`.
  The catalogue already holds Room 3, Coil 3, Media3, OkHttp, Ktor
  [Source: gradle/libs.versions.toml:14-118]. Whether each candidate is really a
  duplicate is unverified.
- Earlier work not to repeat: the 2026-09-08 memory and performance audit and
  its plan [Source: docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md:7];
  deferred items P3, M4, P5, P8-P12 sit in board row `perf-deferred`
  [Source: docs/superpowers/BOARD.md:28]. Detekt config exists
  [Source: config/detekt/detekt.yml] with a baseline.

Deliverable of this task: a prioritised, sourced audit report plus a small set
of safe, behaviour-preserving fixes applied in this branch (user decision,
Q1). Everything risky, behaviour-changing, or adding a dependency becomes a
follow-up board row.

## Acceptance criteria

- **AC-1** Given the finished task, when `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` is opened, then it has one section each for: Structure, Reinvented wheels (hand-written vs library/platform), SOLID, Best practices, Performance.
- **AC-2** Given any finding in the report, when read, then it has an ID, a `[Source: path:line]` that points at existing code on the audited commit, a severity (high/medium/low), an effort (S/M/L), and a recommended approach (named library or platform API, or the refactor).
- **AC-3** Given the "Reinvented wheels" section, when read, then it states a verdict (replace / keep / defer) with a reason for each of `Paginator`, `TypedTtlCache`, `CachedFeed`/`ContentCacheRepository`, `ContentUriCodec`, `IdGenerator` and `SessionEventBus`, and names a replacement only if it is, or can be, added through `gradle/libs.versions.toml`.
- **AC-4** Given the 8 files over 700 lines named in Problem, when the SOLID section is read, then each has either a finding (which responsibility to split out) or an explicit "acceptable" with a reason.
- **AC-5** Given the Performance section, when compared with the 2026-09-08 audit and `perf-deferred`, then no finding repeats an item already fixed or deferred there; overlaps are cross-referenced by ID instead. Any finding not backed by a measurement is marked "static reading, unmeasured".
- **AC-6** Given the report, when its summary table is read, then findings are grouped in the order libraries (reinvented wheels), SOLID, performance (best practices after them), within each group by severity then effort, and proposed follow-up board rows (slug, one-line scope, kind, size, dimension) group them so each row is one reviewable change.
- **AC-7** Given the task branch diff against master, when listed, then production code changes only for fixes the report marks "fixed in this task"; each such fix cites its finding ID, is behaviour-preserving, has its own commit, and `gradle/libs.versions.toml` and `build.gradle.kts` dependencies are unchanged (library replacements stay follow-up rows).
- **AC-8** Given the report, when the detekt result is read, then it records the `./tools/agentw` detekt run on the audited commit (counts per rule family) and the count after the fixes.
- **AC-9** Given the final branch, when the static gate runs through `./tools/agentw` (compile `:app:compileDevDebugKotlin`, unit tests, detekt), then compile and tests pass and detekt reports no more findings than the baseline on master.
- **AC-10** Given the fixes touch runtime code, when qa runs the smoke paths those files reach (named per fix in the evidence) on a leased emulator, then app launch and those paths behave as on master with no crash, and the evidence file records them; if no fix touches runtime code, the evidence states that and no device is used.
- **AC-11** Given any "dependency recommended" finding, when read, then its reason covers TV focus behaviour and the paging model (`docs/architecture/paging-and-filters.md`), and states that adding it is a follow-up decision.


## Out of scope

- Risky or behaviour-changing refactors; they become follow-up rows.
- Adding or upgrading dependencies (recommendations only).
- Splitting the large classes (PlayerVM, KinoPubApiClient, etc.); follow-up rows.
- Formatting sweeps and mass renames.
- Re-auditing memory and performance items already covered by
  `memory-and-performance-fixes` and `perf-deferred`.
- Profiling or frame-stat measurement; perf findings stay static unless a
  follow-up perf row measures them.
- Test-suite quality, UI look and consistency, security/credential review.
- Upstream-sync triage (its own row).

## Design

<!-- architect. Decisions table, changes by layer, alternatives rejected,
risks. Not on the quick route. -->

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

<!-- test-designer.
### Risks
| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|

### Traceability
| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|

### Device checks
1. …

### Baseline (perf route)
-->

## Plan

<!-- analyst, quick route only; otherwise see docs/superpowers/plans/. -->

## Open questions

1. Should this task only deliver the report (proposed), or also apply a small set of safe fixes? Proposed: report only, fixes as follow-up rows.
2. Which dimension matters most, so findings are ordered and follow-up rows opened in that order: structure/SOLID, replacing hand-written code with libraries, or performance? Proposed: libraries first, then SOLID of the 8 big files, then performance.
3. Is adding a dependency (for example `androidx.paging` in place of the 604-line `Paginator`) acceptable to recommend, given the project's TV focus and paging model in `docs/architecture/paging-and-filters.md`?
4. May the audit run detekt through `./tools/agentw` in the worktree (shared Gradle home, `AGENT_GRADLE_SERIAL=1`), or should it rely on static reading only?

Answers (user, 2026-10-12):
- Q1 → answered: report **plus** a small set of safe, behaviour-preserving fixes in this branch (so device verification and code review apply).
- Q2 → answered: order is libraries (reinvented wheels) → SOLID of the big files → performance.
- Q3 → answered: yes, the report may recommend new dependencies through `gradle/libs.versions.toml`, with a reason that covers TV focus and the paging model; the decision to add one belongs to the follow-up row.
- Q4 → answered: yes, run detekt through `./tools/agentw` in the worktree with `AGENT_GRADLE_SERIAL=1`.
