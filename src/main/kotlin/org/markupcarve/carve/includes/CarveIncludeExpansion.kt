package org.markupcarve.carve.includes

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.nio.file.Path

/**
 * Drives the engine's include pass (spec PART 9 section 19) from the preview.
 *
 * The composition is the engine's, entered one step later than `carveToHtml`:
 * parse, [expandIncludes], then `renderDocument`. Reproducing that order by
 * hand is what this avoids - it is internal, and a pipeline that omits a step
 * still renders, so what it omits shows up as output that looks right
 * (markup-carve/carve-js#1677).
 *
 * `renderDocument` names two host obligations, and this is where both are met.
 * The extension set reaches the parse, the child parse and the render alike, so
 * a construct means the same thing in a child as in its parent. `positions:
 * true` RESTATES the parser's own default rather than changing anything; it is
 * written out so a default flip cannot silently alter figure resolution, and no
 * document in the suite tells the two settings apart today.
 */
object CarveIncludeExpansion {

    /** One warning as spec section 19 defines it, flattened for the preview. */
    data class Warning(val rule: String, val message: String, val line: Int, val column: Int)

    /** One include target the expansion touched, resolved or not. */
    data class Dependency(val id: String, val resolved: Boolean, val denial: String?)

    data class Result(
        val html: String,
        val warnings: List<Warning>,
        val dependencies: List<Dependency>,
        val suppressedWarnings: Int,
    )

    /**
     * The host resolver, as a value the bundle can call.
     *
     * Takes the directive path and the including file's canonical id rather
     * than the engine's `IncludeContext`, so nothing about the engine's context
     * shape has to cross into Kotlin. [EXPAND_JS] unpacks the stack and rebuilds
     * a plain JS object from the answer, which also keeps `source: null` a
     * literal JS null - the engine tests it with `===`, and a host value that
     * arrived as `undefined` would be read as a resolver that produced nothing
     * at all rather than as a named unresolved target (I11).
     */
    fun resolver(root: Path): ProxyExecutable = ProxyExecutable { args ->
        val path = args.getOrNull(0)?.asString().orEmpty()
        val parentId = args.getOrNull(1)?.takeIf { !it.isNull }?.asString()
        val answer = CarveIncludeResolver.resolve(root, path, parentId)
        ProxyObject.fromMap(
            mapOf(
                "source" to answer.source,
                "id" to answer.id,
                "denial" to answer.denial,
            ),
        )
    }

    /** Reads the object [EXPAND_JS] returns. */
    fun read(value: Value): Result = Result(
        html = value.getMember("html").asString(),
        warnings = value.getMember("warnings").let { list ->
            (0 until list.arraySize).map { i ->
                val w = list.getArrayElement(i)
                Warning(
                    rule = w.getMember("rule").asString(),
                    message = w.getMember("message").asString(),
                    line = w.getMember("line").asInt(),
                    column = w.getMember("column").asInt(),
                )
            }
        },
        dependencies = value.getMember("dependencies").let { list ->
            (0 until list.arraySize).map { i ->
                val d = list.getArrayElement(i)
                Dependency(
                    id = d.getMember("id").asString(),
                    resolved = d.getMember("resolved").asBoolean(),
                    denial = d.getMember("denial")?.takeIf { !it.isNull }?.asString(),
                )
            }
        },
        suppressedWarnings = value.getMember("suppressedWarnings").asInt(),
    )

    /**
     * `(source, options, hostResolve, sourcePath) -> { html, warnings, dependencies, suppressedWarnings }`.
     */
    val EXPAND_JS = """
        (function (source, options, hostResolve, sourcePath) {
          var doc = carve.parse(source, { extensions: options.extensions, positions: true });
          var result = carve.expandIncludes(doc, source, {
            sourcePath: sourcePath,
            extensions: options.extensions,
            resolve: function (path, ctx) {
              var stack = ctx.stack;
              var parent = stack && stack.length ? stack[stack.length - 1] : null;
              var answer = hostResolve(path, parent);
              if (answer.source === null || answer.source === undefined) {
                return { source: null, id: answer.id, denial: answer.denial || undefined };
              }
              return { source: answer.source, id: answer.id };
            }
          });
          return {
            html: carve.renderDocument(result.doc, options),
            warnings: result.warnings.map(function (w) {
              return { rule: w.rule, message: w.message, line: w.line, column: w.column };
            }),
            dependencies: result.dependencies.map(function (d) {
              return { id: d.id, resolved: d.resolved, denial: d.denial || null };
            }),
            suppressedWarnings: result.suppressedWarnings
          };
        })
    """.trimIndent()
}
