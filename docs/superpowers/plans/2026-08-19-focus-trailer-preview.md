# Focus Trailer Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resting focus on a catalogue card for two seconds replaces the still in the detail panel with the title's trailer, and the side rail becomes the default navigation mode so that panel is what users actually see.

**Architecture:** `ContentListVM` owns the timing. Its existing `focusedItemJob` — already cancelled on every focus change — gains a countdown that runs alongside the details request and publishes `previewTrailerUrl` into `ContentListViewState`. The panel renders a new `TrailerPreviewPlayer` over the poster whenever that field is non-null, and reports playback end, player errors, and backgrounding back through one `TrailerPreviewFinished` action.

**Tech Stack:** Kotlin, Compose TV Material3, Koin, AndroidX Media3 (ExoPlayer + PlayerView), JUnit 5, MockK, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-08-19-focus-trailer-preview-design.md`

## Global Constraints

- File content and commit messages are English.
- Never add a `Co-Authored-By` trailer to commits in this repository.
- Every new user-visible string goes in **both** `app/src/main/res/values/` (Russian — the default locale, split across topic files) and `app/src/main/res/values-en/strings.xml` (English).
- Dependency versions come from `gradle/libs.versions.toml`; never hardcode them. This plan adds no new dependencies — Media3 is already on the classpath (`TrailerOverlay.kt`).
- Compile check: `./gradlew :app:compileDevDebugKotlin`
- Full check (unit tests + detekt with type resolution): `./gradlew testDevDebugUnitTest :app:detektAll`, also available as `make check`.
- Run a single unit test class: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.<FQCN>"`
- Content composables stay pure: state in, `onAction: (UIAction) -> Unit` out. No repository or preference reads inside composables.

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt` | Navigation-mode default; new auto-trailer preference | 1, 2 |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsViewState.kt` | Settings screen state: initial mode, new toggle field | 1, 3 |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsPreferencesStore.kt` | Synchronous settings snapshot + writes | 3 |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsActions.kt` | New `ToggleAutoTrailer` action | 3 |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt` | Toggle handling | 3 |
| `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt` | The switch row | 3 |
| `app/src/main/res/values/main_screen_strings.xml`, `app/src/main/res/values-en/strings.xml` | Switch label and description | 3 |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/ContentListViewState.kt` | `previewTrailerUrl` | 4 |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/ContentListAction.kt` | `TrailerPreviewFinished` | 4 |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt` | The two-second countdown and every path that clears it | 4 |
| `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt` | **New.** Muted-capable ExoPlayer surface sized to the poster, reporting its own end | 5 |
| `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt` | Draws the player over the poster | 6 |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt` | Passes state and the finish callback | 6 |

Tasks 1–3 are independent of 4–6 and can be reviewed separately. Task 4 depends on Task 2's getter. Task 6 depends on Tasks 4 and 5.

---

### Task 1: Side rail becomes the default navigation mode

The detail panel this feature builds on only exists in `NavigationMode.SideDrawer` (`ContentListVM.kt:45-50`), and the stored default is top tabs. The preference key is written only on an explicit choice, so this moves over exactly the users who never opened the setting.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt:55-58`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsViewState.kt:36`
- Test: `app/src/test/kotlin/com/kino/puber/data/preferences/NavigationPreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `NavigationPreferencesRepository.getNavigationMode(): NavigationMode` now returns `NavigationMode.SideDrawer` for untouched preferences.

- [ ] **Step 1: Write the failing tests**

Add to `NavigationPreferencesRepositoryTest.kt`, next to the other default-value tests:

```kotlin
@Test
fun navigationMode_defaultsToTheSideDrawerWithoutWritingPreferences() {
    val fixture = fixture()

    assertEquals(NavigationMode.SideDrawer, fixture.repository.getNavigationMode())
    assertTrue(fixture.preferences.transactions.isEmpty())
}

@Test
fun navigationMode_keepsAnExplicitlyChosenTopTabs() {
    val fixture = fixture()

    fixture.repository.setNavigationMode(NavigationMode.TopTabs)

    assertEquals(NavigationMode.TopTabs, fixture.repository.getNavigationMode())
}

@Test
fun navigationMode_fallsBackToTheSideDrawerForAnUnknownStoredValue() {
    val fixture = fixture()
    fixture.preferences.values[NAVIGATION_MODE_KEY] = "RemovedMode"

    assertEquals(NavigationMode.SideDrawer, fixture.repository.getNavigationMode())
}
```

