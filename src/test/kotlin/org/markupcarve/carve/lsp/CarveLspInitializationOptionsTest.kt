package org.markupcarve.carve.lsp

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.settings.CarveIncludeMode

/**
 * The client half of the section 19 handshake.
 *
 * carve-lsp ships include resolution switched off and waits to be asked:
 * `carve.includes.enabled` defaults to `auto`, `auto` requires
 * `workspaceTrusted`, and `workspaceTrusted` defaults to false. This plugin used
 * to send no `initializationOptions` at all, so the server read silence as a
 * refusal and no user ever had include navigation, completion or diagnostics.
 * These assertions pin the shape the server actually parses - see
 * `readIncludeSettings` and `readWorkspaceTrusted` in the bundled server.
 */
class CarveLspInitializationOptionsTest {

    private fun includes(options: JsonObject): JsonObject =
        options.getAsJsonObject("carve").getAsJsonObject("includes")

    @Test
    fun `asks for resolution in a trusted workspace`() {
        val options = CarveLspInitializationOptions.build(CarveIncludeMode.AUTO, "", workspaceTrusted = true)

        assertEquals("auto", includes(options).get("enabled").asString)
        assertTrue(options.get("workspaceTrusted").asBoolean)
    }

    @Test
    fun `an untrusted workspace is reported as untrusted`() {
        val options = CarveLspInitializationOptions.build(CarveIncludeMode.AUTO, "", workspaceTrusted = false)

        assertFalse(options.get("workspaceTrusted").asBoolean)
    }

    @Test
    fun `each mode reaches the wire under the server's own spelling`() {
        assertEquals("auto", includes(build(CarveIncludeMode.AUTO)).get("enabled").asString)
        assertEquals("on", includes(build(CarveIncludeMode.ON)).get("enabled").asString)
        assertEquals("off", includes(build(CarveIncludeMode.OFF)).get("enabled").asString)
    }

    /**
     * The one that matters. An unset text field in the settings UI is `""`, not
     * null, and the server keeps ANY string it is given and hands it to
     * `realpathSync`. `realpathSync("")` returns the process working directory
     * without erroring, so forwarding a blank root silently makes the IDE's own
     * working directory the containment boundary - the single root section 19
     * names as forbidden. Measured against the bundled server: with `""` a file
     * outside the project resolved through `../../`; with the field omitted the
     * same include was refused.
     */
    @Test
    fun `a blank containment root is omitted, never forwarded`() {
        for (blank in listOf("", "   ", "\t")) {
            val sent = includes(CarveLspInitializationOptions.build(CarveIncludeMode.ON, blank, true))
            assertFalse("a blank root must not reach the server: '$blank'", sent.has("includeRoot"))
        }
    }

    @Test
    fun `a configured containment root is sent, trimmed`() {
        val sent = includes(CarveLspInitializationOptions.build(CarveIncludeMode.ON, "  /srv/docs  ", true))

        assertEquals("/srv/docs", sent.get("includeRoot").asString)
    }

    private fun build(mode: CarveIncludeMode): JsonObject =
        CarveLspInitializationOptions.build(mode, "", workspaceTrusted = true)
}
