package com.lykia

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlinx.coroutines.runBlocking
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.network.WebViewResolver
import java.net.URLEncoder

class YabanciDizi : MainAPI() {
    override var mainUrl              = "https://yabancidizi.news"
    override var name                 = "YabanciDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    private val ajaxHeaders = mapOf("X-Requested-With" to "XMLHttpRequest")
    private fun ajaxCookies()   = mapOf("udys" to "${System.currentTimeMillis()}", "level" to "1")

    private fun form(vararg alanlar: Pair<String, String>) = alanlar.associate { (k, v) -> k to URLEncoder.encode(v, "UTF-8") }

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi/tur/aksiyon-izle-1"     to "Aksiyon Dizileri",
        "${mainUrl}/dizi/tur/bilim-kurgu-izle-1" to "Bilim Kurgu Dizileri",
        "${mainUrl}/dizi/tur/dram-izle"          to "Dram Dizileri",
        "${mainUrl}/dizi/tur/komedi-izle"        to "Komedi Dizileri",
        "${mainUrl}/dizi/tur/polisiye-izle"      to "Polisiye Dizileri",
        "${mainUrl}/dizi/tur/kore-dizileri"      to "Kore Dizileri",
        "${mainUrl}/dizi/tur/mini-dizi"          to "Mini Diziler",
        "${mainUrl}/film/tur/aksiyon-izle-1"     to "Aksiyon Filmleri",
        "${mainUrl}/film/tur/bilim-kurgu-izle-1" to "Bilim Kurgu Filmleri",
        "${mainUrl}/film/tur/komedi-izle"        to "Komedi Filmleri",
        "${mainUrl}/film/tur/korku-izle"         to "Korku Filmleri",
        "${mainUrl}/film/tur/animasyon-izle"     to "Animasyon Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page == 1) request.data else "${request.data}/${page}"
        val document = app.get(url).document
        val home     = document.select("li.mofy-moviesli").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val anchor    = this.selectFirst("div.mofy-movbox-text a") ?: return null
        val title     = anchor.text().trim()
        val href      = fixUrlNull(anchor.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        if (title.isEmpty()) return null

        return newSearchResponse(title, href, posterUrl)
    }

