package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class InternetHaber : MainAPI() {
    override var mainUrl              = "https://www.internethaber.com"
    override var name                 = "İnternet Haber"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video-galeri" to "Video Galeri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page <= 1) request.data else "${request.data}?page=$page"
        val document = app.get(url).document
        val home     = parseVideos(document)

        val maxPage = document.select("a[href*=video-galeri?page=]").mapNotNull { anchor ->
            Regex("""page=(\d+)""").find(anchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page

        return newHomePageResponse(request.name, home, hasNext = page < maxPage)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        return document.select("a[href*=-video-galerisi-][title]").mapNotNull { anchor ->
            anchor.toSearchResult()
        }.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("-video-galerisi-")) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("img")?.attr("alt")?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("key" to query)).document
        return parseVideos(document)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" }

        val rawTitle = video?.optString("name")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = video?.optString("thumbnailUrl")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = video?.optString("description")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
        val stream = MP4_REGEX.find(document.html())?.value ?: return null

        return newMovieLoadResponse(Jsoup.parse(rawTitle).text(), url, TvType.Others, stream) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot?.let { Jsoup.parse(it).text() }
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
        private val MP4_REGEX = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")
    }
}
