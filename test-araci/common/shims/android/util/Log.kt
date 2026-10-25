package android.util

object Log {
    private val verbose = System.getenv("VERBOSE") == "1"
    private fun p(l: String, t: String?, m: String?, e: Throwable? = null): Int { if (verbose) System.err.println("$l/$t: $m${e?.let { " | $it" } ?: ""}"); return 0 }
    @JvmStatic fun d(t: String?, m: String): Int = p("D", t, m)
    @JvmStatic fun i(t: String?, m: String): Int = p("I", t, m)
    @JvmStatic fun w(t: String?, m: String): Int = p("W", t, m)
    @JvmStatic fun v(t: String?, m: String): Int = p("V", t, m)
    @JvmStatic fun e(t: String?, m: String): Int = p("E", t, m)
    @JvmStatic fun e(t: String?, m: String?, e: Throwable?): Int = p("E", t, m, e)
    @JvmStatic fun w(t: String?, m: String?, e: Throwable?): Int = p("W", t, m, e)
}
