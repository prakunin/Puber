# Details on One Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fold the details screen's separate «Информация» page into the film's own screen, so the
whole description and every fact sit in one block at the top left over a full-height still.

**Architecture:** `DetailsMainPage` becomes a hero: the still or trailer fills the right three
quarters and the full page height, and one left-aligned column carries title, ratings, a meta line,
the description, a facts line, a credits line and the action buttons. The description takes the
height left over and auto-scrolls when it overflows. `DetailsInfoPage` and its parts are deleted and
the pager drops from three pages to two.

**Tech Stack:** Kotlin, Jetpack Compose (foundation 1.12.0) with TV Material3, Koin, JUnit 5 +
MockK for unit tests, Compose UI test for instrumented tests.

## Global Constraints

- The design document is `docs/superpowers/specs/2026-08-20-details-single-screen-design.md`. Where
  this plan and the spec disagree, ask rather than choose.
- The description paragraph wraps at `DESCRIPTION_MEASURE = 460.dp`. The single-line rows (meta,
  facts, credits) are capped at `SIDE_TEXT_WIDTH_FRACTION = 0.62F` of the screen width, are one
  line, and end in `TextOverflow.Ellipsis`. They must not wrap.
- The picture keeps the fractions already in `VideoItemGridDetails`: media occupies
  `MediaWidthFraction` (0.75) aligned to the end, scrim reaching zero at `ScrimEndFraction` of the
  media. 0.62 of the screen is where that scrim ends; do not change either constant.
- Auto-scroll timings: `SCROLL_START_DELAY_MS = 3_000L`, `SCROLL_SPEED_DP_PER_SECOND = 18F`,
  `SCROLL_END_PAUSE_MS = 2_000L`, then snap to the top and repeat.
- Auto-scroll runs only when the text overflows its box, and not while
  `state.trailerUrl != null` (the full-screen trailer) or `state.seasonsPanelVisible` is true.
- A row whose content is empty is not drawn at all — no empty line, no stray separator.
- Values are joined with `" · "`. Never leave a leading or trailing separator when a value is
  missing.
- Everything user-visible needs a string resource in **both** `app/src/main/res/values/` (Russian,
  in the feature's own file `video_details.xml`) and `app/src/main/res/values-en/strings.xml`.
- `./gradlew :app:testDevDebugUnitTest` and `:app:detektDevDebug` must pass. detekt's `MagicNumber`,
  `ReturnCount` (max 3) and `LongMethod` rules are enforced — name constants, keep functions short.
- Do not run `connectedAndroidTest`: it uninstalls the app and wipes the device's login. The
  controller runs instrumented checks on the television separately.
- Never `git add -A` or `git add .`. The repository owner works in this tree at the same time, and a
  blanket add sweeps their uncommitted work into your commit. Add the paths your task names.

---

### Task 1: The mapper prepares the two new lines

`DetailsInfoUIState` currently hands the screen `primaryRows` and `secondaryRows` — twelve
label/value pairs, of which the screen already shows year, genres, countries and duration
elsewhere. Replace the rows with the two prepared strings the new layout draws.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenState.kt`
  (the `DetailsInfoUIState` declaration)
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt`
  (`buildPrimaryRows`, `buildSecondaryRows`, and their call site around line 246)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt`
  (create if absent; if it exists, add to it)

**Interfaces:**
- Consumes: `Item` from `com.kino.puber.data.api.models`, `ResourceProvider`.
- Produces:
  ```kotlin
  internal data class DetailsInfoUIState(
      val ratings: List<RatingUIState>,
      val factsLine: String,
      val creditsLine: String,
  )
  ```
  `description` goes too: it is `item.plot`, and so is `VideoDetailsUIState.description`, which is
  what the hero draws. Two fields holding the same text is how they drift apart.
  `DetailsInfoRowUIState` becomes unused — delete it in Task 4, not here, because the composables
  that reference it are still alive until then.

- [ ] **Step 1: Write the failing tests**

Create the test file (or add these cases to the existing one). `FakeResourceProvider` lives in
`app/src/test/kotlin/com/kino/puber/util/` and returns the resource name for an id; assert on
*containment and order* of the values rather than on exact labels, so the test does not depend on
translation strings.

```kotlin
@Test
fun `the facts line carries what the meta line does not`() {
    val item = Item(
        id = 1,
        title = "Фильм / Movie",
        type = ItemType.MOVIE,
        quality = 1080,
        ac3 = 1,
        ageRating = "16+",
        voice = "Дубляж",
    )

    val facts = mapper.map(item, isInWatchlist = false).info.factsLine

    assertTrue(facts.contains("1080")) { facts }
    assertTrue(facts.contains("16+")) { facts }
    assertTrue(facts.contains("Дубляж")) { facts }
    // The meta line already carries these; they must not be repeated here.
    assertFalse(facts.contains("2026")) { facts }
}

@Test
fun `an item with nothing to state has an empty facts line`() {
    val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE)

    assertEquals("", mapper.map(item, isInWatchlist = false).info.factsLine)
}

