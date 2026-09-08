# Аудит утечек памяти и производительности, 2026-09-08

Статический аудит кода тремя параллельными проходами (плеер, Compose UI, data/DI) с ручной
перепроверкой каждой находки, вошедшей в план. Классических утечек (Activity/View в синглтонах,
незакрытые scope, неснятые listener'ы) не найдено. Ниже только то, что подтверждено по коду.

## Память

| # | Где | Что | Серьёзность |
|---|-----|-----|-------------|
| M1 | `data/api/KinoPubApiClient.kt:912-929` | `httpClient.get(url)` + `bodyAsChannel()`: Ktor 3 по умолчанию сохраняет тело в память, APK целиком становится `byte[]` до записи на диск | high |
| M2 | `data/cache/CachedFeed.kt:63-69`, `ContentCacheRepository.kt:34-40` | memory-tier кэша ограничен 1024 записями, а не байтами; хранит сырые JSON-строки карточки и деталей сериала (сезоны, серии, файлы) под одним ключом, TTL 7 дней | high |
| M3 | `core/ui/navigation/AppRouter.kt:48-50, 67-70, 114-121` | once-listener снимается только через `back(resultCode, …)`; pop через Voyager (когда `dispatchBackPressed` вернул false) оставляет bound-метод VM в роутере; LIFO позволяет отдать результат чужому listener'у | medium |
| M4 | `core/di/ScopeModuleManager.kt:37-53`, `DIScope.kt:19-28` | Koin-scope закрывается при уходе из композиции, VM живёт в ViewModelStore экрана; каждый возврат из Details создаёт неиспользуемый набор scoped-интеракторов | medium, структурно |
| M5 | `AndroidManifest.xml:30` | `largeHeap="true"` маскирует M2 до позднего OOM | low |

## Производительность

| # | Где | Что | Серьёзность |
|---|-----|-----|-------------|
| P1 | `core/ui/uikit/component/HeroCarousel.kt:227-246, 281-294` | значения Ken Burns-анимации читаются в композиции (`by animateFloat`, деструктуризация) и передаются в `graphicsLayer` как Float; весь `HeroItem` рекомпозируется каждый кадр, пока виден Home | high |
| P2 | `ui/feature/player/component/PlayerProgressBar.kt:62-71` | infinite shimmer создаётся безусловно и читается в композиции; нужен только при буферизации внутри `drawBehind`; при показанных контролах прогресс-бар перекомпонуется каждый кадр | medium |
| P3 | `ui/feature/player/vm/PlaybackController.kt:681-732` | прогрев следующей серии поднимает второй полноценный ExoPlayer (аппаратный декодер + AudioTrack) параллельно основному | medium, требует дизайна |
| P4 | `ui/feature/home/vm/HomeVM.kt:426-457` | `publishSections()` перемапливает все секции на Main при приходе каждой из 8 секций и при каждом `cardDisplayChanges`; каждый publish создаёт новые экземпляры списков | medium |
| P5 | `proguard-rules.pro:49, 62, 70, 83, 103, 110, 117` | `-keep class … { *; }` для ktor, okhttp3.internal, koin, voyager, media3, coil3, всех Parcelable; R8 почти не работает | medium, требует проверки release на устройстве |
| P6 | `ui/feature/player/vm/PlayerVM.kt:1806-1822` | `exitPlayer` ждёт окончания сетевых записей, а плеер продолжает декодировать до `onCleared` | low |
| P7 | `ui/feature/player/component/PlayerScreen.kt:74` | `vm::getExoPlayer` не обёрнут в `remember`, новый объект на каждую рекомпозицию | low |
| P8 | `PlaybackController.kt:364-428` | каждая смена серии/пресета/DNS пересоздаёт ExoPlayer с нуля | low |
| P9 | `data/repository/WatchStateRepository.kt:49-51` | полное перечитывание `watch_state` на каждую транзакцию записи | low |
| P10 | `ui/feature/history/vm/HistoryVM.kt:441-447` | каждый шаг фокуса переиздаёт всё состояние экрана | low |
| P11 | `ui/feature/contentlist/content/ContentListScreenContent.kt:82-90` | `sections.map { puberViewModel(...) }` на каждую рекомпозицию | low |
| P12 | `domain/interactor/watchstate/RecentlyPlayedOrder.kt:31-37` | лишний no-store GET истории на каждую эмиссию | low |

## Снятые находки

- «AndroidKeyStore + AES на каждый HTTP-запрос» (`KinoPubApiClient.kt:173-180`). `getUsername()` возвращает
  `null` без расшифровки, пока значение не сохранено, а `saveUsername` вызывается только из
  `nonMinifiedRelease/…/BaselineProfileAuthReceiver.kt`. Токен Ktor Auth кэширует после первого
  `loadTokens`. В обычной сборке per-request стоимости нет.

## Что войдёт в план исправлений

M1, M2, M3, P1, P2, P4, P6, P7. Остальное отложено: P3, M4, P5 требуют отдельного дизайна и
проверки на устройстве; P8–P12 малы и не мешают.
