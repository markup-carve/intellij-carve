package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.markupcarve.carve.CarveConverter
import java.nio.file.Files
import java.nio.file.Path

/**
 * The preview's include expansion, driven through the vendored bundle against
 * a real directory tree.
 *
 * Everything asserted here is the ENGINE's behavior reached through the host
 * seam - the merged output, the cycle guard, the containment refusal, the
 * dependency set a preview watches. The plugin's own share is the resolver and
 * the three-argument call; a second expander here would be the duplication
 * markup-carve/carve-press#41 removed on the other side of the org.
 */
class CarveIncludeExpansionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, text: String): Path {
        val file = tmp.root.toPath().resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    private fun root(): Path = tmp.root.toPath().toRealPath()

    private fun expand(document: Path, source: String) =
        CarveConverter.toHtmlWithIncludes(source, root(), document.toRealPath().toString())

    @Test
    fun theChildIsMergedIntoTheParent() {
        write("shared/glossary.crv", "Included body.\n")
        val doc = write("chapters/ch1.crv", "# Chapter\n\n{{ ../shared/glossary.crv }}\n")

        val result = expand(doc, Files.readString(doc))
        assertNotNull("expansion returned nothing", result)
        assertTrue(
            "the child's text is missing from the merged output: ${result!!.html}",
            result.html.contains("Included body."),
        )
        assertFalse(
            "the directive survived into the output: ${result.html}",
            result.html.contains("{{"),
        )
        assertEquals(emptyList<CarveIncludeExpansion.Warning>(), result.warnings)
    }

    /**
     * `renderDocument` names this as a host obligation: the child is parsed with
     * the set the PARENT was parsed with, because an include is textual
     * composition of one document and the same text has to mean the same thing
     * whichever file it sits in. `autolink` is the cheapest witness in the
     * preview's own extension set that reaches the parse stage.
     */
    @Test
    fun theChildIsParsedWithThePreviewsOwnExtensions() {
        write("shared/links.crv", "see https://example.com/x here\n")
        val doc = write("chapters/ch1.crv", "{{ ../shared/links.crv }}\n")

        val html = expand(doc, Files.readString(doc))!!.html
        assertTrue(
            "the child was parsed without the preview's extensions: $html",
            html.contains("""<a href="https://example.com/x""""),
        )
    }

    /**
     * The whole point of reporting dependencies: a preview re-renders when an
     * included file changes, not only when the open document does.
     */
    @Test
    fun everyTargetItTouchedIsReportedForInvalidation() {
        val child = write("shared/glossary.crv", "Glossary.\n")
        write("shared/deep.crv", "Deep.\n")
        Files.writeString(child, "Glossary.\n\n{{ deep.crv }}\n")
        val doc = write("chapters/ch1.crv", "{{ ../shared/glossary.crv }}\n")

        val result = expand(doc, Files.readString(doc))!!
        assertEquals(
            listOf(
                root().resolve("shared/glossary.crv").toString() to true,
                root().resolve("shared/deep.crv").toString() to true,
            ),
            result.dependencies.map { it.id to it.resolved },
        )
    }

    /**
     * A target that is not there is named by WHERE IT WOULD BE (I11), so the
     * preview can watch that path and re-render when the file arrives.
     */
    @Test
    fun aMissingTargetIsNamedByWhereItWouldAppear() {
        val doc = write("chapters/ch1.crv", "{{ ../shared/absent.crv }}\n")

        val result = expand(doc, Files.readString(doc))!!
        assertEquals(
            listOf(root().resolve("shared/absent.crv").toString()),
            result.dependencies.map { it.id },
        )
        assertEquals(listOf("not-found"), result.dependencies.map { it.denial })
        assertEquals(listOf("include-unresolved"), result.warnings.map { it.rule })
    }

    @Test
    fun aTargetAboveTheRootIsRefused() {
        val outside = Files.createTempDirectory("carve-outside").toRealPath()
        Files.writeString(outside.resolve("secret.crv"), "Secret body.\n")
        // Spelled as the author would, relative to the document's own folder,
        // so what is refused is a path that really does reach the file.
        val documentDir = root().resolve("chapters")
        Files.createDirectories(documentDir)
        val spelling = documentDir.relativize(outside.resolve("secret.crv")).toString()
        assertTrue("the fixture must escape the root: $spelling", spelling.startsWith(".."))
        val doc = write("chapters/ch1.crv", "{{ $spelling }}\n")

        val result = expand(doc, Files.readString(doc))!!
        assertFalse(
            "a file above the containment root reached the preview: ${result.html}",
            result.html.contains("Secret body."),
        )
        assertEquals(listOf("outside-root"), result.dependencies.map { it.denial })
    }

    @Test
    fun aCycleIsRefusedRatherThanFollowed() {
        write("a.crv", "A\n\n{{ b.crv }}\n")
        write("b.crv", "B\n\n{{ a.crv }}\n")
        val doc = tmp.root.toPath().resolve("a.crv")

        val result = expand(doc, Files.readString(doc))!!
        assertEquals(listOf("include-cycle"), result.warnings.map { it.rule })
    }

    /**
     * Spec I7: the message names the failure class and the path AS WRITTEN. A
     * resolver's own error carries an absolute path, and this one runs against
     * the user's filesystem, so an echoed message would put host layout into a
     * rendered preview.
     */
    @Test
    fun aRefusalReportsThePathAsWrittenAndNotTheHostPath() {
        val doc = write("chapters/ch1.crv", "{{ ../shared/absent.crv }}\n")

        val message = expand(doc, Files.readString(doc))!!.warnings.single().message
        assertTrue("the message drops the path as written: $message", message.contains("../shared/absent.crv"))
        assertFalse("the message leaks the host path: $message", message.contains(tmp.root.path))
    }
}
