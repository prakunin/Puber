# One catalogue carousel — what the television established

Device: 192.168.1.121:5555 (VS KM6, Android 10), 1920x1080 at density 320, so 960x540 dp.
Branch range: a39103a..0f6a6c6.

## Verified by looking

- Exactly one carousel is visible below the detail panel; no neighbour peeks above or below.
  Headings render smaller and lighter. Roughly 2.5 cards fit across, against 3.3 before. The
  user reviewed this and chose to keep 190 dp cards and the 16 sp heading.
- D-pad Down and Up move between sections on several tabs, including one with a hero, and each
  lands flush with the viewport top. Empty sections are stepped over. Focus reaches the next
  off-screen item through `BeyondBoundsLayout` — a claim that a controller hypothesis initially
  contradicted and the device settled.
- Back from the details screen returns to the section and the card it was left on: screenshots
  before and after are byte-identical.
- In `NavigationMode.TopTabs`, where there is no detail panel, the first implementation left
  ~43% of the screen blank. After scoping the page layout to the panel's presence, TopTabs shows
  two full sections again and a hero measures its intended 280 dp.

## Instrumented suite at HEAD

103 tests, 13 failures.

- `TopTabDetailsBackFocusTest` 2/2 pass — the class asserting the card geometry this branch moved.
- `SectionRowFocusTraversalTest` 4/5; the new test covering the empty-section collapse and the
  section index offset passed on its first run.
- The other 12 failures were **not assumed** to be pre-existing. The six affected classes were
  built and run from a worktree at 48ffeb1, the commit before this branch, and all 12 reproduce
  identically there. This branch introduces no instrumented-test regression.

## Not verified, deliberately

- The cross-section details prefetch fix rests on source reasoning. The television emits no
  app-level logs, so the warm-cache path could not be observed.
- Loading and Error sections were never on screen at a moment a key could be pressed.
- The `Мультфильмы` tab, and held-key repeat.

## Known-red, pre-existing

`downToEndRightThenUpRestoresGenericRowTargetsInsideViewport` and
`HomeFocusTraversalTest.downToEndRightThenUpRestoresHomeRowTargetsInsideViewport` fail the same
way: one row is entered by plain directional focus search, bypassing `focusRestorer` and its
fallback requester, so the wrong card is restored. Bisected to 48ffeb1. It affects Home as well
as the catalogue and deserves its own ticket.
