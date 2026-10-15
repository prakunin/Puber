package com.kino.puber.core.telemetry.di

import com.kino.puber.core.telemetry.SessionLogController
import com.kino.puber.core.telemetry.Telemetry
import org.koin.dsl.module

internal val telemetryModule = module {
    single<SessionLogController> { Telemetry.controller }
}
