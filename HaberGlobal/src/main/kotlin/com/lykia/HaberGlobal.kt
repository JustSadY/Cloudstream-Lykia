package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HaberGlobal : MainAPI() {
    override var mainUrl              = "https://haberglobal.com"
    override var name                 = "Haber Global"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video-galeri" to "Video Galeri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page <= 1) request.data else "${request.data}/$page"
        val document = app.get(url).document
        val home     = document.select("a[href][title]").mapNotNull { it.toSearchResult() }
        val pages    = document.select("a[href*=video-galeri]").mapNotNull { anchor ->
            Regex("""/video-galeri/(\d+)""").find(anchor.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page

        return newHomePageResponse(request.name, home, hasNext = page < pages)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        if (selectFirst(".icon-play") == null) return null

        val href = fixUrlNull(attr("href")) ?: return null
        if (!Regex("""-\d+$""").containsMatchIn(href)) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("span.title")?.text()?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
    }

    private fun parseSearchResults(document: Document): List<SearchResponse> {
        return document.select("a[href][title]").mapNotNull { anchor ->
            val href = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
            if (!Regex("""-\d+$""").containsMatchIn(href)) return@mapNotNull null

            val title = anchor.attr("title").trim().ifBlank {
                anchor.selectFirst("span.title")?.text()?.trim().orEmpty()
            }.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val posterUrl = fixUrlNull(anchor.selectFirst("img")?.attr("src"))
            newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("key" to query)).document
        val adaylar  = parseSearchResults(document).take(12)
        if (adaylar.isEmpty()) return emptyList()

        return coroutineScope {
            adaylar.map { aday ->
                async {
                    runCatching { hasVideo(aday.url) }.getOrDefault(false) to aday
                }
            }.awaitAll().filter { it.first }.map { it.second }
        }
    }

    private suspend fun hasVideo(url: String): Boolean {
        val html = app.get(url).text
        return html.contains("\"VideoObject\"")
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" } ?: return null

        val title = video.optString("name").ifBlank {
            document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        }.trim().ifBlank { return null }

        val videoId = dailymotionId(video.optString("embedUrl"))
            ?: dailymotionId(video.optString("contentUrl"))
            ?: document.selectFirst("meta[property=og:video]")?.attr("content")?.let { dailymotionId(it) }
            ?: return null

        val posterUrl = video.optString("thumbnailUrl").ifBlank { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = video.optString("description").ifBlank { null }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }

        return newMovieLoadResponse(
            title, url, TvType.Others, "https://www.dailymotion.com/video/$videoId"
        ) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot
            this.duration  = parseDuration(video.optString("duration"))
        }
    }

    private fun dailymotionId(url: String): String? {
        if (url.isBlank()) return null
        return Regex("""(?:video=|/video/|/embed/video/)([a-zA-Z0-9]+)""")
            .find(url)?.groupValues?.get(1)
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
        val videoId = when {
            data.contains("dailymotion.com/video/") ->
                data.substringAfter("dailymotion.com/video/").substringBefore("?")
            else -> {
                val page = runCatching { app.get(data).text }.getOrNull() ?: return false
                dailymotionId(page) ?: return false
            }
        }

        val metadata = runCatching {
            app.get(
                "https://www.dailymotion.com/player/metadata/video/$videoId",
                referer = "$mainUrl/",
                cacheTime = 0
            )
        }.getOrNull() ?: return false

        val json   = runCatching { JSONObject(metadata.text) }.getOrNull() ?: return false
        val stream = json.optJSONObject("qualities")
            ?.optJSONArray("auto")
            ?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val cookies = metadata.cookies
        val ts      = queryParam(stream, "dmTs") ?: cookies["ts"]
        val v1st    = queryParam(stream, "dmV1st") ?: cookies["v1st"]
        val cookieHeader = listOfNotNull(
            ts?.let { "ts=$it" },
            v1st?.let { "v1st=$it" },
            "dmvk=${cookies["dmvk"] ?: "1"}"
        ).joinToString("; ")

        callback(
            newExtractorLink(
                source = name,
                name   = "$name Dailymotion",
                url    = stream,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://www.dailymotion.com/"
                this.quality = Qualities.P1080.value
                this.headers = buildMap {
                    put("Origin", "https://www.dailymotion.com")
                    put("Accept", "*/*")
                    put("Cookie", cookieHeader)
                }
            }
        )
        return true
    }

    private fun queryParam(url: String, key: String): String? =
        Regex("""[?&]$key=([^&]+)""").find(url)?.groupValues?.get(1)
}
