CODEX: ran: review
CONFIRMED:
- None.
REJECTED:
- [P2] `app/build.gradle.kts:475-476`: the new inputs sit in `tasks.withType<Test>`, so editing an instrumentation file also invalidates the dev and prod unit-test caches. This does not hold as a defect. The effect is only an extra re-run (a cache miss) after an edit to `src/instrumentation` or `baselineprofile/.../baseline`, which are rarely touched. No test returns a stale result, and staleness is the only thing the brief guards against. The change is a deliberate over-approximation that matches the existing `src/main` and `proguard-rules.pro` inputs declared in the same block. Splitting it per flavor would add complexity for no correctness gain.
VERDICT: clean
