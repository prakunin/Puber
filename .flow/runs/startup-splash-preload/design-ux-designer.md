# Design delta: startup-splash-preload (ux-designer)

Base a22c03bc. Paths relative to `app/src/main/java/com/kino/puber/`.

## Governing stand

No `docs/design/` stand governs this change: the stands are `catalogue`, `content` and `player-controls`; none covers the splash (`ui/feature/root/component/SplashContent.kt`) or Home (`ui/feature/home/component/HomeScreenContent.kt`). By the role's rule the conductor runs `designing-a-screen` with the owner before planning, unless the owner waives it.

The change alters no size, colour or position on either screen; its only new visual value is the reveal fade below. Recommendation: waive the stand session and accept the fade on the TV under brief.md AC-10, as the catalogue stand did for `PanelMotion` (`docs/design/catalogue/README.md`, Motion). Open question for the owner.

## Numbers that apply

Unchanged; the overlay reuses `SplashContent` as it is.

| Element | Value | Source |
|---|---|---|
| Splash background | `#FF000000` (`splash_background`), also `windowBackground` | `res/values/colors.xml:6`, `res/values/themes.xml:6` |
| App surface under it | `OledBackground` = `Color.Black` | `core/ui/uikit/theme/Color.kt:30`, `PuberTheme.kt:49-50` |
| Wordmark | 320 dp wide, 640:169, 18 dp gap to signature | `SplashContent.kt` |
| Signature | 22 sp, letter spacing 4 sp, `#B3FFFFFF` | `SplashContent.kt`, `colors.xml:7` |
| Version | 9 sp, 32 dp from bottom | `SplashContent.kt` |

Both backgrounds are pure black, so the fade changes only the wordmark, signature and version against Home; no background tint shift may appear.

### New, proposed (open until the owner accepts on the TV, AC-10)

| Part | Value |
|---|---|
| Reveal | whole `SplashContent` alpha 1 to 0 via `graphicsLayer`, no scale, no shift |
| Duration | 250 ms |
| Curve | `FastOutLinearInEasing`: the wordmark holds then leaves, leaving no low-alpha ghost of it over the hero |
| Warm start | same fade, no minimum dwell (Q4) |
| After the fade | `SplashContent` leaves composition; the window background stays the only layer under `App()` (AC-8) |

## Must not be seen

| Must not appear after the fade starts | Guard |
|---|---|
| `LoadingTab` spinner (`core/ui/navigation/component/TabComponent.kt:156-165`) | readiness requires Home `Content`; AC-4 |
| `HomeViewState.Loading` spinner (`HomeScreenContent.kt:80`, `:112`) | same; only the 2.5 s cap may reveal it (AC-5) |
| Home with rows but an empty carousel, or rows reshuffling as sections land | readiness waits for `heroSettled` |
| Focus landing in a row and then jumping to the hero, which scrolls the list | readiness waits for the hero claim outcome (`HomeScreenContent.kt:319-345`) |
| Hero auto-scroll page turn during the fade | the 5 s timer (`HeroCarousel.kt:52`) runs only while the hero lacks focus; with the claim settled before reveal it does not run |
| A black or blank frame between splash and Home, cold or warm | the overlay is drawn from the first frame and removed only after alpha reaches 0 (AC-10) |
| A second wordmark jump (launcher's own `SplashContent`) | the launcher copy is removed (AC-1); under the overlay the Launcher to Main switch is not visible |

Allowed after the fade, record in evidence: the hero and card skeleton shimmer (`SkeletonAsyncImage`, Coil `crossfade(true)` in `HeroCarousel.kt:229`) when a poster is not decoded yet, because images are outside the Q2 readiness rule. If the owner sees it as a flash on the TV, it becomes a follow-up, not a reason to widen readiness here.

## Focus

No new focusable element. The overlay must not be focusable and must not hold a `FocusRequester`; keys are dropped in `MainActivity.dispatchKeyEvent`, not through focus (brief.md Risks).

| State | Focus | Visibility |
|---|---|---|
| Covered | wherever the Home claim put it; invisible | n/a |
| From fade start | hero, or the first row when the hero refused | hero: as today (`HeroItem` has no focused border, `HeroCarousel.kt:210-213`); row card: 3 dp `colorScheme.border` ring (`docs/design/content/README.md:228-233`) |
| Update prompt open | its Update button (AC-12) | as in update-dialog-focus-trap |
| Auth or content-link landing | that screen's own launch focus | unchanged |

Open for the owner: D-pad opens at fade start (proposed: the ring is already visible through the fading wordmark, so the first press is not lost), or after the fade ends (adds 250 ms of dead input).

## Shared constants

| Constant | Rule |
|---|---|
| `PanelMotion` (`core/ui/uikit/component/details/VideoItemGridDetails.kt:108`) | do not reuse or retune; the reveal gets its own constant next to `SplashContent` |
| `SplashContent` | stays foundation-only (no `PuberTheme`, DI or VM), so it still draws in the first frame; the fade wraps it from outside |
| `FullScreenProgressIndicator` (`core/ui/uikit/component/Loading.kt:11`) | drawn by many screens; hide the startup spinner by readiness, never by changing this component |
| `HERO_FOCUS_REQUEST_ATTEMPTS`, `HERO_AUTO_SCROLL_DELAY_MS` | unchanged |
| Coil `crossfade(false)` default (`PuberApp.kt:107`) and per-request `crossfade(true)` | unchanged |

## Review measurements (for the visual review)

Device screenrecord at 1080p, frame by frame: fade length 250 ms +/- one frame; no frame with a spinner, an empty carousel or a row ring after alpha starts falling; the first frame after the fade has no wordmark pixels; the background stays `#000000` throughout.
