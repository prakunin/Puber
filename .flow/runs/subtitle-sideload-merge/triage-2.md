# Review triage - round 2

Run: subtitle-sideload-merge   Base: a97ee2b1   Reviewed head: 7ca85b93 (incremental on c6ea3789)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), qa-report.md round 2.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 (major), codex 1, qa round 2 defect 1 | major | `vm/PlayerVM.kt:534-541, 557-560` | Every text-less report is treated as transient with no end condition: on a manual-quality stream without text the picker says Russian while nothing renders (device: 0 cues in ~60 frames on S1E5 720p); also next-episode first report and link refresh keep stale rows | Read 36da8957: `textlessReport` keeps `previous.subtitleTracks` and the pick and sends nothing; device evidence round 2 confirms the user-visible symptom | fix | Design delta requested from the architect (design-architect-2.md), then implementer; beyond the 2-round review cap under the owner's standing instruction (2026-10-14: proceed on recommendations) |
| 2 | tests F1 | minor | `vm/PlaybackController.kt:440, 511` | `currentItemHasSideLoads` is computed separately from the factory spec; no test reaches it | Read: both lines call `subtitles.sideLoadable().isNotEmpty()` independently of `spec()` | fix | Single source from the built spec; folded into the same fix round |

## Rejected codex claims

- Adversarial "the quality-switch fix needs another fix and device verification" as a whole - same issue as #1, not counted separately.

## Next

- Architect delta for #1 (and #2), implementer fix round 2, then one focused round 3: lens edge + codex on the increment, lens tests, qa-device on AC-4/AC-6/AC-7 and D-4/D-8. The extra round is recorded for the judge and the owner.