@Test
fun `the credits line names the director and the cast`() {
    val item = Item(
        id = 1,
        title = "Фильм",
        type = ItemType.MOVIE,
        director = "Иван Иванов",
        cast = "А Актёр, Б Актёр",
    )

    val credits = mapper.map(item, isInWatchlist = false).info.creditsLine

    assertTrue(credits.contains("Иван Иванов")) { credits }
    assertTrue(credits.contains("А Актёр")) { credits }
}

@Test
fun `a missing director leaves no dangling separator`() {
    val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE, cast = "А Актёр")

    val credits = mapper.map(item, isInWatchlist = false).info.creditsLine

    assertFalse(credits.startsWith(" · ")) { credits }
    assertFalse(credits.endsWith(" · ")) { credits }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.details.model.DetailsScreenUIMapperTest"`
Expected: compilation failure — `factsLine` does not exist yet.

- [ ] **Step 3: Reshape the state**

In `DetailsScreenState.kt` replace the `primaryRows`/`secondaryRows` properties:

```kotlin
@Immutable
internal data class DetailsInfoUIState(
    val ratings: List<RatingUIState>,
    /** Quality, sound, age rating, translation, track and subtitle counts, joined by " · ". */
    val factsLine: String,
    /** `Режиссёр: …` and `В ролях: …`, joined by " · ". */
    val creditsLine: String,
)
```

Drop `description` from it and from the mapper's construction. Every reference to `info.description`
belongs to the page Task 4 deletes.

- [ ] **Step 4: Build the lines in the mapper**

Replace `buildPrimaryRows` and `buildSecondaryRows` with the two builders below and update the
`DetailsInfoUIState(...)` construction around line 246 to pass `factsLine` and `creditsLine`.

```kotlin
private fun buildFactsLine(item: Item): String = buildList {
    item.displayQuality()?.let(::add)
    if (item.ac3 == 1 || item.mediaItemsHaveSurroundSound()) {
        add(resources.getString(R.string.video_details_info_sound_surround))
    }
    item.ageRating?.takeIf(String::isNotBlank)?.let(::add)
    item.voice?.takeIf(String::isNotBlank)?.let(::add)
    item.playbackAudioTrackCount().takeIf { it > 0 }?.let { count ->
        add(resources.getString(R.string.video_details_facts_audio_tracks, count))
    }
    item.subtitleCount().takeIf { it > 0 }?.let { count ->
        add(resources.getString(R.string.video_details_facts_subtitles, count))
    }
}.joinToString(FACT_SEPARATOR)

private fun buildCreditsLine(item: Item): String = buildList {
    item.director?.takeIf(String::isNotBlank)?.let { director ->
        add(resources.getString(R.string.video_details_facts_director, director))
    }
    item.castMembers().takeIf { it.isNotEmpty() }?.let { cast ->
        add(resources.getString(R.string.video_details_facts_cast, cast.joinToString(", ")))
    }
}.joinToString(FACT_SEPARATOR)
```

