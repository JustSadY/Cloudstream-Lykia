package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Dizigecesi : MainAPI() {
    override var mainUrl = "https://dizigecesi.com"
    override var name = "Dizigecesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Popüler Diziler",
        "${mainUrl}/filmler" to "Yeni Filmler",
        "${mainUrl}/trend-diziler" to "Trend Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) request.data else "${request.data}?page=${page}"
        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = fixUrlNull(element.attr("href")) ?: return null
        if (!href.contains("/dizi/") && !href.contains("/film/")) return null

        val img = element.selectFirst("img")
        val title = img?.attr("alt")?.trim()
            ?.ifBlank { null }
            ?: element.text().trim().ifBlank { null }
            ?: return null

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        val isMovie = href.contains("/film/")
        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val targetUrl = if (page <= 1) {
            "${mainUrl}/?s=${query}"
        } else {
            "${mainUrl}/page/${page}/?s=${query}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("a:has(img)")
        val results = elements.mapNotNull { toSearchResult(it) }
        return dedupSearchResults(results)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val isMovie = url.contains("/film/")
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("izle").substringBefore("-").trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("div.series-info-detail__image img, img.cover")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
        )

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.series-info-detail__content, div.description, p")?.text()?.trim()

        if (isMovie) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        val episodeLinks = document.select("a[href*='-sezon/']").filter { a ->
            a.attr("href").contains("-bolum")
        }

        val seasonRegex = Regex("""(\d+)-sezon/(\d+)-bolum""")
        val episodes = episodeLinks.mapNotNull { a ->
            val epHref = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val match = seasonRegex.find(epHref)
            val seasonNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = "${seasonNum}. Sezon ${epNum}. Bölüm"
                this.season = seasonNum
                this.episode = epNum
            }
        }.distinctBy { it.data }

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
        var found = false
        val document = app.get(data).document

        val embedIds = mutableSetOf<String>()
        document.select("[data-embed]").forEach { el ->
            val id = el.attr("data-embed").trim()
            if (id.isNotBlank() && id.all { it.isDigit() }) {
                embedIds.add(id)
            }
        }

        val embedCandidates = embedIds.toList()

        val count = resolveProgressive(
            candidates = embedCandidates,
            resolver = { embedId, emitLink ->
                try {
                    val postResponse = app.post(
                        "${mainUrl}/ajax/embed",
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to data,
                            "Origin" to mainUrl,
                            "Accept" to "*/*"
                        ),
                        data = mapOf("id" to embedId)
                    ).text

                    val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(postResponse)?.groupValues?.get(1)
                    if (!iframeSrc.isNullOrBlank()) {
                        val resolvedBridgeUrl = fixUrl(iframeSrc)
                        val bridgeDoc = app.get(
                            resolvedBridgeUrl,
                            headers = mapOf(
                                "Referer" to data,
                                "Origin" to mainUrl,
                                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                                "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
                                "Sec-Fetch-Dest" to "iframe",
                                "Sec-Fetch-Site" to "cross-site",
                                "Sec-Fetch-Mode" to "navigate",
                                "Sec-Fetch-User" to "?1",
                                "Upgrade-Insecure-Requests" to "1"
                            )
                        ).document

                        val finalIframe = bridgeDoc.selectFirst("iframe#main-iframe, iframe")?.attr("src")
                            ?: Regex("""src=["']([^"']+)["']""").find(bridgeDoc.html())?.groupValues?.get(1)

                        if (!finalIframe.isNullOrBlank()) {
                            val resolvedFinalUrl = fixUrl(finalIframe)
                            loadExtractor(resolvedFinalUrl, resolvedBridgeUrl, subtitleCallback, emitLink)
                        }
                    }
                } catch (e: Exception) {

                }
            },
            onLinkFound = { link ->
                found = true
                callback(link)
            }
        )

        return found || count > 0
    }
}