Add the key constant beside the other private constants at the top of the file (after `private const val STARTUP_TAB_KEY = "startup_tab"`):

```kotlin
private const val NAVIGATION_MODE_KEY = "navigation_mode"
```

`NavigationMode` is already imported in this test file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.preferences.NavigationPreferencesRepositoryTest"`
Expected: FAIL — `navigationMode_defaultsToTheSideDrawerWithoutWritingPreferences` and `navigationMode_fallsBackToTheSideDrawerForAnUnknownStoredValue` report `expected: <SideDrawer> but was: <TopTabs>`.

- [ ] **Step 3: Change the default**

In `NavigationPreferencesRepository.kt`, replace the body of `getNavigationMode()`:

```kotlin
    fun getNavigationMode(): NavigationMode {
        val name = prefs.getString(KEY_NAVIGATION_MODE, NavigationMode.SideDrawer.name)
        return NavigationMode.entries.find { it.name == name } ?: NavigationMode.SideDrawer
    }
```

In `DeviceSettingsViewState.kt`, change the initial value inside `DeviceSettingsState.Success`:

```kotlin
        val navigationMode: NavigationMode = NavigationMode.SideDrawer,
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.preferences.NavigationPreferencesRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Run the whole unit suite**

Run: `./gradlew testDevDebugUnitTest`
Expected: PASS. Every other test that cares stubs `getNavigationMode()` explicitly, so nothing else should move. If something fails, it is asserting the old default — fix the assertion, not the production default.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt \
        app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsViewState.kt \
        app/src/test/kotlin/com/kino/puber/data/preferences/NavigationPreferencesRepositoryTest.kt
git commit -m "Default navigation to the side rail"
```

---

### Task 2: `autoTrailerEnabled` preference

The switch that Task 3 draws needs somewhere to live. It goes in `NavigationPreferencesRepository` rather than `PlayerPreferencesRepository` because it governs the catalogue, and `ContentListVM` already injects this repository.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt` (new methods after `setNavigationMode`, new key in the private companion at `:257`)
- Test: `app/src/test/kotlin/com/kino/puber/data/preferences/NavigationPreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `NavigationPreferencesRepository.getAutoTrailerEnabled(): Boolean` — `true` unless stored otherwise
  - `NavigationPreferencesRepository.setAutoTrailerEnabled(enabled: Boolean)`
  - SharedPreferences key `"auto_trailer_enabled"`

- [ ] **Step 1: Write the failing tests**

Add to `NavigationPreferencesRepositoryTest.kt`:

```kotlin
@Test
fun autoTrailer_defaultsToOnWithoutWritingPreferences() {
    val fixture = fixture()

    assertTrue(fixture.repository.getAutoTrailerEnabled())
    assertTrue(fixture.preferences.transactions.isEmpty())
}

@Test
fun autoTrailer_persistsTheStoredChoice() {
    val fixture = fixture()

    fixture.repository.setAutoTrailerEnabled(false)

    assertFalse(fixture.repository.getAutoTrailerEnabled())
    assertEquals(false, fixture.preferences.values[AUTO_TRAILER_KEY])
}
```

And the key constant, beside the ones added in Task 1:

```kotlin
private const val AUTO_TRAILER_KEY = "auto_trailer_enabled"
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.preferences.NavigationPreferencesRepositoryTest"`
Expected: FAIL to compile — "unresolved reference: getAutoTrailerEnabled".

- [ ] **Step 3: Add the preference**

In `NavigationPreferencesRepository.kt`, directly after `setNavigationMode`:

```kotlin
    /**
     * Whether a card that keeps focus swaps its still for the trailer. Defaults to on; the key is
     * written only when the user changes it, so nothing is stored for a default install.
     */
    fun getAutoTrailerEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_TRAILER, true)

    fun setAutoTrailerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_TRAILER, enabled).apply()
    }
```

In the private companion object at the bottom, next to `KEY_SHOW_WATCHED_INDICATORS`:

