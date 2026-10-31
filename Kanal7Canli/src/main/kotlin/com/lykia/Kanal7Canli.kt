package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Kanal7Canli : MainAPI() {
    override var mainUrl              = "https://www.kanal7.com"
    override var name                 = "Kanal 7 Canlı"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    private data class Channel(
        val title: String,
        val stream: String,
        val plot: String
    ) {
        val poster get() = "https://www.google.com/s2/favicons?domain=kanal7.com&sz=256"
    }

    private val channels = listOf(
        Channel(
            "Kanal 7",
            "https://kanal7.daioncdn.net/kanal7/kanal7.m3u8?app=f99587ad-1637-494d-8255-da35b09d17a1&ce=3",
            "Kanal 7'nin resmî canlı yayını."
        ),
        Channel(
            "Kanal 7 Avrupa",
            "https://livetv.radyotvonline.net/kanal7live/kanal7avr/playlist.m3u8",
            "Kanal 7 Avrupa'nın resmî canlı yayını."
        )
    )

    private fun Channel.toSearchResponse() = newLiveSearchResponse(title, stream, TvType.Live) {
        this.posterUrl = poster
    }

    override val mainPage = mainPageOf(
        "kanal7" to "Canlı Yayın"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return newHomePageResponse(request.name, channels.map { it.toSearchResponse() }, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return channels.filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val channel = channels.firstOrNull { it.stream == url } ?: return null
        return newLiveStreamLoadResponse(channel.title, url, url) {
            this.posterUrl = channel.poster
            this.plot      = channel.plot
            this.tags      = listOf("Kanal 7", "Canlı")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = channels.firstOrNull { it.stream == data } ?: return false

        callback(
            newExtractorLink(
                source = name,
                name   = channel.title,
                url    = channel.stream,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
