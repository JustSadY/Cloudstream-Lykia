package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class YirmidortTV : MainAPI() {
    override var mainUrl              = "https://www.yirmidort.tv"
    override var name                 = "Yirmidört TV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Video Galeri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        return newHomePageResponse(request.name, parseVideos(document), hasNext = false)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        return document.select("a[href^=/video/]").mapNotNull { anchor ->
            val href = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
            if (!VIDEO_PATH.containsMatchIn(href)) return@mapNotNull null

            val title = anchor.selectFirst("h4")?.text()?.trim()?.ifBlank { null }
                ?: anchor.selectFirst("img")?.attr("alt")?.trim()?.ifBlank { null }
                ?: return@mapNotNull null

            val posterUrl = fixUrlNull(
                anchor.selectFirst("img")?.attr("src")?.ifBlank {
                    anchor.selectFirst("img")?.attr("data-src")
                }
            )
            newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
                this.posterUrl = posterUrl
            }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama-sonuclari", params = mapOf("q" to query)).document
        return parseVideos(document)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
        val stream = MP4_REGEX.find(document.html())?.value ?: return null

        return newMovieLoadResponse(Jsoup.parse(rawTitle).text(), url, TvType.Others, stream) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            newExtractorLink(
                source = name,
                name   = name,
                url    = data,
                type   = ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    companion object {
        private val VIDEO_PATH = Regex("""/video/.+-\d+$""")
        private val MP4_REGEX  = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")
    }
}
