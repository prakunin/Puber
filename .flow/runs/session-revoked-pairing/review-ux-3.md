No findings.

The `CodeInfoBlockedPreview` fix (triage-2 #4) matches what I asked for in round 2, at `/home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/ui/feature/auth/component/AuthComponent.kt:191-204`:
- **Canvas:** `spec:width=960dp,height=540dp,dpi=320`, which is the box's 1920x1080 px at xhdpi (dp = px / 2).
- **Theme:** it is wrapped in `PuberTheme`, so the blocked line now uses the app's error colour and `bodyLarge` (16 sp / 24 sp), not the stock ones.
- **Layout:** a `Box(fillMaxSize, Alignment.Center)` on `colorScheme.background` reproduces how the Auth screen wraps the column (`AuthComponent.kt:57-72`).

**Fit in the 27 dp safe area (5% of 540 dp, top and bottom):** the code column fits, with more room than I estimated in round 2. I worked this out from the source; nothing was rendered.
- **The mirror hint is one line, not two.** `auth_mirror_content_hint` has no width cap, so it can use the full 960 dp. At 16 sp it is about 100 characters, roughly 720-760 dp in both RU and EN, so it does not wrap. My round-1/2 figure of about 484 dp assumed it wrapped to two lines.
- **Column height:**

  | Element | Height |
  |---|---|
  | Blocked line (two lines inside its 560 dp cap) | 48 dp |
  | Gap | 20 dp |
  | Code | 24-44 dp |
  | Gap | 20 dp |
  | QR | 200 dp |
  | Gap | 20 dp |
  | URL | 24 dp |
  | Gap | 16 dp |
  | Time | 24 dp |
  | Gap | 20 dp |
  | Hint (one line) | 24 dp |
  | **Total** | **about 440-460 dp** |

- **Vertical margins:** that leaves about 40-50 dp above the blocked line and below the hint, against 27 dp.
- **Worst case:** if the blocked line wraps to a third line, or the hint to a second, the column is about 484 dp and the margins about 28 dp, still at or above 27 dp. So the 20 dp to 12 dp spacer fallback I suggested in round 2 isn't needed.
- **Horizontal:** the widest child is the hint at about 760 dp, which leaves about 100 dp each side against the 48 dp side safe area (5% of 960 dp).

The owner can now judge AC-10 from this preview. The code state on the TV itself still needs a build with a client secret.

VISUAL: not measured: read-only incremental review, no device use as briefed, and the code state needs a build with a client secret; the fit comes from the source, not a render.
