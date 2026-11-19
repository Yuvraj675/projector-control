package dev.keystone.auto.platform

import android.content.Context
import dev.keystone.auto.link.ProjectorAssets

/** The helper jar and pattern APK bundled in the app's assets (see tools/build-assets.sh). */
class AndroidAssets(private val context: Context) : ProjectorAssets {
    override fun helperJar(): ByteArray = context.assets.open("projector/ksall.jar").use { it.readBytes() }
    override fun patternApk(): ByteArray = context.assets.open("projector/pattern.apk").use { it.readBytes() }
}
