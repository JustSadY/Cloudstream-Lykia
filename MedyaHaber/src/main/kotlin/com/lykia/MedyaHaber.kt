package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MedyaHaber : MainAPI() {
    override var mainUrl              = "https://www.medyahaber.com"
    override var name                 = "Medya Haber"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video-galeriler" to "Video Galeriler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}?page=$page"
        val document = app.get(url).document
        val home     = parseVideos(document)

        val maxPage = document.select("a[href*=video-galeriler?page=]").mapNotNull { anchor ->
            Regex("""page=(\d+)""").find(anchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page

        return newHomePageResponse(request.name, home, hasNext = page < maxPage)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        val candidates = document.select("a[href]").mapNotNull { anchor ->
            anchor.toSearchResult()
        }
        return candidates.groupBy { it.url }.mapNotNull { (_, list) ->
            list.maxByOrNull { it.name.length }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!VIDEO_PATH.containsMatchIn(href)) return null

        val title = attr("title").trim().ifBlank {
            text().trim()
        }.ifBlank {
            selectFirst("img")?.attr("alt")?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val image = selectFirst("img") ?: parent()?.selectFirst("img")
        val posterUrl = fixUrlNull(image?.attr("src"))

        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("search" to query)).document
        val results  = parseVideos(document)
        if (results.isNotEmpty()) return results

        val home = runCatching { app.get("$mainUrl/video-galeriler").document }.getOrNull()
            ?: return emptyList()
        return parseVideos(home).filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
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
        private val VIDEO_PATH = Regex("""/video/.+/\d+$""")
        private val MP4_REGEX  = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")
    }
}
