package com.kino.puber.profile

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import com.kino.puber.PuberApp
import com.kino.puber.data.repository.AppUpdateRepository
import com.kino.puber.data.repository.IAppUpdateRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal class BaselineProfileApp : PuberApp() {

    override fun shouldInitializeRuntime(): Boolean = !isControlProcess()

    override fun configureRuntime() {
        BaselineAuthSeed.ensure(this)
        BaselineInstrumentationEnvironment.configure(this)
    }

    /** The update check is the real one, pointed at the mock's `/github` route. */
    override fun runtimeModules(): List<Module> = listOf(
        module {
            single<IAppUpdateRepository> {
                AppUpdateRepository(get(), BaselineInstrumentationEnvironment.mockH2Origin() + "/github")
            }
        }
    )

    private fun isControlProcess(): Boolean =
        currentProcessName()?.endsWith(CONTROL_PROCESS_SUFFIX) == true

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return getSystemService(ActivityManager::class.java)
            .runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
    }

    private companion object {
        const val CONTROL_PROCESS_SUFFIX = ":baseline_profile_control"
    }
}
