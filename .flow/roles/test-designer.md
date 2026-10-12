# Puber addendum: test-designer

Read `docs/architecture/testing.md` and look at existing tests next to the code
(`app/src/test`, `app/src/androidTest`) so the design reuses their fakes and
style.

## Test levels

- `unit`: JUnit 5, `@RegisterExtension` with `MainDispatcherExtension`,
  `FakeResourceProvider` for anything that maps strings, coroutines-test with
  virtual time (no sleeps).
- `instrumented`: only through `make DEVICE=<serial> itest [TESTS=<fqcn>]`,
  never `connected*AndroidTest`.
- `device`: manual observation on an emulator, recorded in the evidence file.

Prefer the cheapest level that would catch the defect. Test through VM state or
emitted UI state, not private calls; never mock the class under test.

## Device checks

Numbered steps with D-pad keys, expected result, and what must not happen
(focus loss, crash, stale value). A device check for a TV UI change drives the
remote (`adb -s <serial> shell input keyevent ...`), not taps.

## Perf track

Measure on the same device and build type before and after; name the metric,
the method and the threshold.
