package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * The resolver's policy on its own, without an expansion around it.
 *
 * The refusals reachable through [CarveIncludeExpansionTest] are asserted there
 * against real output. What is left here is the pair that expansion cannot show
 * - the read cap, which needs a cap small enough to hit, and an absolute target,
 * which a document cannot spell portably.
 */
class CarveIncludeResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun root(): Path = tmp.root.toPath().toRealPath()

    private fun write(relative: String, text: String): Path {
        val file = tmp.root.toPath().resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    /**
     * The expander's byte budget cannot stand in for this: it charges a target
     * only once the source is in hand, so without a cap here one oversized file
     * is read into memory in full before expansion is refused.
     */
    @Test
    fun aTargetOverTheReadCapIsRefusedRatherThanRead() {
        write("big.crv", "0123456789")

        val refused = CarveIncludeResolver.resolve(root(), "big.crv", null, maxFileBytes = 4)
        assertNull("a file over the cap was read: ${refused.source}", refused.source)
        assertEquals("denied", refused.denial)

        val admitted = CarveIncludeResolver.resolve(root(), "big.crv", null, maxFileBytes = 10)
        assertEquals("0123456789", admitted.source)
        assertNull(admitted.denial)
    }

    /** carve-js defaults `allowAbsolute` to false, and the plugin exposes no switch. */
    @Test
    fun anAbsoluteTargetIsRefusedEvenInsideTheRoot() {
        val inside = write("shared/glossary.crv", "Glossary.\n")

        val answer = CarveIncludeResolver.resolve(root(), inside.toRealPath().toString(), null)
        assertNull("an absolute target was read: ${answer.source}", answer.source)
        assertEquals("denied", answer.denial)
    }

    @Test
    fun theCanonicalPathIsTheIdentityTheCycleGuardGets() {
        val child = write("shared/glossary.crv", "Glossary.\n")

        assertEquals(
            child.toRealPath().toString(),
            CarveIncludeResolver.resolve(root(), "./shared/../shared/glossary.crv", null).id,
        )
    }
}
