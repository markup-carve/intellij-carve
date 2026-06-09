package org.markupcarve.carve

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.markupcarve.carve.settings.CarveRenderer
import org.markupcarve.carve.settings.CarveSettings

/**
 * Converts Carve markup to an HTML fragment.
 *
 * Two engines, selected in Settings > Tools > Carve:
 *   - [CarveRenderer.CARVE_JS] (default): runs the bundled `carve.iife.js`
 *     (built from `@markup-carve/carve`) on GraalJS - no external dependency.
 *   - [CarveRenderer.CARVE_PHP]: shells out to `markup-carve/carve-php` via CLI.
 */
object CarveConverter {

    private val LOG = Logger.getInstance(CarveConverter::class.java)

    /** The bundled carve.iife.js source, loaded once from plugin resources. */
    private val carveJs: String by lazy {
        CarveConverter::class.java.getResourceAsStream("/js/carve.iife.js")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: ""
    }

    fun toHtml(carve: String, project: Project? = null): String {
        if (project != null && CarveSettings.getInstance(project).renderer == CarveRenderer.CARVE_PHP) {
            val settings = CarveSettings.getInstance(project)
            val result = CarvePhpConverter.toHtml(
                carve = carve,
                phpPath = settings.phpPath,
                scriptPath = settings.phpCarveScript,
                workingDir = project.basePath,
            )
            if (result.isSuccess) {
                return result.getOrThrow()
            }
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            return errorHtml("Carve PHP error", error)
        }

        return toHtmlWithJs(carve)
    }

    private fun toHtmlWithJs(carve: String): String {
        if (carveJs.isEmpty()) {
            return errorHtml("Carve renderer unavailable", "Bundled carve.iife.js is missing from the plugin.")
        }

        return try {
            Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()
                .use { context ->
                    context.eval(Source.newBuilder("js", carveJs, "carve.iife.js").build())
                    val carveGlobal = context.getBindings("js").getMember("carve")
                        ?: return errorHtml("Carve renderer error", "Global 'carve' not found in bundle.")
                    val fn = carveGlobal.getMember("carveToHtml")
                        ?: return errorHtml("Carve renderer error", "'carveToHtml' not found in bundle.")
                    // Pass the source as a polyglot argument - no string interpolation, no injection.
                    fn.execute(carve).asString()
                }
        } catch (e: Exception) {
            LOG.warn("Carve JS render failed", e)
            errorHtml("Carve render error", e.message ?: e.toString())
        }
    }

    private fun errorHtml(title: String, message: String): String {
        val safe = message
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """<div style="color:#dc3545;background:#f8d7da;padding:10px;border-radius:5px;">
            |<strong>$title:</strong> $safe
            |</div>""".trimMargin()
    }
}
