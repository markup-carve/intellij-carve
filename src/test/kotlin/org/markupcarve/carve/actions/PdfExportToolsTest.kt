package org.markupcarve.carve.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PdfExportToolsTest {
    @Test
    fun `crv2pdf wins and command preserves spaces in paths`() {
        val found = PdfExportTools.find("/tools with spaces/crv2pdf", "", "Linux", "", { it.path == "/tools with spaces/crv2pdf" })
        assertEquals(PdfExportTools.Kind.CRV2PDF, found?.kind)
        val command = PdfExportTools.command(found!!, File("/work/my document.crv"), File("/out/my document.pdf"))
        assertEquals(listOf(File("/work/my document.crv").absolutePath, File("/out/my document.pdf").absolutePath), command.parametersList.list)
    }

    @Test
    fun `discovers browser defaults on each OS`() {
        val linux = PdfExportTools.find("", "", "Linux", "", { it.path == "/usr/bin/chromium" })
        val mac = PdfExportTools.find("", "", "Mac OS X", "", { it.path == "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" })
        val windows = PdfExportTools.find("", "", "Windows 11", "", { it.path.replace('\\', '/') == "C:/Program Files/Google/Chrome/Application/chrome.exe" })
        assertEquals(PdfExportTools.Kind.CHROMIUM, linux?.kind)
        assertEquals(PdfExportTools.Kind.CHROMIUM, mac?.kind)
        assertEquals(PdfExportTools.Kind.CHROMIUM, windows?.kind)
    }

    @Test
    fun `finds a tool on PATH and reports no tool otherwise`() {
        val directory = Files.createTempDirectory("pdf tools ")
        try {
            val executable = directory.resolve("crv2pdf").toFile()
            executable.writeText("test")
            executable.setExecutable(true)
            assertEquals(executable.absolutePath, PdfExportTools.find("", "", "Linux", directory.toString())?.executable)
            assertNull(PdfExportTools.find("", "", "Linux", "", { false }))
        } finally {
            Files.deleteIfExists(directory.resolve("crv2pdf"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `browser command uses file URI and a single output argument`() {
        val command = PdfExportTools.command(
            PdfExportTools.Tool(PdfExportTools.Kind.CHROMIUM, "/tools/Google Chrome"),
            File("/work/my document.html"), File("/out/my document.pdf"),
        )
        assertTrue(command.parametersList.list.contains("--headless"))
        assertTrue(command.parametersList.list.contains("--print-to-pdf=/out/my document.pdf"))
        assertEquals(File("/work/my document.html").toURI().toASCIIString(), command.parametersList.list.last())
    }

    @Test
    fun `crv2pdf can resolve includes from the original read-only directory`() {
        val command = PdfExportTools.command(
            PdfExportTools.Tool(PdfExportTools.Kind.CRV2PDF, "/tools/crv2pdf"),
            File("/writable temporary/input.crv"), File("/writable temporary/output.pdf"),
            File("/read only/source"),
        )
        assertEquals(listOf("--include-root", "/read only/source"), command.parametersList.list.takeLast(2))
    }

    @Test
    fun `headless Chromium produces a PDF when installed`() {
        val browser = PdfExportTools.find("missing", "", "Linux")
        if (browser?.kind != PdfExportTools.Kind.CHROMIUM) {
            org.junit.Assume.assumeTrue("Chromium is unavailable on this runner", false)
        }
        val html = Files.createTempFile("carve pdf ", ".html")
        val pdf = Files.createTempFile("carve pdf ", ".pdf")
        try {
            Files.writeString(html, "<html><body><h1>Carve PDF test</h1></body></html>")
            Files.delete(pdf)
            val output = com.intellij.execution.process.CapturingProcessHandler(
                PdfExportTools.command(browser!!, html.toFile(), pdf.toFile()),
            ).runProcess(120_000)
            assertEquals(output.stderr, 0, output.exitCode)
            assertTrue(Files.size(pdf) > 0)
        } finally {
            Files.deleteIfExists(html)
            Files.deleteIfExists(pdf)
        }
    }
}
