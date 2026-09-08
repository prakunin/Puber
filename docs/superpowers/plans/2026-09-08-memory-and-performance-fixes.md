# План исправлений: память и производительность

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Убрать покадровые рекомпозиции на Home и в плеере, ограничить рост памяти (кэш контента, загрузка APK, once-listener'ы роутера) и снять лишнюю работу с главного потока при сборке Home.

**Architecture:** Семь независимых точечных правок в существующих файлах, без новых модулей. Анимации переводятся на чтение `State` в draw-фазе; кэш получает лимит по весу; загрузка APK переводится на `prepareGet().execute {}`; once-listener'ы роутера получают владельца-экран и снимаются при его уходе со стека; маппинг Home уезжает на инжектируемый диспетчер.

**Tech Stack:** Kotlin, Jetpack Compose (tv-material3), Media3, Ktor 3.5.2 (OkHttp), Koin, Voyager, kotlinx.coroutines, JUnit 5 + MockK + coroutines-test.

**Spec:** `docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md`

## Global Constraints

- Коммиты: одна повелительная английская фраза без префиксов, как в `git log` проекта. **Никогда не добавлять `Co-Authored-By`.**
- Юнит-тесты: `./gradlew :app:testDevDebugUnitTest --tests "<FQCN>"`. Сборка: `./gradlew :app:assembleDevDebug`.
- Проверки на телевизоре делаются до `connectedAndroidTest` (он стирает логин dev-сборки и бьёт по всем подключённым устройствам). Тестовое устройство KM6: `adb connect 192.168.1.121:5555`.
- Codex-ревью на этом аккаунте недоступно (401); после каждой задачи делать самопроверку diff и явно писать, что Codex-ревью пропущено.
- Каждая задача заканчивается зелёными тестами затронутых классов и отдельным коммитом.

---

### Task 1: Ken Burns в HeroCarousel читается в graphicsLayer, а не в композиции

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/component/HeroCarousel.kt:214-247, 280-294`

**Interfaces:**
- Consumes: ничего из других задач.
- Produces: `private class KenBurnsTransform(val scale: State<Float>, val translateX: State<Float>)`; `kenBurns(animate: Boolean, driftDirection: Float): KenBurnsTransform`.

Compose UI без Robolectric в проекте не тестируется юнит-тестами; проверка здесь — сборка и замер кадров на телевизоре.

- [ ] **Step 1: Снять базовую метрику на телевизоре**

```bash
adb connect 192.168.1.121:5555
adb -s 192.168.1.121:5555 shell dumpsys gfxinfo com.kino.puber.stage reset >/dev/null
# открыть Home, ничего не нажимать 10 секунд
sleep 10
adb -s 192.168.1.121:5555 shell dumpsys gfxinfo com.kino.puber.stage | grep "Total frames rendered"
```
Ожидание до правки: порядка 600 кадров за 10 с (рекомпозиция каждый кадр). Записать число.

- [ ] **Step 2: Переписать `KenBurnsTransform` и `kenBurns`**

Заменить блок `HeroCarousel.kt:214-247` на:

```kotlin
/** Значения читаются только в draw-фазе (`graphicsLayer`), чтобы анимация не рекомпозировала `HeroItem`. */
private class KenBurnsTransform(val scale: State<Float>, val translateX: State<Float>) {
    companion object {
        val Still = KenBurnsTransform(ConstantFloatState(1f), ConstantFloatState(0f))
    }
}

private class ConstantFloatState(override val value: Float) : State<Float>

/**
 * The pager keeps neighbouring pages composed, and an infinite transition runs for as long as it is
 * composed — so without this every hero page drifts at once, forever, and the ones off screen pay
 * the same per-frame invalidation as the visible one for nothing. On the weaker TV boxes that is
 * continuous GPU work behind a still picture.
 *
 * The animated values are handed back as [State] and read inside `graphicsLayer`, so a frame of the
 * drift invalidates only the layer, never the composition of the hero card.
 */
@Composable
private fun kenBurns(animate: Boolean, driftDirection: Float): KenBurnsTransform {
    if (!animate) return KenBurnsTransform.Still

    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val scale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsScale",
    )
    val translateX = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = KEN_BURNS_DRIFT_PX * driftDirection,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsTranslateX",
    )
    return remember(scale, translateX) { KenBurnsTransform(scale, translateX) }
}
```

Добавить импорт `androidx.compose.runtime.State`.

- [ ] **Step 3: Читать State внутри `graphicsLayer` в `HeroItem`**

В `HeroItem` (строки 280-294) заменить

```kotlin
val (scale, translateX) = kenBurns(animate = animate, driftDirection = driftDirection)
```
на
```kotlin
val transform = kenBurns(animate = animate, driftDirection = driftDirection)
```
и модификатор картинки на
```kotlin
modifier = Modifier
    .fillMaxSize()
    .graphicsLayer {
        val scale = transform.scale.value
        scaleX = scale
        scaleY = scale
        translationX = transform.translateX.value
    },
