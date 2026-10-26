package com.lykia

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class DDiziProvider : MainAPI() {
    override var mainUrl              = "https://www.ddizi.tel"
    override var name                 = "DDizi"
    override var lang                 = "tr"
    override val hasMainPage         = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes      = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/yeni-eklenenler1" to "Son Eklenen Bölümler",
        "$mainUrl/yabanci-dizi-izle" to "Yabancı Diziler",
        "$mainUrl/eski.diziler" to "Eski Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}/$page"
        } else {
            request.data
        }
        Log.d("DDizi:", "Request for $url with page $page")
        val document = app.get(url, headers = getHeaders(mainUrl)).document
        
        val home = mutableListOf<SearchResponse>()
        
        try {
            val boxCatResults = document.select("div.dizi-boxpost-cat").mapNotNull { it.toSearchResult() }
            if (boxCatResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxCatResults.size} box-cat results")
                home.addAll(boxCatResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box-cat results: ${e.message}")
        }
        
        try {
            val boxResults = document.select("div.dizi-boxpost").mapNotNull { it.toSearchResult() }
            if (boxResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxResults.size} box results")
                home.addAll(boxResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box results: ${e.message}")
        }
        
        val hasNextPage = document.select(".pagination a").any { it.text().contains("Sonraki") }
        
        Log.d("DDizi:", "Added ${home.size} total episodes, hasNext: $hasNextPage")
        return newHomePageResponse(request.name, home, hasNextPage)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a") ?: return null
        val title = linkElement.text()?.trim() ?: return null
        val href = fixUrl(linkElement.attr("href") ?: return null)
        
        val img = this.selectFirst("img.img-back, img.img-back-cat")
        val posterUrl = when {
            img?.hasAttr("data-src") == true -> fixUrlNull(img.attr("data-src"))
            img?.hasAttr("src") == true -> fixUrlNull(img.attr("src"))
            else -> null
        }
        
        val description = this.selectFirst("p")?.text()
        val commentCount = this.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        
        Log.d("DDizi:", "Found item: $title, $href, posterUrl: $posterUrl, comments: $commentCount")
        
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        Log.d("DDizi:", "Searching for $query")
        
        val formData = mapOf("arama" to query)
        
        val document = app.post(
            "$mainUrl/arama/", 
            data = formData, 
            headers = getHeaders(mainUrl)
        ).document
        val results = ArrayList<SearchResponse>()
        
        try {
            val boxCatResults = document.select("div.dizi-boxpost-cat").mapNotNull { it.toSearchResult() }
            if (boxCatResults.isNotEmpty()) {
                Log.d("DDizi:", "Found ${boxCatResults.size} box-cat results")
                results.addAll(boxCatResults)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing box-cat search results: ${e.message}")
        }
        
        if (results.isEmpty()) {
            try {
                val boxResults = document.select("div.dizi-boxpost").mapNotNull { it.toSearchResult() }
                if (boxResults.isNotEmpty()) {
                    Log.d("DDizi:", "Found ${boxResults.size} box results")
                    results.addAll(boxResults)
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing box search results: ${e.message}")
            }
        }
        
        if (results.isEmpty()) {
            try {
                val altResults = document.select("div.dizi-listesi a, div.yerli-diziler li a, div.yabanci-diziler li a").mapNotNull { 
                    val title = it.text()?.trim() ?: return@mapNotNull null
                    val href = fixUrl(it.attr("href") ?: return@mapNotNull null)
                    
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = null
                    }
                }
                
                if (altResults.isNotEmpty()) {
                    Log.d("DDizi:", "Found ${altResults.size} alternative results")
                    results.addAll(altResults)
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing alternative search results: ${e.message}")
            }
        }
        
        Log.d("DDizi:", "Returning total ${results.size} search results")
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("DDizi:", "Loading $url")
        val document = app.get(url, headers = getHeaders(mainUrl)).document

        val fullTitle = document.selectFirst("h1, h2, div.dizi-boxpost-cat a")?.text()?.trim() ?: ""
        Log.d("DDizi:", "Full title: $fullTitle")
        
        val seasonRegex = Regex("""(\d+)\.?\s*Sezon""", RegexOption.IGNORE_CASE)
        val episodeRegex = Regex("""(\d+)\.?\s*Bölüm""", RegexOption.IGNORE_CASE)
        val finalRegex = Regex("""Sezon Finali""", RegexOption.IGNORE_CASE)

        val seasonMatch = seasonRegex.find(fullTitle)
        val episodeMatch = episodeRegex.find(fullTitle)
        val isSeasonFinal = finalRegex.find(fullTitle) != null
        
        val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val episodeNumber = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
        
        var title = fullTitle
        
        if (seasonMatch != null) {
            val parts = fullTitle.split(seasonRegex)
            if (parts.isNotEmpty()) {
                title = parts[0].trim()
            }
        } 
        else if (episodeMatch != null) {
            val parts = fullTitle.split(episodeRegex)
            if (parts.isNotEmpty()) {
                title = parts[0].trim()
            }
        }
        
        title = title.replace(Regex("""^\d+\.?\s*"""), "").trim()
        
        Log.d("DDizi:", "Parsed title: $title, Season: $seasonNumber (default: 1), Episode: $episodeNumber, Final: $isSeasonFinal")
        
        val posterImg = document.selectFirst("div.afis img, img.afis, img.img-back, img.img-back-cat")

        val allEpisodes = mutableListOf<Episode>()
        var currentPage = 0
        var hasMorePages = true

        while (hasMorePages) {
            val pageUrl = if (url.contains("/dizi/") || url.contains("/diziler/")) {
                if (currentPage == 0) url else "$url/sayfa-$currentPage"
            } else {
                url
            }

            val pageDocument = if (currentPage == 0) document else app.get(pageUrl, headers = getHeaders(mainUrl)).document
            Log.d("DDizi:", "Loading page: $pageUrl")

            val pageEpisodes = pageDocument.select("div.bolumler a, div.sezonlar a, div.dizi-arsiv a, div.dizi-boxpost-cat a").map { ep ->
                val name = ep.text().trim()
                val href = fixUrl(ep.attr("href"))
                
                val epSeasonMatch = seasonRegex.find(name)
                val epEpisodeMatch = episodeRegex.find(name)
                val epIsSeasonFinal = finalRegex.find(name) != null
                
                val epSeasonNumber = epSeasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epEpisodeNumber = epEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
                
                val epDescription = ep.parent()?.selectFirst("p")?.text()
                val epCommentCount = ep.parent()?.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                
                Log.d("DDizi:", "Found episode: $name, Season: $epSeasonNumber, Episode: $epEpisodeNumber, Final: $epIsSeasonFinal, Comments: $epCommentCount")
                
                newEpisode(href) {
                    this.name = name
                    this.season = epSeasonNumber
                    this.episode = epEpisodeNumber
                    this.description = epDescription
                }
            }

            if (pageEpisodes.isNotEmpty()) {
                allEpisodes.addAll(pageEpisodes)
                currentPage++
                hasMorePages = pageDocument.select(".pagination a").any { it.text().contains("Sonraki") }
                Log.d("DDizi:", "Found ${pageEpisodes.size} episodes on page $currentPage, hasMorePages: $hasMorePages")
            } else {
                hasMorePages = false
            }
        }

        Log.d("DDizi:", "Total episodes found: ${allEpisodes.size}")

        if (allEpisodes.isEmpty() && !url.contains("/dizi/") && !url.contains("/diziler/")) {
            allEpisodes.add(
                newEpisode(url) {
                    this.name = fullTitle
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.description = document.selectFirst("div.dizi-aciklama, div.aciklama, p")?.text()?.trim()
                }
            )
        }
        val poster = when {
            posterImg?.hasAttr("data-src") == true -> fixUrlNull(posterImg.attr("data-src"))
            posterImg?.hasAttr("src") == true -> fixUrlNull(posterImg.attr("src"))
            else -> null
        }
        
        val plot = document.selectFirst("div.dizi-aciklama, div.aciklama, p")?.text()?.trim()
        
        val commentCount = document.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        Log.d("DDizi:", "Comment count: $commentCount")

        Log.d("DDizi:", "Loaded title: $title, poster: $poster")

        if (allEpisodes.isEmpty()) {
            try {
                if (url.contains("/dizi/") || url.contains("/diziler/")) {
                    val eps = document.select("div.bolumler a, div.sezonlar a, div.dizi-arsiv a, div.dizi-boxpost-cat a").map { ep ->
                        val name = ep.text().trim()
                        val href = fixUrl(ep.attr("href"))
                        
                        val epSeasonMatch = seasonRegex.find(name)
                        val epEpisodeMatch = episodeRegex.find(name)
                        val epIsSeasonFinal = finalRegex.find(name) != null
                        
                        val epSeasonNumber = epSeasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epEpisodeNumber = epEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
                        
                        val epDescription = ep.parent()?.selectFirst("p")?.text()
                        val epCommentCount = ep.parent()?.selectFirst("span.comments-ss")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                        
                        Log.d("DDizi:", "Episode: $name, Season: $epSeasonNumber (default: 1), Episode: $episodeNumber, Final: $epIsSeasonFinal, Comments: $epCommentCount")
                        
                        newEpisode(href) {
                            this.name = name
                            this.season = epSeasonNumber
                            this.episode = epEpisodeNumber
                            this.description = epDescription
                        }
                    }
                    Log.d("DDizi:", "Found ${eps.size} episodes")
                    allEpisodes.addAll(eps)
                } else {
                    Log.d("DDizi:", "Single episode page, adding current episode with Season: $seasonNumber (default: 1)")
                    
                    allEpisodes.add(
                        newEpisode(url) {
                            this.name = fullTitle
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.description = plot
                        }
                    )
                }
            } catch (e: Exception) {
                Log.d("DDizi:", "Error parsing episodes: ${e.message}")
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, allEpisodes) {
            this.posterUrl = poster
            this.plot = plot
            this.year = null
            this.tags = null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DDizi:", "Loading links for $data")
        val document = app.get(data, headers = getHeaders(mainUrl)).document
        
        try {
            val ogVideo = document.selectFirst("meta[property=og:video]")?.attr("content")
            if (!ogVideo.isNullOrEmpty()) {
                Log.d("DDizi:", "Found og:video meta tag: $ogVideo")
                
                val playerDoc = app.get(
                    ogVideo, 
                    headers = getHeaders(data)
                ).document
                val scripts = playerDoc.select("script")
                
                scripts.forEach { script ->
                    val content = script.html()
                    if (content.contains("jwplayer") && content.contains("sources")) {
                        Log.d("DDizi:", "Found jwplayer configuration")
                        
                        val sourcesRegex = Regex("""sources:\s*\[\s*\{(.*?)\}\s*,?\s*\]""", RegexOption.DOT_MATCHES_ALL)
                        val sourcesMatch = sourcesRegex.find(content)
                        
                        if (sourcesMatch != null) {
                            val fileRegex = Regex("""file:\s*["'](.*?)["']""")
                            val fileMatch = fileRegex.find(sourcesMatch.groupValues[1])
                            
                            if (fileMatch != null) {
                                val fileUrl = fileMatch.groupValues[1].replace("\\/", "/")
                                Log.d("DDizi:", "Found video source: $fileUrl")
                                
                                val fileType = when {
                                    fileUrl.contains(".m3u8") || fileUrl.contains("hls") || fileUrl.contains("master.txt") -> "hls"
                                    else -> "mp4"
                                }
                                
                                val qualityRegex = Regex("""label:\s*["'](.*?)["']""")
                                val qualityMatch = qualityRegex.find(sourcesMatch.groupValues[1])
                                val quality = qualityMatch?.groupValues?.get(1) ?: "Auto"
                                
                                Log.d("DDizi:", "Video type: $fileType, quality: $quality")
                                
                                val videoHeaders = if (fileUrl.contains("master.txt")) {
                                    mapOf(
                                        "accept" to "*/*",
                                        "accept-language" to "tr-TR,tr;q=0.5",
                                        "cache-control" to "no-cache",
                                        "pragma" to "no-cache",
                                        "sec-ch-ua" to "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\"",
                                        "sec-ch-ua-mobile" to "?0",
                                        "sec-ch-ua-platform" to "\"Windows\"",
                                        "sec-fetch-dest" to "empty",
                                        "sec-fetch-mode" to "cors",
                                        "sec-fetch-site" to "cross-site",
                                        "user-agent" to USER_AGENT,
                                        "referer" to ogVideo 
                                    )
                                } else {
                                    getHeaders(ogVideo)
                                }
                                
                                Log.d("DDizi:", "Using headers for video source: ${videoHeaders.keys.joinToString()}")

                                val twimg = fileUrl.contains("video.twimg.com")
                                val linkHeaders = if (twimg) videoHeaders - "referer" else videoHeaders
                                val linkReferer = if (twimg) "" else ogVideo

                                callback.invoke(
                                    ExtractorLink(
                                        source = name,
                                        name = "$name - $quality",
                                        url = fileUrl,
                                        referer = linkReferer,
                                        quality = getQualityFromName(quality),
                                        headers = linkHeaders,
                                        type = if (fileType == "hls") ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    )
                                )
                                if (fileType == "hls") {
                                    try {
                                        Log.d("DDizi:", "Generating M3u8 for: $fileUrl")
                                        M3u8Helper.generateM3u8(
                                            name,
                                            fileUrl,
                                            linkReferer,
                                            headers = linkHeaders
                                        ).forEach(callback)
                                    } catch (e: Exception) {
                                        Log.d("DDizi:", "Error generating M3u8: ${e.message}")
                                        
                                        if (fileUrl.contains("master.txt")) {
                                            try {
                                                Log.d("DDizi:", "Trying to get master.txt content directly")
                                                val masterContent = app.get(fileUrl, headers = linkHeaders).text
                                                Log.d("DDizi:", "Master.txt content length: ${masterContent.length}")
                                                
                                                val m3u8Regex = Regex("""(https?://.*?\.m3u8[^"\s]*)""")
                                                val m3u8Matches = m3u8Regex.findAll(masterContent)
                                                
                                                m3u8Matches.forEach { m3u8Match ->
                                                    val m3u8Url = m3u8Match.groupValues[1]
                                                    Log.d("DDizi:", "Found m3u8 in master.txt: $m3u8Url")
                                                    
                                                    val m3u8Quality = when {
                                                        m3u8Url.contains("1080") -> "1080p"
                                                        m3u8Url.contains("720") -> "720p"
                                                        m3u8Url.contains("480") -> "480p"
                                                        m3u8Url.contains("360") -> "360p"
                                                        else -> "Auto"
                                                    }
                                                    
                                                    callback.invoke(
                                                        ExtractorLink(
                                                            source = name,
                                                            name = "$name - $m3u8Quality",
                                                            url = m3u8Url,
                                                            referer = linkReferer,
                                                            quality = getQualityFromName(m3u8Quality),
                                                            headers = linkHeaders,
                                                            type = ExtractorLinkType.M3U8
                                                        )
                                                    )
                                                }
                                            } catch (e2: Exception) {
                                                Log.d("DDizi:", "Error parsing master.txt: ${e2.message}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                loadExtractor(ogVideo, data, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.d("DDizi:", "Error parsing og:video meta tag: ${e.message}")
        }
        

        return true
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        
        private fun getHeaders(referer: String): Map<String, String> {
            return mapOf(
                "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                "accept-language" to "tr-TR,tr;q=0.5",
                "cache-control" to "no-cache",
                "pragma" to "no-cache",
                "sec-ch-ua" to "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "sec-fetch-dest" to "document",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-site" to "same-origin",
                "sec-fetch-user" to "?1",
                "upgrade-insecure-requests" to "1",
                "user-agent" to USER_AGENT,
                "referer" to referer
            )
        }
    }
}