package com.kino.puber.ui.feature.showall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.content.ShowAllScreenContent
import com.kino.puber.ui.feature.showall.content.resolve
import com.kino.puber.ui.feature.showall.model.ShowAllTitle
import com.kino.puber.ui.feature.showall.vm.ShowAllVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class ShowAllScreen(
    internal val config: SectionConfig,
    internal val title: ShowAllTitle,
) : PuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = "ShowAllScreen_${config.id}"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::ContentListInteractor)
            scoped { VideoItemUIMapper(get(), get(), get()) }
            viewModel {
                ShowAllVM(
                    paginator = Paginator.Store { old, new -> old.id == new.id },
                    config = config,
                    interactor = get(),
                    genreInteractor = get(),
                    countryInteractor = get(),
                    savedItemInteractor = get(),
                    mapper = get(),
                    router = get(),
                    errorHandler = get(),
                    filterRepository = get(),
                )
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<ShowAllVM>()
        val state by vm.collectViewState()
        val filter by vm.filterState.collectAsStateWithLifecycle()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        ShowAllScreenContent(
            title = title.resolve(),
            state = state,
            onAction = onAction,
            filter = filter,
        )
    }
}
