package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziRella : MainAPI() {
    override var mainUrl = "https://dizirella.one"
    override var name = "DiziRella"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)
    override val hasChromecastSupport = true

    override val mainPage = mainPageOf(
        "$mainUrl/tum-diziler/" to "Tüm Diziler",
        "$mainUrl/asya-dizileri/" to "Asya Dizileri",
        "$mainUrl/tur/dram/" to "Dram",
        "$mainUrl/tur/gerilim/" to "Gerilim",
        "$mainUrl/tur/komedi/" to "Komedi",
        "$mainUrl/tur/romantik/" to "Romantik",
        "$mainUrl/tur/suc/" to "Suç",
        "$mainUrl/tur/gizem/" to "Gizem",
        "$mainUrl/tur/fantastik/" to "Fantastik",
        "$mainUrl/tur/bilim-kurgu/" to "Bilim Kurgu",
        "$mainUrl/tur/aksiyon/" to "Aksiyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document
        val items = document.select("a.r-card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("/dizi/")) return null
        val title = selectFirst("img")?.attr("alt")?.trim()
            ?.removeSuffix(" izle")
            ?.takeIf { it.isNotBlank() }
            ?: selectFirst(".r-cap-t")?.text()?.trim()
            ?: return null
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${query}").document
        return document.select("div.single-item").mapNotNull { it.toSearchItem() }
    }

    private fun Element.toSearchItem(): SearchResponse? {
        val href = fixUrlNull(selectFirst(".cat-img a, a")?.attr("href")) ?: return null
        if (!href.contains("/dizi/")) return null
        val title = selectFirst(".categorytitle a")?.text()?.trim() ?: return null
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim()?.removeSuffix(" izle") ?: return null
        val poster = fixUrlNull(document.selectFirst(".r-dposter img")?.attr("src"))
        val plot = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val year = document.selectFirst(".r-meta span:not(.r-imdb)")?.text()?.toIntOrNull()
        val tags = document.select("a.r-chip:not(.r-chip-yil)").map { it.text().trim() }
        val score = document.selectFirst(".r-imdb")?.text()?.replace("IMDb", "")?.trim()

        val episodes = document.select("a[href*=sezon][href$=-bolum/]").mapNotNull { ep ->
            val epUrl = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            val match = Regex("""(\d+)-sezon-(\d+)-bolum""").find(epUrl) ?: return@mapNotNull null
            val season = match.groupValues[1].toIntOrNull() ?: 1
            val episode = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            newEpisode(epUrl) {
                this.name = "${season}. Sezon ${episode}. Bölüm"
                this.season = season
                this.episode = episode
            }
        }.distinctBy { it.data }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            this.score = Score.from10(score)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val frames = document.select("section#episode iframe[src], .video-container iframe[src]")
            .map { it.attr("src") }
            .filter { it.isNotBlank() && !it.contains("youtube.com") && !it.contains("youtu.be") }
            .distinct()
        var found = false
        for (frame in frames) {
            val url = fixUrl(frame)
            if (url.contains("ksdpictures.site") || url.contains("dzembed.php")) {
                try {
                    val html = app.get(url, referer = data).text
                    val source = Regex("""var\s+SOURCE\s*=\s*["']([^"']+)["']""")
                        .find(html)?.groupValues?.get(1)
                    if (!source.isNullOrBlank()) {
                        callback(
                            newExtractorLink(name, name, source, ExtractorLinkType.M3U8) {
                                this.referer = url
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                    Regex("""\{"file":"([^"]+\.vtt)"[^}]*?"label":"([^"]+)"""")
                        .findAll(html).forEach { m ->
                            subtitleCallback(SubtitleFile(m.groupValues[2], m.groupValues[1]))
                        }
                } catch (_: Exception) {
                }
            } else {
                if (loadExtractor(url, data, subtitleCallback, callback)) found = true
            }
        }
        return found
    }
}