```

- [ ] **Step 4: Собрать и установить**

```bash
./gradlew :app:assembleDevDebug && adb -s 192.168.1.121:5555 install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```
Ожидание: BUILD SUCCESSFUL, установка без ошибок.

- [ ] **Step 5: Повторить замер из Step 1**

Ожидание: за 10 с простоя на Home число кадров падает до десятков (только автопрокрутка пейджера раз в 5 с, по ~30 кадров на анимацию 500 мс). Визуально дрейф картинки в hero продолжается.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/uikit/component/HeroCarousel.kt
git commit -m "Read the hero Ken Burns drift in the graphics layer instead of the composition"
```

---

### Task 2: Shimmer прогресс-бара плеера живёт только во время буферизации

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:62-71, 84, 110-121, 129-140, 168-200, 215-250`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreen.kt:74`

**Interfaces:**
- Produces: `private fun bufferingShimmer(isBuffering: Boolean): State<Float>`; параметр `shimmerProgress: State<Float>` у `ProgressBarRow`, `SeekTrack`, `BufferedTrack`.

- [ ] **Step 1: Снять базовую метрику на телевизоре**

Запустить любой фильм, нажать паузу так, чтобы контролы были на экране, затем:
```bash
adb -s 192.168.1.121:5555 shell dumpsys gfxinfo com.kino.puber.stage reset >/dev/null
sleep 10
adb -s 192.168.1.121:5555 shell dumpsys gfxinfo com.kino.puber.stage | grep "Total frames rendered"
```
Ожидание до правки: порядка 600 кадров. Записать.

- [ ] **Step 2: Заменить безусловную transition на `bufferingShimmer`**

В `PlayerProgressBar` (строки 62-71) заменить `rememberInfiniteTransition` + `animateFloat` на:

```kotlin
val shimmerProgress = bufferingShimmer(isBuffering)
```

Ниже в файле добавить:

```kotlin
/**
 * The shimmer exists only while buffering. An infinite transition drives a frame loop for as long
 * as it is composed, so creating it unconditionally kept the whole progress bar recomposing at
 * 60 fps whenever the controls were up. The value comes back as [State] and is read in draw only.
 */
@Composable
private fun bufferingShimmer(isBuffering: Boolean): State<Float> {
    if (!isBuffering) return NoShimmer
    val infiniteTransition = rememberInfiniteTransition(label = "buffering_shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
}

private val NoShimmer: State<Float> = object : State<Float> {
    override val value: Float = 0f
}
```

Добавить импорт `androidx.compose.runtime.State`; убрать `getValue`, если он больше нигде в файле не используется.

- [ ] **Step 3: Протянуть `State<Float>` до `drawBehind`**

Изменить тип параметра `shimmerProgress: Float` на `shimmerProgress: State<Float>` в `ProgressBarRow`, `SeekTrack` и `BufferedTrack`. В `BufferedTrack` внутри `drawBehind` заменить

```kotlin
val x = shimmerProgress * (size.width + bandWidth) - bandWidth
```
на
```kotlin
val x = shimmerProgress.value * (size.width + bandWidth) - bandWidth
```

- [ ] **Step 4: Запомнить ссылку на `getExoPlayer` в `PlayerScreen`**

`PlayerScreen.kt:74`:
```kotlin
val exoPlayer = remember(vm) { vm::getExoPlayer }
```
и передать `exoPlayer = exoPlayer` в `PlayerScreenContent`. Объявление разместить рядом с `onAction` (строка 63).

- [ ] **Step 5: Собрать, установить, прогнать тесты плеера**

```bash
./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.player.*"
adb -s 192.168.1.121:5555 install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```
Ожидание: BUILD SUCCESSFUL, тесты зелёные.

- [ ] **Step 6: Повторить замер из Step 1**

Ожидание: на паузе с показанными контролами за 10 с рендерится 0–5 кадров. Во время буферизации (перемотать далеко вперёд) полоска по-прежнему мерцает.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreen.kt
git commit -m "Run the seek bar shimmer only while buffering and read it in draw"
```

---

### Task 3: Плеер ставится на паузу в момент выхода, а не после окончания записей

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1806-1822`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt`

**Interfaces:**
- Consumes: `PlaybackControl.pause()` (уже есть, `PlaybackController.kt:132`).

- [ ] **Step 1: Написать падающий тест**

В `PlayerVMPlaybackFlowTest`, регион «Back navigation», после `backPressed_waitsForFinalProgressSave`:

```kotlin
@Test
fun backPressed_pausesPlaybackBeforeTheFinalSaveDrains() {
    // The decoder and the audio track must not keep running for the length of a slow request
    // after the viewer has already left.
    val releaseSave = CompletableDeferred<Unit>()
    coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
        releaseSave.await()
    }
    val vm = startedVM()

    vm.onAction(PlayerAction.OnBackPressed)

    verify(exactly = 1) { playbackController.pause() }
    verify(exactly = 0) { router.back(any(), any()) }
    releaseSave.complete(Unit)
}
```

- [ ] **Step 2: Убедиться, что тест падает**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.player.vm.PlayerVMPlaybackFlowTest"
```
Ожидание: FAIL на `verify(exactly = 1) { playbackController.pause() }` (0 вызовов). Если тест проходит сразу, значит `pause()` уже вызывается на пути выхода; тогда найти этот вызов (`grep -n "pause()" PlayerVM.kt`) и заменить `exactly = 1` на `atLeast = 1`, зафиксировав это в сообщении коммита.

