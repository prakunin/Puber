LENS: state-path  ROUND: 2  BASE: c205c62e

No findings.

Traced: cold open with a restored filter that pages empty (effect focuses the empty-state «Сброс»; Up to «Фильтры»; reset moves focus to «Фильтры» before FilterReset); panel apply that pages empty (headerHoldsFocus already true, focus stays on «Фильтры», also when Empty lands before the panel closes); focus moved to the side rail during Loading then Empty (content focus off while the rail is Open, nothing stolen; on the Dismiss handoff the effect focuses «Сброс» and the handoff settles; leaving «Фильтры» for the rail keeps headerHoldsFocus true and restores «Фильтры»); picking another tab while Loading (ShowAll torn down); Empty while a dialog covers the screen (waits until the dialog closes).

Files read: ShowAllScreenContent.kt, ShowAllHeader.kt, MainScreenComponent.kt, ContentFocusHandoff.kt, ModalNavigationDrawer.kt.