Add to the mapper's `private companion object`:

```kotlin
const val FACT_SEPARATOR = " · "
```

`displayQuality()`, `mediaItemsHaveSurroundSound()`, `playbackAudioTrackCount()`, `subtitleCount()`
and `castMembers()` already exist in this file — do not rewrite them.

- [ ] **Step 5: Add the string resources**

`app/src/main/res/values/video_details.xml`:

```xml
<string name="video_details_facts_audio_tracks">Дорожек: %1$d</string>
<string name="video_details_facts_subtitles">Субтитры: %1$d</string>
<string name="video_details_facts_director">Режиссёр: %1$s</string>
<string name="video_details_facts_cast">В ролях: %1$s</string>
```

`app/src/main/res/values-en/strings.xml`:

```xml
<string name="video_details_facts_audio_tracks">Audio tracks: %1$d</string>
<string name="video_details_facts_subtitles">Subtitles: %1$d</string>
<string name="video_details_facts_director">Director: %1$s</string>
<string name="video_details_facts_cast">Cast: %1$s</string>
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.details.model.*"`
Expected: PASS. The screen does not compile yet — that is Task 3's job. Run
`:app:compileDevDebugKotlin` only after Task 3.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details/model app/src/main/res app/src/test/kotlin/com/kino/puber/ui/feature/details/model
git commit -m "Prepare the details facts as lines rather than a grid"
```

---

### Task 2: A description that scrolls itself when it overflows

A self-contained composable, testable and reviewable on its own, before anything is rearranged
around it.

**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/details/component/SelfScrollingText.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  internal fun SelfScrollingText(
      text: String,
      style: TextStyle,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
  )
  ```
  Fills the height it is given. Draws `text` from the top. When the text is taller than the box and
  `enabled` is true, it cycles: still at the top, scroll down, still at the bottom, snap back.

- [ ] **Step 1: Write the composable**

```kotlin
package com.kino.puber.ui.feature.details.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.clipScrollableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Still at the top before anything moves, so the opening can be read. */
private const val SCROLL_START_DELAY_MS = 3_000L

/** Still at the bottom before the snap back. */
private const val SCROLL_END_PAUSE_MS = 2_000L

/** Slow enough to read along with. */
private const val SCROLL_SPEED_DP_PER_SECOND = 18F

private const val MILLIS_PER_SECOND = 1_000F

/**
 * Text that scrolls itself when it does not fit, and loops.
 *
 * The remote never drives this: the description is not focusable, and on a screen where LEFT and
 * RIGHT move between buttons there is nothing to spare for scrolling text. So it either fits, or it
 * shows itself in turn.
 */
@Composable
internal fun SelfScrollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // `maxValue` is how much of the text is out of sight: zero means it fits, and nothing runs.
    val overflow = scrollState.maxValue
    LaunchedEffect(text, enabled, overflow) {
        if (!enabled || overflow <= 0) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }
        while (true) {
            scrollState.scrollTo(0)
            delay(SCROLL_START_DELAY_MS)
            val distanceDp = with(density) { overflow.toDp().value }
            val durationMs = (distanceDp / SCROLL_SPEED_DP_PER_SECOND * MILLIS_PER_SECOND).toInt()
            scrollState.animateScrollTo(
                value = overflow,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            delay(SCROLL_END_PAUSE_MS)
        }
    }

    Box(modifier = modifier.clipScrollableContainer(Orientation.Vertical)) {
        Text(
            text = text,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = false),
        )
    }
}
```

Note for the implementer: `clipScrollableContainer` needs
`import androidx.compose.foundation.gestures.Orientation`. If the API in this Compose version does
not accept an orientation, use `Modifier.clipToBounds()` instead and say so in your report.

