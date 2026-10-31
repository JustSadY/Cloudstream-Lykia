package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HurriyetTV : MainAPI() {
    override var mainUrl              = "https://www.hurriyet.com.tr"
    override var name                 = "Hürriyet TV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video"                    to "Son Videolar",
        "$mainUrl/sporarena/video-galeri/"  to "Spor Videoları"
    )

    private var galleryCache: List<SearchResponse>? = null

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parseGallery(document)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parseGallery(document: Document): List<SearchResponse> {
        return document.select("a[href][title]").mapNotNull { anchor ->
            anchor.toSearchResult()
        }.distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!VIDEO_PATH.containsMatchIn(href)) return null

        val title = attr("title").trim().ifBlank {
            selectFirst(".news-feed-item-title")?.text()?.trim().orEmpty()
        }.ifBlank {
            selectFirst("img")?.attr("alt")?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val image = selectFirst("img")
        val posterUrl = fixUrlNull(
            image?.attr("data-src")?.ifBlank { null } ?: image?.attr("src")
        )

        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val gallery = galleryCache ?: run {
            val home = CATEGORIES.flatMap { url ->
                runCatching { parseGallery(app.get(url).document) }.getOrDefault(emptyList())
            }.distinctBy { it.url }
            galleryCache = home
            home
        }
        return gallery.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
        val duration = parseDuration(document)

        val videoId = DAILYMOTION_VIDEO_ID.find(document.html())?.groupValues?.get(1)
            ?: DAILYMOTION_URL.find(document.html())?.groupValues?.get(1)
            ?: return null

        return newMovieLoadResponse(
            Jsoup.parse(rawTitle).text(), url, TvType.Others,
            "https://www.dailymotion.com/video/$videoId"
        ) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot?.let { Jsoup.parse(it).text() }
            this.duration  = duration
        }
    }

    private fun parseDuration(document: Document): Int? {
        val value = document.selectFirst("meta[property=video:duration]")?.attr("content")
            ?: DURATION.find(document.html())?.groupValues?.get(1)
            ?: return null
        return value.toIntOrNull()?.div(60)?.takeIf { it > 0 }
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
                DAILYMOTION_VIDEO_ID.find(page)?.groupValues?.get(1)
                    ?: DAILYMOTION_URL.find(page)?.groupValues?.get(1)
                    ?: return false
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
        private val CATEGORIES = listOf(
            "https://www.hurriyet.com.tr/video",
            "https://www.hurriyet.com.tr/sporarena/video-galeri/"
        )
        private val VIDEO_PATH          = Regex("""(?:/videolar/|/video-).+-\d+$""")
        private val DAILYMOTION_VIDEO_ID = Regex("""data-videoid="([a-zA-Z0-9]+)"""")
        private val DAILYMOTION_URL      = Regex("""dailymotion\.com/(?:embed/)?video/([a-zA-Z0-9]+)""")
        private val DURATION             = Regex(""""?duration"?\s*:\s*"?(\d+)""")
    }
}
