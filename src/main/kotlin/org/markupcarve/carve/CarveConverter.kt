package org.markupcarve.carve

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.markupcarve.carve.includes.CarveIncludeExpansion
import org.markupcarve.carve.settings.CarveRenderer
import org.markupcarve.carve.settings.CarveSettings
import java.nio.file.Path

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
     * @param allowRawHtml when false, `=html` blocks and `{=html}` spans render as escaped
     *   source (carve-js) or through carve-php's `--safe` mode. The preview passes false
     *   unless the user allowed raw HTML in a trusted project; export keeps the default.
     */
    fun toHtml(
        carve: String,
        project: Project? = null,
        sourceLine: Boolean = false,
        allowRawHtml: Boolean = true,
    ): String {
        if (project != null && CarveSettings.getInstance(project).renderer == CarveRenderer.CARVE_PHP) {
            val settings = CarveSettings.getInstance(project)
            val result = CarvePhpConverter.toHtml(
                carve = carve,
                phpPath = settings.phpPath,
                scriptPath = settings.phpCarveScript,
                workingDir = project.basePath,
                safe = !allowRawHtml,
            )
            if (result.isSuccess) {
                return result.getOrThrow()
            }
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            return errorHtml("Carve PHP error", error)
        }

        return renderWithJs(carve, RENDER_HTML_JS, sourceLine, allowRawHtml)
    }

    /**
     * Render to Markdown.
     *
     * There is no PHP branch here on purpose: the renderer setting names which
     * engine produces the PREVIEW, and the Markdown target is only reachable
     * through the bundled JS build. A `CarveRenderer.CARVE_PHP` project still
     * exports Markdown, from the bundle, rather than failing.
     */
    fun toMarkdown(carve: String): String = renderWithJs(carve, RENDER_MARKDOWN_JS)

    /**
     * Render to HTML with `{{ path }}` includes expanded (spec PART 9 section 19).
     *
     * [root] is the containment root [org.markupcarve.carve.includes.CarveIncludeRoot]
     * derived, and [documentId] the canonical path of the document being
     * previewed. Both are the host's to supply; the engine performs no file I/O.
     *
     * Returns null when the bundle is unavailable or the expansion throws, so a
     * caller falls back to the plain render rather than showing an error page
     * for a document that renders perfectly well without its includes.
     */
    fun toHtmlWithIncludes(
        carve: String,
        root: Path,
        documentId: String,
        sourceLine: Boolean = false,
        allowRawHtml: Boolean = true,
    ): CarveIncludeExpansion.Result? {
        if (carveJs.isEmpty()) return null
        return try {
            Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()
                .use { context ->
                    context.eval(Source.newBuilder("js", carveJs, "carve.iife.js").build())
                    val optionsBuilder = context.eval(
                        Source.newBuilder("js", CARVE_OPTIONS_JS, "carve-options.js").build(),
                    )
                    val expand = context.eval(
                        Source.newBuilder("js", CarveIncludeExpansion.EXPAND_JS, "carve-includes.js").build(),
                    )
                    CarveIncludeExpansion.read(
                        expand.execute(
                            carve,
                            optionsBuilder.execute(sourceLine, allowRawHtml),
                            CarveIncludeExpansion.resolver(root),
                            documentId,
                        ),
                    )
                }
        } catch (e: Exception) {
            LOG.warn("Carve include expansion failed", e)
            null
        }
    }

    /**
     * Write the document back as ONE self-contained Carve file, every include
     * expanded in place - the `carve flatten` shape.
     *
     * A plain Carve export must NOT do this. Spec I15 says writing a document
     * back as Carve returns the author's document, directives and all, which is
     * why this is a separate named action rather than an option on one.
     */
    fun toFlattenedCarve(
        carve: String,
        root: Path,
        documentId: String,
    ): CarveIncludeExpansion.Flattened? {
        if (carveJs.isEmpty()) return null
        return try {
            Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()
                .use { context ->
                    context.eval(Source.newBuilder("js", carveJs, "carve.iife.js").build())
                    val flatten = context.eval(
                        Source.newBuilder("js", CarveIncludeExpansion.FLATTEN_JS, "carve-flatten.js").build(),
                    )
                    CarveIncludeExpansion.readFlattened(
                        flatten.execute(carve, CarveIncludeExpansion.resolver(root), documentId),
                    )
                }
        } catch (e: Exception) {
            LOG.warn("Carve flatten failed", e)
            null
        }
    }

    /**
     * Convert Markdown or HTML to Carve with the bundled engine's importers.
     *
     * Fails instead of returning an error page, so a broken conversion never
     * lands in a `.crv` file.
     */
    fun importToCarve(source: String, format: CarveImportFormat): Result<String> {
        if (carveJs.isEmpty()) {
            return Result.failure(IllegalStateException("Bundled carve.iife.js is missing from the plugin."))
        }
        return try {
            Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()
                .use { context ->
                    context.eval(Source.newBuilder("js", carveJs, "carve.iife.js").build())
                    val engine = context.getBindings("js").getMember("carve")
                    val importers = engine?.getMember("importers")
                        ?: return Result.failure(IllegalStateException("'carve.importers' not found in bundle."))
                    val options = context.eval("js", "(function (from) { return { from: from }; })").execute(format.from)
                    // importDocument writes canonical Carve source already.
                    val result = importers.getMember("importDocument").execute(source, options)
                    Result.success(result.getMember("source").asString())
                }
        } catch (e: Exception) {
            LOG.warn("Carve import (${format.from}) failed", e)
            Result.failure(e)
        }
    }

    private fun renderWithJs(
        carve: String,
        renderJs: String,
        sourceLine: Boolean = false,
        allowRawHtml: Boolean = true,
    ): String {
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
                    context.getBindings("js").getMember("carve")
                        ?: return errorHtml("Carve renderer error", "Global 'carve' not found in bundle.")
                    val optionsBuilder = context.eval(
                        Source.newBuilder("js", CARVE_OPTIONS_JS, "carve-options.js").build(),
                    )
                    val options = optionsBuilder.execute(sourceLine, allowRawHtml)
                    val render = context.eval(Source.newBuilder("js", renderJs, "carve-render.js").build())
                    render.execute(carve, options).asString()
                }
        } catch (e: Exception) {
            LOG.warn("Carve JS render failed", e)
            errorHtml("Carve render error", e.message ?: e.toString())
        }
    }

    /** `(source, options) -> html`: parse with the extension set, decorate links, render. */
    private val RENDER_HTML_JS = """
        (function (source, options) {
          var tree = carve.parse(source, { extensions: options.extensions });
          return carve.render(carve.decorateLinks(tree), options);
        })
    """.trimIndent()

    /** `(source) -> markdown`: the engine's own Markdown export, with no extension set. */
    private val RENDER_MARKDOWN_JS = """
        (function (source) {
          return carve.exporters.toMarkdown(carve.parse(source)).output;
        })
    """.trimIndent()

    /**
     * Returns the render options: the standard Carve 0.2 extensions an embedded
     * preview can draw zero-config (from `carve.extensions`, tabs from the root),
     * scroll anchors and the raw-HTML switch.
     *
     * `headingReference`, `autolink`, `externalLinks`, `headingPermalinks` and
     * `citations` were 0.1 extensions with no 0.2 counterpart. Bare URLs and
     * off-site links are decorated by `carve.decorateLinks` instead; citations
     * wait for carve-js#5082. Each factory is called fresh per render.
     */
    private val CARVE_OPTIONS_JS = """
        (function (sourceLine, allowRawHtml) {
          var e = carve.extensions;
          var set = e.withExtensions(
            e.details(), e.mermaid(), e.mathBlock(), e.spoiler(), e.chart(), e.codeGroup(), e.listTable(),
            { extensions: carve.tabsExtensions, renderers: { 'carve.tabs': carve.tabsRenderer } }
          );
          return {
            extensions: set.extensions,
            renderers: set.renderers,
            sourceLines: !!sourceLine,
            rawHtml: allowRawHtml !== false
          };
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
