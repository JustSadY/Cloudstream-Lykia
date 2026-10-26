package com.lykia

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink

fun dedupSearchResults(results: List<SearchResponse>): List<SearchResponse> {
    val seenUrls = mutableSetOf<String>()
    val seenKeys = mutableSetOf<String>()
    val output = mutableListOf<SearchResponse>()
    for (item in results) {
        val normUrl = item.url.trim().removeSuffix("/")
        val key = "${item.name.lowercase()}:${item.type?.name ?: "unknown"}"
        if (!seenUrls.add(normUrl) || !seenKeys.add(key)) continue
        output.add(item)
    }
    return output
}

suspend fun <T> resolveProgressive(
    candidates: List<T>,
    resolver: suspend (candidate: T, emitLink: (ExtractorLink) -> Unit) -> Unit,
    onLinkFound: (ExtractorLink) -> Unit
): Int {
    var count = 0
    val seen = mutableSetOf<String>()
    for (candidate in candidates) {
        try {
            resolver(candidate) { link ->
                if (link.url.isNotBlank() && seen.add(link.url.trim())) {
                    count++
                    onLinkFound(link)
                }
            }
        } catch (_: Exception) {
        }
    }
    return count
}
