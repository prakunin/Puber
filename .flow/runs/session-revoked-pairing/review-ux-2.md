I found one problem: the new `CodeInfoBlockedPreview` can't show whether the code state with the blocked line fits the TV safe area. I didn't use a device, build or render anything. All numbers come from reading the code and from the round-1 measurements on the box.

### F1 [minor] CodeInfoBlockedPreview renders at twice the real screen size, so it can't show whether the column fits the safe area
- Where: `app/src/main/java/com/kino/puber/ui/feature/auth/component/AuthComponent.kt:190-194`
- Problem: expected a preview on the box's own canvas, 960x540 dp (1920x1080 px at xhdpi, dp = px / 2), drawn the way the screen draws it: `PuberTheme`, the dark background, and the column centred by the root `Box(fillMaxSize, Alignment.Center)`. Measured from the source:
  - `Devices.TV_1080p` in ui-tooling-preview 1.12.1 is `spec:width=1920dp,height=1080dp`. That is 1080 dp of height against the device's 540 dp. The round-1 fix asked for `id:tv_1080p`, which is the AVD profile (1920x1080 px, xhdpi). That profile is a different thing from the `TV_1080p` constant.
  - The preview calls `CodeInfo` with no `PuberTheme`, so it uses the stock typography and the stock red `error` colour on a white `showBackground` instead of `Error60` on `OledBackground`.
  - It has no centring `Box`, so the column sits at the top-left corner.

  The column is about 484 dp tall with a two-line hint. On this preview it takes under half the height. On the TV it leaves about 28 dp above and below. TV overscan safe area is 5%, which is 27 dp at the top and bottom of 540 dp. So the preview shows plenty of room exactly where the real layout is at the edge.
- Failure scenario: the owner judges AC-10 ("fits the Auth layout in size and placement") from this preview. They see a small block in the top-left corner with lots of space and approve it. On the TV, after a blocked session ends on a build that gets a device code, the red line sits about 28 dp from the top edge and the mirror hint about 28 dp from the bottom. Any extra wrapped line pushes one of them into the overscan area. On an overscanning panel that cuts off the top of the blocked line, the one line this run adds.
- Fix: draw the preview at the box's size and wrap it the way the screen wraps it:
  ```kotlin
  @Preview(device = "spec:width=960dp,height=540dp,dpi=320")
  @Composable
  internal fun CodeInfoBlockedPreview() = PuberTheme {
      Box(
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          contentAlignment = Alignment.Center,
      ) {
          CodeInfo(code = "123456", url = "https://www.example.com", timeLeft = "02:00", accountBlocked = true)
      }
  }
  ```
  Use `"id:tv_1080p"` instead if the device profile is preferred; it gives the same 960x540 dp. If the correct preview shows less than 27 dp above the blocked line or below the hint, the smallest layout change is to drop the spacer under the blocked line from 20 dp to 12 dp. That gains 8 dp and leaves the 20/16/20 dp gaps the spec agreed untouched.
VISUAL: not measured: the code state needs a build with a client secret, which a worktree build doesn't have. The review was read-only with no device, and nobody has rendered the preview.

The other `TV_1080p` previews in the project (for example `SplashContent.kt:86`, `VideoItemGridDetails.kt:787`) have the same 2x canvas. They are older than this run and out of its scope, but that is likely where this preview's setup was copied from.

Files:
- /home/dev/orca/workspaces/Puber/session-revoked-pairing/app/src/main/java/com/kino/puber/ui/feature/auth/component/AuthComponent.kt
- /home/dev/Puber/.flow/runs/session-revoked-pairing/review-ux-1.md (where the round-1 ~484 dp estimate comes from)
