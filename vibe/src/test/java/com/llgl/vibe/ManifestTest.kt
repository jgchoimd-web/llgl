package com.llgl.vibe

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app crashed on launch once because the Application subclass was not declared; never again. */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `application class and service are declared`() {
        assertTrue(manifest.contains("android:name=\".VibeApp\""))
        assertTrue(manifest.contains("android:name=\".capture.CaptureService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"mediaProjection\""))
    }

    @Test
    fun `live mode permissions are present`() {
        for (p in listOf("RECORD_AUDIO", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PROJECTION", "POST_NOTIFICATIONS", "VIBRATE")) {
            assertTrue(p, manifest.contains("android.permission.$p"))
        }
    }
}
