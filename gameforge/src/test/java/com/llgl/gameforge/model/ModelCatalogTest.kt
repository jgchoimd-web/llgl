package com.llgl.gameforge.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ModelCatalogTest {

    @Test
    fun `presets are well formed`() {
        val presets = ModelCatalog.presets
        assertTrue(presets.isNotEmpty())
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        assertEquals(presets.size, presets.map { it.fileName }.toSet().size)
        assertEquals(presets.size, presets.map { it.sha256 }.toSet().size)
        for (p in presets) {
            assertTrue(p.id, p.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(p.id, p.officialUrl.startsWith("https://huggingface.co/"))
            assertTrue(p.id, p.officialUrl.endsWith("/" + p.fileName))
            p.mirrorUrl?.let { assertTrue(p.id, it.startsWith("https://") && it.endsWith("/" + p.fileName)) }
            assertTrue(p.id, p.contextTokens >= 1024)
            assertTrue(p.id, p.sizeMb > 100)
            assertTrue(p.id, p.quality in 1..3 && p.speed in 1..3)
            assertEquals(p.mirrorUrl == null, p.needsToken)
        }
        // At least one preset must be usable without an account.
        assertTrue(presets.any { !it.needsToken })
    }

    @Test
    fun `lookups by file and hash`() {
        val first = ModelCatalog.presets.first()
        assertEquals(first, ModelCatalog.byFile(first.fileName))
        assertEquals(first, ModelCatalog.bySha(first.sha256.uppercase()))
        assertNull(ModelCatalog.byFile("nope.task"))
        assertEquals(first.contextTokens, ModelCatalog.defaultContext(first.fileName))
        assertEquals(4096, ModelCatalog.defaultContext("custom.task"))
        assertNotNull(first.sizeLabel)
        assertEquals("3.1 GB", ModelCatalog.presets.first { it.sizeMb == 3136 }.sizeLabel)
        assertEquals("555 MB", ModelCatalog.presets.first { it.sizeMb == 555 }.sizeLabel)
    }

    @Test
    fun `sha256 helper matches a known digest and reports progress`() {
        var seen = 0L
        val digest = Sha256.of(ByteArrayInputStream("abc".toByteArray())) { seen = it }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
        assertEquals(3L, seen)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.of(ByteArrayInputStream(ByteArray(0))))
    }
}
