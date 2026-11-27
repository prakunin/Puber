# Review edge, round 1

LENS: edge  ROUND: 1  BASE: 08f50b81

### F1 [minor] The evidence file for AC-7 (and AC-5) is missing at head
- Where: docs/superpowers/evidence/ (no file for this slug); spec AC-7 "the counts go into the evidence file"
- Problem: counts and the egress result live only in .flow qa-report.md, which does not merge.
- Fix: add docs/superpowers/evidence/2026-11-26-baseline-mock-protocol-parity-device.md with the Perf table, journey and unknown=[] summary, device and run ids; audit with tools/mobile-evidence-audit.sh.

Handled: wrong-server route -> 404 -> unknown; test hosts allowed only on their server's port; ports from BuildConfig and runner args; chunked only on H1; Date in Locale.US UTC; close() idempotent with the second close in finally; h2 bind failure closes h1; series route out of Playback; seasons/subtitles only in item_serial (validate + unit test); profile has all seven AC-6 prefixes and no generator/profile classes.

### Unproven
- item_serial.json subtitles {{MOCK}}/media/subtitle.vtt has no route/asset; only matters if a journey fetches a series episode subtitle.
- gradle.properties:5-6 equal H1/H2 ports via -P fail to bind; only the environment test asserts they differ.
- phosphoricons 57 vs 56; only fill/EyeKt and fill/BookmarksimpleKt are test-guarded.
