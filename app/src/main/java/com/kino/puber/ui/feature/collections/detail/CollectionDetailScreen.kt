package com.kino.puber.ui.feature.collections.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContent
import com.kino.puber.ui.feature.collections.detail.vm.CollectionDetailVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

/**
 * Opened from Home and from the collections tab, and routed through the root flow the way a details
 * screen is.
 *
 * A screen pushed inside the tab flow instead would strand the list it was opened from: only a root
 * navigation captures the caller's lazy-list anchor and only a root return replays it together with
 * the focused card. A tab-level pop has nothing but `restoreFocusedChild()`, which cannot fire after
 * the tab content composition was disposed under the pushed screen, so focus falls back to the first
 * card on the screen and drags Home back to the top — burying the collections row, which is drawn
 * last.
 */
@Parcelize
internal class CollectionDetailScreen(
    private val collectionId: Int,
    private val collectionTitle: String,
) : RootPuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = "CollectionDetailScreen_$collectionId"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::CollectionInteractor)
            scoped { VideoItemUIMapper(get(), get(), get()) }
            viewModel {
                CollectionDetailVM(
                    router = get(),
                    collectionId = collectionId,
                    collectionTitle = collectionTitle,
                    interactor = get(),
                    savedItemInteractor = get(),
                    mapper = get(),
                    errorHandler = get(),
                )
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<CollectionDetailVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        CollectionDetailScreenContent(
            state = state,
            onAction = onAction,
            onItemClick = { item -> onAction(CommonAction.ItemSelected(item)) },
        )
    }
}