`verticalScroll(enabled = false)` keeps the remote out of it while still allowing programmatic
scrolling — check this on the version in use; if a disabled scroll also blocks `animateScrollTo`,
leave it enabled and report that, because the description is not focusable and cannot receive keys
either way.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL. The composable is not referenced yet; that is expected.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details/component/SelfScrollingText.kt
git commit -m "Add a description that scrolls itself when it overflows"
```

---

### Task 3: Rebuild the main page as one hero

The layout change itself. `DetailsMainPage` stops stacking a panel over a button row and becomes a
picture with a column of text on top of it.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`
  (`DetailsMainPage` around line 300, `DetailsContentSkeleton` around line 885, the constants block
  at the end)
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt`
  (expose the media alone)

**Interfaces:**
- Consumes: `DetailsInfoUIState.factsLine` / `.creditsLine` from Task 1; `SelfScrollingText` from
  Task 2.
- Produces: a new public composable in `VideoItemGridDetails.kt`:
  ```kotlin
  @Composable
  fun VideoDetailsMedia(
      modifier: Modifier,
      state: VideoDetailsUIState,
      trailerUrl: String? = null,
      onTrailerFinished: () -> Unit = {},
  )
  ```
  This is the existing private `VideoDetailsPoster` with `fullBleed = true`, made available to the
  details screen so the hero can place it itself. `VideoItemGridDetails` keeps working exactly as it
  does for the catalogue and the favourites screen — do not change its behaviour or its callers.

- [ ] **Step 1: Expose the media**

In `VideoItemGridDetails.kt`, add the wrapper above. It must call the existing `VideoDetailsPoster`
with `fullBleed = true` and the same width fraction and alignment the full-bleed branch uses, so the
catalogue and the details screen show the picture identically:

```kotlin
@Composable
fun VideoDetailsMedia(
    modifier: Modifier,
    state: VideoDetailsUIState,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
) {
    Box(modifier = modifier) {
        VideoDetailsPoster(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(MediaWidthFraction)
                .align(Alignment.CenterEnd),
            imageUrl = state.imageUrl,
            imageFallbackUrls = state.imageFallbackUrls,
            trailerUrl = trailerUrl,
            onTrailerFinished = onTrailerFinished,
            fullBleed = true,
        )
    }
}
```

- [ ] **Step 2: Rebuild `DetailsMainPage`**

Replace the body of `DetailsMainPage`. The signature keeps its current parameters.

```kotlin
Box(modifier = modifier) {
    VideoDetailsMedia(
        modifier = Modifier.fillMaxSize(),
        state = state.details,
        trailerUrl = state.previewTrailerUrl,
        onTrailerFinished = { onAction(DetailsAction.TrailerPreviewFinished) },
    )

    // Drawn after the media: the trailer is a SurfaceView and clears whatever the window painted
    // before it, so only what comes later survives over a playing video.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = HERO_PADDING_START, top = HERO_PADDING_TOP, bottom = HERO_PADDING_BOTTOM),
    ) {
        // `VideoItemUIMapper.formatTitle` has already split `Русское / Original` onto two lines, so
        // this one Text carries both. It is left-aligned here, unlike the centred panel it replaces.
        Text(
            text = state.details.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(DESCRIPTION_MEASURE),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.info.ratings.forEach { rating -> Rating(rating) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HeroLine(text = metaLine(state.details))
        Spacer(modifier = Modifier.height(12.dp))
        SelfScrollingText(
            text = state.details.description,
            style = MaterialTheme.typography.bodySmall,
            enabled = !state.seasonsPanelVisible && state.trailerUrl == null,
            modifier = Modifier
                .width(DESCRIPTION_MEASURE)
                .weight(1F),
        )
        Spacer(modifier = Modifier.height(12.dp))
        HeroLine(text = state.info.factsLine)
        HeroLine(text = state.info.creditsLine)
        Spacer(modifier = Modifier.height(16.dp))
        ActionButtonsRow(
            buttons = state.buttons,
            isInWatchlist = state.isInWatchlist,
            isWatched = state.isWatched,
            onAction = onAction,
            currentEpisode = state.currentEpisode,
            onEpisodeContextMenu = onEpisodeContextMenu,
            seasonsPanelVisible = seasonsPanelVisible,
            trailerVisible = state.trailerUrl != null,
            recoverActionFocus = recoverActionFocus,
            scrollToMainPage = scrollToMainPage,
        )
        if (showPageChevron) {
            ChevronIndicator()
        }
    }
}
```

- [ ] **Step 3: Add the row helper and the meta line**

In the same file:

```kotlin
/**
 * A single line of facts. It runs over the artwork rather than wrapping inside the text column: a
 * list of countries folding onto a second line pushes everything below it down, and the scrim
 * reaches zero at the same fraction this stops at, so no part of it lands on an unmuted frame.
 */
@Composable
private fun HeroLine(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = HERO_LINE_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(SIDE_TEXT_WIDTH_FRACTION),
    )
}

