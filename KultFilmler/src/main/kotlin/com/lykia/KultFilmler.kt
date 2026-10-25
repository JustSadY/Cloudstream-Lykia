package com.lykia

import android.util.Base64
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class KultFilmler : MainAPI() {
    override var mainUrl              = "https://kultfilmler.net"
    override var name                 = "KultFilmler"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override var supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.text().contains("Just a moment...")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/"                                          to "Ana Sayfa",
        "${mainUrl}/film-arsivi/"                              to "Film Arsivi",
        "${mainUrl}/category/aile-filmleri-izle"               to "Aile",
        "${mainUrl}/category/aksiyon-filmleri-izle"            to "Aksiyon",
        "${mainUrl}/category/animasyon-filmleri-izle"          to "Animasyon",
        "${mainUrl}/category/belgesel-izle"                    to "Belgesel",
        "${mainUrl}/category/bilim-kurgu-filmleri-izle"        to "Bilim Kurgu",
        "${mainUrl}/category/biyografi-filmleri-izle"          to "Biyografi",
        "${mainUrl}/category/dram-filmleri-izle"               to "Dram",
        "${mainUrl}/category/fantastik-filmleri-izle"          to "Fantastik",
        "${mainUrl}/category/gerilim-filmleri-izle"            to "Gerilim",
        "${mainUrl}/category/gizem-filmleri-izle"              to "Gizem",
        "${mainUrl}/category/komedi-filmleri-izle"             to "Komedi",
        "${mainUrl}/category/korku-filmleri-izle"              to "Korku",
        "${mainUrl}/category/macera-filmleri-izle"             to "Macera",
        "${mainUrl}/category/polisiye-filmleri-izle"           to "Polisiye",
        "${mainUrl}/category/romantik-filmleri-izle"           to "Romantik",
        "${mainUrl}/category/suc-filmleri-izle"                to "Suc",
        "${mainUrl}/category/tarih-filmleri-izle"              to "Tarih",
        "${mainUrl}/category/yerli-filmleri-izle"              to "Yerli",
        "${mainUrl}/dizi-kategori/mini-dizi-izle"              to "Mini Diziler",
        "${mainUrl}/dizi-kategori/dizi-izle"                   to "Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("a.mcard").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val img      = this.selectFirst("img.pimg") ?: this.selectFirst("img")
        val title    = img?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: this.selectFirst("h3")?.text()?.trim()
            ?: return null
        val href     = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(img?.attr("src") ?: img?.attr("data-src"))

        return if (href.contains("/dizi/") || href.contains("/bolum/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document
        return document.select("a.mcard").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val isSeries = url.contains("/dizi/")

        val title = document.selectFirst("h1.vtitle")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("[property='og:title']")?.attr("content")?.trim()
            ?: return null
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("p.desc")?.text()?.trim()
            ?: document.selectFirst("div.dsyn p")?.text()?.trim()
            ?: document.selectFirst("[property='og:description']")?.attr("content")?.trim()
        val tags = (document.select("div.genres a") + document.select("div.gn a"))
            .map { it.text().trim() }.filter { it.isNotEmpty() }.distinct()

        val year = document.selectFirst("a[href*='/yapim/']")?.text()?.trim()?.toIntOrNull()

        val durationText = (document.select("div.irow").firstOrNull {
            it.selectFirst(".ilabel, .il")?.text()?.contains("Süre", true) == true
        }?.text()) ?: ""
        val duration = Regex("""(\d+)""").find(durationText)?.groupValues?.get(1)?.toIntOrNull()

        val actors = (document.select("div.cast a.cmember h5") + document.select("div.ccast a.cm h5"))
            .map { Actor(it.text().trim()) }.filter { it.name.isNotEmpty() }

        if (isSeries) {
            val episodes = document.select("section.epsection a.ep").mapNotNull {
                val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epText = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val season  = Regex("""(\d+)\.\s*Sezon""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                val episode = Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epHref) { this.name = epText; this.season = season; this.episode = episode }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.year = year; this.plot = description
                this.tags = tags; this.duration = duration; this.actors = actors.map { ActorData(it) }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster; this.year = year; this.plot = description
            this.tags = tags; this.duration = duration; this.actors = actors.map { ActorData(it) }
        }
    }

    private suspend fun loadVidPapi(
        iframe: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val hash = iframe.substringAfterLast("/")
        val playerPage = app.get(iframe, referer = "${mainUrl}/").text
        Regex("""playerjsSubtitle\s*=\s*"\[([^\]]*)\]([^"]+)"""")
            .find(playerPage)?.let {
                subtitleCallback.invoke(
                    SubtitleFile(it.groupValues[1], it.groupValues[2].replace("\\/", "/"))
                )
            }

        val body = app.post(
            "https://vidpapi.xyz/player/index.php?data=$hash&do=getVideo",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            ),
            data = mapOf("hash" to hash, "r" to "${mainUrl}/"),
            referer = "${mainUrl}/"
        ).text

        val link = Regex(""""videoSource"\s*:\s*"([^"]+)"""")
            .find(body)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: Regex(""""securedLink"\s*:\s*"([^"]+)"""")
                .find(body)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: return

        callback.invoke(
            newExtractorLink(source = "VidPapi", name = "VidPapi", url = link, type = ExtractorLinkType.M3U8) {
                this.referer = "${mainUrl}/"
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
                    "Origin" to "https://vidpapi.xyz",
                    "Sec-Fetch-Dest" to "video",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )
            }
        )
    }

    private fun getIframe(sourceCode: String): String {
        val atob = Regex("""PHA\+[0-9a-zA-Z+/=]*""").find(sourceCode)?.value ?: return ""
        val padding    = 4 - atob.length % 4
        val atobPadded = if (padding < 4) atob.padEnd(atob.length + padding, '=') else atob
        val iframe = Jsoup.parse(String(Base64.decode(atobPadded, Base64.DEFAULT), Charsets.UTF_8))
        return fixUrlNull(iframe.selectFirst("iframe")?.attr("src")) ?: ""
    }

    private fun normalizeUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/")  -> "$mainUrl$url"
        else                 -> url
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframes  = mutableSetOf<String>()

        document.select("div#player iframe[src]").forEach {
            normalizeUrl(it.attr("src")).let { src -> if (src.isNotBlank()) iframes.add(src) }
        }

        val srcData = document.selectFirst("script#kf-srcdata")?.data()
            ?: document.selectFirst("script#kf-srcdata")?.html() ?: ""
        if (srcData.isNotBlank()) {
            val unescaped = srcData
                .replace("\\u003C", "<").replace("\\u003E", ">")
                .replace("\\/", "/").replace("\\\"", "\"")
                .replace("\\n", "\n").replace("&amp;", "&")
            Regex("""<iframe[^>]*?\ssrc\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).findAll(unescaped)
                .forEach { normalizeUrl(it.groupValues[1]).let { src -> if (src.isNotBlank()) iframes.add(src) } }
            Regex("""<iframe[^>]*?\ssrc\s*=\s*'([^']+)'""", RegexOption.IGNORE_CASE).findAll(unescaped)
                .forEach { normalizeUrl(it.groupValues[1]).let { src -> if (src.isNotBlank()) iframes.add(src) } }
        }

        if (iframes.isEmpty()) {
            val legacy = getIframe(document.html())
            if (legacy.isNotBlank()) iframes.add(legacy)
            document.select("div.container#player").forEach {
                val alt = it.selectFirst("iframe")?.attr("src")
                if (!alt.isNullOrBlank()) {
                    val altDoc = app.get(alt).document
                    val altFrame = getIframe(altDoc.html())
                    if (altFrame.isNotBlank()) iframes.add(altFrame)
                }
            }
        }

        iframes.forEach { iframe ->
            if (iframe.contains("vidmoly")) {
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                    "Sec-Fetch-Dest" to "iframe"
                )
                val iSource   = app.get(iframe, headers = headers, referer = "${mainUrl}/").text
                val m3uLink   = Regex("""file:"([^"]+)""").find(iSource)?.groupValues?.get(1)
                    ?: throw ErrorLoadingException("m3u link not found")
                callback.invoke(
                    newExtractorLink(source = "VidMoly", name = "VidMoly", url = m3uLink, type = INFER_TYPE) { quality = Qualities.Unknown.value }
                )
            } else if (iframe.contains("vidpapi.")) {
                runCatching { loadVidPapi(iframe, subtitleCallback, callback) }
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }
        return iframes.isNotEmpty()
    }
}
