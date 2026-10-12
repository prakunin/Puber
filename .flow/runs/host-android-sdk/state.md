---
slug: host-android-sdk
title: "Host android sdk"
stage: done
size:
tracks: []
kind:
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
created: 2026-10-10
updated: 2026-10-12
---

## Next
Done; nothing pending.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- landed: 2026-10-10
- notes: `~/Android/Sdk` (platform 37, build-tools 36/37, emulator, `android-36/android-tv/x86_64`). AVD `puber_tv_36` = `emulator-5554`, paired, snapshot in `.auth/`. install-preserve needs `cmdline-tools/latest/bin` on PATH for apkanalyzer.
- 2026-10-12 migrated from the former board file (Done table)
