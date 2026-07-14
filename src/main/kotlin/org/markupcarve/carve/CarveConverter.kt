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

    /**
     * @param sourceLine when true, carve-js stamps each top-level block with
     *   `data-source-line="{n}"` (1-based). The preview uses those anchors to scroll in
     *   step with the editor. Off by default so HTML export stays clean. The carve-php
     *   renderer has no equivalent option, so scroll sync simply does nothing there -
     *   the preview still renders, it just has no anchors to jump to.
     */
    fun toHtml(carve: String, project: Project? = null, sourceLine: Boolean = false): String {
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

        return toHtmlWithJs(carve, sourceLine)
    }

    private fun toHtmlWithJs(carve: String, sourceLine: Boolean = false): String {
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
                    // Build the options object (with the showcase extension set) inside
                    // the bundle's own context, so the extension factories come from the
                    // same Carve build that renders. Defined as a global helper once per
                    // context, then invoked with the source as a polyglot argument - no
                    // string interpolation of the user's Carve, so no injection.
                    val optionsBuilder = context.eval(
                        Source.newBuilder("js", CARVE_OPTIONS_JS, "carve-options.js").build(),
                    )
                    val options = optionsBuilder.execute(sourceLine)
                    fn.execute(carve, options).asString()
                }
        } catch (e: Exception) {
            LOG.warn("Carve JS render failed", e)
            errorHtml("Carve render error", e.message ?: e.toString())
        }
    }

    /**
     * Returns a `carveToHtml` options object enabling the showcase extension
     * set the Carve docs site renders with (see carve-js
     * `docs/.vitepress/carve-extensions.js`): authoring constructs and inline
     * sugar that work zero-config and are safe in an embedded preview.
     *
     * One deliberate deviation from the docs-site list: this plugin uses
     * `headingReference` rather than `wikilinks` for `[[Heading]]`. The plugin's
     * own `cwiki` live template and README document `[[Heading]]` as an implicit
     * heading reference (resolving to the local `#id`), whereas `wikilinks`
     * would render it as an off-document wiki page link - making the plugin's
     * own template produce a broken-looking link in preview.
     *
     * Each factory is called fresh per render. Diagram presets that need a
     * client-side JS library to paint (mermaid, chart) emit their container
     * markup here; the preview surface decides whether to load those libraries.
     * Factories absent from the bundle are skipped so an older bundle still
     * renders core Carve rather than throwing.
     */
    private val CARVE_OPTIONS_JS = """
        (function (sourceLine) {
          var names = [
            'details', 'mermaid', 'mathBlock', 'spoiler', 'chart',
            'headingReference', 'autolink', 'codeGroup', 'tabs', 'listTable',
            'headingPermalinks', 'citations', 'externalLinks'
          ];
          var exts = [];
          for (var i = 0; i < names.length; i++) {
            var factory = carve[names[i]];
            if (typeof factory === 'function') {
              exts.push(factory());
            }
          }
          return { extensions: exts, sourceLine: !!sourceLine };
        })
    """.trimIndent()

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
