package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BeyazTV : MainAPI() {
    override var mainUrl              = "https://www.beyaztv.com.tr"
    override var name                 = "Beyaz TV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/program" to "Programlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = parsePrograms(document)
        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun parsePrograms(document: Document): List<SearchResponse> {
        return document.select("div.card-item").mapNotNull { card ->
            val anchor = card.selectFirst("a[href^=/program/]") ?: return@mapNotNull null
            val href   = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
            if (href.substringAfter("/program/").trim('/').contains("/")) return@mapNotNull null

            val title = card.selectFirst("h3")?.text()?.trim()
                ?.ifBlank { anchor.attr("title").trim() }
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/program").document
        return parsePrograms(document).filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val path     = url.substringAfter("/program/").trim('/')
        if (path.contains("/")) return loadEpisode(url, document)
        return loadSeries(url, document)
    }

    private suspend fun loadSeries(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val plot  = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?.takeIf { it.isNotBlank() }

        val seriesUrl = url.trimEnd('/')
        val episodes  = document.select("div.card-item").mapNotNull { card ->
            val anchor = card.selectFirst("a[href^=/program/]") ?: return@mapNotNull null
            val href   = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
            if (!href.startsWith("$seriesUrl/")) return@mapNotNull null

            val name = anchor.attr("title").trim()
                .ifBlank { card.selectFirst("h3")?.text()?.trim().orEmpty() }
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            newEpisode(href) { this.name = name }
        }.distinctBy { it.data }

        if (episodes.isEmpty()) return null

        val posterUrl = fixUrlNull(
            document.selectFirst("div.card-item img[src*=tvprogram]")?.attr("src")
                ?: document.selectFirst("div.card-item img")?.attr("src")
        )

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = posterUrl
            this.plot      = plot
        }
    }

    private suspend fun loadEpisode(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val videoId = Regex("""video:\s*["']([^"']+)["']""").find(document.html())
            ?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: return null
        val posterUrl = fixUrlNull(document.selectFirst("img[src*=tvprogram]")?.attr("src"))
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?.takeIf { it.isNotBlank() }

        return newMovieLoadResponse(
            title, url, TvType.Movie, "https://www.dailymotion.com/video/$videoId"
        ) {
            this.posterUrl = posterUrl
            this.plot      = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val videoId = when {
            data.contains("dailymotion.com/video/") -> data.substringAfter("dailymotion.com/video/").substringBefore("?")
            else -> {
                val page = runCatching { app.get(data).text }.getOrNull() ?: return false
                Regex("""video:\s*["']([^"']+)["']""").find(page)?.groupValues?.get(1) ?: return false
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
                name = "$name Dailymotion",
                url = stream,
                type = ExtractorLinkType.M3U8
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