- [ ] **Step 3: Вызвать `pause()` в начале `exitPlayer`**

`PlayerVM.kt`, функция `exitPlayer()`, сразу после `closing = true`:

```kotlin
private fun exitPlayer() {
    if (closeJob != null) return
    closing = true
    // Stop the decoder now. The writes below can take a request timeout on a slow network, and
    // nothing the viewer sees justifies keeping video and audio running for that long.
    playbackController.pause()
    mediaGeneration += 1
    ...
```

- [ ] **Step 4: Прогнать все тесты плеера**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.player.*"
```
Ожидание: PASS. Если какой-то тест ждёт `isPlaying == true` после back, он описывает старое поведение: обновить ожидание и указать это в коммите.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt
git commit -m "Pause playback as soon as the player starts closing"
```

---

### Task 4: Once-listener'ы роутера знают экран-владельца и снимаются, когда он уходит со стека

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/navigation/AppRouter.kt:26, 48-50, 114-121`
- Modify: `app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:155-160 (FlowNavigator), 204-209 (FlowCommandRunner), 565-572 (TabFlowNavigator), 731-734 (onTabNavigateForResult)`
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/navigation/AppRouterTest.kt`
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentResultTest.kt`

**Interfaces:**
- Produces:
  - `AppRouter.setOnceResultListener(resultCode: Int, owner: PuberScreen? = null, listener: OnResult<T>)` — `owner` вставлен ПЕРЕД `listener`, чтобы существующие вызовы с trailing-lambda не менялись.
  - `AppRouter.dropResultListeners(owner: PuberScreen)`.
  - `internal fun removedScreens(previous: List<Screen>, current: List<Screen>): List<Screen>` в `FlowComponent.kt` (сравнение по идентичности).
  - `@Composable internal fun DropResultListenersOnPop(navigator: Navigator, router: AppRouter)` в `FlowComponent.kt`.

Ключи Voyager-экранов по умолчанию строятся из имени класса, поэтому два `DetailsScreen` на стеке неразличимы по `key`. Владелец хранится как ссылка на экран и сравнивается через `===`.

- [ ] **Step 1: Падающие тесты для `AppRouter`**

В `AppRouterTest` добавить:

```kotlin
@Test
fun droppingAnOwnersListeners_leavesOtherOwnersInPlace() = runTest {
    val router = createRouter()
    val outerScreen = mockk<PuberScreen>()
    val innerScreen = mockk<PuberScreen>()
    val outerResults = mutableListOf<String?>()
    val innerResults = mutableListOf<String?>()
    router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED, owner = outerScreen) { outerResults += it }
    router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED, owner = innerScreen) { innerResults += it }

    router.dropResultListeners(innerScreen)
    router.back(RESULT_CONTENT_CHANGED, "outer")

    assertEquals(emptyList<String?>(), innerResults)
    assertEquals(listOf("outer"), outerResults)
}

@Test
fun droppingAnUnknownOwner_changesNothing() = runTest {
    val router = createRouter()
    val results = mutableListOf<String?>()
    router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED, owner = mockk<PuberScreen>()) { results += it }

    router.dropResultListeners(mockk<PuberScreen>())
    router.back(RESULT_CONTENT_CHANGED, "kept")

    assertEquals(listOf("kept"), results)
}
```

- [ ] **Step 2: Падающий тест для `removedScreens`**

В `FlowComponentResultTest` добавить (импорт `cafe.adriel.voyager.core.screen.Screen`):

```kotlin
@Test
fun removedScreens_reportsScreensThatLeftTheStack_byIdentity() {
    val first = mockk<PuberScreen>()
    val second = mockk<PuberScreen>()

    assertEquals(listOf<Screen>(second), removedScreens(previous = listOf(first, second), current = listOf(first)))
    assertEquals(emptyList<Screen>(), removedScreens(previous = listOf(first), current = listOf(first, second)))
    assertEquals(listOf<Screen>(first), removedScreens(previous = listOf(first), current = emptyList()))
}
```

- [ ] **Step 3: Убедиться, что тесты не компилируются / падают**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.core.ui.navigation.AppRouterTest" --tests "com.kino.puber.core.ui.navigation.component.FlowComponentResultTest"
```
Ожидание: ошибка компиляции (`owner`, `dropResultListeners`, `removedScreens` не определены).