```kotlin
        const val KEY_AUTO_TRAILER = "auto_trailer_enabled"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.preferences.NavigationPreferencesRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt \
        app/src/test/kotlin/com/kino/puber/data/preferences/NavigationPreferencesRepositoryTest.kt
git commit -m "Add the auto-trailer preference"
```

---

### Task 3: Auto-trailer switch on the settings screen

Wires the preference from Task 2 through the settings store, VM, and screen. Nothing reads the value yet — Task 4 does that.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsPreferencesStore.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsActions.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsViewState.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt` (`contentItems`, around `:549-576`)
- Modify: `app/src/main/res/values/main_screen_strings.xml`, `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMAutoTrailerTest.kt` (create)

**Interfaces:**
- Consumes: `NavigationPreferencesRepository.getAutoTrailerEnabled()` / `setAutoTrailerEnabled(Boolean)` from Task 2.
- Produces:
  - `DeviceSettingsActions.ToggleAutoTrailer` (data object)
  - `DeviceSettingsState.Success.autoTrailerEnabled: Boolean`
  - `DeviceSettingsPreferencesStore.setAutoTrailer(enabled: Boolean)`
  - `DeviceSettingsPreferencesSnapshot.autoTrailerEnabled: Boolean`
  - String resources `R.string.settings_auto_trailer`, `R.string.settings_auto_trailer_description`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMAutoTrailerTest.kt`:

```kotlin
package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class DeviceSettingsVMAutoTrailerTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val navigationPreferences = mockk<NavigationPreferencesRepository>(relaxed = true)
    private var storedAutoTrailer = true

    @Test
    fun theSectionOpensOnTheStoredChoice() {
        storedAutoTrailer = false

        val vm = createVM()

        assertEquals(false, vm.successState().autoTrailerEnabled)
    }

    @Test
    fun togglingStoresTheNewValueAndShowsIt() {
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ToggleAutoTrailer)

        verify { navigationPreferences.setAutoTrailerEnabled(false) }
        assertEquals(false, vm.successState().autoTrailerEnabled)
    }

    private fun DeviceSettingsVM.successState(): DeviceSettingsState.Success {
        return testStateValue.state as DeviceSettingsState.Success
    }

    private fun createVM(): DeviceSettingsVM {
        every { navigationPreferences.getAutoTrailerEnabled() } answers { storedAutoTrailer }
        every { navigationPreferences.setAutoTrailerEnabled(any()) } answers {
            storedAutoTrailer = firstArg()
        }
        every { navigationPreferences.contentPreferences } returns MutableStateFlow(
            ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true)
        )
        every { navigationPreferences.getNavigationMode() } returns NavigationMode.SideDrawer
        every { navigationPreferences.getStartupTab() } returns TabType.Home
        every { navigationPreferences.getVisibleTabs(any()) } returns
            listOf(TabType.Home, TabType.Settings)
        every { navigationPreferences.getStartupTabOptions(any()) } returns listOf(TabType.Home)

        val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.success(mockk<DeviceResponse>(relaxed = true)))
        val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )

        val vm = DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true),
            deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true),
            preferencesStore = DefaultDeviceSettingsPreferencesStore(
                playerPreferences = mockk<PlayerPreferencesRepository>(relaxed = true),
                navigationPreferences = navigationPreferences,
                appLanguagePreferences = mockk<AppLanguageRepository>(relaxed = true),
                appUpdatePreferences = mockk<IAppUpdateInteractor>(relaxed = true),
            ),
            apiDomainInteractor = apiDomainInteractor,
            watchStateRepository = mockk<WatchStateRepository>(relaxed = true),
            watchStateSyncInteractor = mockk<WatchStateSyncInteractor>(relaxed = true),
            updateCheckCoordinator = AppUpdateCheckCoordinator(),
            errorHandler = mockk<ErrorHandler>(relaxed = true),
            resources = FakeResourceProvider(),
            router = mockk<AppRouter>(relaxed = true),
        )
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        return vm
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVMAutoTrailerTest"`
Expected: FAIL to compile — "unresolved reference: ToggleAutoTrailer" and "unresolved reference: autoTrailerEnabled".

- [ ] **Step 3: Add the action and the state field**

