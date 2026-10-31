package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class TRTCanli : MainAPI() {
    override var mainUrl              = "https://www.trt.com.tr"
    override var name                 = "TRT Canlı"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "trt" to "TRT Kanalları"
    )

    private data class Channel(
        val path: String,
        val title: String,
        val domain: String,
        val plot: String
    ) {
        val stream get() = "https://tv-$path.medya.trt.com.tr/master.m3u8"
        val poster get() = "https://www.google.com/s2/favicons?domain=$domain&sz=256"
    }

    private val channels = listOf(
        Channel("trt1", "TRT 1", "trt1.com.tr", "TRT 1 resmî canlı yayını."),
        Channel("trt2", "TRT 2", "trt2.com.tr", "TRT 2 resmî canlı yayını."),
        Channel("trthaber", "TRT Haber", "trthaber.com", "TRT Haber resmî canlı yayını."),
        Channel("trtspor1", "TRT Spor", "trtspor.com.tr", "TRT Spor resmî canlı yayını."),
        Channel("trtspor2", "TRT Spor Yıldız", "trtspor.com.tr", "TRT Spor Yıldız resmî canlı yayını."),
        Channel("trtbelgesel", "TRT Belgesel", "trtbelgesel.com.tr", "TRT Belgesel resmî canlı yayını."),
        Channel("trtcocuk", "TRT Çocuk", "trtcocuk.com.tr", "TRT Çocuk resmî canlı yayını."),
        Channel("trtmuzik", "TRT Müzik", "trtmuzik.com.tr", "TRT Müzik resmî canlı yayını."),
        Channel("trtavaz", "TRT Avaz", "trtavaz.com.tr", "TRT Avaz resmî canlı yayını."),
        Channel("trtkurdi", "TRT Kurdî", "trtkurdi.com.tr", "TRT Kurdî resmî canlı yayını."),
        Channel("trtarabi", "TRT Arabi", "trtarabi.com", "TRT Arabi resmî canlı yayını."),
        Channel("trtworld", "TRT World", "trtworld.com", "TRT World resmî canlı yayını.")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = channels.map { channel ->
            newLiveSearchResponse(channel.title, channel.stream, TvType.Live) {
                this.posterUrl = channel.poster
            }
        }
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return channels.filter { it.title.contains(query, ignoreCase = true) }.map { channel ->
            newLiveSearchResponse(channel.title, channel.stream, TvType.Live) {
                this.posterUrl = channel.poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val channel = channels.firstOrNull { it.stream == url } ?: return null
        val recommendations = channels.filter { it.stream != url }.map { other ->
            newLiveSearchResponse(other.title, other.stream, TvType.Live) {
                this.posterUrl = other.poster
            }
        }

        return newLiveStreamLoadResponse(channel.title, url, url) {
            this.posterUrl       = channel.poster
            this.plot            = channel.plot
            this.tags            = listOf("TRT", "Canlı")
            this.recommendations = recommendations
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