- [ ] **Step 4: Реализовать владельца в `AppRouter`**

```kotlin
private class ResultListener(val owner: PuberScreen?, val listener: OnResult<Any>)

private val onceListeners = HashMap<Int, ArrayDeque<ResultListener>>()

/**
 * @param owner the screen that will deliver the result. When it leaves the stack without one —
 * a Voyager pop that never reached [back] — [dropResultListeners] takes the listener with it,
 * so the router does not keep the caller's view model alive, and a later result for the same
 * code cannot land on a listener that belongs to a screen already gone.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> setOnceResultListener(resultCode: Int, owner: PuberScreen? = null, listener: OnResult<T>) {
    onceListeners.getOrPut(resultCode) { ArrayDeque() }
        .addLast(ResultListener(owner, listener as OnResult<Any>))
}

fun dropResultListeners(owner: PuberScreen) {
    val codes = onceListeners.entries.iterator()
    while (codes.hasNext()) {
        val entry = codes.next()
        entry.value.removeIf { it.owner === owner }
        if (entry.value.isEmpty()) codes.remove()
    }
}

private fun dispatchResult(resultCode: Int, result: Any?) {
    val resultListeners = onceListeners[resultCode] ?: return
    val resultListener = resultListeners.pollLast() ?: return
    resultListener.listener.invoke(result)
    if (resultListeners.isEmpty()) {
        onceListeners.remove(resultCode)
    }
}
```

- [ ] **Step 5: Передавать владельца при регистрации**

`FlowComponent.kt:207` (FlowCommandRunner):
```kotlin
router.setOnceResultListener(event.requestCode, owner = event.screen, listener = event.listener)
```
`FlowComponent.kt:732` (onTabNavigateForResult, ветка `Tab`):
```kotlin
router.setOnceResultListener(event.requestCode, owner = event.screen, listener = event.listener)
```
Ветка `Root` идёт через `rootRouter.navigateForResult`, и владелец проставится в корневом `FlowCommandRunner`.

- [ ] **Step 6: Снимать listener'ы при уходе экрана со стека**

В `FlowComponent.kt` добавить:

```kotlin
/**
 * Voyager pops a screen itself when no back dispatcher claimed the press, and that path never
 * reaches [AppRouter.back]. Whatever result listener that screen was meant to serve would stay in
 * the router for the life of the flow, holding the caller's view model.
 */
@Composable
internal fun DropResultListenersOnPop(navigator: Navigator, router: AppRouter) {
    LaunchedEffect(navigator, router) {
        var previous: List<Screen> = navigator.items.toList()
        snapshotFlow { navigator.items.toList() }.collect { current ->
            removedScreens(previous, current).forEach { screen ->
                if (screen is PuberScreen) router.dropResultListeners(screen)
            }
            previous = current
        }
    }
}

internal fun removedScreens(previous: List<Screen>, current: List<Screen>): List<Screen> =
    previous.filter { screen -> current.none { it === screen } }
```

Импорты: `androidx.compose.runtime.snapshotFlow`, `cafe.adriel.voyager.core.screen.Screen`.

Вызвать рядом с командными раннерами:
- в `FlowNavigator` после `FlowCommandRunner(...)` (строка ~160): `DropResultListenersOnPop(navigator = navigator, router = router)`;
- в `TabFlowNavigator` после `TabFlowCommandRunner(...)` (строка ~572): `DropResultListenersOnPop(navigator = navigator, router = tabRouter)`.

Порядок безопасен: `back(resultCode, result)` вызывает `dispatchResult` синхронно до того, как `Command.Back` дойдёт до навигатора, поэтому доставленный результат никогда не «сбрасывается».

- [ ] **Step 7: Прогнать тесты навигации**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.core.ui.navigation.*"
```
Ожидание: PASS, включая старые `resultListenersWithSameCode_*` и `newRootScreen_discardsResultListenersFromReplacedStack`.

- [ ] **Step 8: Проверка на телевизоре**

Установить debug-сборку. Сценарий: Home → карточка → Details → похожий фильм (Details поверх Details) → Back → Back → поставить лайк на карточке Home. Ожидание: сердечко на Home обновляется (результат дошёл до нужного listener'а), приложение не падает.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/ui/navigation/AppRouter.kt app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt app/src/test/kotlin/com/kino/puber/core/ui/navigation/AppRouterTest.kt app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/FlowComponentResultTest.kt
git commit -m "Drop a screen's pending result listener when it leaves the stack without one"
```

---