In `DeviceSettingsActions.kt`, after `ToggleWatchedIndicators`:

```kotlin
    data object ToggleAutoTrailer : DeviceSettingsActions
```

In `DeviceSettingsViewState.kt`, inside `DeviceSettingsState.Success`, after `watchedIndicatorsEnabled`:

```kotlin
        val autoTrailerEnabled: Boolean = true,
```

- [ ] **Step 4: Extend the preferences store**

In `DeviceSettingsPreferencesStore.kt`, add to the interface after `setShowWatchedIndicators`:

```kotlin
    fun setAutoTrailer(enabled: Boolean)
```

Add to `DeviceSettingsPreferencesSnapshot` after `watchedIndicatorsEnabled`:

```kotlin
    val autoTrailerEnabled: Boolean,
```

In `DefaultDeviceSettingsPreferencesStore.read()`, add to the returned snapshot after `watchedIndicatorsEnabled = content.showWatchedIndicators,`:

```kotlin
            autoTrailerEnabled = navigationPreferences.getAutoTrailerEnabled(),
```

And the override, after `setShowWatchedIndicators`:

```kotlin
    override fun setAutoTrailer(enabled: Boolean) {
        navigationPreferences.setAutoTrailerEnabled(enabled)
    }
```

- [ ] **Step 5: Handle the action in the ViewModel**

In `DeviceSettingsVM.kt`, add to the `when` in `onAction`, after the `ToggleWatchedIndicators` branch:

```kotlin
            DeviceSettingsActions.ToggleAutoTrailer -> toggleAutoTrailer()
```

Add to the `DeviceSettingsState.Success(...)` construction in `loadDeviceSettings`, after `watchedIndicatorsEnabled = preferences.watchedIndicatorsEnabled,`:

```kotlin
                                autoTrailerEnabled = preferences.autoTrailerEnabled,
```

Add the toggle beside `toggleWatchedIndicators`:

```kotlin
    private fun toggleAutoTrailer() = updatePreference(
        value = { !autoTrailerEnabled },
        persist = preferencesStore::setAutoTrailer,
        update = { copy(autoTrailerEnabled = it) },
    )
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVMAutoTrailerTest"`
Expected: PASS

- [ ] **Step 7: Add the strings**

In `app/src/main/res/values/main_screen_strings.xml`, after the `settings_hide_watched_description` entry:

```xml
    <string name="settings_auto_trailer">Трейлер при наведении</string>
    <string name="settings_auto_trailer_description">Если задержаться на карточке, вместо кадра начнёт играть трейлер со звуком</string>
```

In `app/src/main/res/values-en/strings.xml`, next to `settings_hide_watched_description`:

```xml
    <string name="settings_auto_trailer">Trailer on focus</string>
    <string name="settings_auto_trailer_description">Rest on a card and the still gives way to the trailer, with sound</string>
```

- [ ] **Step 8: Draw the switch**

In `DeviceSettingsContent.kt`, inside `private fun LazyListScope.contentItems(...)`, after the `hide-watched` item:

```kotlin
    item(key = "auto-trailer") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_auto_trailer),
            description = stringResource(R.string.settings_auto_trailer_description),
            checked = state.autoTrailerEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleAutoTrailer) },
        )
    }
```

- [ ] **Step 9: Compile and run the full check**

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/device/settings/ \
        app/src/main/res/values/main_screen_strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMAutoTrailerTest.kt
git commit -m "Add the auto-trailer switch to device settings"
```

---

### Task 4: Two-second countdown in `ContentListVM`

The heart of the feature. The countdown runs *alongside* the details request rather than after it — sequencing them would cost two seconds plus network time on a cold cache.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/ContentListViewState.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/ContentListAction.kt`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt:81-110` and the private companion at `:193`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/ContentListVMTest.kt`

**Interfaces:**
- Consumes: `NavigationPreferencesRepository.getAutoTrailerEnabled()` from Task 2.
- Produces:
  - `ContentListViewState.previewTrailerUrl: String?` — non-null only while a preview should be on screen
  - `ContentListAction.TrailerPreviewFinished` (data object) — the UI's report that playback stopped for any reason

- [ ] **Step 1: Write the failing tests**

