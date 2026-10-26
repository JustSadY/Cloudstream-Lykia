package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Izle7 : MainAPI() {
    override var mainUrl = "https://www.izle7.com"
    override var name = "Izle7"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/kanal7/diziler" to "Diziler",
        "$mainUrl/kanal7/filmler" to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
        val document = app.get(request.data).document
        val items = if (request.data.endsWith("/diziler")) {
            document.select("a.category-item[href*=/kategori/]").mapNotNull { it.toSeriesResult() }
        } else {
            document.select("div.category-item").mapNotNull { it.toMovieResult() }
        }
        return newHomePageResponse(request.name, items.distinctBy { it.url }, hasNext = false)
    }

    private fun Element.toSeriesResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        val name = attr("title").ifBlank {
            selectFirst("div.category-item-title")?.text().orEmpty()
        }.trim()
        if (name.isBlank()) return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-original")?.ifBlank { selectFirst("img")?.attr("src") }
        )
        return newTvSeriesSearchResponse(name, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    private fun Element.toMovieResult(): SearchResponse? {
        val anchor = selectFirst("a.category-item[href*=/izle-]") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val name = anchor.attr("title").ifBlank {
            anchor.selectFirst("div.category-item-title")?.text().orEmpty()
        }.trim()
        if (name.isBlank()) return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-original")?.ifBlank { selectFirst("img")?.attr("src") }
        )
        return newMovieSearchResponse(name, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("q" to query)).document
        return document.select("div.category-item").mapNotNull { card ->
            val anchor = card.selectFirst("a.category-item") ?: return@mapNotNull null
            val href = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
            val name = anchor.attr("title").ifBlank {
                anchor.selectFirst("div.category-item-title")?.text().orEmpty()
            }.trim()
            if (name.isBlank()) return@mapNotNull null
            val poster = fixUrlNull(
                card.selectFirst("img")?.attr("data-original")
                    ?: card.selectFirst("img")?.attr("src")
            )
            if (href.contains("/kategori/")) {
                newTvSeriesSearchResponse(name, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(name, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return when {
            url.contains("/kategori/") -> loadSeries(url, document)
            url.contains("/izle-") -> loadMovie(url, document)
            else -> null
        }
    }

    private suspend fun loadSeries(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
            .removeSuffix(" Dizisi").trim()
            .ifBlank { document.title().substringBefore(" Kategorisindeki").trim() }
            .ifBlank { return null }
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val poster = fixUrlNull(
            document.selectFirst("div.category-item img")?.attr("data-original")
                ?: document.selectFirst("div.category-item img")?.attr("src")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()
        val pages = mutableListOf(document)
        val categoryUrl = url.substringBefore("?")
        val maxPage = document.select("div.pagination a.pagination-item")
            .mapNotNull { anchor ->
                Regex("""[?&]page=(\d+)""").find(anchor.attr("href"))
                    ?.groupValues?.get(1)?.toIntOrNull()
            }
            .maxOrNull() ?: 1
        if (maxPage > 1) {
            val extraUrls = (2..maxPage).take(39).map { "$categoryUrl?page=$it" }
            coroutineScope {
                for (chunk in extraUrls.chunked(6)) {
                    val fetched = chunk.map { pageUrl ->
                        async { runCatching { app.get(pageUrl).document }.getOrNull() }
                    }.awaitAll()
                    pages.addAll(fetched.filterNotNull())
                }
            }
        }
        for (current in pages) {
            for (card in current.select("div.category-item")) {
                val anchor = card.selectFirst("a.category-item") ?: continue
                val href = fixUrlNull(anchor.attr("href"))?.substringBefore("?") ?: continue
                if (!seen.add(href)) continue
                val name = anchor.attr("title").ifBlank {
                    anchor.selectFirst("div.category-item-title")?.text().orEmpty()
                }.trim()
                if (name.isBlank() || name.contains("Fragman", true)) continue
                val number = Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE)
                    .find(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val episodePoster = fixUrlNull(
                    card.selectFirst("img")?.attr("data-original")
                        ?: card.selectFirst("img")?.attr("src")
                ) ?: poster
                episodes.add(
                    newEpisode(href) {
                        this.name = name
                        this.season = 1
                        this.episode = number
                        this.posterUrl = episodePoster
                    }
                )
            }
        }

        if (episodes.isEmpty()) return null
        episodes.sortWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    private suspend fun loadMovie(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.removeSuffix(" - izle7")?.trim()
            ?: document.title().removeSuffix(" - izle7").trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val embed = Regex("""https?://(?:www\.)?izle7\.com/embed-\d+""")
            .find(document.html())?.value
        return newMovieLoadResponse(title, url, TvType.Movie, embed ?: url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val embedUrl = if (data.contains("/embed-")) {
            data
        } else {
            val html = runCatching { app.get(data).text }.getOrNull() ?: return false
            Regex("""https?://(?:www\.)?izle7\.com/embed-\d+""").find(html)?.value ?: return false
        }
        val embed = runCatching { app.get(embedUrl, referer = "$mainUrl/").text }.getOrNull()
            ?: return false
        val videoId = Regex("""play_video\s*=\s*["']([^"']+)["']""")
            .find(embed)?.groupValues?.get(1) ?: return false

        var candidate: String? = null
        var candidateCookie: String? = null
        for (attempt in 1..3) {
            val metadata = runCatching {
                app.get(
                    "https://www.dailymotion.com/player/metadata/video/$videoId",
                    referer = "$mainUrl/",
                    cacheTime = 0
                )
            }.getOrNull() ?: continue
            val json = runCatching { JSONObject(metadata.text) }.getOrNull() ?: continue
            val stream = json.optJSONObject("qualities")
                ?.optJSONArray("auto")
                ?.optJSONObject(0)
                ?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?: continue

            val cookies = metadata.cookies
            val ts = queryParam(stream, "dmTs") ?: cookies["ts"]
            val v1st = queryParam(stream, "dmV1st") ?: cookies["v1st"]
            val dmvk = cookies["dmvk"] ?: "1"
            val cookieHeader = listOfNotNull(
                ts?.let { "ts=$it" },
                v1st?.let { "v1st=$it" },
                "dmvk=$dmvk"
            ).joinToString("; ")

            candidate = stream
            candidateCookie = cookieHeader
            val probe = runCatching {
                app.get(
                    stream,
                    headers = mapOf(
                        "Origin" to "https://www.dailymotion.com",
                        "Accept" to "*/*",
                        "Cookie" to cookieHeader
                    ),
                    referer = "https://www.dailymotion.com/",
                    cacheTime = 0
                ).code
            }.getOrNull()
            if (probe != null && probe in 200..299) break
        }

        val stream = candidate ?: return false
        val cookieHeader = candidateCookie ?: return false

        callback.invoke(
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
                    if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
                }
            }
        )
        return true
    }

    private fun queryParam(url: String, key: String): String? =
        Regex("""[?&]$key=([^&]+)""").find(url)?.groupValues?.get(1)
}
