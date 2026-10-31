package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BloombergHT : MainAPI() {
    override var mainUrl              = "https://www.bloomberght.com"
    override var name                 = "Bloomberg HT"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Son Videolar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("a[href*=/video/][title]").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!Regex("""/video/[^/]+/\d+$""").containsMatchIn(href)) return null

        val title = attr("title").trim().takeIf { it.isNotBlank() } ?: return null
        val posterUrl = fixUrlNull(
            selectFirst("img")?.attr("data-src")?.ifBlank {
                selectFirst("img")?.attr("src")
            }
        )

        return newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("q" to query)).document
        return document.select("a[href*=/video/][title]").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" } ?: return null

        val title = video.optString("name").ifBlank {
            document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        }.trim().ifBlank { return null }

        val stream = streamFromEmbed(video.optString("embedUrl"), url) ?: return null

        return newMovieLoadResponse(title, url, TvType.Others, stream) {
            this.posterUrl = fixUrlNull(thumbnail(video))
            this.plot      = video.optString("description").ifBlank { null }
            this.duration  = parseDuration(video.optString("duration"))
        }
    }

    private suspend fun streamFromEmbed(embedUrl: String, referer: String): String? {
        if (embedUrl.isBlank()) return null
        val html = runCatching {
            app.get(embedUrl, referer = referer).text
        }.getOrNull() ?: return null

        val raw = Regex("""data-hope-video='([^']+)'""").find(html)?.groupValues?.get(1)
            ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val mp4  = json.optJSONObject("media")?.optJSONArray("mp4") ?: return null

        return bestMp4(mp4)
    }

    private fun bestMp4(array: JSONArray): String? {
        var best: String? = null
        var bestLabel = -1
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("src").takeIf { it.isNotBlank() } ?: continue
            val label = item.optString("label").toIntOrNull() ?: 0
            if (label > bestLabel) {
                bestLabel = label
                best = url
            }
        }
        return best
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
                type   = ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }
}
