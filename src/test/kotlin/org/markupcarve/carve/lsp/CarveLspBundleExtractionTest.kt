package org.markupcarve.carve.lsp

import com.intellij.openapi.application.PathManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CarveLspBundleExtractionTest {
    @Test
    fun `the extracted server has its analysis worker beside it`() {
        val worker = Path.of(PathManager.getSystemPath(), "carve-lsp", "analysis-worker.js")
        Files.deleteIfExists(worker)
        val server = CarveLspBundle.extractServer()
        assertNotNull(server)
        assertEquals(worker, server!!.resolveSibling("analysis-worker.js"))
        val expected = javaClass.getResourceAsStream("/lsp/analysis-worker.js")!!.use { it.readBytes() }
        assertArrayEquals(expected, Files.readAllBytes(worker))
    }
}
