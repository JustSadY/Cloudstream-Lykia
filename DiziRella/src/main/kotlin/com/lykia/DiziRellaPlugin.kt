package com.lykia

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DiziRellaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziRella())
    }
}
