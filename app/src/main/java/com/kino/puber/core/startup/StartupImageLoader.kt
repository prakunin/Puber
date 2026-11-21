package com.kino.puber.core.startup

import coil3.ImageLoader

/** Makes the loader report its requests in flight to the startup reveal gate. */
internal fun ImageLoader.Builder.reportingToStartupReveal(): ImageLoader.Builder =
    eventListener(StartupReveal.imageActivity)
