# Review triage - round 2

Run: subtitle-upstream-port   Base: be8395a4   Reviewed head: 0127f0c3 (incremental on 4ae32d90)

Sources: review-edge-2.md, review-tests-2.md (no findings),
review-codex-2.md (ran: review; adversarial pass not run), qa-report.md round 3 (no defects).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex | minor | `PlaybackController.kt:372-374, 852-854`; `PlayerVM.kt:211-221, 862-884` | Clearing `pendingSubtitleTrack` on every `prepare()` drops the viewer's live pick when the same media is rebuilt (buffer preset, Fast DNS) and the stream exposes no audio group, because the VM restore never runs | Read the code: `prepare()` is reached from `selectBufferPreset`/`toggleFastDns` through `initializePlayer`; `notifyTracksUpdated` returns early without audio groups; text starts disabled | defer (owner, 2026-10-14) | Review cap reached; owner chose to defer to run subtitle-restore-model (restore across player recreation); Log line added there. Trigger needs an audio-less stream, practically absent on KinoPub |

## Rejected codex claims

- None. Codex ran only the plain review; the adversarial pass was not run this round (recorded for the judge).

## Next

- No fixes; go to judge.
