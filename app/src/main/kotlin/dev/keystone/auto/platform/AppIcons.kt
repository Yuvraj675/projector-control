package dev.keystone.auto.platform

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.File

/**
 * Real app icons for the apps on the projector. The projector's APK is copied once, Android itself reads the icon out of
 * it (adaptive icons included), and the result is kept as a small PNG so the next start needs no copy.
 */
object AppIcons {
    private const val SIZE = 144
    private fun dir(context: Context) = File(context.filesDir, "app-icons").apply { mkdirs() }
    private fun file(context: Context, pkg: String) = File(dir(context), "$pkg.png")

    fun cached(context: Context, pkg: String): Bitmap? =
        file(context, pkg).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /** Reads the icon out of a downloaded APK, saves and returns it; null when the APK has no readable icon. */
    fun extract(context: Context, apk: File, pkg: String): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = apk.path
        app.publicSourceDir = apk.path
        val drawable: Drawable = try { app.loadIcon(pm) } catch (_: Exception) { return null }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bmp))
        file(context, pkg).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun save(context: Context, pkg: String, bmp: Bitmap) { file(context, pkg).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    private fun toBitmap(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, SIZE, SIZE); d.draw(Canvas(bmp)); return bmp
    }

    /** The same app installed on this phone: its icon is already here, no network needed. */
    fun fromPhone(context: Context, pkg: String): Bitmap? = try {
        toBitmap(context.packageManager.getApplicationIcon(pkg)).also { save(context, pkg, it) }
    } catch (_: Exception) { null }

    /**
     * The app's Google Play page names its icon in the og:image tag near the top of the page, so only the first
     * bytes are read. The image link takes a size suffix, which asks for a small picture.
     */
    fun fromPlayStore(context: Context, pkg: String): Bitmap? = try {
        val page = java.net.URL("https://play.google.com/store/apps/details?id=$pkg&hl=en").openConnection() as java.net.HttpURLConnection
        page.connectTimeout = 5000; page.readTimeout = 6000
        page.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        var url: String? = null
        if (page.responseCode == 200) page.inputStream.use { ins ->
            val sb = StringBuilder(); val buf = ByteArray(8192)
            while (sb.length < 300_000 && url == null) {
                val n = ins.read(buf); if (n < 0) break
                sb.append(String(buf, 0, n, Charsets.UTF_8))
                url = Regex("property=\"og:image\" content=\"([^\"]+)\"").find(sb)?.groupValues?.get(1)
            }
        }
        page.disconnect()
        url?.let { u ->
            val small = u.substringBeforeLast('=') + "=s$SIZE"
            val conn = java.net.URL(small).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 6000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.also { save(context, pkg, it) }
        }
    } catch (_: Exception) { null }

    /** Remembers that nothing was found, so the next start doesn't ask again for a week. */
    fun recentlyMissed(context: Context, pkg: String): Boolean =
        File(dir(context), "$pkg.miss").let { it.exists() && System.currentTimeMillis() - it.lastModified() < 7L * 24 * 3600 * 1000 }

    fun markMissed(context: Context, pkg: String) { try { File(dir(context), "$pkg.miss").writeText("") } catch (_: Exception) { } }

    @Suppress("unused") private val keep = PackageManager.GET_META_DATA
}
