package com.lykia

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class RapidVid : ExtractorApi() {
    override val name            = "RapidVid"
    override val mainUrl         = "https://rapidvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoReq = app.get(url, referer=extRef).text

        val subUrls = mutableSetOf<String>()
        Regex("""captions","file":"([^"]+)","label":"([^"]+)"""").findAll(videoReq).forEach {
            val (subUrl, subLang) = it.destructured

            if (subUrl in subUrls) { return@forEach }
            subUrls.add(subUrl)

            subtitleCallback.invoke(
                SubtitleFile(
                    lang = subLang.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç"),
                    url  = fixUrl(subUrl.replace("\\", ""))
                )
            )
        }

        val telemetryUrl = Regex(""""h264":"([^"]+)"""").find(videoReq)?.groupValues?.get(1)?.replace("\\/", "/")
        if (telemetryUrl != null) {
            Log.d("${this.name}", "telemetryUrl » $telemetryUrl")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name   = this.name,
                    url    = telemetryUrl,
                    type   = ExtractorLinkType.M3U8
                ) {
                    this.referer = "${mainUrl}/"
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }

        var extractedValue   = Regex("""file": "(.*)",""").find(videoReq)?.groupValues?.get(1)
        val decoded: String?

        if (extractedValue != null) {
            val bytes = extractedValue.split("\\x").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
            decoded   = String(bytes, Charsets.UTF_8)
        } else {
            val evalJWSsetup = Regex("""\};\s*(eval\(function[\s\S]*?)var played = \d+;""").find(videoReq)?.groupValues?.get(1) ?: throw ErrorLoadingException("File not found")
            @Suppress("LocalVariableName")
            val JWSsetup      = getAndUnpack(getAndUnpack(evalJWSsetup)).replace("\\\\", "\\")
            extractedValue  = Regex("""file":"(.*)","label""").find(JWSsetup)?.groupValues?.get(1)?.replace("\\\\x", "")

            val bytes = extractedValue?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
            decoded   = bytes?.toString(Charsets.UTF_8) ?: throw ErrorLoadingException("File not found")
        }
        Log.d("${this.name}", "decoded » $decoded")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = decoded,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = extRef
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class RapidVidOrg : RapidVid() {
    override val mainUrl = "https://rapidvid.org"
}