First extend the existing `item(...)` helper at the bottom of `ContentListVMTest.kt` so it can carry a trailer. Replace it with:

```kotlin
    private fun item(
        id: Int,
        ratingPercentage: Int? = null,
        trailer: Trailer? = null,
    ) = Item(
        id = id,
        title = "Item $id",
        type = ItemType.MOVIE,
        ratingPercentage = ratingPercentage,
        trailer = trailer,
    )
```

Add the imports `com.kino.puber.data.api.models.Trailer` and
`org.junit.jupiter.api.Assertions.assertNull` (the null assertions below need it; a bare
`assertEquals(null, ...)` does not resolve cleanly against the JUnit 5 overloads).

Add a VM factory that controls the preference, beside the existing `createVM()` overloads:

```kotlin
    private fun createVM(autoTrailerEnabled: Boolean) = ContentListVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        genreInteractor = mockk(relaxed = true),
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
            every { getNavigationMode() } returns NavigationMode.SideDrawer
            every { getAutoTrailerEnabled() } returns autoTrailerEnabled
        },
        contentListRefreshCoordinator = refreshCoordinator,
    )
```

Then the tests:

```kotlin
    @Test
    fun focusHeldForTwoSeconds_publishesTheTrailerUrl() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun focusMovedBeforeTwoSeconds_neverStartsATrailer() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        coEvery { interactor.getItemDetails(43) } returns item(43)
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1500)
        vm.onAction(CommonAction.ItemFocused(videoItem(43)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun autoTrailerDisabled_leavesTheStillInPlace() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = false)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun itemWithoutATrailer_leavesTheStillInPlace() {
        coEvery { interactor.getItemDetails(42) } returns item(42)
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerWithoutAUrl_fallsBackToTheFile() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = null, file = "https://cdn/trailer.file"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/trailer.file", vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerPreviewFinished_clearsTheUrlAndDoesNotReplay() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        vm.onAction(ContentListAction.TrailerPreviewFinished)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun openingAnItem_stopsTheTrailerPreview() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        every { screens.details(42) } returns mockk<PuberScreen>()
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        vm.onAction(CommonAction.ItemSelected(videoItem(42)))

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun withoutTheDetailPanel_noTrailerIsEverPublished() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = ContentListVM(
            router = router,
            interactor = interactor,
            mapper = mapper,
            genreInteractor = mockk(relaxed = true),
            navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
                every { getNavigationMode() } returns NavigationMode.TopTabs
                every { getAutoTrailerEnabled() } returns true
            },
            contentListRefreshCoordinator = refreshCoordinator,
        )
        vm.testOnStart()

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }
```

Note: `createVM()` does not call `testOnStart()` in the existing tests — `showDetailPanel` defaults to `true` in `ContentListViewState`, so the panel-on tests work without it. The last test starts the VM explicitly, because it needs `onStart` to read `TopTabs` and flip `showDetailPanel` to false.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.contentlist.vm.ContentListVMTest"`
Expected: FAIL to compile — "unresolved reference: previewTrailerUrl" and "unresolved reference: TrailerPreviewFinished".

- [ ] **Step 3: Add the state field and the action**

In `ContentListViewState.kt`, after `showGenreChips`:

```kotlin
    /**
     * The trailer to play over the still, or null for the still itself. Set only once focus has
     * rested on a card long enough, and cleared the moment focus moves or playback stops.
     */
    val previewTrailerUrl: String? = null,
```

In `ContentListAction.kt`:

```kotlin
    data object TrailerPreviewFinished : ContentListAction
```

- [ ] **Step 4: Implement the countdown**

In `ContentListVM.kt`, replace `onItemFocused`, `onItemSelected`, and `onItemPlayed`:

