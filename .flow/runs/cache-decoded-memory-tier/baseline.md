# Baseline for cache-decoded-memory-tier (AC-10, unchanged build)

Build: worktree at base e9c383d0, `./tools/agentw :app:assembleDevDebug` (AGENT_GRADLE_SERIAL=1), dev debug, version 1.12.3-dev. APK sha256 6cec249b...8c39b16, matched on device after install-preserve (outcome `installed`, `compatible_replace`, signer unchanged).
Device: emulator-5554 (AVD puber_tv_36, API 36 TV, booted without wipe, software GPU), lease held and released. On-device Room user_version before install: 4 (header read), equal to the build's `PuberDatabase` version 4.
Account: existing pairing; no data touched, no uninstall or clear.

## Method (no production changes)

Sequence: force-stop, cold start (MainActivity), 15 s settle, D-pad into the "Continue watching" row, then five times: DPAD_CENTER (Details), 5 s, BACK, 3 s, DPAD_RIGHT (next card).
Decode = `kotlinx.serialization.json.Json.decodeFromString` (all record and ktor body decodes go through it; ktor `KotlinxSerializationConverter.deserialize` listed separately). ART method traces (`am start --start-profiler ... --streaming`, parsed with a script of mine from the streaming v3 format; thread 1 = main).

Limits found:
- Exact call counts need full method tracing. It was stable for the cold start only (500-670 MB trace). The same process dies when a Details screen opens under full tracing (twice), so Details counts cannot be exact.
- For the whole sequence a sampling trace (100 us requested, ~355 us median gap on Main) was used: it gives decode "episodes" (contiguous presence on the stack; back-to-back decodes merge, so a lower bound on count) and inclusive Main time. Sampling and tracing slow the app, so milliseconds are inflated and are comparable only with the same method after the change.

## Results

### Cold start to Home, exact count (full trace, two runs)
| Run | decodeFromString total | on Main | on workers |
|---|---|---|---|
| c | 165 | 66 | 99 |
| c2 | 101 | 31 | 70 |
Run-to-run spread is large (cache vs network refresh timing); Main decode is non-zero in both.

### Whole sequence (cold start + five Details opens), sampling, three runs
Main thread, cold window (first 15 s) and opens window (after 15 s):
| Run | cold episodes | cold ms in decode | opens episodes | opens ms in decode | worker episodes |
|---|---|---|---|---|---|
| s1 | 72 | 2620 | 213 | 9321 | 79 |
| s2 | 87 | 1763 | 197 | 9477 | 50 |
| s3 | 95 | 2698 | 240 | 10181 | 49 |
Main-thread decode inside the ktor converter is a part of these (10-15 episodes, 0.2-0.4 s per run in the opens window). Total trace length 81-86 s. After the change decode on Main must be 0 episodes in both windows (AC-10).

### App heap after the sequence (no profiler, three runs, dumpsys meminfo)
| Run | Dalvik Heap Alloc (KB) | Java Heap PSS summary (KB) | Native Heap PSS (KB) |
|---|---|---|---|
| h1 | 26347 | 40648 | 60904 |
| h2 | 26219 | 52344 | 55076 |
| h3 | 26203 | 58728 | 52248 |
Sampling runs (profiler on): Dalvik Heap Alloc 26935, 26210, 27183 KB. Alloc is stable near 26.2-26.3 MB; the PSS figure varies with GC timing, compare Alloc and use at least three runs.

Re-measure after the change with the same sequence, the same emulator and a dev debug build.
