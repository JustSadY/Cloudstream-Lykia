package com.lykia

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class VidMoly : ExtractorApi() {
    override val name            = "VidMoly"
    override val mainUrl         = "https://vidmoly.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val embedUrl = (if (url.contains("/w/")) url.replaceFirst("/w/", "/embed-") + ".html" else url)
            .replace(Regex("""^https?://(www\.)?vidmoly\.[a-z]+"""), "https://vidmoly.biz")
        val response = app.get(embedUrl, referer = referer, headers = mapOf("Sec-Fetch-Dest" to "iframe"))
        val html     = response.text
        val m3u8     = Regex("""sources:\s*\[\{\s*file:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: throw ErrorLoadingException("Video bulunamadı")
        Log.d("VidMoly", "m3u8 » $m3u8")

        Regex("""\{\s*file:\s*['"]([^'"]+\.vtt[^'"]*)['"]\s*,\s*label:\s*['"]([^'"]+)['"]""").findAll(html).forEach {
            subtitleCallback.invoke(SubtitleFile(it.groupValues[2], fixUrl(it.groupValues[1])))
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = m3u8,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://${java.net.URI(response.url).host}/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class VidMolyBiz : VidMoly() {
    override val mainUrl = "https://vidmoly.biz"
}

class VidMolyTo : VidMoly() {
    override val mainUrl = "https://vidmoly.to"
}
