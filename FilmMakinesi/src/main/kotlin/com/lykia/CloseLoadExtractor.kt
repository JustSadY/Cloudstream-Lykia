package com.lykia

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
import java.net.URI

private fun getm3uLink(data: String): String {
    val first  = Base64.decode(data,Base64.DEFAULT).reversedArray()
    val second = Base64.decode(first, Base64.DEFAULT)
    val result = second.toString(Charsets.UTF_8).split("|")[1]

    return result
}

private val TRACK_REGEX = Regex(""""file":"([^"]+)"[^}]*?"label":"([^"]+)"[^}]*?"kind":"captions"|"file":"([^"]+)"[^}]*?"kind":"captions"[^}]*?"label":"([^"]+)"""")

private fun String.jsonUnescape() = this.replace("\\/", "/").replace(Regex("""\\u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }

open class CloseLoad : ExtractorApi() {
    override val name            = "CloseLoad"
    override val mainUrl         = "https://closeload.filmmakinesi.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        Log.d("${this.name}", "url » $url")

        val iSource = app.get(url, referer = extRef)
        val html    = iSource.text

        iSource.document.select("track").forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = it.attr("label"),
                    url  = fixUrl(it.attr("src"))
                )
            )
        }
        TRACK_REGEX.findAll(html).forEach {
            val (file1, label1, file2, label2) = it.destructured
            subtitleCallback.invoke(SubtitleFile(lang = (label1 + label2).jsonUnescape(), url = fixUrl((file1 + file2).jsonUnescape())))
        }

        val m3uLink = OynaticiCozucu.coz(html, URI(url).host) ?: run {
            val obfuscatedScript = iSource.document.select("script[type=text/javascript]")[1].data().trim()
            val rawScript        = getAndUnpack(obfuscatedScript)
            val (data)           = Regex("""return result\}var .*?=.*?\("(.*?)"\)""").find(rawScript)?.destructured ?: throw ErrorLoadingException("data not found")
            getm3uLink(data)
        }
        Log.d("${this.name}", "m3uLink » $m3uLink")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = m3uLink,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "${mainUrl}/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

open class RapidFilmMakinesi : ExtractorApi() {
    override val name            = "Rapid"
    override val mainUrl         = "https://rapid.filmmakinesi.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("${this.name}", "url » $url")
        val html = app.get(url, referer = referer ?: "").text

        TRACK_REGEX.findAll(html).forEach {
            val (file1, label1, file2, label2) = it.destructured
            subtitleCallback.invoke(SubtitleFile(lang = (label1 + label2).jsonUnescape(), url = fixUrl((file1 + file2).jsonUnescape())))
        }

        val m3uLink = OynaticiCozucu.coz(html, URI(url).host) ?: throw ErrorLoadingException("Video bulunamadı")
        Log.d("${this.name}", "m3uLink » $m3uLink")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = m3uLink,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "${mainUrl}/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
