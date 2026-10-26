package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class TV8 : MainAPI() {
    override var mainUrl = "https://www.tv8.com.tr"
    override var name = "TV8"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/programlar" to "Programlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val document = app.get(request.data).document
        val items = document.select("div.list-type-one-content a[href]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items, false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        val title = attr("title").trim()
            .ifBlank { selectFirst("div.list-type-one-title")?.text()?.trim() ?: return null }
        val image = selectFirst("img")
        val poster = fixUrlNull(image?.attr("data-original")?.ifBlank { image.attr("src") })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val normalized = query.lowercase()
        val document = app.get("$mainUrl/programlar").document
        return document.select("div.list-type-one-content a[href]")
            .mapNotNull { it.toSearchResult() }
            .filter { it.name.lowercase().contains(normalized) }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" | ")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val tabId = document.select("a.tab")
            .firstOrNull { it.text().contains("Tüm Bölümler", true) }
            ?.attr("data-tab")
            ?.takeIf { it.isNotBlank() }
        val container = tabId?.let { document.selectFirst("div#$it") } ?: document
        val episodeRegex = Regex("""-(\d+)-bolum""")
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()

        container.select("a[href*='-video.htm']").forEach { link ->
            val episodeUrl = fixUrlNull(link.attr("href")) ?: return@forEach
            if (episodeUrl.contains("tanitim", true) || episodeUrl.contains("fragman", true)) return@forEach
            if (!seen.add(episodeUrl)) return@forEach
            val episodeTitle = link.selectFirst("h3.tile-title")?.text()?.trim()
                ?.ifBlank { link.attr("title").trim() }
                ?.ifBlank { null }
            val number = episodeRegex.find(episodeUrl)?.groupValues?.get(1)?.toIntOrNull()
            val episodePoster = fixUrlNull(
                link.selectFirst("img")?.let { it.attr("data-original").ifBlank { it.attr("src") } }
            ) ?: poster
            episodes.add(
                newEpisode(episodeUrl) {
                    this.name = episodeTitle ?: number?.let { "$it. Bölüm" }
                    this.episode = number
                    this.posterUrl = episodePoster
                }
            )
        }

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
        val html = app.get(data).text
        val hls = Regex("""https://videocdn\.tv8\.com\.tr/[^"'\s<>]+\.m3u8[^"'\s<>]*""")
            .find(html)?.value
        if (!hls.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name HLS",
                    url = hls,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = data
                    this.quality = Qualities.P720.value
                }
            )
            return true
        }

        val mp4 = Regex(""""contenturl"\s*:\s*"([^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        if (!mp4.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = mp4,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                    this.quality = Qualities.P480.value
                }
            )
            return true
        }

        return false
    }
}
