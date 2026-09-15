package org.markupcarve.carve.includes

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.markupcarve.carve.lsp.CarveLspInitializationOptions
import org.markupcarve.carve.lsp.NodeLocator
import org.markupcarve.carve.settings.CarveIncludeMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the VENDORED carve-lsp over its real stdio protocol.
 *
 * Nothing here is a fake. A stub server would pin the shape of the walk's own
 * JSON and say nothing about the two questions the walk exists to delegate -
 * which `{{ ... }}` is a live directive, and which target the containment root
 * lets it read. Both are the server's answers, so both have to come from the
 * server the plugin actually ships.
 *
 * Skipped, not failed, when `node` is absent: without it the plugin's language
 * server does not run either, so there is no behavior to measure on such a
 * host. CI has node.
 */
class CarveIncludeWalkTest {

    private val server: Path? by lazy {
        val vendored = Path.of("src/main/resources/lsp/server.js").toAbsolutePath()
        if (Files.isRegularFile(vendored)) vendored else null
    }

    private fun options(root: Path): JsonObject =
        CarveLspInitializationOptions.build(
            mode = CarveIncludeMode.ON,
            includeRoot = root.toString(),
            workspaceTrusted = true,
        )

    private fun walk(
        root: Path,
        document: Path,
        maxDepth: Int = CarveIncludeWalk.MAX_DEPTH,
        maxRequests: Int = CarveIncludeWalk.MAX_REQUESTS,
    ): IncludeWalk {
        val node = NodeLocator.find(null)
        assumeTrue("node is not on PATH, so the bundled server cannot run", node != null)
        assumeTrue("the vendored lsp/server.js is missing", server != null)
        return CarveIncludeWalk.walk(
            nodePath = node!!,
            serverPath = server!!,
            documentPath = document,
            source = Files.readString(document),
            workspaceRoot = root,
            initializationOptions = options(root),
            maxDepth = maxDepth,
            maxRequests = maxRequests,
        )
    }

    /** proj/root.crv -> sub/child.crv -> sub/grandchild.crv, plus three refusals. */
    private fun fixture(): Pair<Path, Path> {
        val base = Files.createTempDirectory("carve-walk")
        val project = base.resolve("proj")
        Files.createDirectories(project.resolve("sub"))
        Files.createDirectories(base.resolve("outside"))
        Files.writeString(base.resolve("outside/secret.crv"), "Secret body.\n")
        Files.writeString(project.resolve("sub/grandchild.crv"), "Grandchild body.\n")
        Files.writeString(
            project.resolve("sub/child.crv"),
            """
            ## Child

            {{ grandchild.crv }}
            """.trimIndent() + "\n",
        )
        Files.writeString(
            project.resolve("root.crv"),
            """
            # Root

            {{ sub/child.crv }}

            {{ missing.crv }}

            {{ ../outside/secret.crv }}

            ``` text
            {{ fenced.crv }}
            ```
            """.trimIndent() + "\n",
        )
        return project to project.resolve("root.crv")
    }

    private fun namesOf(walk: IncludeWalk, root: Path): List<String> =
        walk.documents.map { root.toRealPath().relativize(Path.of(it.id)).joinToString("/") }

    @Test
    fun readsTheChildAndTheGrandchild() {
        val (root, document) = fixture()
        assertEquals(
            "the walk must follow a directive inside a child, not only the ones in the open document",
            listOf("sub/child.crv", "sub/grandchild.crv"),
            namesOf(walk(root, document), root),
        )
    }

    @Test
    fun carriesEachChildSourceVerbatim() {
        val (root, document) = fixture()
        val child = walk(root, document).documents.single { it.id.endsWith("sub/child.crv") }
        assertEquals(
            "a bundle copies bytes, so the walk has to hand back the child exactly as it is on disk",
            Files.readString(root.resolve("sub/child.crv")),
            child.source,
        )
    }

    @Test
    fun refusesATargetOutsideTheContainmentRoot() {
        val (root, document) = fixture()
        val resolved = walk(root, document).dependencies.filter { it.resolved }.map { it.id }
        assertTrue(
            "a target above the include root must never be readable: $resolved",
            resolved.none { it.contains("secret.crv") },
        )
    }

    @Test
    fun reportsTheRefusedTargetAsUnreadRatherThanDroppingIt() {
        val (root, document) = fixture()
        val unread = walk(root, document).dependencies.filterNot { it.resolved }.map { it.id }
        assertEquals(
            "a refusal the export swallows is worse than one it names",
            listOf("missing.crv", "../outside/secret.crv"),
            unread,
        )
    }

    @Test
    fun leavesADirectiveInAFencedBlockAlone() {
        val (root, document) = fixture()
        val walked = walk(root, document)
        val named = (walked.documents.map { it.id } + walked.dependencies.map { it.id })
        assertTrue(
            "`{{ fenced.crv }}` inside a code fence is text, not a directive: $named",
            named.none { it.contains("fenced.crv") },
        )
    }

    @Test
    fun offersEveryDirectiveOnALineAsACandidate() {
        assertEquals(
            "two directives on one line are two candidates; stopping at the first drops the second",
            listOf(
                CarveIncludeWalk.Candidate(0, 2),
                CarveIncludeWalk.Candidate(0, 18),
            ),
            CarveIncludeWalk.directiveCandidates("{{ a.crv }} and {{ b.crv }}"),
        )
    }

    @Test
    fun putsEveryCandidateInsideTheDirectiveItOpens() {
        // The server answers definition for a position within the directive's
        // SPAN and null outside it, so the only thing the column has to get
        // right is landing inside. `at + 2` is inside whatever follows the
        // braces, spaces included.
        assertEquals(
            listOf(CarveIncludeWalk.Candidate(0, 2)),
            CarveIncludeWalk.directiveCandidates("{{    child.crv }}"),
        )
    }

