# Triage round 1: resume-progress-anomaly

Reviewed HEAD f2e0f25f against base 2fd851a4. Inputs: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (plain + adversarial, clean).

No findings from any lens; codex clean on both passes. Unproven notes, checked against the code and recorded, no action:

| ID | Source | Note | Decision | Reason |
|----|--------|------|----------|--------|
| U1 | blind, edge | Movie with several videos / video duration 0 rather than absent: length falls back wrongly | reject | Becomes real only with a payload the API docs do not describe; device round 2 showed correct time left for the movie |
| U2 | blind, tests | progressFraction gives null instead of 0f for time 0 with a known watching.duration | reject | Recorded deviation in the plan; the bar is empty either way; item payloads carry no watching.duration |
| U3 | edge | Movie reads videos.first().watching before item.watching | reject | Accepted design-delta risk 1, covered by the P1 fallback test |
| U4 | edge | Any 2xx counts as success even with an error body | reject | Q1 decision (Result<Unit>, like the other write calls); no evidence of HTTP 200 with an error body for marktime |
| U5 | tests | No test where watching.duration > 0 wins over the media length | reject | Not produced by item payloads per the API docs; card readers that do read it are in card-progress-watching-duration |

Fix list: none. Single review round (no blocker or major).
