package com.lykia

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Izle7Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Izle7())
    }
}
