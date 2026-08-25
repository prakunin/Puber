package com.kino.puber.ui.feature.contentlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.trailer.TrailerLinkInteractor
import com.kino.puber.ui.feature.contentlist.content.ContentListScreenContent
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.contentlist.vm.ContentListRefreshCoordinator
import com.kino.puber.ui.feature.contentlist.vm.ContentListVM
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class ContentListScreen(
    private val tabType: TabType,
) : PuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = "ContentListScreen_${tabType.name}"

    private val sections get() = TabTypeConfig.sectionsFor(tabType)

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::ContentListInteractor)
            scopedOf(::TrailerLinkInteractor)
            scopedOf(::ContentListRefreshCoordinator)
            scoped { VideoItemUIMapper(get(), get(), get()) }
            viewModel {
                ContentListVM(
                    router = get(),
                    interactor = get(),
                    mapper = get(),
                    navPrefs = get(),
                    trailerLinks = get(),
                    contentListRefreshCoordinator = get(),
                    heroConfigs = TabTypeConfig.heroConfigsFor(tabType),
                )
            }

            // A view model rather than a plain scoped object, because the Koin scope dies with the
            // composition: opening a card destroys it, and a section rebuilt on the way back starts
            // its paging again at page one. The card the user left then no longer exists in the row
            // — the pages that held it have not been fetched again yet — and the focus the row
            // restores lands wherever the shorter list happens to reach. Held in the screen's
            // ViewModelStore the section outlives the trip with every page it had loaded, which is
            // what the restore needs, and the tab stops re-fetching all of its sections on every
            // return.
            sections.forEach { sec ->
                viewModel(named(sec.id)) {
                    SectionVM(
                        paginator = Paginator.Store { old, new -> old.id == new.id },
                        config = sec,
                        interactor = get(),
                        savedItemInteractor = get(),
                        mapper = get(),
                        router = get(),
                        errorHandler = get(),
                        contentListRefreshCoordinator = get(),
                    )
                }
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val contentListVm = puberViewModel<ContentListVM>()
        val state by contentListVm.collectViewState()
        val onAction = remember(contentListVm) { contentListVm::onAction }
        ContentListScreenContent(
            state = state,
            sections = sections,
            onAction = onAction,
        )
    }
}
