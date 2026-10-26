package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TrtBelgesel : MainAPI() {
    override var mainUrl = "https://www.trtbelgesel.com.tr"
    override var name = "TRT Belgesel"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Documentary, TvType.Live)

    private val canliAdres = "$mainUrl/canli-yayin"
    private val canliAkis = "https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8"
    private val canliPoster = "https://cdn-i.pr.trt.com.tr/trtbelgesel/w960/h540/q75/17905999_0-0-4319-2100.jpeg"

    private val kategoriler = listOf(
        "lezzet" to "Lezzet",
        "doga-gezi" to "Doğa & Gezi",
        "tarih" to "Tarih",
        "bilim-teknoloji" to "Bilim & Teknoloji",
        "kultur-antropoloji" to "Kültür & Antropoloji",
        "insan-hikayeleri" to "İnsan Hikâyeleri",
        "macera" to "Macera",
        "populer-kultur" to "Popüler Kültür",
        "suc" to "Suç",
    )

    override val mainPage = mainPageOf(
        "canli" to "Canlı Yayın",
        *kategoriler.map { (yol, ad) -> yol to ad }.toTypedArray()
    )

    private fun Element.resim(): String? {
        val img: Element = selectFirst("img") ?: return null
        val adres: String = img.attr("data-src").ifBlank { img.attr("src") }
        return adres.ifBlank { null }
    }

    private fun kategoriProgramlari(doc: Document, yol: String): List<SearchResponse> =
        doc.select("figure").mapNotNull { f ->
            val a = f.selectFirst("figcaption a[href^=/$yol/]") ?: return@mapNotNull null
            val ad = f.selectFirst("figcaption strong")?.text()?.trim().orEmpty()
                .ifBlank { f.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
            if (ad.isBlank()) return@mapNotNull null
            newTvSeriesSearchResponse(ad, fixUrl(a.attr("href")), TvType.TvSeries) { posterUrl = f.resim() }
        }.distinctBy { it.url }

    private fun canliOge() = newLiveSearchResponse("TRT Belgesel Canlı", canliAdres, TvType.Live) {
        posterUrl = canliPoster
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data == "canli")
            return newHomePageResponse(HomePageList(request.name, listOf(canliOge()), isHorizontalImages = true), hasNext = false)
        val doc = app.get("$mainUrl/${request.data}").document
        return newHomePageResponse(request.name, kategoriProgramlari(doc, request.data), hasNext = false)
    }

    private var tumProgramlar: List<SearchResponse>? = null

    override suspend fun search(query: String): List<SearchResponse> {
        val liste = tumProgramlar ?: kategoriler.flatMap { (yol, _) ->
            runCatching { kategoriProgramlari(app.get("$mainUrl/$yol").document, yol) }.getOrDefault(emptyList())
        }.distinctBy { it.url }.also { tumProgramlar = it }
        val q = query.trim().lowercase(java.util.Locale("tr"))
        val bulunan = liste.filter { it.name.lowercase(java.util.Locale("tr")).contains(q) }
        return if ("canlı".contains(q) || "trt belgesel".contains(q)) listOf(canliOge()) + bulunan else bulunan
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.trimEnd('/') == canliAdres)
            return newLiveStreamLoadResponse("TRT Belgesel Canlı", url, canliAkis) {
                posterUrl = canliPoster
                plot = "TRT Belgesel'in resmi canlı yayını."
            }
        val doc = app.get(url).document
        val ad = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.title().substringBefore("|").trim()
        val bolumler = doc.select("div.global--card").mapNotNull { k ->
            val a = k.selectFirst("a.link[href]") ?: return@mapNotNull null
            val bAd = k.selectFirst("img")?.attr("alt")?.trim().orEmpty()
                .ifBlank { k.selectFirst("div[data-number]")?.text()?.trim().orEmpty() }
            newEpisode(fixUrl(a.attr("href"))) {
                name = bAd
                posterUrl = k.resim()
            }
        }.distinctBy { it.data }
        return newTvSeriesLoadResponse(ad, url, TvType.TvSeries, bolumler) {
            posterUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
            plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == canliAkis) {
            callback(newExtractorLink(name, "TRT Belgesel Canlı", canliAkis, ExtractorLinkType.M3U8) {
                referer = "$mainUrl/"
                quality = Qualities.Unknown.value
            })
            return true
        }

        val doc = app.get(data).document
        val youtube = doc.select("script[type=application/ld+json]").map { it.data() }
            .firstNotNullOfOrNull { Regex("\"contentUrl\"\\s*:\\s*\"(https://www\\.youtube\\.com/watch\\?v=[\\w-]+)").find(it)?.groupValues?.get(1) }
            ?: doc.selectFirst("iframe[src*=youtube.com/embed/]")?.attr("src")
                ?.let { Regex("embed/([\\w-]+)").find(it)?.groupValues?.get(1) }?.let { "https://www.youtube.com/watch?v=$it" }
            ?: return false
        return loadExtractor(youtube, data, subtitleCallback, callback)
    }
}
