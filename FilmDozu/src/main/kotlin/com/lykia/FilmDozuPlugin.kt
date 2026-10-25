package com.lykia

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmDozuPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmDozu())
        registerExtractorAPI(VidMoly())
        registerExtractorAPI(VidMolyBiz())
        registerExtractorAPI(VidMolyTo())
        registerExtractorAPI(Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
    }
}
