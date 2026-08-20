# Seasons on the series screen — design

**Goal:** a series shows its seasons on its own screen, one season at a time, without a button that
opens a panel over everything. The description stays where it is while the season changes below it.

## What is wrong now

A series has a «Выбрать сезон» button. Pressing it slides a panel over the whole screen holding
every season and every episode in one long grid. Everything the screen was showing — the title, the
plot, the ratings — is behind that panel, and the only way back is Back.

So choosing an episode means leaving the page that told you what the series is.

## The screen

The film's hero, shortened, with the seasons underneath it. Both are on screen at once and the top
half does not move while you go through the seasons.

```
┌────────────────────────────────────────────────────────────────┐
│ Алхимия душ                                                    │
│ Hwanhon                                                        │
│ IMDb 8.7   KP 9.7                                              │
│ 2022 · Дорама · Южная Корея · Сезонов: 2                       │
│ Душа великой воительницы оказывается запертой в теле слабой…   │
│ Дорожек: 2 · Субтитры: 10                                      │
│ Режиссёр: Пак Чун-хва · В ролях: Ли Джэ-ук, Чон Со-мин…        │
│ [▶ 2 сезон, 2 серия] [⤴] [🔖]                                   │
├────────────────────────────────────────────────────────────────┤
│ 1 сезон, 20 серий                                              │
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐         │
│ │   1    │ │   2    │ │   3    │ │   4    │ │   5    │   →     │
│ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘         │
└────────────────────────────────────────────────────────────────┘
                    ↓  the next season, the text above unchanged
```

This is the catalogue's arrangement, and it should be built from the same parts: a fixed block on
top, and below it a list whose every row is exactly one viewport tall, so the next season is never
half on screen. `ContentListScreenContent` already does this with `fillParentMaxHeight()`; the
season list follows it rather than inventing a second way.

### Heights

The screen is 540 dp. The hero needs about 306 dp for a title of two lines, the ratings, the meta
line, a readable minimum of description, the facts and credits lines and the buttons. The season
row is a heading plus one card: 24 dp and the shared 150 dp card, with padding, is about 234 dp.

So the season area takes a fixed height and the hero takes the rest, rather than the two splitting
the screen evenly. The description is what absorbs the difference: it already takes the space left
over and scrolls itself when there is not enough, and on a series it will simply do that more often.

### Which season is showing when the screen opens

The one holding the episode the buttons offer to play — the next unwatched one. Opening a series
you are part way through puts you in the season you are watching, not in season one. The state
already carries `initialEpisodeFocusId` for this; it now positions the list instead of the panel.

### What a card does

Selecting an episode plays it, as it does in the panel today. The long-press context menu on an
episode stays.

## What goes

The «Выбрать сезон» button, and with it `DetailsAction.SelectSeasonClicked`,
`DetailsAction.CloseSeasonsPanel`, `seasonsPanelVisible` and the panel's focus machinery on this
screen. `EpisodesPanel` itself stays: the player screen uses it, and there it is the right shape,
because the player has nothing else on screen to preserve.

The deep link that opens a series at a particular episode keeps working — it focuses that episode in
the list rather than opening the panel at it.

## Films are untouched

A film has no season list, so its hero keeps the whole page. Only a series is divided.

## Похожее

Stays where it is: the page below, reached by pressing down past the last season. This is the one
part of the arrangement the catalogue has no equivalent for — there is nothing under the catalogue's
list — so it is also the part most likely to need work on the television. Note that every
similar-items answer this account returns is empty, so the page below will usually not exist at all
and DOWN from the last season will correctly do nothing.

## Risks to watch

A list that pages by viewport, inside a pager that also pages by viewport, both driven by the same
DOWN key. The season list must consume DOWN while it has another season, and let it through only at
the last one. Getting this wrong strands the user in either direction, and the television is the
only place it can be judged.

The auto-scrolling description will now run on far more screens, because the hero is shorter. It is
already gated to stop when the page is not visible; that gate matters more here.