    private fun newSearchResponse(title: String, href: String, posterUrl: String?): SearchResponse =
        if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/search",
            params  = mapOf("qr" to query),
            data    = mapOf(),
            headers = ajaxHeaders,
            referer = "${mainUrl}/",
            cookies = ajaxCookies()
        ).parsedSafe<AramaYaniti>() ?: return emptyList()

        return response.data?.result?.mapNotNull { sonuc ->
            val link  = sonuc.link ?: return@mapNotNull null
            val title = sonuc.name ?: return@mapNotNull null
            val tur   = if (sonuc.type == "1") "film" else "dizi"

            newSearchResponse(title, "${mainUrl}/${tur}/${link}", sonuc.image?.let { "${mainUrl}/uploads/series/${it}" })
        } ?: emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1.page-title")?.ownText()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("img.series-profile-thumb")?.attr("src"))
        val description = document.selectFirst("p#tv-series-desc")?.text()?.trim()
        val year        = document.selectFirst("h1.page-title span.light-title")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val tags        = document.select("div.series-summary-wrapper a[href*=tur/]").map { it.text().trim() }
        val rating      = document.selectFirst("div.color-imdb")?.text()?.trim()
        val trailer     = document.selectFirst("div.media-trailer[data-yt]")?.attr("data-yt")?.let { "https://www.youtube.com/embed/${it}" }
        val actors      = document.select("#common-cast-list a[href*=oyuncu/]").map {
            Actor(it.selectFirst("h5")?.text()?.trim() ?: it.text().trim(), fixUrlNull(it.selectFirst("img")?.attr("src")))
        }.ifEmpty {
            document.select("div.series-summary-wrapper a[href*=oyuncu/]").map { Actor(it.text().trim()) }
        }

        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot      = description
                this.year      = year
                this.tags      = tags
                this.score     = Score.from10(rating)
                addActors(actors)
                addTrailer(trailer)
            }
        }

        val episodes = document.select("td.table-episodes-title a").mapNotNull {
            val href  = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val match = Regex("""sezon-(\d+)/bolum-(\d+)""").find(href) ?: return@mapNotNull null

            newEpisode(href) {
                this.name    = it.text().trim()
                this.season  = match.groupValues[1].toIntOrNull()
                this.episode = match.groupValues[2].toIntOrNull()
            }
        }.distinctBy { it.data }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.score     = Score.from10(rating)
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("YBD", "data » ${data}")
        val document = app.get(data).document

        val kaynaklar = mutableListOf<Pair<String, Element>>()
        val diller    = document.select("[data-lango]")
        if (diller.isEmpty()) {
            document.select("div.alternatives-for-this [data-link]").forEach { kaynaklar.add("" to it) }
        }
        diller.forEach { dil ->
            val dilAdi        = dil.text().trim()
            val alternatifler = if (dil.hasClass("active")) {
                document.select("div.alternatives-for-this [data-link]")
            } else {
                val html = app.post(
                    "${mainUrl}/ajax/service",
                    headers = ajaxHeaders,
                    referer = data,
                    cookies = ajaxCookies(),
                    data    = form("lang" to dil.attr("data-type"), "episode" to dil.attr("data-eid"), "type" to "langTab")
                ).parsedSafe<DilYaniti>()?.data ?: return@forEach

                Jsoup.parse(html).select("[data-link]")
            }
            alternatifler.forEach { kaynaklar.add(dilAdi to it) }
        }

        Log.d("YBD", "diller » ${diller.size}, kaynaklar » ${kaynaklar.size}")

        var found = false
        kaynaklar.forEach { (dilAdi, kaynak) ->
            val etiket = listOf(dilAdi, kaynak.text().trim()).filter { it.isNotEmpty() }.joinToString(" - ")

            val apiIframe = app.post(
                "${mainUrl}/ajax/service",
                headers = ajaxHeaders,
                referer = data,
                cookies = ajaxCookies(),
                data    = form(
                    "link"      to kaynak.attr("data-link"),
                    "hash"      to kaynak.attr("data-hash"),
                    "querytype" to kaynak.attr("data-querytype"),
                    "type"      to "videoGet"
                )
            ).parsedSafe<VideoYaniti>()?.apiIframe ?: return@forEach
            val iframe    = fixUrlNull(app.get(apiIframe, referer = data).document.selectFirst("iframe")?.attr("src"))
                ?: webViewIleCoz(apiIframe, data)
                ?: return@forEach
            Log.d("YBD", "${etiket} » ${iframe}")

            val loaded = loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                runBlocking {
                    callback.invoke(
                        newExtractorLink(
                            source = etiket,
                            name   = etiket,
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

    private suspend fun webViewIleCoz(url: String, referer: String): String? = runCatching {
        WebViewResolver(Regex("""vidmoly\.|ok\.ru/videoembed|molystream\.org/embed"""), useOkhttp = false)
            .resolveUsingWebView(url, referer).first?.url?.toString()
    }.getOrNull().also { Log.d("YBD", "webView » $it") }

    data class AramaYaniti(@JsonProperty("data") val data: AramaVerisi? = null)
    data class AramaVerisi(@JsonProperty("result") val result: List<AramaSonucu>? = null)
    data class AramaSonucu(
        @JsonProperty("s_type")  val type: String? = null,
        @JsonProperty("s_link")  val link: String? = null,
        @JsonProperty("s_name")  val name: String? = null,
        @JsonProperty("s_image") val image: String? = null
    )
    data class DilYaniti(@JsonProperty("data") val data: String? = null)
    data class VideoYaniti(@JsonProperty("api_iframe") val apiIframe: String? = null)
}
