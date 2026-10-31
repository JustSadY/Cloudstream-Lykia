package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element

class TV100 : MainAPI() {
    override var mainUrl              = "https://www.tv100.com"
    override var name                 = "TV100"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video-galeri" to "Son Videolar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page <= 1) request.data else "${request.data}?sayfa=$page"
        val document = app.get(url).document
        val home     = document.select("a.post-card[href]").mapNotNull { it.toSearchResult() }
        val pages    = document.select("a[href*=sayfa=]").mapNotNull { anchor ->
            Regex("""[?&]sayfa=(\d+)""").find(anchor.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page

        return newHomePageResponse(request.name, home, hasNext = page < pages)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (href.contains("-galeri-")) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("h2.title, h3.title, div.title")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) return null

        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/ara", params = mapOf("key" to query)).document
        return document.select("a.post-card[href]").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" } ?: return null

        val title = video.optString("name").ifBlank {
            document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        }.trim().ifBlank { return null }

        val stream = video.optString("contentUrl").ifBlank {
            document.selectFirst("meta[property=og:video]")?.attr("content").orEmpty()
        }.trim().ifBlank { return null }

        val posterUrl = video.optString("thumbnailUrl").ifBlank {
            document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        }.ifBlank { null }

        val plot = video.optString("description").ifBlank {
            document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        }.ifBlank { null }

        return newMovieLoadResponse(title, url, TvType.Others, stream) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot
            this.duration  = parseDuration(video.optString("duration"))
        }
    }

    private fun parseDuration(value: String): Int? {
        val match = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(value) ?: return null
        val hours   = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return (hours * 60 + minutes + if (seconds > 0) 1 else 0).takeIf { it > 0 }
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
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
