package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TV2 : MainAPI() {
    override var mainUrl = "https://www.tv2.com.tr"
    override var name = "TV2"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Güncel Diziler",
        "$mainUrl/diziler/arsiv" to "Dizi Arşivi",
        "$mainUrl/filmler" to "Güncel Filmler",
        "$mainUrl/filmler/arsiv" to "Film Arşivi",
        "$mainUrl/programlar" to "Güncel Programlar",
        "$mainUrl/programlar/arsiv" to "Program Arşivi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page == 1) request.data else "${request.data}?page=$page"
        val document = app.get(pageUrl).document
        val items = parseCards(document)
        val hasNext = if (request.data.endsWith("/arsiv")) page < maxPage(document) else false
        return newHomePageResponse(request.name, items, hasNext = hasNext)
    }

    private fun maxPage(document: Document): Int =
        document.select("a[href*=page=]").mapNotNull { anchor ->
            Regex("""[?&]page=(\d+)""").find(anchor.attr("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1

    private fun parseCards(document: Document): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        for (card in document.select("div.program-card")) {
            val anchor = card.selectFirst("div.program-card-footer a[href]")
                ?: card.selectFirst("div.program-detail a[href]")
                ?: continue
            val href = fixUrlNull(anchor.attr("href")) ?: continue
            val title = card.selectFirst("div.program-card-footer div.title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: card.selectFirst("div.detail-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: continue
            val style = card.selectFirst("div.program-image")?.attr("style").orEmpty()
            val poster = fixUrlNull(
                Regex("""url\(([^)]+)\)""").find(style)?.groupValues?.get(1)
                    ?.trim(' ', '\'', '"')
            )
            items.add(cardResult(title, href, poster))
        }
        for (card in document.select("a.thumbnail[href]")) {
            val href = fixUrlNull(card.attr("href")) ?: continue
            val image = card.selectFirst("img")
            val title = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: card.selectFirst("div.title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: continue
            val poster = fixUrlNull(
                image?.attr("data-src")?.ifBlank { image.attr("src") }
            )
            items.add(cardResult(title, href, poster))
        }
        return items.distinctBy { it.url }
    }

    private fun cardResult(title: String, href: String, poster: String?): SearchResponse {
        return if (isSingleVideo(href)) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
    }

    private fun isSingleVideo(href: String): Boolean =
        href.contains("/filmler/") ||
            href.contains("/bolumler/") ||
            href.contains("/kisa-klipler/") ||
            href.contains("/fragmanlar/") ||
            href.contains("/videolar/") ||
            href.contains("/foto-galeri/")

    private fun isSeriesUrl(url: String): Boolean =
        Regex("""/(?:diziler|programlar)/(?:guncel|arsiv)/[^/?#]+/?$""").containsMatchIn(url) ||
            (url.contains("/diziler/") || url.contains("/programlar/")) && url.endsWith("/bolumler")

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/arama", params = mapOf("q" to query)).document
        return parseCards(document)
    }

    override suspend fun load(url: String): LoadResponse? {
        val clean = url.substringBefore("?")
        val document = app.get(clean).document
        if (isSeriesUrl(clean)) return loadSeries(clean, document)
        return loadSingle(clean, document)
    }

    private suspend fun loadSeries(url: String, document: Document): LoadResponse? {
        val detailUrl = url.removeSuffix("/bolumler").removeSuffix("/")
        val baseUrl = "$detailUrl/bolumler"
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h2.title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val firstPage = app.get(baseUrl).document
        val pages = mutableListOf(firstPage)
        val lastPage = maxPage(firstPage)
        val pageNumbers = when {
            lastPage <= 1 -> emptyList()
            lastPage <= 60 -> (2..lastPage).toList()
            else -> ((lastPage - 29)..lastPage).toList()
        }
        if (pageNumbers.isNotEmpty()) {
            coroutineScope {
                for (chunk in pageNumbers.chunked(8)) {
                    val fetched = chunk.map { page ->
                        async {
                            runCatching { app.get("$baseUrl?page=$page").document }.getOrNull()
                        }
                    }.awaitAll()
                    pages.addAll(fetched.filterNotNull())
                }
            }
        }

        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()
        for (pageDocument in pages) {
            for (anchor in pageDocument.select("a.thumbnail[href*=/bolumler/]")) {
                val href = fixUrlNull(anchor.attr("href")) ?: continue
                if (!seen.add(href)) continue
                val image = anchor.selectFirst("img")
                val episodeTitle = image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: anchor.selectFirst("div.title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: continue
                if (episodeTitle.contains("Fragman", true)) continue
                val number = Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE)
                    .find(episodeTitle)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""-(\d+)-bolum""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                    ?: continue
                val episodePoster = fixUrlNull(
                    image?.attr("data-src")?.ifBlank { image.attr("src") }
                ) ?: poster
                episodes.add(
                    newEpisode(href) {
                        this.name = episodeTitle
                        this.season = 1
                        this.episode = number
                        this.posterUrl = episodePoster
                    }
                )
            }
        }

        if (episodes.isEmpty()) return null
        episodes.sortWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
        return newTvSeriesLoadResponse(title, detailUrl, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    private suspend fun loadSingle(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.title().trim().takeIf { it.isNotBlank() }
            ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        var stream = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeStream(it) }

        if (stream == null) {
            val embed = Regex("""https?://(?:www\.)?(?:kanald|tv2)\.com\.tr/embed/[^"'\s<>]+""")
                .find(html)?.value
            if (embed != null) {
                val embedUrl = if (embed.contains("kanald.com.tr")) {
                    embed
                } else {
                    embed.replace("tv2.com.tr", "kanald.com.tr")
                }
                val embedHtml = runCatching { app.get(embedUrl).text }.getOrNull()
                if (embedHtml != null) {
                    stream = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
                        .find(embedHtml)?.groupValues?.get(1)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { normalizeStream(it) }
                }
            }
        }

        val streamUrl = stream ?: return false
        callback.invoke(
            newExtractorLink(
                source = name,
                name = "$name HLS",
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    private fun normalizeStream(raw: String): String {
        val url = raw.trim()
        return when {
            url.startsWith("http") -> url.replace(".tvS1/", ".tv/S1/")
            url.startsWith("//") -> "https:$url"
            else -> "https://tv2vod.duhnet.tv/" + url.trimStart('/')
        }
    }
}
