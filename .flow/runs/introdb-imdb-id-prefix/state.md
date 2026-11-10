---
slug: introdb-imdb-id-prefix
title: IntroDB.app lookup may send the IMDb id without its tt prefix, so no skip segments load
stage: idea
size:
tracks: []
kind: bug
branch:
worktree:
base:
spec:
plan:
rounds: {verify: 0, review: 0, judge: 0}
blocked_by: []
source: seek-blocked-by-skip-prompt
created: 2026-11-10
migrated:
updated: 2026-11-10
---

## Next
Triage with /task:run introdb-imdb-id-prefix; first read the item's real `imdb` value to confirm.

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|

## Decisions
| Date | Decision | By |
|------|----------|----|

## Log
- 2026-11-10 created (stage idea) from the seek-blocked-by-skip-prompt qa report (M3): `api.introdb.app/segments?imdb_id=tt0903747` returns intro/outro for S5E1, `imdb_id=tt903747` (the unpadded id `IntroDbAppApiClient` builds when `imdb` lacks a `tt` prefix) returns null; on the device no intro prompt appeared. Cause unconfirmed: the item's real `imdb` value was not read.
