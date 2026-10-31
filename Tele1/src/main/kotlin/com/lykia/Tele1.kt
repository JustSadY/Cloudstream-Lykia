package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Tele1 : MainAPI() {
    override var mainUrl              = "https://www.tele1.com.tr"
    override var name                 = "Tele1"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Son Videolar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parseVideos(document)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parseVideos(document: Document): List<SearchResponse> {
        return document.select("a[href^=/video/][title]").mapNotNull { card ->
            val href = fixUrlNull(card.attr("href")) ?: return@mapNotNull null
            if (href.contains("/video/kategori/") || href.endsWith("/video/test")) return@mapNotNull null

            val title = card.attr("title").trim().ifBlank {
                card.selectFirst("img")?.attr("alt")?.trim().orEmpty()
            }.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val posterUrl = fixUrlNull(
                card.selectFirst("img")?.attr("data-src")?.ifBlank {
                    card.selectFirst("img")?.attr("src")
                }
            )
            newMovieSearchResponse(title, href, TvType.Others) { this.posterUrl = posterUrl }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/video").document
        return parseVideos(document).filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" }

        val videoId = dailymotionId(document.html())
        if (videoId == null) return null

        val title = video?.optString("name")?.ifBlank { null }
            ?: document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = video?.optString("thumbnailUrl")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = video?.optString("description")?.ifBlank { null }

        return newMovieLoadResponse(
            title, url, TvType.Others, "https://www.dailymotion.com/video/$videoId"
        ) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot
        }
    }

    private fun dailymotionId(html: String): String? {
        return Regex("data-video=\"([a-zA-Z0-9]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("""dailymotion\.com/(?:player/[^"]+video=|video/|embed/video/)([a-zA-Z0-9]+)""")
                .find(html)?.groupValues?.get(1)
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
