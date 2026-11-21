package dev.keystone.auto

import dev.keystone.auto.platform.PhoneApps
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PhoneAppsTest {
    private fun apk(vararg entries: String): File {
        val f = File.createTempFile("t", ".apk").apply { deleteOnExit() }
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { z.putNextEntry(ZipEntry(it)); z.write(1); z.closeEntry() } }
        return f
    }

    @Test fun pureJavaAppIsFine() = assertNull(PhoneApps.warningFor(apk("classes.dex"), emptyList()))
    @Test fun appWithThirtyTwoBitLibsIsFine() = assertNull(PhoneApps.warningFor(apk("lib/armeabi-v7a/a.so", "lib/arm64-v8a/a.so"), emptyList()))
    @Test fun sixtyFourBitOnlyLibsAreFlagged() = assertNotNull(PhoneApps.warningFor(apk("lib/arm64-v8a/a.so"), emptyList()))
    @Test fun sixtyFourBitSplitIsFlagged() = assertNotNull(PhoneApps.warningFor(apk("classes.dex"), listOf(File("split_config.arm64_v8a.apk"))))
    @Test fun thirtyTwoBitSplitAlongsideIsFine() = assertNull(PhoneApps.warningFor(apk("classes.dex"), listOf(File("split_config.arm64_v8a.apk"), File("split_config.armeabi_v7a.apk"))))
}
