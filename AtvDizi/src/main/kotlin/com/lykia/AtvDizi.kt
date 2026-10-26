package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AtvDizi : MainAPI() {
    override var mainUrl = "https://www.atv.com.tr"
    override var name = "ATV Dizileri"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "son" to "Son Bölümler",
        "diziler" to "Diziler",
        "eski" to "Eski Diziler",
    )

    private val basliklar = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept-Language" to "tr-TR,tr;q=0.9",
    )

    private val bolumYolu = Regex("^/([a-z0-9-]+)/([0-9]+)-bolum/izle/?$")

    private fun yol(al: org.jsoup.nodes.Element): String? {
        val ham = al.attr("href").trim()
        if (ham.isEmpty() || ham.startsWith("#") || ham.startsWith("javascript")) return null
        val p = if (ham.startsWith("http")) {
            val u = runCatching { java.net.URI(ham) }.getOrNull() ?: return null
            val host = u.host ?: return null
            if (!host.endsWith("atv.com.tr")) return null
            u.path ?: return null
        } else {
            ham.substringBefore("?").substringBefore("#")
        }
        return if (p.startsWith("/")) p else "/$p"
    }

    private fun mutlak(yol: String): String = mainUrl + yol
    private val diziKaraListe = setOf(
        "diziler", "eski-diziler", "canli-yayin", "yayin-akisi", "filmler", "programlar",
        "iletisim", "uye-ol", "giris-yap", "ara", "galeri", "video", "haber", "rss",
    )

    private fun videoNesnesi(html: String): JSONObject? {
        val re = Regex(
            "<script[^>]*application/ld\\+json[^>]*>(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        for (m in re.findAll(html)) {
            runCatching {
                val d = JSONObject(m.groupValues[1].trim())
                if (d.optString("@type") == "VideoObject") return d
            }
        }
        return null
    }

    private fun gorsel(el: Element?): String? {
        val img = el?.selectFirst("img") ?: return null
        val ham = img.attr("data-src").ifBlank { img.attr("src") }.trim()
        return when {
            ham.isEmpty() -> null
            ham.startsWith("http") -> ham
            ham.startsWith("//") -> "https:$ham"
            ham.startsWith("/") -> mainUrl + ham
            else -> null
        }
    }

    private fun baslikTuret(slug: String, bolum: String): String =
        slug.split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } } +
            " · $bolum. Bölüm"

    private fun diziOgeleri(doc: Document): List<SearchResponse> {
        val out = ArrayList<SearchResponse>()
        for (a in doc.select("a[href]")) {
            val p = yol(a)?.removeSuffix("/") ?: continue
            if (!p.matches(Regex("^/[a-z0-9-]+$"))) continue
            if (p.removePrefix("/") in diziKaraListe) continue
            val ad = a.text().trim().ifBlank { a.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
            if (ad.isBlank() || ad.length < 3) continue
            out += newTvSeriesSearchResponse(ad, mutlak(p), TvType.TvSeries) { posterUrl = gorsel(a) }
        }
        return out.distinctBy { it.url }
    }

    private fun sonBolumler(doc: Document): List<SearchResponse> {
        val out = ArrayList<SearchResponse>()
        for (a in doc.select("a[href*=/izle]")) {
            val p = yol(a) ?: continue
            val m = bolumYolu.matchEntire(p.trimEnd('/')) ?: continue
            val ad = baslikTuret(m.groupValues[1], m.groupValues[2])
            out += newMovieSearchResponse(ad, mutlak(p), TvType.Movie) { posterUrl = gorsel(a) }
        }
        return out.distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(if (request.data == "son") mainUrl else "$mainUrl/${request.data}", headers = basliklar).document
        val ogeler = when (request.data) {
            "son" -> sonBolumler(doc)
            else -> diziOgeleri(doc)
        }
        return newHomePageResponse(request.name, ogeler, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        val tumu = ArrayList<SearchResponse>()
        tumu += diziOgeleri(app.get("$mainUrl/diziler", headers = basliklar).document)
        tumu += diziOgeleri(app.get("$mainUrl/eski-diziler", headers = basliklar).document)
        tumu += sonBolumler(app.get(mainUrl, headers = basliklar).document)
        return tumu.distinctBy { it.url }.filter {
            it.name.lowercase().contains(q) || it.url.lowercase().contains(q.replace(" ", "-"))
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val yol = runCatching { java.net.URI(url).path }.getOrDefault(url)
        val doc = app.get(url, headers = basliklar).document
        val html = doc.html()

        if (bolumYolu.matchEntire(yol.trimEnd('/')) != null) {
            val v = videoNesnesi(html)
            val ad = v?.optString("name")?.ifBlank { null } ?: doc.title()
            return newMovieLoadResponse(ad, url, TvType.Movie, v?.optString("contentUrl") ?: url) {
                posterUrl = v?.optString("thumbnailUrl")?.ifBlank { null }
                plot = v?.optString("description")?.ifBlank { null }
            }
        }

        val diziAd = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?.substringBefore("|")?.trim()?.ifBlank { null } ?: doc.title().substringBefore("|").trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
        val ozet = doc.selectFirst("meta[property=og:description]")?.attr("content")?.ifBlank { null }
        val bolumler = ArrayList<Episode>()
        val gorulen = HashSet<String>()
        for (a in doc.select("a[href*=/izle]")) {
            val p0 = yol(a) ?: continue
            val href = mutlak(p0)
            val p = p0.trimEnd('/')
            val m = bolumYolu.matchEntire(p) ?: continue
            if (!gorulen.add(href)) continue
            val no = m.groupValues[2].toIntOrNull() ?: continue
            bolumler += newEpisode(href) {
                name = "$no. Bölüm"
                season = 1
                episode = no
                posterUrl = gorsel(a) ?: poster
            }
        }
        bolumler.sortBy { it.episode ?: 0 }
        return newTvSeriesLoadResponse(diziAd, url, TvType.TvSeries, bolumler) {
            posterUrl = poster
            plot = ozet
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        var adres = data
        if (!adres.contains("/vms/") && !adres.endsWith(".mp4", true) && !adres.endsWith(".m3u8", true)) {
            val html = app.get(adres, headers = basliklar).text
            val v = videoNesnesi(html) ?: return false
            adres = v.optString("contentUrl")
        }
        if (adres.isBlank()) return false
        val tur = if (adres.endsWith(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        callback(newExtractorLink(name, "MP4", adres, tur) {
            referer = "$mainUrl/"
            quality = Qualities.P1080.value
        })
        return true
    }
}