```kotlin
    private fun onItemFocused(item: VideoItemUIState) {
        if (!stateValue.showDetailPanel) return
        focusedItemJob?.cancel()
        updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
        focusedItemJob = launch {
            // Counts from the moment focus landed, in parallel with the request: waiting for the
            // details first would push the trailer out by however long the network took.
            val trailerGate = async { delay(TRAILER_PREVIEW_DELAY_MS) }
            delay(FOCUS_DETAILS_DEBOUNCE_MS)
            updateViewState<ContentListViewState> { copy(selectedItem = VideoDetailsUIState.Loading) }
            val details = interactor.getItemDetails(item.id)
            updateViewState<ContentListViewState> { copy(selectedItem = mapper.mapDetailedItem(details)) }

            val trailerUrl = details.trailer?.url ?: details.trailer?.file
            if (trailerUrl == null || !navPrefs.getAutoTrailerEnabled()) {
                trailerGate.cancel()
                return@launch
            }
            trailerGate.await()
            updateViewState<ContentListViewState> { copy(previewTrailerUrl = trailerUrl) }
        }
    }

    private fun onItemSelected(item: VideoItemUIState) {
        stopTrailerPreview()
        openDetails(item.id)
    }

    private fun onItemPlayed(item: VideoItemUIState) {
        stopTrailerPreview()
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    /**
     * The ViewModel outlives a trip to the details screen or the player. Without this the trailer
     * would be playing the instant the user came back, with none of the pause that starts it.
     */
    private fun stopTrailerPreview() {
        focusedItemJob?.cancel()
        updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
    }
```

Add the new branch to `onAction`, after `is ContentListAction.HeroSelected`:

```kotlin
            is ContentListAction.TrailerPreviewFinished ->
                updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
```

Add to the private companion:

```kotlin
        const val TRAILER_PREVIEW_DELAY_MS = 2000L
```

Add the imports `kotlinx.coroutines.async` (`kotlinx.coroutines.delay` is already there).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.contentlist.vm.ContentListVMTest"`
Expected: PASS, including the pre-existing `returnedChangesForFocusedItem_reloadSelectedDetailsFromTheSharedRepository`. That test focuses an item, advances 151ms, then plays it; `stopTrailerPreview` cancels a job whose details request has already completed, so the reload it asserts still happens.

- [ ] **Step 6: Run the full check**

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/contentlist/ \
        app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/ContentListVMTest.kt
git commit -m "Publish a trailer preview url after focus rests on a card"
```

---

### Task 5: `TrailerPreviewPlayer`

A player surface sized to the poster. `TrailerOverlay` is deliberately left alone: it is full-screen, button-triggered, and owns its own Back handling.

There is no unit test here. ExoPlayer cannot be instantiated in the Compose test renderer — the same limitation already recorded at `DetailsScreenPreview.kt:253`. The component is verified by the manual smoke run in Task 6.

**Files:**
- Create: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `@Composable internal fun TrailerPreviewPlayer(url: String, onFinished: () -> Unit, modifier: Modifier = Modifier)` — annotated `@UnstableApi`.

- [ ] **Step 1: Write the component**

```kotlin
package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The trailer that replaces the still in a detail panel once focus has rested on a card.
 *
 * Every way playback can stop — the end of the trailer, a player error, the app going to the
 * background — reports through [onFinished] rather than being handled here, so the panel and the
 * state that drives it never disagree about what is on screen.
 */
@UnstableApi
@Composable
internal fun TrailerPreviewPlayer(
    url: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnFinished()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnFinished()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    DisposableEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url.toUri()))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Coming back from the background into a running trailer is not what the user left.
    // `LifecycleAction` is not used here: it dispatches a `UIAction`, and `CommonAction` has no
    // no-op member to dispatch. The shape below is the one `AppForegroundReporter.kt:25-27` uses.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                currentOnFinished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        },
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL. The component is not referenced yet; that is fine.

- [ ] **Step 3: Run detekt**

