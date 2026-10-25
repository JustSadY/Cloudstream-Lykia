@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.lykia

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class DiziGom : MainAPI() {
    override var mainUrl = "https://www.dizigom.news"
    override var name = "DiziGom"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper by lazy { ObjectMapper() }

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Ana Sayfa",
        "${mainUrl}/filmler" to "Filmler",
        "${mainUrl}/dizi-izle/" to "Diziler",
        "${mainUrl}/platform/netflix" to "Netflix",
        "${mainUrl}/platform/disney" to "Disney+",
        "${mainUrl}/platform/exxen" to "Exxen",
        "${mainUrl}/platform/blutv" to "BluTV",
        "${mainUrl}/platform/amazon-prime" to "Prime Video",
        "${mainUrl}/platform/hbo-max" to "HBO Max",
        "${mainUrl}/platform/apple-tv" to "Apple TV+",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val separator = if (request.data.contains("?")) "&" else "?"
        val url = if (page <= 1) request.data else "${request.data}${separator}page=$page"
        val document = app.get(url).document
        val home = document.select("a.group.block").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        if (!href.contains("/film/") && !href.contains("/diziler/")) return null

        val img = this.selectFirst("img")
        val title = this.selectFirst("h3")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: img?.attr("alt")?.substringBefore(" izle")?.substringBefore(" Dizi")?.trim()
            ?: return null
        val posterUrl = fixUrlNull(img?.attr("src") ?: img?.attr("data-src"))
        val year = this.selectFirst("p")?.text()?.trim()?.take(4)?.toIntOrNull()
        val score = this.selectFirst("span.badge-rating")?.text()?.trim()
            ?.let { Regex("""\d+(?:\.\d+)?""").find(it)?.value }

        return if (href.contains("/diziler/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = Score.from10(score)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = app.get("${mainUrl}/ara?q=${encoded}").document

        return document.select("a.group.block").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val item = extractJsonLd(document) ?: return null
        val type = item.get("@type")?.asText() ?: return null

        val title = item.get("name")?.asText()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val poster = fixUrlNull(item.get("image")?.asText())
        val plot = item.get("description")?.asText()?.trim()
        val year = item.get("datePublished")?.asText()?.take(4)?.toIntOrNull()
        val tags = item.get("genre")?.mapNotNull { it.asText() } ?: emptyList()
        val score = item.get("aggregateRating")?.get("ratingValue")?.asText()
        val actors = item.get("actor")?.mapNotNull { it.get("name")?.asText() } ?: emptyList()
        val trailer = item.get("trailer")?.get("embedUrl")?.asText()
        val duration = Regex("""PT(\d+)M""").find(item.get("duration")?.asText() ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()

        if (type == "TVSeries") {
            val episodes = document.select("a[href*='-sezon-'][href*='-bolum']").mapNotNull {
                val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val match = Regex("""-(\d+)-sezon-(\d+)-bolum""").find(epHref)
                    ?: return@mapNotNull null
                val name = it.selectFirst("img")?.attr("alt")?.trim()?.takeIf { n -> n.isNotBlank() }
                    ?: it.selectFirst("h3, h4")?.text()?.trim()
                    ?: "Bölüm ${match.groupValues[2]}"

                newEpisode(epHref) {
                    this.name = name
                    this.season = match.groupValues[1].toIntOrNull()
                    this.episode = match.groupValues[2].toIntOrNull()
                }
            }.distinctBy { it.data }

            if (episodes.isEmpty()) return null

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.score = Score.from10(score)
                addActors(actors.map { Actor(it) })
                addTrailer(trailer)
            }
        }

        val watchUrl = item.get("potentialAction")?.get("target")?.asText()
            ?: "${url.trimEnd('/')}/izle"

        return newMovieLoadResponse(title, url, TvType.Movie, watchUrl) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = tags
            this.score = Score.from10(score)
            this.duration = duration
            addActors(actors.map { Actor(it) })
            addTrailer(trailer)
        }
    }

    private fun extractJsonLd(document: Document): JsonNode? {
        for (script in document.select("script[type=application/ld+json]")) {
            val root = runCatching { mapper.readTree(script.data()) }.getOrNull() ?: continue
            val graph = root.get("@graph") ?: root
            val nodes = if (graph.isArray) graph.toList() else listOf(graph)
            nodes.firstOrNull { it.get("@type")?.asText() in setOf("Movie", "TVSeries") }
                ?.let { return it }
        }
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = "${mainUrl}/").document
        val slug = document.selectFirst("[data-pv]")?.attr("data-pv")

        if (slug.isNullOrBlank()) {
            val iframe = document.selectFirst("iframe[src]")?.attr("src") ?: return false
            return loadExtractor(fixUrl(iframe), "${mainUrl}/", subtitleCallback, callback)
        }

        val playerUrl = "https://pilavyerplay.top/assets/js/s.php?s=$slug"
        val playerPage = app.get(playerUrl, referer = data).text
        val rawStream = Regex("""https://pilavyerplay\.top/api/stream\.php\?[^"']+""")
            .find(playerPage)?.value ?: return false
        val stream = rawStream
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\/", "/")

        callback.invoke(
            newExtractorLink(
                source = "Pilavyer",
                name = "DiziGom",
                url = stream,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = playerUrl
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
