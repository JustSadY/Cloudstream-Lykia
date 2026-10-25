@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.lykia

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class DiziMom : MainAPI() {
    override var mainUrl              = "https://www.dizidad.com"
    override var name                 = "DiziMom"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler/page/"        to "Son Bölümler",
        "${mainUrl}/yerli-dizi-izle/page/"     to "Yerli Diziler",
        "${mainUrl}/yabanci-dizi-izle/page/"   to "Yabancı Diziler",
        "${mainUrl}/tv-programlari-izle/page/" to "TV Programları",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}/").document
        val home     = if (request.data.contains("/tum-bolumler/")) {
            document.select("div.episode-box").mapNotNull { it.sonBolumler() }
        } else {
            document.select("a[href*='/diziler/']").mapNotNull { it.diziler() }
                .distinctBy { it.url }
        }

        return newHomePageResponse(request.name, home)
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val link     = this.select("a[href]").firstOrNull { !it.attr("href").contains("/diziler/") }
            ?: this.selectFirst("a[href*='/diziler/']")
            ?: return null
        val href      = fixUrlNull(link.attr("href")) ?: return null
        val img       = this.selectFirst("div.img img") ?: this.selectFirst("img")
        val title     = img?.attr("alt")?.trim()?.substringBefore(" izle")?.trim()?.takeIf { it.isNotEmpty() }
            ?: this.selectFirst("div.episode-name")?.text()?.trim()?.substringBefore(" izle")?.trim()?.takeIf { it.isNotEmpty() }
            ?: link.text().trim().substringBefore(" izle").trim().takeIf { it.isNotEmpty() }
            ?: return null

        val posterUrl = fixUrlNull(img?.attr("data-src")) ?: fixUrlNull(img?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun Element.diziler(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("/diziler/")) return null
        val img       = selectFirst("img")
        val title     = img?.attr("alt")?.trim()?.substringBefore(" izle")?.trim()
            ?: text().trim().substringBefore(" izle").trim()
        if (title.isEmpty()) return null
        val posterUrl = fixUrlNull(img?.attr("data-src")) ?: fixUrlNull(img?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document

        return document.select("div.single-item").mapNotNull { item ->
            val link  = item.selectFirst("div.categorytitle a[href]") ?: return@mapNotNull null
            val title = link.text().trim().substringBefore(" izle").trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            if (!title.contains(query, ignoreCase = true)) return@mapNotNull null
            val href = fixUrlNull(link.attr("href")) ?: return@mapNotNull null
            val posterUrl = fixUrlNull(item.selectFirst("div.cat-img img")?.attr("src"))
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        var document = app.get(url).document
        if (document.selectFirst("div.category_image") == null) {
            val seriesUrl = document.selectFirst("a[href*='/diziler/']")?.attr("href")
            if (seriesUrl != null) document = app.get(fixUrl(seriesUrl)).document
        }

        val title       = document.selectFirst("div.title h1")?.text()?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.category_image img")?.attr("src")) ?: return null
        val year        = document.selectXpath("//div[span[contains(text(), 'Yapım Yılı')]]").text().substringAfter("Yapım Yılı : ").trim().toIntOrNull()
        val description = document.selectFirst("div.category_desc")?.text()?.trim()
        val tags        = document.select("div.genres a").mapNotNull { it.text().trim() }
        val rating      = document.selectXpath("//div[span[contains(text(), 'IMDB')]]").text().substringAfter("IMDB : ").trim().toRatingInt()
        val actors      = document.selectXpath("//div[span[contains(text(), 'Oyuncular')]]").text().substringAfter("Oyuncular : ").split(", ").map {
            Actor(it.trim())
        }

        val episodes    = document.select("div.bolumust").mapNotNull {
            val epName    = it.selectFirst("div.baslik")?.text()?.trim() ?: return@mapNotNull null
            val epHref    = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\.Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\.Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name    = epName.substringBefore(" izle").replace(title, "").trim()
                this.season  = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        val iframes = mutableListOf<String>()
        fun collect(doc: org.jsoup.nodes.Document) {
            doc.select("div.video p iframe").forEach {
                val src = it.attr("data-src").takeIf { u -> u.startsWith("http") }
                    ?: it.attr("src").takeIf { u -> u.startsWith("http") }
                if (src != null) iframes.add(src)
            }
        }
        collect(document)

        document.select("div.sources a").forEach {
            val subUrl = fixUrlNull(it.attr("href")) ?: return@forEach
            val subDocument = runCatching { app.get(subUrl).document }.getOrNull() ?: return@forEach
            collect(subDocument)
        }

        var found = false
        for (iframe in iframes.distinct()) {
            val ok = if (iframe.contains("hdplayersystem.")) {
                runCatching { loadHdPlayerSystem(iframe, data, callback) }.getOrDefault(false)
            } else {
                runCatching { loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback) }.getOrDefault(false)
            }
            if (ok) found = true
        }
        return found
    }

    private suspend fun loadHdPlayerSystem(
        iframe: String,
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val hash = iframe.substringAfterLast("/")
        if (hash.isBlank()) return false

        val body = app.post(
            "https://hdplayersystem.com/player/index.php?data=$hash&do=getVideo",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            ),
            data    = mapOf("hash" to hash, "r" to "${mainUrl}/"),
            referer = iframe
        ).text

        val link = Regex("""videoSource"\s*:\s*"([^"]+)"""").find(body)
            ?.groupValues?.get(1)?.replace("\\/", "/")
            ?: Regex("""securedLink"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues?.get(1)?.replace("\\/", "/")
            ?: return false

        callback.invoke(
            newExtractorLink(source = "HDPlayerSystem", name = "DiziMom", url = link, type = ExtractorLinkType.M3U8) {
                this.referer = iframe
                this.quality = Qualities.Unknown.value
                this.headers = mapOf("Accept" to "*/*")
            }
        )
        return true
    }
}
