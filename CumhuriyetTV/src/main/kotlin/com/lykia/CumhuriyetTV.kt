package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CumhuriyetTV : MainAPI() {
    override var mainUrl              = "https://www.cumhuriyet.com.tr"
    override var name                 = "Cumhuriyet TV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Video Haberler"
    )

    private var galleryCache: List<SearchResponse>? = null

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parseGallery(document)
        galleryCache = home
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parseGallery(document: Document): List<SearchResponse> {
        return document.select("a[href]").mapNotNull { anchor ->
            anchor.toSearchResult()
        }.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!VIDEO_PATH.containsMatchIn(href)) return null

        val title = selectFirst("img")?.attr("alt")?.trim()?.ifBlank { null }
            ?: text().trim().ifBlank { null }
            ?: return null

        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val gallery = galleryCache ?: run {
            val home = parseGallery(app.get("$mainUrl/video").document)
            galleryCache = home
            home
        }
        return gallery.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" }

        val rawTitle = video?.optString("name")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = video?.optString("thumbnailUrl")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = video?.optString("description")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }

        val videoId = video?.optString("contentUrl")?.let { dailymotionId(it) }
            ?: video?.optString("embedUrl")?.let { dailymotionId(it) }
            ?: DAILYMOTION_URL.find(document.html())?.groupValues?.get(1)
            ?: return null

        return newMovieLoadResponse(
            Jsoup.parse(rawTitle).text(), url, TvType.Others,
            "https://www.dailymotion.com/video/$videoId"
        ) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot?.let { Jsoup.parse(it).text() }
        }
    }

    private fun dailymotionId(url: String): String? {
        if (url.isBlank()) return null
        return Regex("""(?:video=|/video/|/embed/video/)([a-zA-Z0-9]+)""")
            .find(url)?.groupValues?.get(1)
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

    companion object {
        private val VIDEO_PATH      = Regex("""/video-.+-\d+$""")
        private val DAILYMOTION_URL = Regex("""dailymotion\.com/(?:embed/)?video/([a-zA-Z0-9]+)""")
    }
}
