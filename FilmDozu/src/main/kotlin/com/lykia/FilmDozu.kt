package com.lykia

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FilmDozu : MainAPI() {
    override var mainUrl              = "https://filmdozu.com"
    override var name                 = "FilmDozu"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/"                       to "Son Eklenenler",
        "${mainUrl}/dublaj/"                to "Türkçe Dublaj",
        "${mainUrl}/altyazili/"             to "Türkçe Altyazılı",
        "${mainUrl}/kategori/aksiyon/"      to "Aksiyon",
        "${mainUrl}/kategori/animasyon/"    to "Animasyon",
        "${mainUrl}/kategori/bilim-kurgu/"  to "Bilim Kurgu",
        "${mainUrl}/kategori/dram/"         to "Dram",
        "${mainUrl}/kategori/fantastik/"    to "Fantastik",
        "${mainUrl}/kategori/gerilim/"      to "Gerilim",
        "${mainUrl}/kategori/komedi/"       to "Komedi",
        "${mainUrl}/kategori/korku/"        to "Korku",
        "${mainUrl}/kategori/macera/"       to "Macera",
        "${mainUrl}/kategori/romantik/"     to "Romantik",
        "${mainUrl}/kategori/suc/"          to "Suç",
        "${mainUrl}/kategori/belgesel/"     to "Belgesel"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = when {
            page == 1                     -> request.data
            request.data == "${mainUrl}/" -> "${mainUrl}/?page=${page}"
            else                          -> "${request.data}sayfa/${page}/"
        }
        val document = app.get(url).document
        val home     = document.select("article.card").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("h3.card-title")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/ara", params = mapOf("q" to query)).document

        return document.select("article.card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1.hero-title")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("img.poster-img")?.attr("src"))
        val description     = document.selectFirst("div.synopsis p")?.text()?.trim()
        val year            = Regex("""\((\d{4})\)""").find(document.selectFirst("div.hero-sub")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val rating          = document.selectFirst("div.spec-item:has(span.spec-label:contains(IMDb)) span.spec-val")?.text()?.trim()
        val actors          = document.select("a.chip[itemprop=actor]").map { Actor(it.ownText().trim()) }
        val recommendations = document.select("a.similar-card").mapNotNull { it.toRecommendationResult() }
        val trailer         = document.select("button.src-btn").map { it.sourceUrl() }.firstOrNull { it?.contains("youtube.com") == true }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.score           = Score.from10(rating)
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("div.similar-name")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun Element.sourceUrl(): String? =
        Regex("""loadSource\('([^']+)'""").find(this.attr("onclick"))?.groupValues?.get(1)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FDZ", "data » ${data}")
        val document = app.get(data).document

        var found = false
        document.select("button.src-btn").forEach { button ->
            val source = button.sourceUrl() ?: return@forEach
            if (source.contains("youtube.com")) return@forEach 

            Log.d("FDZ", "${button.text().trim()} » ${source}")
            if (loadExtractor(source, "${mainUrl}/", subtitleCallback, callback)) found = true
        }

        return found
    }
}
