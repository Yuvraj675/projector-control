package dev.keystone.auto.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.keystone.auto.link.InstallSource
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** An app installed on this phone that can be sent to the projector. */
class PhoneApp(
    val pkg: String,
    val label: String,
    val icon: Bitmap?,
    val sizeMb: Int,
    val apks: List<File>,
    /** Why it will probably not run on the projector, or null. */
    val warning: String?,
)

/**
 * The phone's own apps as install sources. The projector has a 32-bit ARM processor and most phones are 64-bit, so apps
 * with native code usually can't be moved over; those are flagged up front instead of failing after the transfer.
 */
object PhoneApps {
    private val ABI_32 = listOf("armeabi-v7a", "armeabi")

    fun list(context: Context): List<PhoneApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .map { ai ->
                val splits = ai.splitSourceDirs.orEmpty().map { File(it) }
                // splits that carry another processor's code are of no use on the projector
                val keep = splits.filter { f -> val n = f.name.lowercase(); !(n.contains("arm64") || n.contains("x86") || n.contains("mips")) }
                val apks = listOf(File(ai.sourceDir)) + keep
                PhoneApp(
                    pkg = ai.packageName, label = pm.getApplicationLabel(ai).toString(), icon = iconOf(pm.getApplicationIcon(ai)),
                    sizeMb = (apks.sumOf { it.length() } / (1024 * 1024)).toInt(), apks = apks, warning = warningFor(File(ai.sourceDir), splits),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Native-code check: the base APK's lib/ folders, or a 64-bit-only split. */
    fun warningFor(base: File, splits: List<File>): String? {
        val abis = try {
            ZipFile(base).use { z -> z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") }.mapNotNull { it.split('/').getOrNull(1) }.toSet() }
        } catch (_: Exception) { emptySet() }
        val sixtyFourOnly = abis.isNotEmpty() && abis.none { it in ABI_32 }
        val names = splits.map { it.name.lowercase() }
        val splitHas64 = names.any { it.contains("arm64") }
        val splitHas32 = names.any { it.contains("armeabi") || it.contains("v7a") }
        return if (sixtyFourOnly || (splitHas64 && !splitHas32)) "Built for 64-bit phones, likely won't run on the projector" else null
    }

    /** One APK is sent as it is; an app with parts is zipped into a bundle the installer already understands. */
    fun source(context: Context, app: PhoneApp): Pair<InstallSource, () -> Unit> {
        if (app.apks.size == 1) {
            val f = app.apks[0]
            return object : InstallSource {
                override val name = "${safe(app.label)}.apk"
                override fun open() = FileInputStream(f)
            } to {}
        }
        val tmp = File(context.cacheDir, "${app.pkg}.apks")
        ZipOutputStream(tmp.outputStream().buffered()).use { z ->
            z.setLevel(0)
            app.apks.forEachIndexed { i, f ->
                z.putNextEntry(ZipEntry(if (i == 0) "base.apk" else "split$i.apk"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return object : InstallSource {
            override val name = "${safe(app.label)}.apks"
            override fun open() = FileInputStream(tmp)
        } to { tmp.delete(); Unit }
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "app" }

    private fun iconOf(d: android.graphics.drawable.Drawable): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }
}
