package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class ShowTV : MainAPI() {
    override var mainUrl = "https://www.showtv.com.tr"
    override var name = "ShowTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val document = app.get(request.data).document
        val items = document.select("a[href*='/dizi/tanitim/']")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items, false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("/dizi/tanitim/")) return null
        val title = attr("title").trim()
            .ifBlank { selectFirst("span")?.text()?.trim() ?: return null }
        val image = selectFirst("img")
        val poster = fixUrlNull(image?.attr("data-src")?.ifBlank { image.attr("src") })
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama?ara=${URLEncoder.encode(query, "UTF-8")}").document
        return document.select("ul#search-grid-series a[href*='/dizi/tanitim/']")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" | ")?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val slug = url.substringAfter("/dizi/tanitim/").substringBefore("/")
        val sitemap = runCatching { app.get("$mainUrl/sitemap_$slug.xml").text }.getOrDefault("")
        val locationRegex = Regex("""<loc>https://www\.showtv\.com\.tr/dizi/tum_bolumler/([^<]+)</loc>""")
        val episodeRegex = Regex("""-sezon-(\d+)-bolum-(\d+)""")
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()

        locationRegex.findAll(sitemap).forEach { match ->
            val path = match.groupValues[1]
            val episodeUrl = "$mainUrl/dizi/tum_bolumler/$path"
            if (!seen.add(episodeUrl)) return@forEach
            val numbers = episodeRegex.find(path) ?: return@forEach
            val season = numbers.groupValues[1].toIntOrNull() ?: 1
            val episode = numbers.groupValues[2].toIntOrNull() ?: 1
            episodes.add(
                newEpisode(episodeUrl) {
                    this.name = "$season. Sezon $episode. Bölüm"
                    this.season = season
                    this.episode = episode
                    this.posterUrl = poster
                }
            )
        }

        if (episodes.isEmpty()) {
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                this.posterUrl = poster
                this.plot = description
            }
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

        val escapedHls = Regex("""&quot;m3u8&quot;:\s*\[\s*\{[^}]*?&quot;src&quot;:\s*&quot;([^&]+)&quot;""")
            .find(html)?.groupValues?.get(1)
        val plainHls = Regex(""""m3u8"\s*:\s*\[\s*\{[^}]*?"src"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1)
        val jsonLdHls = Regex(""""contentUrl"\s*:\s*"([^"]+\.m3u8[^"]*)"""")
            .find(html)?.groupValues?.get(1)
        val hls = escapedHls ?: plainHls ?: jsonLdHls

        if (!hls.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name HLS",
                    url = hls,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        val escapedMp4 = Regex("""&quot;mp4&quot;:\s*\[\s*\{[^}]*?&quot;src&quot;:\s*&quot;([^&]+)&quot;""")
            .find(html)?.groupValues?.get(1)
        val plainMp4 = Regex(""""mp4"\s*:\s*\[\s*\{[^}]*?"src"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1)
        val mp4 = escapedMp4 ?: plainMp4

        if (!mp4.isNullOrBlank()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = mp4,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P480.value
                }
            )
            return true
        }

        return false
    }
}
