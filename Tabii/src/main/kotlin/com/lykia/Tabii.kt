package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

class Tabii : MainAPI() {
    override var mainUrl = "https://www.tabii.com"
    override var name = "Tabii"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Documentary)

    private val apiBase = "https://eu1.tabii.com/apigateway"
    private val imgBase = "https://cms-tabii-public-image.tabii.com/int"

    override val mainPage = mainPageOf(
        "0" to "Öne Çıkanlar",
        "1" to "tabii'ye Özel İçerikler",
        "2" to "Diziler",
        "3" to "Filmler",
        "4" to "Popüler Yapımlar",
    )

    private fun nextData(html: String): JSONObject? {
        val m = Regex(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(html) ?: return null
        return runCatching { JSONObject(m.groupValues[1]) }.getOrNull()
    }

    private fun img(name: String?): String? =
        name?.takeIf { it.isNotBlank() }?.let { "$imgBase/$it" }

    private fun ilkGorsel(d: JSONObject): String? {
        val arr = d.optJSONArray("images") ?: return null
        var yedek: String? = null
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val n = g.optString("name")
            if (n.isBlank()) continue
            if (g.optString("imageType") == "main") return img(n)
            if (yedek == null) yedek = img(n)
        }
        return yedek
    }

    private suspend fun anaSayfaOgeleri(): List<JSONObject> {
        val html = app.get("$mainUrl/tr").text
        val data = nextData(html)?.optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("data") ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (anahtar in listOf("carousel", "promoter")) {
            val gruplar = data.optJSONArray(anahtar) ?: continue
            for (g in 0 until gruplar.length()) {
                val ogeler = gruplar.optJSONObject(g)?.optJSONArray("items") ?: continue
                for (i in 0 until ogeler.length()) {
                    val o = ogeler.optJSONObject(i) ?: continue
                    if (o.optLong("id") > 0 && o.optString("slug").isNotBlank()) out += o
                }
            }
        }
        return out
    }

    private fun oge2Search(o: JSONObject): SearchResponse {
        val id = o.optLong("id")
        val slug = o.optString("slug")
        val ad = o.optString("title").ifBlank { slug }
        val url = "$mainUrl/tr/detail/$id/$slug"
        val poster = ilkGorsel(o)
        val yil = o.optString("airDate").take(4).toIntOrNull()
        val tur = if (o.optString("contentType") == "movie") TvType.Movie else TvType.TvSeries
        return if (tur == TvType.Movie) {
            newMovieSearchResponse(ad, url, tur) { posterUrl = poster; this.year = yil }
        } else {
            newTvSeriesSearchResponse(ad, url, tur) { posterUrl = poster; this.year = yil }
        }
    }

    private fun oynatmaVerisi(media: JSONArray?, subs: JSONArray?): String {
        val o = JSONObject()
        o.put("media", media ?: JSONArray())
        o.put("subs", subs ?: JSONArray())
        return o.toString()
    }

    private fun medyaJson(d: JSONObject): JSONArray? =
        d.optJSONArray("media") ?: d.optJSONObject("currentContent")?.optJSONArray("media")

    private fun altyaziJson(localization: JSONObject?): JSONArray? {
        val s = localization?.optJSONArray("subtitles") ?: return null
        val out = JSONArray()
        for (i in 0 until s.length()) {
            val e = s.optJSONObject(i) ?: continue
            val u = e.optString("url")
            if (u.isBlank()) continue
            out.put(JSONObject().put("lang", e.optString("language").ifBlank { "altyazı" }).put("url", u))
        }
        return if (out.length() == 0) null else out
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val gruplar = anaSayfaOgeleri()
        val idx = request.data.toIntOrNull() ?: 0
        val secili = if (idx == 0) gruplar else gruplar.filterIndexed { i, _ -> i % 5 == idx - 1 }
        val ogeler = (if (idx == 0) gruplar else secili).map { oge2Search(it) }.distinctBy { it.url }
        return newHomePageResponse(request.name, ogeler, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        return anaSayfaOgeleri()
            .filter { it.optString("title").lowercase().contains(q) || it.optString("slug").contains(q) }
            .map { oge2Search(it) }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val parcalar = url.trimEnd('/').split("/")
        val id = parcalar.getOrNull(parcalar.size - 2) ?: ""
        val slug = parcalar.last()
        val html = app.get("$mainUrl/tr/detail/$id/$slug").text
        val data = nextData(html)?.optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("data") ?: throw ErrorLoadingException("detay okunamadı")
        val ad = data.optString("title").ifBlank { slug }
        val poster = ilkGorsel(data)
        val ozet = data.optString("description").ifBlank { data.optString("spot") }
        val yil = (data.optString("madeYear").take(4).toIntOrNull())
            ?: data.optString("airDate").take(4).toIntOrNull()
        val turler = ArrayList<String>()
        for (anahtar in listOf("genres", "categories")) {
            val arr = data.optJSONArray(anahtar) ?: continue
            for (i in 0 until arr.length()) {
                val t = when (val v = arr.get(i)) {
                    is JSONObject -> v.optString("title").ifBlank { v.optString("name") }
                    else -> v.toString()
                }
                if (t.isNotBlank()) turler += t
            }
        }

        val sezonlar = data.optJSONArray("seasons")
        if (sezonlar != null && sezonlar.length() > 0) {
            val bolumler = ArrayList<Episode>()
            for (s in 0 until sezonlar.length()) {
                val sezonNo = sezonlar.optJSONObject(s)?.optInt("seasonNumber") ?: (s + 1)
                val eps = sezonlar.optJSONObject(s)?.optJSONArray("episodes") ?: JSONArray()
                for (e in 0 until eps.length()) {
                    val ep = eps.optJSONObject(e) ?: continue
                    val ad2 = ep.optString("title").ifBlank { ep.optString("episodeName") }
                    val veri = oynatmaVerisi(
                        ep.optJSONArray("media") ?: medyaJson(ep),
                        altyaziJson(ep.optJSONObject("localization"))
                    )
                    bolumler += newEpisode(veri) {
                        name = "S$sezonNo E${ep.optInt("episodeNumber", e + 1)} · $ad2"
                        season = sezonNo
                        episode = ep.optInt("episodeNumber", e + 1)
                        posterUrl = ilkGorsel(ep) ?: poster
                        description = ep.optString("description")
                    }
                }
            }
            return newTvSeriesLoadResponse(ad, url, TvType.TvSeries, bolumler) {
                posterUrl = poster
                plot = ozet
                this.year = yil
                this.tags = turler
            }
        }

        val veri = oynatmaVerisi(medyaJson(data), altyaziJson(data.optJSONObject("localization")))
        return newMovieLoadResponse(ad, url, TvType.Movie, veri) {
            posterUrl = poster
            plot = ozet
            this.year = yil
            this.tags = turler
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val o = JSONObject(data)
        var bulundu = false

        val subs = o.optJSONArray("subs")
        if (subs != null) {
            for (i in 0 until subs.length()) {
                val s = subs.optJSONObject(i) ?: continue
                val u = s.optString("url")
                if (u.isBlank()) continue
                val mutlak = if (u.startsWith("http")) u else "$apiBase/pbr/v1/media$u"
                val dil = when {
                    s.optString("lang").contains("Türk", true) -> "tr"
                    else -> s.optString("lang").ifBlank { "altyazı" }
                }
                subtitleCallback(newSubtitleFile(dil, mutlak))
            }
        }

        val media = o.optJSONArray("media") ?: return bulundu
        for (i in 0 until media.length()) {
            val m = media.optJSONObject(i) ?: continue
            val ham = m.optString("url")
            if (ham.isBlank()) continue
            val temel = if (ham.startsWith("http")) ham else "$apiBase/pbr/v1/media$ham"
            val drm = m.optString("drmSchema").lowercase()
            val tur = m.optString("type").lowercase()
            val adres = if (drm == "widevine") {
                val lisans = "$apiBase/drm/v1/wv?ticket=&resource_id=" +
                    URLEncoder.encode(m.optString("resourceId"), "UTF-8")
                temel + "#drm=" + URLEncoder.encode(lisans, "UTF-8")
            } else temel
            val turTipi = when {
                tur == "dash" -> ExtractorLinkType.DASH
                tur == "hls" -> ExtractorLinkType.M3U8
                else -> ExtractorLinkType.VIDEO
            }
            val etiket = buildString {
                append(if (drm == "widevine") "DASH (DRM)" else "DASH")
                m.optString("videoSizes").takeIf { it.isNotBlank() && it != "{}" }?.let { append(" · ").append(it) }
            }
            callback(newExtractorLink(name, etiket, adres, turTipi) {
                referer = "$mainUrl/"
                quality = Qualities.Unknown.value
            })
            bulundu = true
        }
        return bulundu
    }
}
