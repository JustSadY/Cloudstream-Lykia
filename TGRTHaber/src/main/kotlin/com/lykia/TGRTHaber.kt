package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TGRTHaber : MainAPI() {
    override var mainUrl              = "https://www.tgrthaber.com"
    override var name                 = "TGRT Haber"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    private val kategoriler = listOf(
        "/gundem-videolari",
        "/dunya-videolari",
        "/ekonomi-videolari",
        "/spor-videolari",
        "/magazin-videolari"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/gundem-videolari" to "Gündem Videoları"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parseVideos(document)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        return document.select("a[href*=-videolari/][title]").mapNotNull { anchor ->
            anchor.toSearchResult()
        }.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("-videolari/")) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("div.title")?.text()?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val posterUrl = fixUrlNull(
            selectFirst("img")?.attr("src")?.ifBlank {
                selectFirst("img")?.attr("data-src")
            }
        )
        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val needle = query.substringBefore("'").trim()
        val found  = kategoriler.flatMap { kategori ->
            runCatching {
                parseVideos(app.get("$mainUrl$kategori").document)
            }.getOrDefault(emptyList())
        }.distinctBy { it.url }

        return found.filter { it.name.contains(needle, ignoreCase = true) }
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
        private val MP4_REGEX = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")
    }
}