### Task 5: Маппинг секций Home уходит с главного потока

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:41-52, 321-330, 426-457`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreen.kt:29-34`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt`

**Interfaces:**
- Produces: конструктор `HomeVM(..., errorHandler: ErrorHandler, presentationDispatcher: CoroutineDispatcher)`; `publishSections()` и `markHeroSettled()` становятся `suspend`.

Koin-обёртка `viewModelOf(::HomeVM)` пытается резолвить каждый параметр через `get()`, поэтому диспетчер передаётся явным `viewModel { }`.

- [ ] **Step 1: Обновить оба `createVM` в тестах, чтобы они компилировались с новым параметром**

В `HomeVMTest.kt:391` и `HomeVMIncrementalPublishTest.kt:461`:

```kotlin
private fun createVM(
    presentationDispatcher: CoroutineDispatcher = mainDispatcher.dispatcher,
) = HomeVM(
    router = router,
    interactor = interactor,
    mapper = mapper,
    videoItemMapper = videoItemMapper,
    apiDomainInteractor = apiDomainInteractor,
    savedItemInteractor = savedItemInteractor,
    cardDisplayChanges = cardDisplayChanges,
    resources = FakeResourceProvider(),
    errorHandler = errorHandler,
    presentationDispatcher = presentationDispatcher,
)
```
Импорт `kotlinx.coroutines.CoroutineDispatcher`. Unconfined-диспетчер по умолчанию сохраняет синхронность всех существующих тестов.

- [ ] **Step 2: Падающий тест**

В `HomeVMTest` (импорты `kotlinx.coroutines.test.StandardTestDispatcher`, `kotlinx.coroutines.test.advanceUntilIdle`):

```kotlin
@Test
fun sectionsAreMappedOnThePresentationDispatcher() = runTest {
    // Sorting and mapping eight sections is main-thread work the rows do not need; the publish
    // has to hop to the injected dispatcher and only then land as content.
    val presentation = StandardTestDispatcher(testScheduler)
    val vm = createVM(presentationDispatcher = presentation)

    vm.testOnStart()
    assertTrue(vm.testStateValue is HomeViewState.Loading)

    advanceUntilIdle()
    assertTrue(vm.testStateValue is HomeViewState.Content)
}
```

- [ ] **Step 3: Убедиться, что не компилируется**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.home.vm.HomeVMTest"
```
Ожидание: ошибка компиляции — нет параметра `presentationDispatcher`.

- [ ] **Step 4: Добавить параметр и перевести `publishSections` на `withContext`**

Конструктор `HomeVM`:
```kotlin
    override val errorHandler: ErrorHandler,
    private val presentationDispatcher: CoroutineDispatcher,
) : PuberVM<HomeViewState>(router) {
```

`publishSections()` (строки 426-457) заменить на:

```kotlin
/** Maps what the sections returned into cards, against whatever the index and settings say now. */
private suspend fun publishSections() {
    // Snapshot the main-thread state before hopping: the maps below are written by collectors on
    // the main dispatcher and must not be read from another thread.
    val sections = loadedSections.toMap()
    val collections = loadedCollections
    val watchedAt = lastWatchedAt
    val mapped = withContext(presentationDispatcher) {
        MappedHome(
            heroItems = videoItemMapper.mapHeroItems(
                interactor.prepareHomeItems(
                    items = sections[HomeSectionType.Hot].orEmpty(),
                    lastWatchedAt = watchedAt,
                    sortByLastWatched = false,
                ).take(HERO_ITEMS_COUNT),
            ),
            sections = listOfNotNull(
                *sections
                    .map { (type, items) ->
                        mapper.mapItemSection(
                            interactor.prepareHomeItems(
                                items = items,
                                lastWatchedAt = watchedAt,
                                sortByLastWatched = type == HomeSectionType.WatchLater ||
                                    type == HomeSectionType.Bookmarks,
                            ),
                            type,
                        )
                    }
                    .toTypedArray(),
                collections?.let { mapper.mapCollectionSection(it) },
            ).sortedBy { it.type.ordinal },
        )
    }
    updateViewState(
        HomeViewState.Content(
            heroItems = mapped.heroItems,
            sections = applySavedOverrides(mapped.sections),
            heroSettled = heroSettled,
            apiDomainDialog = currentDialogState(),
        )
    )
}

private class MappedHome(
    val heroItems: List<HeroItemState>,
    val sections: List<HomeSectionState>,
)
```

`applySavedOverrides` остаётся на вызывающем (главном) диспетчере, потому что читает `savedOverrides`.

`markHeroSettled` (строка 321) сделать `private suspend fun`. Остальные вызывающие (`refreshHomePresentation`, `collectSection`, `collectCollections`) уже suspend.

Импорты: `kotlinx.coroutines.CoroutineDispatcher`, `kotlinx.coroutines.withContext`, `com.kino.puber.core.ui.uikit.component.HeroItemState` (в `HomeVM.kt` его ещё нет).

- [ ] **Step 5: Wiring в Koin**

`HomeScreen.kt`, `buildModule`:
```kotlin
viewModel {
    HomeVM(
        router = get(),
        interactor = get(),
        mapper = get(),
        videoItemMapper = get(),
        apiDomainInteractor = get(),
        savedItemInteractor = get(),
        cardDisplayChanges = get(),
        resources = get(),
        errorHandler = get(),
        presentationDispatcher = Dispatchers.Default,
    )
}
```
Импорты: заменить `org.koin.core.module.dsl.viewModelOf` на `org.koin.core.module.dsl.viewModel` (как в `ContentListScreen.kt:24`), добавить `kotlinx.coroutines.Dispatchers`.

