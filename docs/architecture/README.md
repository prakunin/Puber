# Architecture Notes

How this project is actually built, written from the code on 2026-08-21. Each
document names the files it describes, so a claim here can be checked in one
grep. When a document and the code disagree, the code wins - and the document
is wrong and should be fixed.

- [screens-and-di.md](screens-and-di.md) - screen anatomy, Koin scopes, `DIScope`.
- [viewmodel.md](viewmodel.md) - `PuberVM`, view state, actions, error handling.
- [navigation.md](navigation.md) - `AppRouter`, `Screens`, results, flows, tabs.
- [paging-and-filters.md](paging-and-filters.md) - `PagingVM`, `Paginator`, content filters.
- [api.md](api.md) - `KinoPubApiClient`, response models, caching.
- [ui-and-compose.md](ui-and-compose.md) - shared components, focus, performance, previews.
- [testing.md](testing.md) - JUnit 5 setup, fakes, what a good test asserts.

These replaced a recipe tree under `.kent/`, removed in the same change,
which had been written for an older shape of the code: it taught
`androidx.tv.foundation` lists the app never imported, router methods
`AppRouter` does not have, a bottom sheet container that exists nowhere, and
JUnit 4 rules for a JUnit 5 suite.