    @Test
    fun countsCandidateColumnsInUtf16CodeUnits() {
        // An astral emoji is two UTF-16 code units, which is what an LSP
        // position counts. Measured in codepoints the candidate drifts one
        // column left per astral character earlier in the line, and far enough
        // left it leaves the directive's span and answers nothing.
        assertEquals(
            listOf(CarveIncludeWalk.Candidate(0, 5)),
            CarveIncludeWalk.directiveCandidates("😀 {{ a.crv }}"),
        )
    }

    @Test
    fun theSameFileReachedTwiceIsCarriedOnce() {
        val base = Files.createTempDirectory("carve-walk-dedupe")
        Files.writeString(base.resolve("shared.crv"), "Shared.\n")
        Files.writeString(base.resolve("doc.crv"), "{{ shared.crv }}\n\n{{ ./shared.crv }}\n")
        val walked = walk(base, base.resolve("doc.crv"))
        assertEquals(
            "two spellings of one file are one file in the bundle",
            1,
            walked.documents.size,
        )
    }

    @Test
    fun aDocumentWithNoDirectivesWalksToNothing() {
        val base = Files.createTempDirectory("carve-walk-empty")
        Files.writeString(base.resolve("doc.crv"), "# Title\n\nJust prose.\n")
        val walked = walk(base, base.resolve("doc.crv"))
        assertEquals(emptyList<WalkedDocument>(), walked.documents)
        assertEquals(emptyList<WalkedDependency>(), walked.dependencies)
    }

    @Test
    fun resolvesAcrossTheRootFromASubdirectory() {
        // `../shared/glossary.crv` from `chapters/ch1.crv` is a normal book
        // layout and its canonical target is inside the root, so containment
        // must admit it. A lexical ban on ".." would refuse it.
        val base = Files.createTempDirectory("carve-walk-sibling")
        Files.createDirectories(base.resolve("chapters"))
        Files.createDirectories(base.resolve("shared"))
        Files.writeString(base.resolve("shared/glossary.crv"), "Glossary.\n")
        Files.writeString(base.resolve("chapters/ch1.crv"), "{{ ../shared/glossary.crv }}\n")
        val walked = walk(base, base.resolve("chapters/ch1.crv"))
        assertEquals(
            listOf("shared/glossary.crv"),
            walked.documents.map { base.toRealPath().relativize(Path.of(it.id)).joinToString("/") },
        )
    }

    @Test
    fun refusesRatherThanTruncatingAnIncludeChainDeeperThanTheBound() {
        // Truncating would write a bundle whose deepest directives point at
        // files that are not in it, under a summary reporting a clean export -
        // which is the one outcome section 19 forbids a host.
        val base = Files.createTempDirectory("carve-walk-depth")
        Files.writeString(base.resolve("c.crv"), "Leaf.\n")
        Files.writeString(base.resolve("b.crv"), "{{ c.crv }}\n")
        Files.writeString(base.resolve("a.crv"), "{{ b.crv }}\n")
        val failure = runCatching { walk(base, base.resolve("a.crv"), maxDepth = 1) }.exceptionOrNull()
        assertTrue(
            "the depth bound has to refuse as loudly as the request bound: $failure",
            failure is CarveIncludeWalk.WalkFailed && failure.message!!.contains("nest more than 1 deep"),
        )
    }

    @Test
    fun aChainThatFitsTheBoundIsWalkedWhole() {
        val base = Files.createTempDirectory("carve-walk-depth-ok")
        Files.writeString(base.resolve("c.crv"), "Leaf.\n")
        Files.writeString(base.resolve("b.crv"), "{{ c.crv }}\n")
        Files.writeString(base.resolve("a.crv"), "{{ b.crv }}\n")
        assertEquals(
            listOf("b.crv", "c.crv"),
            walk(base, base.resolve("a.crv"), maxDepth = 3).documents
                .map { base.toRealPath().relativize(Path.of(it.id)).joinToString("/") },
        )
    }

    @Test
    fun refusesRatherThanReadingPastTheRequestBound() {
        val base = Files.createTempDirectory("carve-walk-bound")
        Files.writeString(base.resolve("a.crv"), "Leaf.\n")
        Files.writeString(base.resolve("doc.crv"), "{{ a.crv }}\n\n{{ a.crv }}\n\n{{ a.crv }}\n")
        val failure = runCatching { walk(base, base.resolve("doc.crv"), maxRequests = 2) }.exceptionOrNull()
        assertTrue(
            "the bound has to refuse loudly, not truncate silently: $failure",
            failure is CarveIncludeWalk.WalkFailed &&
                failure.message!!.contains("more than 2 include candidates"),
        )
    }

    @Test
    fun aCycleSettlesInsteadOfWalkingTheParentAgain() {
        val base = Files.createTempDirectory("carve-walk-cycle")
        Files.writeString(base.resolve("child.crv"), "{{ parent.crv }}\n")
        Files.writeString(base.resolve("parent.crv"), "{{ child.crv }}\n")
        val walked = walk(base, base.resolve("parent.crv"))
        assertEquals(
            "the parent is the document, not one of its own includes",
            listOf("child.crv"),
            walked.documents.map { base.toRealPath().relativize(Path.of(it.id)).joinToString("/") },
        )
    }

    @Test
    fun theVendoredServerIsTheOneBeingMeasured() {
        // Without this the suite above could pass against nothing: every test
        // is `assumeTrue`-guarded, so a missing bundle would read as a clean
        // run rather than an unmeasured one.
        assertTrue(
            "src/main/resources/lsp/server.js is missing, so nothing above measured the server",
            server != null && File(server!!.toString()).length() > 0,
        )
    }
}