- [ ] **Step 6: Прогнать тесты Home**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.ui.feature.home.*"
```
Ожидание: PASS, включая новый тест.

- [ ] **Step 7: Собрать и проверить Home на телевизоре**

```bash
./gradlew :app:assembleDevDebug && adb -s 192.168.1.121:5555 install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```
Ожидание: Home открывается, все ряды и hero на месте, лайк на карточке применяется сразу, без «отката» после обновления соседних рядов.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreen.kt app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt
git commit -m "Map the Home sections off the main thread"
```

---

### Task 6: Memory-tier кэша контента ограничен по байтам

**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/collections/TypedTtlCache.kt:33-40, 42-45, 63-70, 283-321`
- Modify: `app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:45-69`
- Modify: `app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:34-40`
- Test: `app/src/test/kotlin/com/kino/puber/core/collections/TypedTtlCacheTest.kt`

**Interfaces:**
- Produces:
  - `TypedTtlCacheImpl(defaultTtl, maximumSize, maximumConcurrentLoads, nowNanos, maximumWeight: Long = Long.MAX_VALUE, weigher: (V) -> Int = { 1 })`.
  - `CachedFeed(..., maximumSize, maximumMemoryWeight: Long = Long.MAX_VALUE, memoryWeigher: (V) -> Int = { 1 })`.
  - `ContentCacheRepository.MEMORY_TIER_BYTES = 16L * 1024 * 1024`.

- [ ] **Step 1: Падающие тесты**

В `TypedTtlCacheTest`:

```kotlin
@Test
fun put_evictsTheLeastRecentlyUsedEntriesOnceTheWeightLimitIsExceeded() {
    val cache = TypedTtlCacheImpl<String, String>(maximumWeight = 10, weigher = { it.length })
    cache.put("a", "aaaa")
    cache.put("b", "bbbb")
    // Touching "a" makes "b" the least recently used entry.
    assertEquals("aaaa", cache.get("a"))

    cache.put("c", "cccc")

    assertEquals(null, cache.get("b"))
    assertEquals("aaaa", cache.get("a"))
    assertEquals("cccc", cache.get("c"))
}

@Test
fun replacingAValue_countsOnlyTheNewWeight() {
    val cache = TypedTtlCacheImpl<String, String>(maximumWeight = 10, weigher = { it.length })
    cache.put("a", "aaaaaaaa")
    cache.put("a", "aa")

    cache.put("b", "bbbbbbbb")

    assertEquals("aa", cache.get("a"))
    assertEquals("bbbbbbbb", cache.get("b"))
}

@Test
fun removingAnEntry_releasesItsWeight() {
    val cache = TypedTtlCacheImpl<String, String>(maximumWeight = 10, weigher = { it.length })
    cache.put("a", "aaaaaaaa")
    cache.remove("a")

    cache.put("b", "bbbbbbbb")
    cache.put("c", "cc")

    assertEquals("bbbbbbbb", cache.get("b"))
    assertEquals("cc", cache.get("c"))
}
```

- [ ] **Step 2: Убедиться, что не компилируется**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.core.collections.TypedTtlCacheTest"
```
Ожидание: ошибка компиляции — нет параметров `maximumWeight`, `weigher`.

- [ ] **Step 3: Реализовать вес в `TypedTtlCacheImpl`**

Конструктор:
```kotlin
class TypedTtlCacheImpl<K : Any, V : Any>(
    private val defaultTtl: Duration = TypedTtlCache.DefaultTtl,
    private val maximumSize: Int = 256,
    maximumConcurrentLoads: Int = 16,
    private val nowNanos: () -> Long = System::nanoTime,
    private val maximumWeight: Long = Long.MAX_VALUE,
    private val weigher: (V) -> Int = { 1 },
) : TypedTtlCache<K, V> {
```

`Entry` получает вес:
```kotlin
private data class Entry<V>(
    val value: V,
    val expiresAtNanos: Long,
    val weight: Int,
)
```

Поле `private var totalWeight = 0L` рядом с `completed`. В `init` добавить `require(maximumWeight > 0) { "maximumWeight must be positive" }`.

Все удаления из `completed` идут через один helper, чтобы вес не расходился:

```kotlin
private fun removeCompletedUnsafe(key: K): Entry<V>? {
    val removed = completed.remove(key) ?: return null
    totalWeight -= removed.weight
    return removed
}
```

Заменить:
- в `remove(key)`: `completed.remove(key)` → `removeCompletedUnsafe(key)`;
- в `clear()`: `completed.clear()` → `completed.clear(); totalWeight = 0L`;
- в `getValidValueUnsafe`: `completed.remove(key)` → `removeCompletedUnsafe(key)`;
- в `reload`: `completed.remove(key)` → `removeCompletedUnsafe(key)`.

