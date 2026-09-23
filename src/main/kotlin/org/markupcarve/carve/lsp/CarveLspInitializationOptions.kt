package org.markupcarve.carve.lsp

import com.google.gson.JsonObject
import org.markupcarve.carve.settings.CarveIncludeMode

/**
 * Builds the `initializationOptions` the bundled carve-lsp reads at
 * `initialize`. Pure, so the shape can be asserted without an IDE fixture.
 *
 * The server's contract (carve-lsp `readIncludeSettings`/`readWorkspaceTrusted`):
 * `carve.includes.enabled` is `on`, `off` or `auto`, and `auto` - the server's
 * own default - resolves includes only when `workspaceTrusted` is true. Sending
 * nothing therefore reads as "no": spec PART 9 section 19 makes the capability
 * opt-in, so silence is a refusal, not a default.
 */
object CarveLspInitializationOptions {

    fun build(
        mode: CarveIncludeMode,
        includeRoot: String,
        workspaceTrusted: Boolean,
    ): JsonObject {
        val includes = JsonObject()
        includes.addProperty("enabled", mode.wireValue)

        // A blank root is OMITTED, never forwarded. The server keeps any string
        // it is given and hands it to realpathSync, and realpathSync("") returns
        // THE PROCESS WORKING DIRECTORY without erroring - so passing an unset
        // text field through makes the containment boundary the IDE's cwd, which
        // is the one root section 19 names as forbidden. Measured against the
        // bundled server: with "" a file outside the project resolved; with the
        // field omitted the same include was refused.
        includeRoot.trim().takeIf { it.isNotEmpty() }?.let { includes.addProperty("includeRoot", it) }

        val carve = JsonObject()
        carve.add("includes", includes)
        // The plugin ships its own export actions; the server's would list export twice.
        carve.addProperty("exportActions", false)

        val options = JsonObject()
        options.add("carve", carve)
        options.addProperty("workspaceTrusted", workspaceTrusted)
        return options
    }
}
