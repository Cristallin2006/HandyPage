package dev.handypage.app.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Converts image bytes to a reader-safe format; returns (bytes, newExt) or null when undecodable. */
fun interface ImageTranscoder {
    fun toSafeFormat(bytes: ByteArray, fromExt: String): Pair<ByteArray, String>?
}

/**
 * M28: downloads article `<img>` resources and embeds them into the EPUB
 * package (calibre-style), rewriting `src` to package-relative paths.
 *
 * Why embed instead of letting the reader WebView load remote URLs:
 * - image CDNs hotlink-block: img2.chinadaily.com.cn 403s anything without a
 *   full browser header set (curl-verified 2026-08-03 — UA + image Accept +
 *   Referer together pass, any subset fails);
 * - offline reading: a cached article keeps its figures forever;
 * - protocol-relative `//host/x.jpg` inside the locally-served EPUB resolves
 *   to cleartext http and dies on Android's network-security policy.
 *
 * Pure JVM (OkHttp + Jsoup), unit-tested via MockWebServer.
 */
class ImageEmbedder(
    private val client: OkHttpClient,
    /** Test hook: production sleeps on the calling (IO) thread. */
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    /**
     * M30: optional transcoder for formats the reader won't display (webp /
     * avif). Null in JVM tests; the app injects a BitmapFactory-backed one —
     * Readium's resource serving silently fails webp in practice (jpg/png
     * render, webp shows the broken-image icon), so we normalize at embed
     * time, calibre-style.
     */
    private val transcoder: ImageTranscoder? = null,
) {

    data class Result(
        val html: String,
        /** Insertion-ordered: package path ("images/img-<sha1>.<ext>") → bytes. */
        val images: Map<String, ByteArray>,
        /** Remote images left in place because the download failed (never worse than pre-M28). */
        val failedCount: Int,
    )

    /**
     * Embeds every absolute http(s) `<img src>` in [contentHtml], at most
     * [MAX_IMAGES] images of [MAX_BYTES] each. [referer] is the article page
     * URL — image CDNs gate on it.
     */
    fun embed(contentHtml: String, referer: String, userAgent: String): Result {
        val doc = Jsoup.parseBodyFragment(contentHtml)
        val images = linkedMapOf<String, ByteArray>()
        var failed = 0
        for (img in doc.select("img[src]")) {
            // M30: `srcset`/`sizes` OVERRIDE `src` in the WebView and still
            // point at remote variants — leaving them re-breaks the embedded
            // image (NASA wind-tunnel figure: local jpg in the package, but
            // the WebView chased the remote srcset and showed the broken
            // icon). Strip them from every image, embedded or not; `loading`
            // (lazy) never fires reliably in the paginated reader either.
            img.removeAttr("srcset")
            img.removeAttr("sizes")
            img.removeAttr("loading")
            val src = img.attr("src").trim()
            if (!src.startsWith("http://") && !src.startsWith("https://")) continue
            if (images.size >= MAX_IMAGES) continue
            val downloaded = runCatching { download(src, referer, userAgent) }.getOrNull()
            if (downloaded == null) {
                failed++
                continue
            }
            val (bytes, ext) = downloaded
            val name = "images/img-" + sha1Hex(src).substring(0, 12) + "." + ext
            images[name] = bytes
            img.attr("src", name)
        }
        return Result(doc.body().html(), images, failed)
    }

    /** Bytes + file extension on success; null after retries are exhausted. */
    private fun download(url: String, referer: String, userAgent: String): Pair<ByteArray, String>? {
        // M30: retry — a single 20s attempt on a flaky CN mobile link to a US
        // CDN loses intermittently (device-verified: the same URL 200s in <1s
        // on desktop but times out on the phone). 4xx answers are permanent
        // and fail fast; 5xx/429 and IO/timeouts get 3 attempts (2s/4s waits).
        var backoffMs = RETRY_BACKOFF_MS
        var attempt = 0
        while (true) {
            attempt++
            when (val outcome = downloadOnce(url, referer, userAgent)) {
                is Outcome.Success -> return outcome.bytes to outcome.ext
                is Outcome.Fatal -> return null
                is Outcome.Retryable -> {
                    if (attempt >= MAX_ATTEMPTS) return null
                    sleeper(backoffMs)
                    backoffMs *= 2
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Success(val bytes: ByteArray, val ext: String) : Outcome
        data object Retryable : Outcome
        data object Fatal : Outcome
    }

    /** M30: formats we normalize away because the reader can't show them. */
    private fun needsTranscode(ext: String): Boolean = ext == "webp" || ext == "avif"

    private fun downloadOnce(url: String, referer: String, userAgent: String): Outcome {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            // img2.chinadaily.com.cn 403s without the full browser header set
            // (curl-verified): the image Accept header is the unusual one.
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .header("Referer", referer)
            .build()
        return try {
            client.newBuilder()
                .callTimeout(IMAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@use if (resp.code == 429 || resp.code >= 500) {
                            Outcome.Retryable
                        } else {
                            Outcome.Fatal
                        }
                    }
                    val contentType = resp.header("Content-Type")
                        ?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                    val ext = EXT_FOR_MIME[contentType] ?: extFromUrl(url)
                        ?: return@use Outcome.Fatal
                    val bytes = resp.body.byteStream().use { it.readNBytes(MAX_BYTES + 1) }
                    if (bytes.size > MAX_BYTES) return@use Outcome.Fatal
                    // M30: normalize reader-hostile formats when a transcoder
                    // is available; if it can't decode, keep the original
                    // bytes (never worse than before).
                    if (needsTranscode(ext) && transcoder != null) {
                        val converted = transcoder.let { t ->
                            runCatching { t.toSafeFormat(bytes, ext) }.getOrNull()
                        }
                        if (converted != null) return@use Outcome.Success(converted.first, converted.second)
                    }
                    Outcome.Success(bytes, ext)
                }
        } catch (e: java.io.IOException) {
            Outcome.Retryable
        }
    }

    /** URL-extension fallback: only known image extensions are trustworthy. */
    private fun extFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in KNOWN_EXTS }
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_IMAGES = 20
        const val MAX_BYTES = 10 * 1024 * 1024
        const val IMAGE_TIMEOUT_SECONDS = 30L
        const val MAX_ATTEMPTS = 3
        const val RETRY_BACKOFF_MS = 2000L

        private val EXT_FOR_MIME = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/svg+xml" to "svg",
            "image/avif" to "avif",
        )

        private val KNOWN_EXTS = EXT_FOR_MIME.values.toSet() + "jpeg"
    }
}
