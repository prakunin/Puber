# Puber addendum: reviewer

Applies to lens `edge` (read as `also_consider`) and lens `tests` (its section
below). Lens `blind` never reads this file.

## also_consider (lens edge)

1. **Correctness**: wrong state transitions, races between coroutines,
   cancellation that leaks or is swallowed, stale state after process death or
   navigating back, null or empty API fields, off-by-one in paging, a
   `StateFlow` updated from two places.
2. **Lifecycle and DI**: anything holding a VM, Context or screen past its Koin
   scope; `koinViewModel()` instead of `puberViewModel` in `DIScope`;
   parameterized screens without a stable `key` (`@IgnoredOnParcel` for
   computed keys); once-listeners never removed.
3. **TV**: focus requesters on nodes that may not be composed, focus traps,
   Back handling, D-pad paths skipping a row, overscan.
4. **Compose performance**: unstable lambdas or collections passed to hot
   composables, state read in composition that belongs in layout or draw,
   `Lazy*` items without keys, work in composition that belongs in the VM, IO
   on the main thread.
5. **Conventions**: pure content composables, strings in resources, versions
   from the catalog, API access only through `KinoPubApiClient`.
6. **Structure**: duplication of an existing helper (name it), dead code the
   change leaves behind, a file growing a second responsibility.

Anything detekt already owns is out.

## Lens tests

Project test rules are in `docs/architecture/testing.md`; read it.

- Traceability: every P0 row of the spec's Test design has a test under that
  name or an equivalent; list ACs with neither a test nor a device check.
- Would it fail? For a bug, would the failing-first test fail on the base
  commit? Reason it through against `git -C {wt} show {base}:<path>`; never
  stash, reset or check out in the worktree.
- Behaviour, not implementation: assertions on VM state or emitted UI state,
  not only `verify { private collaborator call }`; no mock of the class under
  test; no sleeps, virtual time via coroutines-test.
- Edges: empty and error responses, cancellation, timer and paging boundaries,
  repeated actions, process re-creation where state is saved.
- Hygiene: JUnit 5 with `MainDispatcherExtension` via `@RegisterExtension`,
  `FakeResourceProvider` for strings, existing fakes reused instead of copied,
  deterministic names.
