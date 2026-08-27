package org.markupcarve.carve.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * The vendored browser assets are present, complete, and exactly what `INDEX` says.
 *
 * `INDEX` is not documentation - [CarvePreviewAssets.extractTo] reads it to know
 * what to unpack, because a jar cannot be listed. So a file that is in the tree
 * but not in the index never reaches the preview, and an index line whose file
 * is gone makes every preview log an error. Neither shows up as a build failure
 * on its own, which is what this test is for.
 *
 * Read from the SOURCE TREE rather than the classpath: the point is that the
 * committed files are right, not that some copy of them loaded.
 */
class CarvePreviewAssetsTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val root = File("src/main/resources/preview-assets")

    private fun index(): List<CarvePreviewAssets.Entry> =
        CarvePreviewAssets.parseIndex(File(root, "INDEX").readText())

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `every indexed file is present and unmodified`() {
        val entries = index()
        assertTrue("preview-assets/INDEX is empty - re-run tools/vendor-preview-assets.sh", entries.isNotEmpty())
        for (entry in entries) {
            val file = File(root, entry.path)
            assertTrue("missing vendored asset: ${file.path}", file.isFile)
            assertEquals("wrong size for ${entry.path}", entry.size, file.length())
            assertEquals(
                "${entry.path} does not match the SHA-256 in INDEX. Vendored assets are not " +
                    "hand-edited; re-run tools/vendor-preview-assets.sh instead.",
                entry.sha256,
                sha256(file),
            )
        }
    }

    /**
     * The other direction, and the one that actually bites: a file added to the
     * tree without regenerating INDEX is never unpacked, so the preview looks
     * exactly as if the file had never been vendored.
     */
    @Test
    fun `no vendored file is missing from INDEX`() {
        val indexed = index().map { it.path }.toSet()
        val onDisk = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { it == "INDEX" || it == "VENDOR.md" }
            .toSet()
        assertEquals(
            "preview-assets/ and INDEX disagree - re-run tools/vendor-preview-assets.sh",
            emptySet<String>(),
            onDisk - indexed,
        )
        assertEquals(
            "INDEX lists files that are not in preview-assets/ - re-run tools/vendor-preview-assets.sh",
            emptySet<String>(),
            indexed - onDisk,
        )
    }

    /** Every path the scaffold hardcodes has to be a path the index actually carries. */
    @Test
    fun `every asset the page references is vendored`() {
        val indexed = index().map { it.path }.toSet()
        for (path in CarvePreviewAssets.REFERENCED) {
            assertTrue("the preview references $path, which is not in INDEX", path in indexed)
            assertTrue("the preview references $path, which is not on disk", File(root, path).isFile)
        }
    }

    /**
     * MathJax's CHTML output resolves its webfonts against its own script URL,
     * so the fonts have to sit in `output/chtml/fonts/woff-v2` NEXT TO the
     * bundle. Flattening that directory does not fail anything - it silently
     * renders every formula in a fallback face with the wrong metrics.
     */
    @Test
    fun `the MathJax webfonts keep the layout MathJax looks them up by`() {
        val fonts = File(root, "mathjax/output/chtml/fonts/woff-v2")
        assertTrue("MathJax font directory is missing: ${fonts.path}", fonts.isDirectory)
        val woff = fonts.listFiles { f -> f.extension == "woff" }?.size ?: 0
        assertTrue("expected the full MathJax woff-v2 set, found $woff files", woff >= 20)
    }

    @Test
    fun `extraction reproduces the whole tree byte for byte`() {
        val target = CarvePreviewAssets.extractTo(temp.newFolder("assets"))
        for (entry in index()) {
            val file = File(target, entry.path)
            assertTrue("extraction skipped ${entry.path}", file.isFile)
            assertEquals("extraction corrupted ${entry.path}", entry.sha256, sha256(file))
        }
    }

    /** Idempotent: a second call must not re-write a root that is already complete. */
    @Test
    fun `extraction into a finished root is a no-op`() {
        val target = CarvePreviewAssets.extractTo(temp.newFolder("assets"))
        val marker = File(target, CarvePreviewAssets.REFERENCED.first())
        marker.writeText("tampered")
        CarvePreviewAssets.extractTo(target)
        assertEquals("tampered", marker.readText())
    }

    /**
     * Redistributing someone else's code is a licensing act, and BSD-3-Clause
     * and Apache-2.0 both require the licence text to travel with the copy. It
     * travels because these files sit inside `preview-assets/`, so this checks
     * they are still there and still named in the note the repo keeps.
     */
    @Test
    fun `each vendored package ships its licence and is recorded in VENDOR-md`() {
        val vendorNote = File(root, "VENDOR.md")
        assertTrue("preview-assets/VENDOR.md is missing", vendorNote.isFile)
        val note = vendorNote.readText()

        val licences = mapOf(
            "highlight/LICENSE" to Triple("highlight.js", "11.9.0", "BSD-3-Clause"),
            "chart/LICENSE.md" to Triple("Chart.js", "4.5.1", "MIT"),
            "mathjax/LICENSE" to Triple("MathJax", "3.2.2", "Apache-2.0"),
            "mermaid/LICENSE" to Triple("Mermaid", "11.17.2", "MIT"),
        )
        for ((path, meta) in licences) {
            val (name, version, licence) = meta
            val file = File(root, path)
            assertTrue("$name is vendored without its licence text ($path)", file.isFile)
            assertTrue("$path is empty", file.length() > 0)
            assertTrue("VENDOR.md does not record $name", note.contains(name))
            assertTrue("VENDOR.md does not record $name's version $version", note.contains(version))
            assertTrue("VENDOR.md does not record $name's licence ($licence)", note.contains(licence))
        }
    }
}
