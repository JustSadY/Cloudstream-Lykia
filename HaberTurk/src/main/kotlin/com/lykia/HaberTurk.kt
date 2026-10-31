package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HaberTurk : MainAPI() {
    override var mainUrl              = "https://www.haberturk.com"
    override var name                 = "HaberTürk"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video"          to "Son Videolar",
        "$mainUrl/video/haber"    to "Haber",
        "$mainUrl/video/spor"     to "Spor",
        "$mainUrl/video/ekonomi"  to "Ekonomi",
        "$mainUrl/video/magazin"  to "Magazin",
        "$mainUrl/video/tv"       to "TV",
        "$mainUrl/video/sinema"   to "Sinema",
        "$mainUrl/video/saglik"   to "Sağlık",
        "$mainUrl/video/eglence"  to "Eğlence"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parseVideos(document)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        return document.select("a[href^=/video/][href*=/izle/]").mapNotNull { card ->
            val href = fixUrlNull(card.attr("href")) ?: return@mapNotNull null
            val title = card.attr("title").trim()
                .ifBlank { card.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
            newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val pages = listOf(
            "$mainUrl/video",
            "$mainUrl/video/haber",
            "$mainUrl/video/spor",
            "$mainUrl/video/ekonomi",
            "$mainUrl/video/magazin"
        )
        return pages.flatMap { url ->
            runCatching { parseVideos(app.get(url).document) }.getOrDefault(emptyList())
        }.distinctBy { it.url }.filter { it.name.contains(query, ignoreCase = true) }
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

        val posterUrl = thumbnail(video) ?: fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val plot = video.optString("description").ifBlank {
            document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        }.ifBlank { null }

        return newMovieLoadResponse(title, url, TvType.Others, stream) {
            this.posterUrl = posterUrl
            this.plot      = plot
            this.duration  = parseDuration(video.optString("duration"))
        }
    }

    private fun thumbnail(video: JSONObject): String? {
        return when (val value = video.opt("thumbnailUrl")) {
            is JSONArray -> value.optString(0).ifBlank { null }
            is String    -> value.ifBlank { null }
            else         -> null
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
                type   = if (data.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
