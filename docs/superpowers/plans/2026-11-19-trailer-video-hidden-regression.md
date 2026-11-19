# Trailer preview shows video again after a card change - plan

**Spec:** `docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md`
**Goal:** Force a redraw of the trailer's `SurfaceView` when it gets a non-zero size late, so `updateSurface()` creates the surface and the video shows after every card change.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Work in `/home/user1/orca/workspaces/Puber/trailer-video-hidden-regression`; never `./gradlew` there. Prefix every build with `AGENT_GRADLE_SERIAL=1`.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests: `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.TrailerSurfaceRedrawTest'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style: one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Production code changes only `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt` (below: `TPP.kt`). Out of scope (spec: Out of scope): `VideoItemGridDetails.kt`, `SkeletonAsyncImage.kt`, existing tests, fallback B, the version bump.
- Device checks D0-D4 and owner checks T1-T3 (spec: Test design) belong to qa and the owner, not to these tasks.

---

## Tasks

### Task 1: Add the size predicate with its unit tests, test first

**Covers:** AC-6
**Files:**
- Test (new): `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt` - `internal class TrailerSurfaceRedrawTest`, plain JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions.assertTrue/assertFalse`), no extensions, same shape as `VideoDetailsPosterStateTest.kt:20-29`.
- Modify: `TPP.kt` - add the top-level predicate after the composable (`TPP.kt:140` closes `TrailerPreviewPlayer`), before `updateContentGravity` (`TPP.kt:142-154`).

**Consumes:** nothing.
**Produces:** `internal fun surfaceNeedsRedraw(oldWidth: Int, oldHeight: Int, newWidth: Int, newHeight: Int): Boolean` = `newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)`.

Tests (names and cases from spec: Test design, Traceability):

| Test | Call | Expect |
|---|---|---|
| `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` | `(0, 0, 1350, 759)` | true |
| `surfaceNeedsRedraw_false_whenSizeUnchanged` | `(1350, 759, 1350, 759)` | false |
| `surfaceNeedsRedraw_true_whenSizeChangesBetweenNonZeroSizes` | `(1350, 759, 1280, 720)` | true |
| `surfaceNeedsRedraw_false_whenSizeBecomesZero` | `(1350, 759, 0, 0)` | false |
| `surfaceNeedsRedraw_false_whenOnlyOneDimensionIsZero` | `(0, 0, 1350, 0)` and `(0, 0, 0, 759)` | false for both |

- [x] Write `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` first and run the touched-tests command: it must fail with `Unresolved reference 'surfaceNeedsRedraw'` (red run; record it).
- [x] Add the other four tests.
- [x] Add `surfaceNeedsRedraw` to `TPP.kt` with a one-line KDoc: a redraw is needed only for a change to a non-zero size.
- [x] Run the fast gate and the touched tests; commit.

**Done when:** the touched-tests command reports 5 tests passed, 0 failed, and the fast gate is green.

---

### Task 2: Redraw the SurfaceView when it gets a size

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-7
**Files:**
- Modify: `TPP.kt`
  - Imports (`TPP.kt:3-27`): add `android.view.View`.
  - New top-level `private val` listener next to `surfaceNeedsRedraw`, e.g. `surfaceRedrawOnSizeChange = View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom -> if (surfaceNeedsRedraw(oldRight - oldLeft, oldBottom - oldTop, right - left, bottom - top)) v.post { v.invalidate() } }`. It captures nothing; any lambda parameter left unused is named `_` (detekt `UnusedParameter`, `config/detekt/detekt.yml:817-820`).
  - `factory` (`TPP.kt:126-133`): inside the `PlayerView(ctx).apply { ... }` (`:127-132`), add `videoSurfaceView?.addOnLayoutChangeListener(surfaceRedrawOnSizeChange)`. `videoSurfaceView` is media3 `@UnstableApi` and nullable; the composable is already `@UnstableApi` (`TPP.kt:40`).
  - `AndroidView` (`TPP.kt:125-139`): add `onRelease = { playerView -> playerView.videoSurfaceView?.removeOnLayoutChangeListener(surfaceRedrawOnSizeChange) }`. Leave `onReset` unset and `update` (`:134-137`) unchanged.
  - KDoc (`TPP.kt:36-38`): append one sentence: the `SurfaceView` is redrawn once it gets a size, because a size that arrives after the window's last pre-draw gets no surface otherwise.

**Consumes:** `surfaceNeedsRedraw` (Task 1); `PlayerView.getVideoSurfaceView()` (media3 1.11.0, `gradle/libs.versions.toml:27`); `AndroidView(factory, modifier, onReset, onRelease, update)` (Compose BOM `gradle/libs.versions.toml:7`).
**Produces:** nothing consumed later.

- [x] Make the `TPP.kt` changes above; no other production file changes.
- [x] Run the fast gate and the touched tests.
- [x] Run the full gate (AC-7).
- [x] Check the AC-5 guard: `git diff 3ec85287 --stat -- app/src/test/kotlin/com/kino/puber/core/ui/uikit/component app/src/main/java/com/kino/puber/core/ui/uikit/component` lists only `TrailerSurfaceRedrawTest.kt` and `TrailerPreviewPlayer.kt`.
- [x] Commit.

**Done when:** the full gate is green; the guard diff lists only the two files; `grep -n surfaceRedrawOnSizeChange TPP.kt` shows exactly one declaration, one `addOnLayoutChangeListener` and one `removeOnLayoutChangeListener`. AC-1 to AC-5 are then settled by qa (D0-D4) and the owner (T1-T3).

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- 1bdff891 Add the surface redraw size predicate with unit tests
- d977f7e5 Redraw the trailer SurfaceView once it gets a size

**Red run** (P0 `red` tests on the unchanged code):

- red: `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` - `Unresolved reference 'surfaceNeedsRedraw'` (compile failure, as the plan expects; the predicate did not exist).
- red: `surfaceNeedsRedraw_false_whenSizeUnchanged` - `Unresolved reference 'surfaceNeedsRedraw'`.
- red: `surfaceNeedsRedraw_true_whenSizeChangesBetweenNonZeroSizes` - `Unresolved reference 'surfaceNeedsRedraw'`.
- red: `surfaceNeedsRedraw_false_whenSizeBecomesZero` - `Unresolved reference 'surfaceNeedsRedraw'`.

The last three were not run separately: all four tests live in `TrailerSurfaceRedrawTest.kt`, one compilation unit, so on base 3ec85287 they fail with the same compile failure. None reached its assertion.

**Deviations from the plan:**

None.

**Files touched outside the plan:**

None.

**For the reviewers:**

`check.full` green (BUILD SUCCESSFUL in 4m 27s). The AC-5 guard diff lists only `TrailerPreviewPlayer.kt` and `TrailerSurfaceRedrawTest.kt`. AC-1 to AC-5 need qa (D0-D4) and the owner (T1-T3).

**Blocked:**

None.

## Readiness

Rounds 1 and 2 were NOT READY (`.flow/runs/trailer-video-hidden-regression/readiness-1.md`, `readiness-2.md`). Round 3:

# Readiness 3: trailer-video-hidden-regression

Verdict: **READY**

Spec and plan at worktree HEAD 3e70c43f, base 3ec85287. Rounds 1 and 2 (`readiness-1.md`, `readiness-2.md`) hold for AC coverage, citations, build order and scope; this round checks the round-2 fix only.

| Check | Evidence | Status |
|---|---|---|
| Stale copy removed | `git show --stat 3e70c43f`: spec only, 90 deletions, no additions | ok |
| One `## Test design`, one `## Open questions` | spec headings at lines 116 and 204 | ok |
| Kept Test design matches `test-design.md` | diff differs only in heading levels and the title | ok |
| P0 rows marked | spec lines 140-152: every P0 row is `red` or `guard` | ok |
| D4 includes M2 | spec D4 row and the headless note after it | ok |
| Q1/Q2 table kept, both answered | spec lines 206-209 | ok |
| Plan unchanged | `git diff --stat 84eced53 3e70c43f` touches no plan file | ok |

Must fix: none.
