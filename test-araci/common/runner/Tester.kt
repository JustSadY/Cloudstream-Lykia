package lykia.tester

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration


private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()

private suspend fun <T> step(name: String, notes: MutableList<String>, seconds: Long = 60, block: suspend () -> T): T? = try {
    withTimeout(seconds * 1000) { block() }
} catch (e: TimeoutCancellationException) {
    notes.add("$name:timeout"); null
} catch (e: Throwable) {
    notes.add("$name:${e::class.simpleName}:${e.message?.take(80)?.replace("|", "/")}"); null
}

private fun probe(link: ExtractorLink): String = try {
    val req = HttpRequest.newBuilder(URI(link.url.replace(" ", "%20"))).timeout(Duration.ofSeconds(20)).GET()
    req.header("User-Agent", USER_AGENT)
    if (link.referer.isNotBlank()) req.header("Referer", link.referer)
    link.headers.forEach { (k, v) -> if (k.lowercase() !in setOf("host", "connection", "content-length")) runCatching { req.header(k, v) } }
    req.header("Range", "bytes=0-2047")
    val resp = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream())
    val head = resp.body().use { it.readNBytes(512) }
    val text = String(head, Charsets.ISO_8859_1)
    val kind = when {
        text.startsWith("#EXTM3U") -> "m3u8"
        text.contains("<MPD") -> "dash"
        head.size > 8 && (String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" || head[0] == 0x47.toByte() || head[0] == 0x1A.toByte()) -> "video"
        resp.headers().firstValue("content-type").orElse("").startsWith("video") -> "video"
        else -> "?" + resp.headers().firstValue("content-type").orElse("").take(25)
    }
    "${resp.statusCode()}:$kind"
} catch (e: Throwable) {
    "ERR:${e::class.simpleName}"
}

private suspend fun test(plugin: String, api: MainAPI) {
    val notes = mutableListOf<String>()
    step("home", notes, 30) {
        val r = app.get(api.mainUrl)
        val title = Regex("<title>([^<]{0,50})").find(r.text)?.groupValues?.get(1)?.trim()
        notes.add("home:${r.code}:${r.url.take(60)}:${r.text.length}b:${title}")
    }
    var items: List<SearchResponse> = emptyList()
    var mainCount = "-"
    if (api.hasMainPage && api.mainPage.isNotEmpty()) {
        val req = api.mainPage.first()
        val home = step("main", notes) { api.getMainPage(1, MainPageRequest(req.name, req.data, req.horizontalImages)) }
        items = home?.items?.flatMap { it.list } ?: emptyList()
        mainCount = items.size.toString()
    }
    val query = items.firstOrNull()?.name?.split(" ")?.firstOrNull { it.length >= 3 } ?: "the"
    val found = step("search", notes) { api.search(query) }
    if (items.isEmpty()) items = found ?: emptyList()

    var loadOk = "-"
    var linkCount = "-"
    var play = "-"
    val adaylar = listOfNotNull(System.getenv("TEST_PAGE")?.takeIf { it.isNotBlank() }) + items.take(5).map { it.url }
    for (itemUrl in adaylar) {
        val lr = step("load", notes) { api.load(itemUrl) } ?: continue
        loadOk = "ok"
        val data = when (lr) {
            is MovieLoadResponse      -> lr.dataUrl
            is TvSeriesLoadResponse   -> lr.episodes.firstOrNull()?.data
            is AnimeLoadResponse      -> lr.episodes.values.flatten().firstOrNull()?.data
            is LiveStreamLoadResponse -> lr.dataUrl
            else                      -> null
        }
        if (data == null) { notes.add("load:veri-yok(${lr::class.simpleName})"); continue }
        val links = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
        step("links", notes, 120) { api.loadLinks(data, false, {}, { links.add(it) }) }
        linkCount = links.size.toString()
        if (links.isNotEmpty()) {
            play = links.take(3).joinToString(",") { "${it.source}=${probe(it)}" }
            break
        }
    }
    println("SONUC|$plugin|${api.name}|${api.mainUrl}|main=$mainCount|search=${found?.size ?: "x"}($query)|load=$loadOk|links=$linkCount|play=$play|${notes.distinct().joinToString(" ; ")}")
}

fun main(args: Array<String>): Unit = runBlocking(Dispatchers.IO) {
    val pluginDir = File(args[0])
    val pluginName = pluginDir.name
    val pluginFile = pluginDir.walk().firstOrNull { it.name.endsWith("Plugin.kt") } ?: error("Plugin.kt yok")
    val text = pluginFile.readText()
    val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)!!.groupValues[1]

    Regex("""registerExtractorAPI\((\w+)\(\)\)""").findAll(text).forEach {
        runCatching { extractorApis.add(Class.forName("$pkg.${it.groupValues[1]}").getDeclaredConstructor().newInstance() as ExtractorApi) }
            .onFailure { e -> println("UYARI|$pluginName|extractor ${it.groupValues[1]}: $e") }
    }
    System.getenv("TEST_URL")?.takeIf { it.isNotBlank() }?.let { url ->
        val links = mutableListOf<ExtractorLink>()
        val ok = loadExtractor(url, System.getenv("TEST_REF"), { println("ALTYAZI|${it.lang}|${it.url}") }, { links.add(it) })
        println("EXTRACT|$url|found=$ok|links=${links.size}")
        links.forEach { println("LINK|${it.source}|${it.name}|${it.type}|${it.url.take(150)}|ref=${it.referer}|${probe(it)}") }
        System.out.flush(); Runtime.getRuntime().halt(0)
    }
    Regex("""registerMainAPI\(`?(\w+)`?\(\)\)""").findAll(text).forEach {
        val api = runCatching { Class.forName("$pkg.${it.groupValues[1]}").getDeclaredConstructor().newInstance() as MainAPI }
            .getOrElse { e -> println("SONUC|$pluginName|${it.groupValues[1]}|-|olusturulamadi: $e"); return@forEach }
        test(pluginName, api)
    }
    System.out.flush(); Runtime.getRuntime().halt(0)
}
