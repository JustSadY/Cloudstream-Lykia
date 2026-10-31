package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class TVNET : MainAPI() {
    override var mainUrl              = "https://www.tvnet.com.tr"
    override var name                 = "TVNET"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    private val stream = "https://tvnet-live.lg.mncdn.com/tvnet-live/tvnet/playlist.m3u8"
    private val poster = "https://www.google.com/s2/favicons?domain=tvnet.com.tr&sz=256"

    override val mainPage = mainPageOf(
        "tvnet" to "Canlı Yayın"
    )

    private fun liveItem() = newLiveSearchResponse("TVNET Canlı", stream, TvType.Live) {
        this.posterUrl = poster
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return newHomePageResponse(request.name, listOf(liveItem()), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return if (query.contains("tvnet", ignoreCase = true)) listOf(liveItem()) else emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        if (url != stream) return null
        return newLiveStreamLoadResponse("TVNET Canlı", url, url) {
            this.posterUrl = poster
            this.plot      = "TVNET'in resmî canlı yayını."
            this.tags      = listOf("TVNET", "Canlı")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data != stream) return false

        callback(
            newExtractorLink(
                source = name,
                name   = "TVNET Canlı",
                url    = stream,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
