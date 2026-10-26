package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URLEncoder

class KanalD : MainAPI() {
    override var mainUrl = "https://www.kanald.com.tr"
    override var name = "KanalD"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Güncel Diziler",
        "$mainUrl/diziler/arsiv" to "Arşiv Dizileri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        val document = app.get(request.data).document
        val items = document.select("section.listing-holder a.poster-card")
            .mapNotNull { card ->
                val href = fixUrlNull(card.attr("href")) ?: return@mapNotNull null
                val image = card.selectFirst("img")
                val title = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: card.attr("title").trim().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val poster = fixUrlNull(
                    image?.attr("data-src")?.ifBlank { image.attr("src") }
                )
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val endpoint = "$mainUrl/actions/search?f=Title,TitleWithoutHypen" +
            "&keyword=${URLEncoder.encode(query, "UTF-8")}" +
            "&top=30&skip=0" +
            "&contentTypes=SerieContainer,TVShowContainer,MovieContainer" +
            "&orderBy=StartDate%20-1"
        val response = app.get(
            endpoint,
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).text
        val items = runCatching { JSONObject(response).getJSONObject("data").getJSONArray("items") }
            .getOrNull() ?: return emptyList()
        val results = mutableListOf<SearchResponse>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val href = fixUrlNull(item.optString("url")) ?: continue
            val title = item.optString("title").trim().ifBlank { href.substringAfterLast("/") }
            val poster = item.optJSONArray("files")
                ?.optJSONObject(0)
                ?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?.let { fixUrlNull(it) }
            results.add(
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            )
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" İzle")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val seriesPath = url.substringAfter(mainUrl).trim('/').substringBefore("/")
        val episodeRegex = Regex("""/$seriesPath/bolumler/.*?-(\d+)-bolum""")
        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()

        for (page in 1..3) {
            val listingUrl = if (page == 1) "$mainUrl/$seriesPath/bolumler"
            else "$mainUrl/$seriesPath/bolumler?page=$page"
            val listing = runCatching { app.get(listingUrl).document }.getOrNull() ?: break
            val links = listing.select("a[href*='/bolumler/']")
            var added = 0
            for (link in links) {
                val episodeUrl = fixUrlNull(link.attr("href")) ?: continue
                if (!seen.add(episodeUrl)) continue
                val number = episodeRegex.find(episodeUrl)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                added++
                episodes.add(
                    newEpisode(episodeUrl) {
                        this.name = "$number. Bölüm"
                        this.episode = number
                        this.posterUrl = poster
                    }
                )
            }
            if (added == 0) break
        }

        episodes.sortBy { it.episode ?: 0 }

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
        val hls = Regex(""""contentUrl"\s*:\s*"([^"]+\.m3u8[^"]*)"""")
            .find(html)?.groupValues?.get(1)
            ?: Regex("""(https://kanaldvod\.duhnet\.tv/hls/[^"'\s]+playlist\.m3u8)""")
                .find(html)?.groupValues?.get(1)

        if (hls.isNullOrBlank()) return false

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
}
