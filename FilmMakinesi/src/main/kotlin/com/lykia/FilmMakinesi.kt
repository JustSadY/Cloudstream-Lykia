package com.lykia

import android.util.Log
import org.jsoup.nodes.Element
import kotlinx.coroutines.runBlocking
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.to"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override var sequentialMainPage            = true 
    override var sequentialMainPageDelay       = 50L  
    override var sequentialMainPageScrollDelay = 50L  

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler-1/"                                to "Son Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler-fm1/" to "Ölmeden İzle",
        "${mainUrl}/tur/aksiyon-fmy54y/film/"                  to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu-fmy58w/film/"              to "Bilim Kurgu",
        "${mainUrl}/tur/macera-fm1/film/"                      to "Macera",
        "${mainUrl}/tur/komedi-fma6cz/film/"                   to "Komedi",
        "${mainUrl}/tur/romantik-fm1/film/"                    to "Romantik",
        "${mainUrl}/tur/belgesel/film/"                        to "Belgesel",
        "${mainUrl}/tur/fantastik-fm1/film/"                   to "Fantastik",
        "${mainUrl}/tur/polisiye/film/"                        to "Polisiye",
        "${mainUrl}/tur/korku-fm2/film/"                       to "Korku",
        "${mainUrl}/tur/gerilim-fm1/film/"                     to "Gerilim",
        "${mainUrl}/tur/dram-fm1/film/"                        to "Dram",
        "${mainUrl}/tur/animasyon-fm2/film/"                   to "Animasyon",
        "${mainUrl}/tur/aile-fm2/film/"                        to "Aile"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page == 1) request.data else "${request.data}sayfa/${page}/"
        val document = app.get(url).document
        val home     = document.select("a.item[href*=/film/]").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.attr("data-title").ifBlank { this.selectFirst("div.title")?.text() ?: "" }.trim()
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        if (title.isEmpty()) return null

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score     = Score.from10(this@toSearchResult.attr("data-score"))
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/", params = mapOf("s" to query)).document

        return document.select("a.item[href*=/film/]").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1.title")?.ownText()?.trim()?.removeSuffix(" izle")?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description     = document.selectFirst("div.info-description")?.text()?.trim()
        val year            = document.selectFirst("h1.title span.date a")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("div.info div.type a").map { it.text().trim() }
        val rating          = document.selectFirst("div.info div.imdb b")?.text()?.trim()
        val duration        = document.selectFirst("div.info div.time")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val recommendations = document.select("a.item[href*=/film/]").mapNotNull { it.toSearchResult() }.filter { it.url != url }
        val trailer         = document.select("iframe[data-src*=youtube]").attr("data-src").ifBlank { null }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.score           = Score.from10(rating)
            this.duration        = duration
            this.recommendations = recommendations
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMM", "data » $data")
        val document = app.get(data).document

        val kaynaklar = document.select("a[data-video_url]").map { it.text().trim() to it.attr("data-video_url") }
            .ifEmpty { document.select("iframe[data-src*=closeload]").map { "" to it.attr("data-src") } }

        var found = false
        for ((etiket, iframe) in kaynaklar) {
            Log.d("FLMM", "$etiket » $iframe")
            val loaded = loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                runBlocking {
                    callback.invoke(
                        newExtractorLink(
                            source = link.source,
                            name   = listOf(etiket, link.name).filter { it.isNotEmpty() }.joinToString(" - "),
                            url    = link.url,
                            type   = link.type
                        ) {
                            this.referer       = link.referer
                            this.quality       = link.quality
                            this.headers       = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
            }
            if (loaded) found = true
        }

        return found
    }
}
