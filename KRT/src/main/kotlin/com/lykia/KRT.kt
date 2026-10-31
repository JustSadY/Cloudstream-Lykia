package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class KRT : MainAPI() {
    override var mainUrl              = "https://www.krttv.com.tr"
    override var name                 = "KRT TV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Others)

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Video Galeri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page <= 1) request.data else "${request.data}?sayfa=$page"
        val document = app.get(url).document
        val home     = document.select("a.post-card-image-left[title]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val maxPage = document.select("a[href*=sayfa=]").mapNotNull { anchor ->
            Regex("""sayfa=(\d+)""").find(anchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: page

        return newHomePageResponse(request.name, home, hasNext = page < maxPage)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (href == "$mainUrl/video" || !href.startsWith(mainUrl)) return null

        val title = attr("title").trim().ifBlank {
            selectFirst("span")?.text()?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(Jsoup.parse(title).text(), href, TvType.Others) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document   = app.get("$mainUrl/arama", params = mapOf("key" to query)).document
        val candidates = document.select("a.post-card-image-left[title]")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
            .take(12)

        val found = filterVideos(candidates)
        if (found.isNotEmpty()) return found

        // Site araması bazı sorgularda boş dönüyor; video galerisinde yerel arama yap.
        val fallback = app.get("$mainUrl/video").document
            .select("a.post-card-image-left[title]")
            .mapNotNull { it.toSearchResult() }
            .filter { it.name.contains(query.substringBefore("'"), ignoreCase = true) }

        return filterVideos(fallback.distinctBy { it.url })
    }

    private suspend fun filterVideos(candidates: List<SearchResponse>): List<SearchResponse> {
        if (candidates.isEmpty()) return emptyList()
        return coroutineScope {
            candidates.map { candidate ->
                async { runCatching { hasVideo(candidate.url) }.getOrDefault(false) to candidate }
            }.awaitAll().filter { it.first }.map { it.second }
        }
    }

    private suspend fun hasVideo(url: String): Boolean {
        val html = app.get(url).text
        return html.contains("\"VideoObject\"") || MP4_REGEX.containsMatchIn(html)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val video = document.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { JSONObject(script.data()) }.getOrNull()
        }.firstOrNull { it.optString("@type") == "VideoObject" }

        val rawTitle = video?.optString("name")?.ifBlank { null }
            ?: document.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: return null

        val posterUrl = video?.optString("thumbnailUrl")?.ifBlank { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val plot = video?.optString("description")?.ifBlank { null }

        val stream = MP4_REGEX.find(document.html())?.value
            ?: video?.optString("embedUrl")?.ifBlank { null }?.let { embedUrl ->
                runCatching {
                    MP4_REGEX.find(app.get(embedUrl, referer = url).text)?.value
                }.getOrNull()
            }
            ?: return null

        return newMovieLoadResponse(Jsoup.parse(rawTitle).text(), url, TvType.Others, stream) {
            this.posterUrl = fixUrlNull(posterUrl)
            this.plot      = plot?.let { Jsoup.parse(it).text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            newExtractorLink(
                source = name,
                name   = name,
                url    = data,
                type   = ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    companion object {
        private val MP4_REGEX = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""")
    }
}
