package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Exercises the carve-php CLI path. Skipped unless PHP and a carve-php install
 * are present at [testDir].
 */
class CarvePhpConverterTest {

    private val testDir = "/tmp/carve-php-test"

    @Before
    fun setUp() {
        val phpAvailable = try {
            ProcessBuilder("php", "-v").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
        assumeTrue("PHP not available", phpAvailable)
        assumeTrue(
            "carve-php not installed in $testDir",
            File("$testDir/vendor/autoload.php").exists(),
        )
    }

    @Test
    fun testBasicConversion() {
        val result = CarvePhpConverter.toHtml(
            carve = "# Hello World\n\nThis is *bold*.",
            workingDir = testDir,
        )
        assertTrue("Conversion should succeed", result.isSuccess)
        assertTrue(result.getOrThrow().contains("<h1>"))
    }

    @Test
    fun testInvalidWorkingDir() {
        val result = CarvePhpConverter.toHtml(carve = "# Test", workingDir = "/nonexistent/path")
        assertTrue("Should fail with invalid working dir", result.isFailure)
    }
}
