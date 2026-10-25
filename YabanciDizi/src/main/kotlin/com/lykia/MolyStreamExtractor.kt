package com.lykia

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

open class MolyStream : ExtractorApi() {
    override val name            = "MolyStream"
    override val mainUrl         = "https://ydx.molystream.org"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        Log.d("YBD_${this.name}", "url » $url")

        val html     = app.get(url, referer = referer).text
        val sifreli  = Regex("""CryptoJS\.AES\.decrypt\("([^"]+)",\s*"([^"]+)"\)""").find(html) ?: throw ErrorLoadingException("Şifreli oynatıcı bulunamadı")
        val oynatici = cryptoJsDecrypt(sifreli.groupValues[1], sifreli.groupValues[2])
        val m3u8     = Regex("""file:\s*['"]([^'"]+)['"]""").find(oynatici)?.groupValues?.get(1) ?: throw ErrorLoadingException("Video bulunamadı")
        Log.d("YBD_${this.name}", "m3u8 » $m3u8")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name   = this.name,
                url    = m3u8,
                type   = ExtractorLinkType.M3U8
            ) {
                this.referer = "${mainUrl}/"
                this.quality = Qualities.Unknown.value
            }
        )
    }

    private fun cryptoJsDecrypt(sifreliMetin: String, parola: String): String {
        val veri     = Base64.decode(sifreliMetin, Base64.DEFAULT)
        val tuz      = veri.copyOfRange(8, 16)
        val anahtar  = evpBytesToKey(parola.toByteArray(), tuz, 48)
        val cipher   = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(anahtar.copyOfRange(0, 32), "AES"), IvParameterSpec(anahtar.copyOfRange(32, 48)))

        return String(cipher.doFinal(veri.copyOfRange(16, veri.size)), Charsets.UTF_8)
    }

    private fun evpBytesToKey(parola: ByteArray, tuz: ByteArray, uzunluk: Int): ByteArray {
        val md5    = MessageDigest.getInstance("MD5")
        val sonuc  = ByteArrayOutputStream()
        var onceki = ByteArray(0)
        while (sonuc.size() < uzunluk) {
            md5.update(onceki)
            md5.update(parola)
            md5.update(tuz)
            onceki = md5.digest()
            sonuc.write(onceki)
        }

        return sonuc.toByteArray().copyOf(uzunluk)
    }
}
