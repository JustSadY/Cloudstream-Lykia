package com.lykia

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Animeler : MainAPI() {
    override var mainUrl = "https://animeler.live"
    override var name = "Animeler"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "${mainUrl}" to "Son Eklenen Bölümler",
        "${mainUrl}/filter?sort=popular" to "Popüler Animeler",
        "${mainUrl}/filter?sort=new" to "Yeni Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("?")) "${request.data}&page=${page}" else "${request.data}?page=${page}"
        }

        val document = app.get(targetUrl).document
        val items = parseSearchResults(document)
        return newHomePageResponse(request.name, items)
    }

    fun toSearchResult(element: Element): SearchResponse? {
        val isCard = element.tagName() == "article"
        val link = when {
            element.tagName() == "a" -> element
            isCard -> element.selectFirst("a.mac__title-row, a.mac__hit, a") ?: return null
            else -> element.selectFirst("a") ?: return null
        }
        var href = fixUrlNull(link.attr("href")) ?: return null
        if (href.contains("/user/") || href.contains("/filter") || href.contains("/filtre") || href.contains("/discord")) return null

        if (href.contains("/bolum-")) {
            href = href.substringBefore("/bolum-")
        }

        val img = element.selectFirst("img")
        val title = (if (isCard) element.attr("data-title-main").trim() else "")
            .ifBlank { element.selectFirst("span.mac__title, .title, h3, h2, h4, span")?.text()?.trim() ?: "" }
            .ifBlank { img?.attr("alt")?.trim() ?: "" }
            .ifBlank { link.text().trim() }
            .ifBlank { null }
            ?: return null

        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("src")?.ifBlank { null }
        )

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), hasNext = false)

        val response = app.get("$mainUrl/api/search?q=${URLEncoder.encode(query, "UTF-8")}").text
        val results = runCatching { JSONObject(response).optJSONArray("results") }.getOrNull()
        val items = mutableListOf<SearchResponse>()
        if (results != null) {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val href = fixUrlNull(item.optString("url")) ?: continue
                val title = item.optString("title").trim().ifBlank { href.substringAfterLast("/") }
                val poster = item.optString("image").takeIf { it.isNotBlank() }
                items.add(
                    newAnimeSearchResponse(title, href, TvType.Anime) {
                        this.posterUrl = poster
                    }
                )
            }
        }
        return newSearchResponseList(items, hasNext = false)
    }

    fun parseSearchResults(document: Document): List<SearchResponse> {
        val elements = document.select("article.mac, a.anime-card-modern, a:has(img[src*='animecover']), a:has(img[src*='portrait']), div.anime-card, div.col a")
        val results = elements.mapNotNull { toSearchResult(it) }
        return dedupSearchResults(results)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return parseLoadMetadata(document, url)
    }

    suspend fun parseLoadMetadata(document: Document, url: String): LoadResponse? {
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("İzle").substringBefore("-").trim()

        var poster = fixUrlNull(
            document.selectFirst("div.mir-poster img, img[src*='anilistcdn']")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }
        )
        if (poster == null) {
            for (img in document.select("img")) {
                val src = img.attr("data-src").ifBlank { img.attr("src") }
                if (src.contains("animecover") || src.contains("portrait")) {
                    poster = fixUrlNull(src)
                    break
                }
            }
        }

        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: document.selectFirst("div.anime-synopsis, p.synopsis, div.synopsis, div.desc")?.text()?.trim()

        val episodeElements = document.select("a[href*='/bolum-']")
        val episodes = mutableListOf<Episode>()

        val epRegex = Regex("""/bolum-(\d+)""")
        val seenUrls = mutableSetOf<String>()

        for (el in episodeElements) {
            val href = fixUrlNull(el.attr("href")) ?: continue
            if (!seenUrls.add(href)) continue

            val match = epRegex.find(href)
            val epNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            val epName = el.text().trim().takeIf { it.isNotBlank() } ?: "$epNum. Bölüm"

            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.season = 1
                    this.episode = epNum
                }
            )
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        val document = app.get(data).document

        val iframes = mutableListOf<String>()
        document.select("iframe[id='fansubPlayerIframe'], iframe[src*='embed'], iframe[data-player-src]").forEach {
            val src = fixUrlNull(it.attr("src").ifBlank { it.attr("data-player-src") })
            if (!src.isNullOrBlank()) {
                iframes.add(src)
            }
        }

        val candidates = iframes.distinct()

        val embedHeaders = mapOf(
            "Referer" to data,
            "Origin" to mainUrl,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1"
        )

        val count = resolveProgressive(
            candidates = candidates,
            resolver = { embedUrl, emitLink ->
                try {
                    val html = app.get(embedUrl, headers = embedHeaders).text
                    val bootJson = Regex(
                        """window\.__PLAYER_BOOT__\s*=\s*(\{.*?\});""",
                        RegexOption.DOT_MATCHES_ALL
                    ).find(html)?.groupValues?.get(1)
                    val master = bootJson?.let { runCatching { JSONObject(it).optString("master") }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }

                    if (master != null) {
                        emitLink(
                            newExtractorLink(
                                source = name,
                                name = "$name HLS",
                                url = master,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = embedUrl
                                this.headers = mapOf("Accept" to "*/*")
                                this.quality = Qualities.P1080.value
                            }
                        )
                    } else {
                        val embedDoc = org.jsoup.Jsoup.parse(html)
                        val innerPlayer = embedDoc.selectFirst("iframe#innerPlayer, iframe[src]")?.attr("src")
                        if (!innerPlayer.isNullOrBlank()) {
                            val fixedInner = fixUrl(innerPlayer)
                            loadExtractor(fixedInner, embedUrl, subtitleCallback, emitLink)
                        } else {
                            val m3u8Regex = Regex("""["'](https?://[^\s"']+\.m3u8[^\s"']*)["']""")
                            m3u8Regex.findAll(html).forEach { m ->
                                val streamUrl = m.groupValues[1]
                                emitLink(
                                    newExtractorLink(
                                        source = name,
                                        name = "$name HLS",
                                        url = streamUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = embedUrl
                                        this.headers = mapOf("Accept" to "*/*")
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            },
            onLinkFound = { link ->
                callback(link)
                found = true
            }
        )

        return found || count > 0
    }
}
