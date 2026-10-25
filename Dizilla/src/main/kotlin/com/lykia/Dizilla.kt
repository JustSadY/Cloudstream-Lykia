@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.lykia

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element
import kotlin.math.roundToInt

class Dizilla : MainAPI() {
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.peekBody(1024 * 1024).string().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }
    override var mainUrl              = "https://dizilla.now"
    override var name                 = "Dizilla"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler"          to "Son Bölümler",
        "${mainUrl}/yabanci-dizi-izle"     to "Yabancı Diziler",
        "${mainUrl}/dizi-turu/aile"        to "Aile",
        "${mainUrl}/dizi-turu/aksiyon"     to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/romantik"    to "Romantik",
        "${mainUrl}/dizi-turu/komedi"      to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isEpisodePage = request.data.contains("/tum-bolumler")
        if (isEpisodePage && page > 1) return newHomePageResponse(request.name, emptyList())

        val url = if (page <= 1) request.data
                  else "${request.data}${if ('?' in request.data) "&" else "?"}page=$page"
        val document = app.get(url, interceptor = interceptor).document

        val home = if (isEpisodePage) {
            document.select("a[href*='-bolum']").mapNotNull { it.sonBolum() }
        } else {
            document.select("a[href*='dizi/']").mapNotNull { it.diziler() }
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val hrefRaw = attr("href")?.trim() ?: return null
        if (!hrefRaw.contains("dizi/")) return null
        val href = if (hrefRaw.startsWith("http")) hrefRaw
                    else "${mainUrl}/${hrefRaw.trimStart('/')}"
        val img       = selectFirst("img")
        val label     = attr("aria-label").substringBefore(',').trim()
        val title     = selectFirst("span.mvk-t")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: label.takeIf { it.isNotEmpty() }
            ?: img?.attr("alt")?.trim()?.removeSuffix(" izle")?.trim()?.takeIf { it.isNotEmpty() }
            ?: hrefRaw.substringAfterLast('/')
        val posterUrl = fixUrlNull(img?.attr("src") ?: img?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun Element.sonBolum(): SearchResponse? {
        val hrefRaw = attr("href")?.trim() ?: return null
        val slug = Regex("""^(.*?)-\d+-sezon-\d+-bolum""").find(hrefRaw.trimStart('/'))
            ?.groupValues?.get(1) ?: return null
        val href = "${mainUrl}/dizi/$slug"

        val label = attr("aria-label").substringBefore(',').trim().takeIf { it.isNotEmpty() }
        val title = label ?: slug.replace('-', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        val out = LinkedHashMap<String, SearchResponse>()

        listOf("${mainUrl}/yabanci-dizi-izle", "${mainUrl}/arsiv", mainUrl).forEach { page ->
            runCatching {
                val doc = app.get(page, interceptor = interceptor).document
                doc.select("a[href*='dizi/']").forEach { el ->
                    val item = el.diziler() ?: return@forEach
                    val name = item.name?.lowercase() ?: ""
                    if (q.isNotEmpty() && !name.contains(q) && !item.url.lowercase().contains(q)) return@forEach
                    out.putIfAbsent(item.url, item)
                }
            }
        }

        return out.values.toList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document
        val mapper   = jacksonObjectMapper()

        val jsonLd = document.select("script[type=application/ld+json]").mapNotNull {
            runCatching { mapper.readTree(it.data()) }.getOrNull()
        }
        val seriesNodes = jsonLd.filter { it.path("@type").asText() == "TVSeries" }
        val seriesNode = seriesNodes.firstOrNull { node ->
            val seasons = node.path("containsSeason")
            seasons.isArray && seasons.any { it.path("episode").size() > 0 }
        } ?: seriesNodes.firstOrNull()
        val episodeNode = jsonLd.firstOrNull { it.path("@type").asText() == "TVEpisode" }

        val title = seriesNode?.textOrNull("name")
            ?: episodeNode?.textOrNull("name")
            ?: document.selectFirst("h1")?.text()?.trim()?.removeSuffix(" izle")?.trim()
            ?: return null

        val poster = jsonImage(seriesNode?.path("image"))
            ?: jsonImage(episodeNode?.path("image"))
            ?: fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("img[src*=macellan]")?.attr("src"))

        val description = seriesNode?.textOrNull("description")
            ?: episodeNode?.textOrNull("description")
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val year = Regex("""\d{4}""").find(seriesNode?.textOrNull("datePublished") ?: "")
            ?.value?.toIntOrNull()
            ?: Regex("""\d{4}""").find(episodeNode?.textOrNull("datePublished") ?: "")
                ?.value?.toIntOrNull()

        val rating = seriesNode?.path("aggregateRating")?.path("ratingValue")
            ?.takeIf { it.isNumber }?.asDouble()?.let { (it * 10).roundToInt() }

        val duration = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""")
            .find(seriesNode?.textOrNull("timeRequired") ?: "")
            ?.let { match ->
                (match.groupValues[1].toIntOrNull() ?: 0) * 60 +
                    (match.groupValues[2].toIntOrNull() ?: 0)
            }?.takeIf { it > 0 }

        val tags = seriesNodes.flatMap { node ->
            val genre = node.path("genre")
            when {
                genre.isArray   -> genre.mapNotNull { it.takeIf { g -> g.isTextual }?.asText() }
                genre.isTextual -> listOf(genre.asText())
                else            -> emptyList()
            }
        }.filter { it.isNotBlank() }.distinct()
            .ifEmpty {
                document.select("a[href*='dizi-turu']").map { it.text().trim() }
                    .filter { it.isNotBlank() }.distinct()
            }

        val actors = seriesNode?.path("actor")?.mapNotNull { actor ->
            actor.textOrNull("name")?.let { Actor(it) }
        } ?: emptyList()

        val episodes = mutableListOf<Episode>()
        seriesNode?.path("containsSeason")?.forEach { seasonNode ->
            val seasonNumber = seasonNode.path("seasonNumber").takeIf { it.isInt }?.asInt()
            seasonNode.path("episode").forEach { epNode ->
                val epUrl = fixUrlNull(epNode.textOrNull("url")) ?: return@forEach
                episodes.add(newEpisode(epUrl) {
                    this.name        = epNode.textOrNull("name")
                    this.season      = seasonNumber
                    this.episode     = epNode.path("episodeNumber").takeIf { it.isInt }?.asInt()
                    this.description = epNode.textOrNull("description")
                    this.posterUrl   = poster
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZL", "data » $data")
        val document = app.get(data, interceptor = interceptor).document

        val payload = document.select("script").joinToString("\n") { it.data() }
            .replace("\\\"", "\"")
        val sourcesIndex = payload.indexOf("\"sources\"")
        if (sourcesIndex >= 0) {
            val arrayStart = payload.indexOf('[', sourcesIndex)
            val arrayText  = if (arrayStart >= 0) extractJsonArray(payload, arrayStart) else null
            val sources    = arrayText?.let {
                runCatching { jacksonObjectMapper().readTree(it) }.getOrNull()
            }

            if (sources != null && sources.isArray) {
                var emitted = 0
                sources.forEach { source ->
                    if (source.path("trailer").asBoolean(false)) return@forEach
                    val src = source.textOrNull("src") ?: return@forEach
                    val langLabel = source.textOrNull("langLabel") ?: ""
                    val quality   = source.textOrNull("quality") ?: ""

                    loadExtractor(src, "$mainUrl/", subtitleCallback) { link ->
                        val linkQuality = getQualityFromName(quality)
                            .takeIf { it != Qualities.Unknown.value } ?: link.quality
                        val label = if (langLabel.isNotBlank()) {
                            " ($langLabel${if (quality.isNotBlank() && quality != "Unknown") " $quality" else ""})"
                        } else ""
                        emitted++
                        callback.invoke(
                            ExtractorLink(
                                source = if (langLabel.isNotBlank()) "${link.source} - $langLabel" else link.source,
                                name = "${link.name}$label",
                                url = link.url,
                                referer = link.referer,
                                quality = linkQuality,
                                isM3u8 = link.isM3u8,
                                headers = link.headers,
                                extractorData = link.extractorData
                            )
                        )
                    }
                }
                if (emitted > 0) return true
            }
        }

        val iframes = document.select("iframe[src]").mapNotNull { fixUrlNull(it.attr("src")) }
        iframes.forEach { iframe ->
            Log.d("DZL", "iframe » $iframe")
            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        return iframes.isNotEmpty()
    }

    private fun extractJsonArray(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped   -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"'  -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun jsonImage(node: JsonNode?): String? = when {
        node == null -> null
        node.isTextual -> node.asText().takeIf { it.isNotBlank() }
        node.isArray -> node.firstOrNull()?.let { jsonImage(it) }
        node.isObject -> node.textOrNull("url")
        else -> null
    }

    private fun JsonNode.textOrNull(field: String): String? =
        path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
}
