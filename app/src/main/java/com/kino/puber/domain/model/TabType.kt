package com.kino.puber.domain.model

import android.os.Parcelable
import com.kino.puber.R
import kotlinx.parcelize.Parcelize

/**
 * A section of the app's menu.
 *
 * Lives here rather than with the main screen's view state because the navigation preferences store
 * the menu — which sections exist, in which order, and which one the app opens on — and the data
 * layer must not have to import a UI model to do it. The title stays a resource id: the sections
 * are named in one place, and every reader of this enum is showing it to someone.
 */
@Parcelize
enum class TabType(val title: Int, val enabled: Boolean = true) : Parcelable {
    Home(R.string.main_tabs_home),
    Search(R.string.main_tabs_search),
    Favourites(R.string.main_tabs_favorites),
    Bookmarks(R.string.main_tabs_bookmarks, enabled = false),
    History(R.string.main_tabs_history),
    Movies(R.string.main_tabs_movies),
    Series(R.string.main_tabs_series),
    Cartoons(R.string.main_tabs_cartoons, enabled = false),
    Anime(R.string.main_tabs_anime, enabled = false),
    For4k(R.string.main_tabs_f4k),
    Concerts(R.string.main_tabs_concerts),
    DocMovies(R.string.main_tabs_docmovies),
    DocSeries(R.string.main_tabs_docseries),
    TvShows(R.string.main_tabs_tvshows),
    Collections(R.string.main_tabs_collections, enabled = false),
    SportTV(R.string.main_tabs_sport_tv, enabled = false),
    Settings(R.string.main_tabs_settings),
}
