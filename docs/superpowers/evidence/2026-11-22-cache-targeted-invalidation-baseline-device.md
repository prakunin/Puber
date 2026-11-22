# Baseline device evidence: cache-targeted-invalidation

Base 40762767 plus a temporary measure patch (not committed). Device emulator-5554 (puber_tv_36). Installed APK sha256 a5e0bfdc60f18027bf29d7243fd7269d992d95c0f18168ea6a6f9d468df6fe2f equals the local build. Room user_version 4. Full method, shim and numbers are in the run's baseline.md; summary:

| Check | Steps | Expected | Observed |
|---|---|---|---|
| Per-save markStale | play Odd Taxi episode 1 via D-pad, 7.6 min in front | one mark per 30 s save at base | 15 saves, 15 markItemDetailsStale |
| Per-save TV home refresh | same | one refreshNow per save | 16 refreshNow (15 saves + 1 Watched at episode end) |
| Next-episode refetch | auto switch to episode 2 | details refetch at prefetch and switch | 3 details network loads (prefetch, switch, next prefetch) |
| Exit | Back from the player | one more save, mark, refresh | 1 save, 1 mark, 1 refreshNow, 1 details load; Details visible within ~1.5 s |
| Environment | marktime saves | succeed | HTTP 200 but ApiError.Serialization without the shim (see baseline.md) |

Result: baseline recorded, with the stated limits (7.6 min window instead of 30 min because the episode 2 stream failed; shim required).
