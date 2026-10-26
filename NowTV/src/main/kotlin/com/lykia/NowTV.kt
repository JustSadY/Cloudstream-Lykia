package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

class NowTV : MainAPI() {
    override var mainUrl = "https://www.nowtv.com.tr"
    override var name = "NowTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/dizi-izle" to "Güncel Diziler",
        "$mainUrl/dizi-arsivi" to "Dizi Arşivi",
        "$mainUrl/program-izle" to "Programlar",
        "$mainUrl/program-arsivi" to "Program Arşivi"
    )

    private val ajaxHeaders = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Accept" to "application/json, text/javascript, */*; q=0.01"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = if (page == 1) {
            app.get(request.data).document.select("div.list-item")
                .mapNotNull { it.toSearchResult() }
        } else {
            fetchMore(request, page)
        }
        return newHomePageResponse(
            request.name,
            items.distinctBy { it.url },
            hasNext = hasNextPage(request, page)
        )
    }

    private fun hasNextPage(request: MainPageRequest, page: Int): Boolean {
        return when {
            request.data.endsWith("/dizi-arsivi") -> page < 12
            request.data.endsWith("/program-arsivi") -> page < 6
            else -> false
        }
    }

    private suspend fun fetchMore(request: MainPageRequest, page: Int): List<SearchResponse> {
        val type = when {
            request.data.endsWith("/dizi-arsivi") -> "series"
            request.data.endsWith("/program-arsivi") -> "programs"
            else -> return emptyList()
        }
        val response = runCatching {
            app.post(
                "$mainUrl/ajax/archive",
                data = mapOf(
                    "page" to (page - 1).toString(),
                    "type" to type,
                    "count" to "10",
                    "orderBy" to "id",
                    "sorting" to "desc"
                ),
                headers = ajaxHeaders,
                referer = request.data
            ).text
        }.getOrNull() ?: return emptyList()
        val data = runCatching { JSONObject(response).optString("data") }.getOrDefault("")
        return Jsoup.parse(data).select("div.list-item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("div.list-item-meta a[href$='/izle']")
            ?: selectFirst("a[href$='/izle']")
            ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null
        val title = selectFirst("div.program-name strong")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: selectFirst("div.program-name")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: titleFromUrl(href)
        val image = selectFirst("img")
        val poster = fixUrlNull(
            image?.attr("data-src")?.ifBlank { image.attr("src") }?.takeIf { it.isNotBlank() }
        )
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    private fun titleFromUrl(url: String): String {
        val slug = url.removeSuffix("/izle").substringAfterLast("/")
        val decoded = runCatching { URLDecoder.decode(slug, "UTF-8") }.getOrDefault(slug)
        return decoded.split("-", " ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
    }

    private fun parseSearchSection(document: Document, section: String): List<SearchResponse> {
        val heading = document.select("h2").firstOrNull { it.text().trim() == section } ?: return emptyList()
        val posters = heading.parent()?.nextElementSibling() ?: return emptyList()
        return posters.select("div.poster").mapNotNull { poster ->
            val link = poster.selectFirst("a[href$='/izle']") ?: return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val image = poster.selectFirst("img")
            val posterUrl = fixUrlNull(
                image?.attr("data-src")?.ifBlank { image.attr("src") }?.takeIf { it.isNotBlank() }
            )
            newTvSeriesSearchResponse(titleFromUrl(href), href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = runCatching {
            app.post(
                "$mainUrl/arama",
                data = mapOf("query" to query),
                headers = ajaxHeaders,
                referer = "$mainUrl/"
            ).document
        }.getOrNull() ?: return emptyList()
        return (parseSearchSection(document, "DİZİLER") + parseSearchSection(document, "PROGRAMLAR"))
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val html = document.html()
        val programId = Regex(
            """GOSpockShared\.program\s*=\s*\{[^}]*?id\s*:\s*'(\d+)'""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)
        val isSeries = html.contains("\"@type\": \"TVSeries\"") ||
            html.contains("\"@type\":\"TVSeries\"")
        if (!isSeries || programId == null) return null

        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" | NOW")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = "$mainUrl/i/posters/$programId"
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val seasons = Regex("\"name\"\\s*:\\s*\"(\\d+)\\. Sezon\"")
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct()
            .sorted()
            .toList()
            .ifEmpty { listOf(1) }

        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()
        for (season in seasons) {
            var page = 0
            var total = Int.MAX_VALUE
            while (page * 40 < total) {
                val response = runCatching {
                    app.post(
                        "$mainUrl/ajax/videos",
                        data = mapOf(
                            "filter" to "season",
                            "program_id" to programId,
                            "season" to season.toString(),
                            "page" to page.toString(),
                            "type" to "2",
                            "count" to "40",
                            "orderBy" to "id",
                            "sorting" to "DESC"
                        ),
                        headers = ajaxHeaders,
                        referer = url
                    ).text
                }.getOrNull() ?: break
                val json = runCatching { JSONObject(response) }.getOrNull() ?: break
                total = json.optInt("count", 0)
                val fragment = Jsoup.parse(json.optString("data"))
                val blocks = fragment.select("div.list-item")
                if (blocks.isEmpty()) break
                for (block in blocks) {
                    val link = block.selectFirst("div.list-item-meta a[href*='/bolum/']")
                        ?: block.selectFirst("a[href*='/bolum/']")
                        ?: continue
                    val episodeUrl = fixUrlNull(link.attr("href")) ?: continue
                    if (!seen.add(episodeUrl)) continue
                    val number = episodeUrl.substringAfterLast("/").toIntOrNull()
                    val episodeTitle = block.selectFirst("div.program-name strong")
                        ?.text()?.trim()?.takeIf { it.isNotBlank() }
                        ?: number?.let { "$it. Bölüm" }
                    val image = block.selectFirst("img")
                    val episodePoster = fixUrlNull(
                        image?.attr("data-src")?.ifBlank { image.attr("src") }
                            ?.takeIf { it.isNotBlank() }
                    ) ?: poster
                    episodes.add(
                        newEpisode(episodeUrl) {
                            this.name = episodeTitle
                            this.season = season
                            this.episode = number
                            this.posterUrl = episodePoster
                        }
                    )
                }
                page++
            }
        }

        if (episodes.isEmpty()) return null
        episodes.sortWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        val html = runCatching { app.get(data).text }.getOrNull() ?: return false
        val videoId = Regex("""video_id\s*[:=]\s*['"]?(\d+)""").find(html)?.groupValues?.get(1)
            ?: Regex("""videoId\s*[:=]\s*['"]?(\d+)""").find(html)?.groupValues?.get(1)

        if (videoId != null) {
            val response = runCatching {
                app.post(
                    "$mainUrl/ajax/stream",
                    data = mapOf("video_id" to videoId),
                    headers = ajaxHeaders,
                    referer = data
                ).text
            }.getOrNull() ?: return false
            val stream = runCatching { JSONObject(response).optString("video_url") }.getOrNull()
            if (!stream.isNullOrBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name HLS",
                        url = stream,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P1080.value
                    }
                )
                return true
            }
        }

        val direct = Regex("""https?://[^"'\s<>]+\.(?:m3u8|mp4)[^"'\s<>]*""")
            .find(html)?.value
        if (!direct.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name HLS",
                    url = direct,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }
        return false
    }
}
