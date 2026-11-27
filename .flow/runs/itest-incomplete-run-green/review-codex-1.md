CODEX: ran: review, adversarial

CONFIRMED:
- None.

REJECTED:
- [P2] `rm -rf` of the shared `connected` results dir also deletes the results of other variants, so scope it to the selected variant. This does not hold. The dir holds build outputs that Gradle regenerates. The brief fixes the scope to `app/build/outputs/androidTest-results/connected` and states the clearing risk (APKs untouched). The brief and the Makefile both rely on a flat clear so that stale XML cannot count (AC-5). No caller is shown to need another variant's old report. The scenario is only "a stale artifact disappears", which is not wrong behaviour.
- [P3] If `rm -rf` fails, make stops before `kill-server` and leaves the private adb server running. This is unproven. It needs an undeletable file in the build output dir, and nothing shows that happens. Gradle writes that dir as the same user.
- [high] A report truncated after its first `<testcase` start would pass. This does not hold. AGP writes the XML as a whole file after the run, and Codex gave no case of a truncated report. The brief's AC-1..AC-3 cover the observed failure, which is the synthetic `testRunFailed` case. The real evidence file `itest-r5-2.xml` writes `name` first, as the grep expects: `<testcase name="testRunFailed" classname="android-test" ...>`. The real passing reports also write `name` first. Codex's alternative attribute order is hypothetical and has not been seen in AGP output.
- [medium] Two concurrent `itest` runs in one checkout could accept each other's XML. This is unproven. Both runs share one Gradle project dir and one `app/build` tree, so concurrent runs already collide on assembly, and `AGENT_GRADLE_SERIAL` and the lease rules exist for that. AGENTS.md allows at most one leasing agent at a time. The scenario starts from a use the project forbids.

VERDICT: clean
