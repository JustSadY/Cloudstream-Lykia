package com.lykia

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Kanal7CanliPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kanal7Canli())
    }
}