private fun metaLine(details: VideoDetailsUIState): String = listOf(
    details.year,
    details.genres,
    details.country,
    details.duration,
).filter { it.isNotBlank() }.joinToString(" · ")
```

`details.duration` already reads `Длительность: 2:00` for a film and the season count for a series —
use it as it stands rather than reformatting.

- [ ] **Step 4: Add the constants**

At the bottom of the file, beside the existing ones:

```kotlin
private val HERO_PADDING_START = 48.dp
private val HERO_PADDING_TOP = 40.dp
private val HERO_PADDING_BOTTOM = 24.dp
private val DESCRIPTION_MEASURE = 460.dp
private const val SIDE_TEXT_WIDTH_FRACTION = 0.62F
private const val HERO_LINE_ALPHA = 0.72F
private const val TITLE_MAX_LINES = 3
```

Delete `FIRST_PAGE_DESCRIPTION_LINES` and `DETAILS_CONTENT_WEIGHT` if nothing else references them.

- [ ] **Step 5: Match the skeleton to the new shape**

`DetailsContentSkeleton` currently draws a `VideoItemGridDetails` panel over a button row. Give it
the same outline as the hero: a `Column` with the same `HERO_PADDING_*` values holding, top to
bottom, a 24 dp title bar 320 dp wide, a 16 dp meta bar 280 dp wide, four 12 dp description bars
440 dp wide spaced 8 dp apart, a 12 dp facts bar 380 dp wide, and the button placeholders the
function already builds. Reuse the existing `Modifier.placeholder(visible = true, shape = …)` calls
rather than inventing another shimmer.

- [ ] **Step 6: Verify it compiles and the unit tests still pass**

Run: `./gradlew :app:compileDevDebugKotlin :app:testDevDebugUnitTest`
Expected: BUILD SUCCESSFUL. `DetailsInfoPage` still exists and still compiles at this point; it is
simply no longer what the user sees first.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details app/src/main/java/com/kino/puber/core/ui/uikit/component/details
git commit -m "Rebuild the film's screen as one hero with everything on it"
```

---

### Task 4: Delete the information page — FOLDED INTO TASK 1

**Do not dispatch this task.** It moved into Task 1 during execution and is recorded here only so
the numbering of the tasks around it stays stable.

Why it moved: unit tests and detekt both compile against the main variant, so Task 1 — which changes
`DetailsInfoUIState` out from under `DetailsScreenContent.kt` — could not be verified while the page
that reads the old shape still existed. Splitting a compile-breaking change across two tasks left
the first one unverifiable. The deletion belongs with the change that makes it necessary.

The steps below were carried out as part of Task 1.


**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenState.kt`
  (delete `DetailsInfoRowUIState`)
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenPreview.kt`
  if it references anything deleted

- [ ] **Step 1: Delete the page and its parts**

Remove `DetailsInfoPage`, `DetailsInfoHeader`, `DetailsCastRow`, `DetailsInfoGrid`,
`DetailsInfoChip` and the `infoPageFocusRequester` / `focusInfoPage` machinery. Remove
`INFO_CHIP_MAX_LINES`, `INFO_ROW_MAX_LINES` and `INFO_CHIP_FOCUS_SAFE_PADDING` if nothing else uses
them. Delete `DetailsInfoRowUIState` from the model file.

- [ ] **Step 2: Renumber the pager**

