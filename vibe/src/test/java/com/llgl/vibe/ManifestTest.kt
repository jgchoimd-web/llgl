package com.llgl.vibe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app crashed on launch once because the Application subclass was not declared; never again. */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `application class and the capture service are declared`() {
        assertTrue(manifest.contains("android:name=\".VibeApp\""))
        assertTrue(manifest.contains("android:name=\".capture.CaptureService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaProjection\""))
    }

    @Test
    fun `only the live-mode permissions are requested`() {
        for (p in listOf("VIBRATE", "RECORD_AUDIO", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PROJECTION", "POST_NOTIFICATIONS")) {
            assertTrue(p, manifest.contains("android.permission.$p"))
        }
        for (p in listOf("READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE", "INTERNET")) {
            assertFalse(p, manifest.contains("android.permission.$p"))
        }
    }
}
