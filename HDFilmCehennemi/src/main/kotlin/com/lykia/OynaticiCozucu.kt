package com.lykia

import android.util.Log
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import com.lagradost.cloudstream3.utils.getAndUnpack

object OynaticiCozucu {
    private val KAYNAK = Regex("""sources:\s*\[\{\s*file:\s*([A-Za-z_$][\w$]*)\s*[,}]""")
    private val PAKET  = Regex("""eval\(function\(p,a,c,k,e,d\)[\s\S]*?\.split\('\|'\),0,\{\}\)\)""")

    fun coz(html: String, host: String): String? {
        val betikler = Jsoup.parse(html).select("script:not([src])").map { it.data() }

        val degisken = betikler.firstNotNullOfOrNull { betik ->
            KAYNAK.find(betik.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n"))?.groupValues?.get(1)
        } ?: return null
        val tanim    = Regex("""var\s+${Regex.escape(degisken)}\s*=""")

        val kod = betikler.firstOrNull { tanim.containsMatchIn(it) }
            ?: betikler.flatMap { betik -> PAKET.findAll(betik).map { it.value }.toList() }
                .map { getAndUnpack(it) }
                .firstOrNull { tanim.containsMatchIn(it) }
            ?: return null

        return calistir(kod, host, degisken)
    }

    private fun calistir(kod: String, host: String, degisken: String): String? {
        val cx = Context.enter()
        return try {
            cx.isInterpretedMode = true 
            val scope = cx.initSafeStandardObjects()
            cx.evaluateString(scope, tarayiciShim(host), "shim", 1, null)
            cx.evaluateString(scope, kod, "oynatici", 1, null)

            Context.toString(scope.get(degisken, scope)).takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            Log.d("OynaticiCozucu", "çözülemedi » ${e.message}")
            null
        } finally {
            Context.exit()
        }
    }

    private fun tarayiciShim(host: String) = """
        var self = this, window = this;
        var location = { hostname: "$host", host: "$host" };
        var _b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        function atob(s) {
            s = String(s).replace(/[^A-Za-z0-9+\/]/g, "");
            var o = "", b = 0, n = 0;
            for (var i = 0; i < s.length; i++) {
                b = ((b << 6) | _b64.indexOf(s.charAt(i))) & 0xFFFFFF; n += 6;
                if (n >= 8) { n -= 8; o += String.fromCharCode((b >> n) & 255); }
            }
            return o;
        }
        function btoa(s) {
            s = String(s); var o = "";
            for (var i = 0; i < s.length; i += 3) {
                var t = (s.charCodeAt(i) << 16) | ((s.charCodeAt(i + 1) || 0) << 8) | (s.charCodeAt(i + 2) || 0);
                o += _b64.charAt(t >> 18 & 63) + _b64.charAt(t >> 12 & 63)
                   + (i + 1 < s.length ? _b64.charAt(t >> 6 & 63) : "=") + (i + 2 < s.length ? _b64.charAt(t & 63) : "=");
            }
            return o;
        }
    """.trimIndent()
}