`putUnsafe`:
```kotlin
private fun putUnsafe(key: K, value: V, ttl: Duration) {
    removeCompletedUnsafe(key)
    val weight = weigher(value).coerceAtLeast(0)
    completed[key] = Entry(
        value = value,
        expiresAtNanos = expirationTime(ttl),
        weight = weight,
    )
    totalWeight += weight
    evictExpiredAndOverflowUnsafe()
}
```

`evictExpiredAndOverflowUnsafe`:
```kotlin
private fun evictExpiredAndOverflowUnsafe() {
    val iterator = completed.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next().value
        if (isExpired(entry)) {
            totalWeight -= entry.weight
            iterator.remove()
        }
    }
    while (completed.size > maximumSize || (totalWeight > maximumWeight && completed.size > 1)) {
        completed.entries.iterator().run {
            totalWeight -= next().value.weight
            remove()
        }
    }
}
```
Условие `completed.size > 1` оставляет единственную запись, даже если она тяжелее лимита: иначе значение, которое только что загрузили, исчезнет до того, как его прочитают.

- [ ] **Step 4: Прогнать тесты кэша**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.core.collections.TypedTtlCacheTest"
```
Ожидание: PASS, все старые тесты тоже.

- [ ] **Step 5: Протянуть вес через `CachedFeed`**

Конструктор `CachedFeed`:
```kotlin
    maximumSize: Int = DEFAULT_MAXIMUM_SIZE,
    maximumMemoryWeight: Long = Long.MAX_VALUE,
    memoryWeigher: (V) -> Int = { 1 },
) {
```
и memory-tier:
```kotlin
private val memory = TypedTtlCacheImpl<String, Stamped<V>>(
    defaultTtl = defaultTtl,
    maximumSize = maximumSize,
    nowNanos = { clock() * NANOS_PER_MILLI },
    maximumWeight = maximumMemoryWeight,
    weigher = { stamped -> memoryWeigher(stamped.value) },
)
```

- [ ] **Step 6: Задать лимит в `ContentCacheRepository`**

```kotlin
private val cache = CachedFeed(
    store = store,
    serializer = String.serializer(),
    ttl = CachedFeed.HardCeiling,
    keyPrefix = CONTENT_PREFIX,
    clock = clock,
    // A details record carries the whole seasons/episodes/files tree of a series, a megabyte of
    // JSON for a long one, and the hard ceiling keeps it pinned for a week. Count alone let a
    // browsing session pile tens of megabytes of strings into this tier; Room still has them all.
    maximumMemoryWeight = MEMORY_TIER_BYTES,
    memoryWeigher = { payload -> payload.length * Char.SIZE_BYTES },
)
```
В `companion object` (или рядом с `CONTENT_PREFIX`): `private const val MEMORY_TIER_BYTES = 16L * 1024 * 1024`.

- [ ] **Step 7: Прогнать тесты кэша и репозитория**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.core.collections.*" --tests "com.kino.puber.data.cache.*"
```
Ожидание: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kino/puber/core/collections/TypedTtlCache.kt app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt app/src/test/kotlin/com/kino/puber/core/collections/TypedTtlCacheTest.kt
git commit -m "Bound the content cache memory tier by bytes, not only by entry count"
```

---

### Task 7: Загрузка APK обновления идёт потоком на диск

**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:900-947`
- Create: `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientDownloadTest.kt`

**Interfaces:**
- Consumes: `KinoPubApiClient.downloadUpdateAsset(url: String, targetFile: File, onProgress: (Int) -> Unit): Result<File>` (сигнатура не меняется).

Ktor 3 по умолчанию сохраняет тело ответа в память (плагин `SaveBody`); только `prepareGet(url).execute { }` даёт настоящий поток.

- [ ] **Step 1: Падающий тест**

Создать `KinoPubApiClientDownloadTest.kt`:

```kotlin
package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class KinoPubApiClientDownloadTest {

    companion object {
        private const val CHUNK_BYTES = 256 * 1024

        @JvmStatic
        @BeforeAll
        fun setUpAndroidLogging() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAndroidLogging() {
            unmockkStatic(Log::class)
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun downloadUpdateAsset_writesBytesToDiskAsTheyArrive(
        @TempDir dir: Path,
    ) = runTest {
        // The server holds the second half back until the client has reported progress on the
        // first. A client that buffers the whole body before handing it over never reports, the
        // server gives up, and the download comes back short.
        val firstChunkReported = CountDownLatch(1)
        val chunk = ByteArray(CHUNK_BYTES) { 7 }
        withServer(
            path = "/v1/update.apk",
            handler = { exchange ->
                exchange.sendResponseHeaders(200, chunk.size * 2L)
                exchange.responseBody.use { body ->
                    body.write(chunk)
                    body.flush()
                    if (firstChunkReported.await(10, TimeUnit.SECONDS)) {
                        body.write(chunk)
                    }
                }
            },
        ) { baseUrl ->
            val target = dir.resolve("update.apk").toFile()

            val result = client(dir.resolve("cache"), baseUrl).downloadUpdateAsset(
                url = "${baseUrl}update.apk",
                targetFile = target,
                onProgress = { percent -> if (percent in 1..99) firstChunkReported.countDown() },
            )

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals(chunk.size * 2L, target.length())
        }
    }

    private fun client(cacheDir: Path, baseUrl: String): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } returns true

        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns null
        every { preferences.getRefreshToken() } returns null
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null

        return KinoPubApiClient(
            okHttpClient = OkHttpClient(),
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
            mainApiBaseUrl = { baseUrl },
        )
    }

    private suspend fun withServer(
        path: String,
        handler: (HttpExchange) -> Unit,
        block: suspend (baseUrl: String) -> Unit,
    ) {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        server.createContext(path, handler)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/v1/")
        } finally {
            server.stop(0)
        }
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.api.KinoPubApiClientDownloadTest"
```
Ожидание: FAIL примерно через 10 с — `result.isSuccess` ложно (обрыв тела) либо длина файла равна одному чанку.

- [ ] **Step 3: Перевести загрузку на `prepareGet().execute`**

В `downloadUpdateAsset` заменить блок от `val response = httpClient.get(url)` до вызова `streamToFile(...)` включительно на:

```kotlin
// `get()` would hand back a response whose body Ktor has already read into memory in full;
// only a prepared statement streams, and the statement is valid inside `execute` alone.
httpClient.prepareGet(url).execute { response ->
    if (!response.status.isSuccess()) {
        throw IllegalStateException("Update download failed with HTTP ${response.status.value}")
    }

    val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    var lastProgress = -1

    fun dispatchProgress(percent: Int) {
        val coercedPercent = percent.coerceIn(0, PERCENT_MAX)
        if (coercedPercent != lastProgress) {
            lastProgress = coercedPercent
            onProgress(coercedPercent)
        }
    }

    streamToFile(
        channel = response.bodyAsChannel(),
        tempFile = tempFile,
        totalBytes = totalBytes,
        dispatchProgress = ::dispatchProgress,
    )
}
moveIntoPlace(tempFile, targetFile)
```
Импорт: `io.ktor.client.request.prepareGet`. Существующий `try/catch` вокруг (удаление temp-файла при ошибке и отмене) не трогать.

- [ ] **Step 4: Прогнать тесты клиента**

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.kino.puber.data.api.*" --tests "com.kino.puber.domain.interactor.update.*" --tests "com.kino.puber.data.repository.AppUpdate*"
```
Ожидание: PASS; новый тест проходит за секунды, а не по таймауту.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientDownloadTest.kt
git commit -m "Stream the update APK to disk instead of buffering it in memory"
```

---

### Task 8: Полная проверка

- [ ] **Step 1: Все юнит-тесты**

```bash
./gradlew :app:testDevDebugUnitTest
```
Ожидание: BUILD SUCCESSFUL, 0 failed.

- [ ] **Step 2: Lint/detekt, как принято в проекте**

```bash
./gradlew :app:detektAll :app:lintDevDebug
```
Ожидание: без новых предупреждений в затронутых файлах.

- [ ] **Step 3: Сквозной прогон на телевизоре (до любого `connectedAndroidTest`)**

Установить debug-сборку и пройти: Home (hero дрейфует, ряды на месте) → Details → похожий → Back → Back → лайк на Home → плеер → пауза с контролами → перемотка (shimmer при буферизации) → Back из плеера (видео глохнет сразу) → Настройки → проверка обновления (если доступно).

- [ ] **Step 4: Итоговая самопроверка diff**

```bash
git log --oneline master@{u}..HEAD 2>/dev/null || git log --oneline -8
git diff master@{u}..HEAD --stat 2>/dev/null || git diff HEAD~7 --stat
```
Просмотреть diff каждого коммита на предмет случайных правок; Codex-ревью зафиксировать как пропущенное.

---

## Отложено в отдельные планы

- **P3, прогрев следующей серии вторым ExoPlayer.** Нужен дизайн: либо прогрев кэша через `CacheWriter` по плейлисту и первым сегментам без плеера, либо плеер с отключёнными рендерерами. Требует замеров на KM6 с высокобитрейтным потоком.
- **M4, ViewModel переживает Koin-scope.** Структурная правка `ScopeModuleManager`/`DIScope` (привязать scope к ViewModelStore экрана, а не к композиции). Затрагивает все экраны.
- **P5, keep-правила R8 и `largeHeap`.** Поэтапное сужение правил с release-сборкой и прогоном на устройстве после каждого шага.
- **P8–P12.** Малые улучшения (пересоздание ExoPlayer при смене серии, снапшот `watch_state`, History focus, ContentList VM lookups, лишний GET истории).