```kotlin
private const val MAIN_PAGE_INDEX = 0
private const val SIMILAR_PAGE_INDEX = 1

private const val DETAILS_PAGES_BASE = 1
private const val DETAILS_PAGES_WITH_SIMILAR = 2
```

`DetailsSimilarPage`'s `onPreviousPageRequested` becomes `focusMainPage`. The `LaunchedEffect` that
requests focus per page loses its `INFO_PAGE_INDEX` branch. `DetailsMainPage` keeps
`scrollToMainPage`.

- [ ] **Step 3: Check nothing dangles**

Run: `grep -rn "INFO_PAGE_INDEX\|DetailsInfoPage\|DetailsInfoRowUIState\|infoPageFocusRequester" app/src`
Expected: no matches outside comments.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:compileDevDebugKotlin :app:testDevDebugUnitTest :app:detektDevDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/details
git commit -m "Delete the information page the film's screen replaced"
```

---

### Task 5: Pin the new screen with tests

**Files:**
- Modify: `app/src/androidTest/java/com/kino/puber/ui/feature/details/` — add a Compose UI test for
  the hero. Look for an existing details instrumented test first and add to it; create
  `DetailsHeroLayoutTest.kt` only if there is none.
- Modify: `app/src/test/kotlin/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapperTest.kt`

- [ ] **Step 1: Cover what a unit test can reach**

The lines are built in the mapper, so most of this belongs to Task 1's tests. Add the two cases that
are about the screen's contract rather than the mapper's:

```kotlin
@Test
fun `a series states its seasons where a film states its duration`() {
    val series = Item(
        id = 1,
        title = "Сериал",
        type = ItemType.SERIAL,
        seasons = listOf(Season(id = 1, number = 1, episodes = emptyList())),
    )

    val mapped = mapper.map(series, isInWatchlist = false)

    assertTrue(mapped.details.duration.isNotBlank()) { mapped.details.duration }
    assertFalse(mapped.info.factsLine.contains(mapped.details.duration)) { mapped.info.factsLine }
}

@Test
fun `an item with no facts and no credits maps to two empty lines`() {
    val bare = Item(id = 1, title = "Фильм", type = ItemType.MOVIE)

    val info = mapper.map(bare, isInWatchlist = false).info

    assertEquals("", info.factsLine)
    assertEquals("", info.creditsLine)
}
```

- [ ] **Step 2: Write the Compose test for the hero**

Assert, with a `DetailsScreenState.Content` fixture:
- the title, the meta line, the facts line and the credits line are all displayed;
- a state whose `factsLine` is empty displays no blank row — assert the credits line is still
  displayed, which fails if an empty row consumed the space;
- the action buttons are displayed on the same screen as the description, with no scrolling.

Use `createComposeRule()` and `onNodeWithText(...).assertIsDisplayed()`. Do not use
`waitForIdle()` around the auto-scrolling description without a bounded `waitUntil`: the scroll is a
looping animation and an unbounded wait for idleness may never return. Prefer a fixture whose
description is short enough not to overflow, and cover the overflowing case by asserting the text
node exists rather than by waiting for the animation.

- [ ] **Step 3: Run the unit tests**

Run: `./gradlew :app:testDevDebugUnitTest`
Expected: PASS. Do **not** run `connectedAndroidTest` — see the global constraints.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/kotlin/com/kino/puber/ui/feature/details app/src/androidTest
git commit -m "Pin the film screen's rows and buttons with tests"
```

---

## Notes for the controller

- The television check belongs to you, not to the task subagents: deploy with
  `make run DEVICE=192.168.1.121:5555` and confirm on a film and on a series that the title, meta,
  description, facts, credits and buttons are all on one screen, that a long description scrolls and
  loops, and that scrolling down still reaches «Похожее».
- `adb exec-out screencap` blanks while a trailer plays. Check the layout before the two-second
  pause elapses, or with the auto-trailer setting off.
- The instrumented suite has pre-existing failures unrelated to this work. If you run it, compare
  against the same suite on `master` before treating a failure as new.
