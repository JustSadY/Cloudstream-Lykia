package android.util

object Base64 {
    const val DEFAULT = 0; const val NO_PADDING = 1; const val NO_WRAP = 2; const val CRLF = 4; const val URL_SAFE = 8; const val NO_CLOSE = 16
    @JvmStatic fun decode(str: String?, flags: Int): ByteArray = decode((str ?: "").toByteArray(Charsets.ISO_8859_1), flags)
    @JvmStatic fun decode(input: ByteArray, flags: Int): ByteArray {
        val s = String(input, Charsets.ISO_8859_1).filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
        return java.util.Base64.getDecoder().decode(s + "=".repeat((4 - s.length % 4) % 4))
    }
    @JvmStatic fun encodeToString(input: ByteArray, flags: Int): String = String(encode(input, flags), Charsets.ISO_8859_1)
    @JvmStatic fun encode(input: ByteArray, flags: Int): ByteArray {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        val out = String(enc.encode(input), Charsets.ISO_8859_1)
        return (if (flags and NO_WRAP != 0) out else out.chunked(76).joinToString("\n", postfix = "\n")).toByteArray(Charsets.ISO_8859_1)
    }
}
