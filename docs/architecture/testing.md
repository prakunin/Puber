# Testing

`app/src/test/` holds 127 test files on **JUnit 5** (`org.junit.jupiter`) with
MockK and `kotlinx-coroutines-test`. Instrumented tests live in
`app/src/androidTest/`. Anything describing `@get:Rule`, `TestWatcher`, or
`org.junit.Test` here is from an older setup.

Run them with `./gradlew testDevDebugUnitTest :app:detektAll`, or `make check`.
In a worktree, `./tools/agentw` instead of `./gradlew`.

## Skeleton

```kotlin
class FavoriteVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: FavoritesInteractor

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        coEvery { interactor.observeWatchlist(force = any()) } returns
            flowOf(Cached.Value(listOf(item()), isStale = false))
    }

    @Test
    fun itemSelected_navigatesForContentChangeResultToDetails() { ... }
}
```

`MainDispatcherExtension` (`app/src/test/kotlin/com/kino/puber/util/`) is a
`BeforeEachCallback`/`AfterEachCallback` that swaps `Dispatchers.Main` for an
`UnconfinedTestDispatcher` by default; pass a `StandardTestDispatcher` when the
test needs to control virtual time, and drive it with `runTest(dispatcher)`
(see `AppRouterSessionTest`).

## What to mock and what to build

- Mock the boundaries: `KinoPubApiClient`, `AppRouter`, `Screens`,
  `ErrorHandler`, interactors under test from the outside.
- Use the real class for mappers and pure logic.
- `FakeResourceProvider` (`app/src/test/kotlin/com/kino/puber/util/`) implements
  the whole `ResourceProvider` interface with predictable strings such as
  `string_$resId`, so mapper assertions never depend on real resources.
- `slot()` plus `verify` is the established way to assert what a VM handed to
  the router.

Names read as `method_expectedResult_whenCondition`, e.g.
`itemSelected_navigatesForContentChangeResultToDetails`.

## What a good test asserts

- A regression test must fail without the fix. If it passes on the old code
  too, the data does not reproduce the bug - redesign it.
- Never assert on locale-dependent strings: formatted dates, numbers, and
  currency depend on `Locale.getDefault()`. Assert structure instead - values,
  ordering, counts, ids.
- Prefer explicit expected values over counts:
  `assertEquals(listOf("movie", "serial"), result.map { it.type })` says more on
  failure than `assertEquals(2, result.size)`.
- Mapper edge cases are mandatory: empty input, a single item, null or missing
  fields, malformed values, and - for sectioned lists - the order and grouping,
  not just the total.
- Cover both `Result.success` and `Result.failure` for anything that calls the
  API, and assert the failure path lands in the state the screen actually shows.
