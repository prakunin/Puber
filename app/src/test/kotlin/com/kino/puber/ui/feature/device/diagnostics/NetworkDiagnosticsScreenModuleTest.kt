package com.kino.puber.ui.feature.device.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.ui.feature.device.diagnostics.vm.NetworkDiagnosticsVM
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class NetworkDiagnosticsScreenModuleTest {

    @Test
    fun screenScope_resolvesTheViewModelFromItsOwnModule() {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)
        val screen = NetworkDiagnosticsScreen()
        val application = koinApplication {
            androidContext(context)
            modules(
                module {
                    single { mockk<NetworkDiagnosticsInteractor>() }
                    single { mockk<ErrorHandler>(relaxed = true) }
                    single { mockk<AppRouter>(relaxed = true) }
                },
            )
        }

        try {
            val parent = application.koin.createScope("parent", named("parent"))
            application.koin.loadModules(listOf(screen.buildModule(SCOPE_ID, parent)))
            val scope = application.koin.createScope(SCOPE_ID, named(SCOPE_ID))

            assertNotNull(scope.get<NetworkDiagnosticsVM>())
        } finally {
            application.close()
        }
    }

    private companion object {
        const val SCOPE_ID = "network-diagnostics-test"
    }
}
