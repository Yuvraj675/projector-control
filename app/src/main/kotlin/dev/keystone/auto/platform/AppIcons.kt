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

    @Suppress("unused") private val keep = PackageManager.GET_META_DATA
}
