LENS: blind  ROUND: 1  BASE: 2fd851a4

No findings.

### Unproven
- /home/user1/orca/workspaces/Puber/resume-progress-anomaly/app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt:369 a movie with several videos, where the first video has no `watching` but the item does: the item-level position is measured against the first video's `duration` rather than `item.duration.total`, so the time left could be wrong. This becomes real only if the API sends an item-level `watching.time` for a movie with several parts, measured against the whole item.
- /home/user1/orca/workspaces/Puber/resume-progress-anomaly/app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt:140 an episode with `watching.time == 0` and `watching.duration > 0` now gets `progressPercent = null` (no bar) instead of `0f` (an empty bar). This becomes real only if an item payload carries a non-zero `watching.duration` for episodes; the new model comment says it does not.