Run: `./gradlew :app:detektAll`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt
git commit -m "Add the trailer preview player surface"
```

---

### Task 6: Draw the trailer over the still

Connects Tasks 4 and 5. `VideoItemGridDetails` has four other callers (`FavoriteScreenContent.kt:73`, `DetailsScreenContent.kt:311` and `:888`, plus its own previews); the new parameters carry defaults so none of them change.

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:43-78` and `:161-195`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:120-128`

**Interfaces:**
- Consumes: `ContentListViewState.previewTrailerUrl` and `ContentListAction.TrailerPreviewFinished` (Task 4); `TrailerPreviewPlayer(url, onFinished, modifier)` (Task 5).
- Produces: `VideoItemGridDetails(modifier, state, descriptionMaxLines, trailerUrl, onTrailerFinished)`.

- [ ] **Step 1: Widen `VideoItemGridDetails`**

Replace the function at `:43-78`. The two branches of the existing `if (state.isLoading)` are identical, so collapse them while passing the new arguments through:

```kotlin
@Composable
fun VideoItemGridDetails(
    modifier: Modifier,
    state: VideoDetailsUIState,
    descriptionMaxLines: Int = Int.MAX_VALUE,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
) {
    Row(modifier = modifier) {
        VideoDetailsDescription(
            modifier = Modifier.weight(3F),
            state = state,
            descriptionMaxLines = descriptionMaxLines,
        )
        VideoDetailsPoster(
            modifier = Modifier
                .fillMaxHeight()
                .weight(5F),
            imageUrl = state.imageUrl,
            imageFallbackUrls = state.imageFallbackUrls,
            trailerUrl = trailerUrl,
            onTrailerFinished = onTrailerFinished,
        )
    }
}
```

- [ ] **Step 2: Draw the player over the poster**

In `VideoDetailsPoster`, add the two parameters and the overlay. The signature becomes:

```kotlin
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoDetailsPoster(
    modifier: Modifier,
    imageUrl: String,
    imageFallbackUrls: List<String>,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
) {
```

Immediately after the existing `AsyncImage(...)` call and **before** the `gradientWidth` block — so the gradients keep covering the trailer exactly as they cover the still — insert:

```kotlin
        AnimatedVisibility(
            visible = trailerUrl != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val playingUrl = remember(trailerUrl) { trailerUrl }
            if (playingUrl != null) {
                TrailerPreviewPlayer(
                    url = playingUrl,
                    onFinished = onTrailerFinished,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                )
            }
        }
```

Add the imports:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
```

- [ ] **Step 3: Pass the state in from the screen**

In `ContentListScreenContent.kt`, replace the `VideoItemGridDetails(...)` call inside `ContentListLayout`:

```kotlin
        if (state.showDetailPanel) {
            VideoItemGridDetails(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PuberTheme.Defaults.DetailsWeight),
                state = state.selectedItem,
                trailerUrl = state.previewTrailerUrl,
                onTrailerFinished = { onAction(ContentListAction.TrailerPreviewFinished) },
            )
        }
```

`ContentListAction` is already imported in this file.

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL. If the `@androidx.annotation.OptIn` on `VideoDetailsPoster` is not enough, propagate `@UnstableApi` up to `VideoItemGridDetails` the way `TrailerOverlay.kt:21` does it, and re-run.

- [ ] **Step 5: Run the full check**

Run: `./gradlew testDevDebugUnitTest :app:detektAll`
Expected: PASS

- [ ] **Step 6: Smoke on a real device**

Follow `.kent/commands/smoke-test.md` and `.kent/context/smoke.md`: acquire a TV emulator or device lease, build and install the dev APK, and confirm by hand:

1. A fresh install opens with the side rail, not top tabs.
2. Focus a catalogue card with a trailer and hold it — after roughly two seconds the still is replaced by the trailer, with sound.
3. Scroll quickly along a row — no trailer starts.
4. Let a trailer play to the end — the still comes back, and it does not restart.
5. Move focus to another card mid-trailer — playback stops at once.
6. Press OK to open details and come back — no trailer is playing on return.
7. Turn the switch off in Settings → the still never gives way.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt \
        app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt
git commit -m "Play the focused card's trailer over its still"
```

---

## Deviation from the spec

The spec's test section asks for a Compose/instrumented test covering the no-trailer branch
(`trailerUrl == null` shows the still). No such test is in this plan. `app/src/androidTest`
holds focus-traversal tests only, there is none for `VideoItemGridDetails`, and the assertion
would restate the component's unchanged default — every existing render already exercises it.
The trailer branch itself cannot be tested there at all (`DetailsScreenPreview.kt:253`). If a
reviewer wants the coverage anyway, it is a standalone task after Task 6, not a blocker for it.

## Out of scope

Auto-trailer in `HeroCarousel`, in Favourites, or on any other screen; a muted/unmuted choice in settings; prefetching trailers for neighbouring cards.
