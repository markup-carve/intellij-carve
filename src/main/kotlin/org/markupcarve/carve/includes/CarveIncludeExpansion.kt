package org.markupcarve.carve.includes

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.nio.file.Path

/**
 * Drives the engine's include pass (spec PART 9 section 19) from the preview.
 *
 * The composition is the engine's: `parse` with the `includes` option merges
 * every child, then `render` draws the tree (Carve 0.2, C2-N-001). The
 * extension set reaches the parse, every child parse (C2-N-015) and the
 * render alike, so a construct means the same thing in a child as in its
 * parent. Warnings are the parse diagnostics whose code starts with `include`.
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

    /** The document written back as ONE self-contained Carve file. */
    data class Flattened(
        val carve: String,
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

    /** Reads the object [FLATTEN_JS] returns. */
    fun readFlattened(value: Value): Flattened = Flattened(
        carve = value.getMember("carve").asString(),
        warnings = warningsOf(value),
        dependencies = dependenciesOf(value),
        suppressedWarnings = value.getMember("suppressedWarnings").asInt(),
    )

    /** Reads the object [EXPAND_JS] returns. */
    fun read(value: Value): Result = Result(
        html = value.getMember("html").asString(),
        warnings = warningsOf(value),
        dependencies = dependenciesOf(value),
        suppressedWarnings = value.getMember("suppressedWarnings").asInt(),
    )

    private fun warningsOf(value: Value): List<Warning> =
        value.getMember("warnings").let { list ->
            (0 until list.arraySize).map { i ->
                val w = list.getArrayElement(i)
                Warning(
                    rule = w.getMember("rule").asString(),
                    message = w.getMember("message").asString(),
                    line = w.getMember("line").asInt(),
                    column = w.getMember("column").asInt(),
                )
            }
        }

    private fun dependenciesOf(value: Value): List<Dependency> =
        value.getMember("dependencies").let { list ->
            (0 until list.arraySize).map { i ->
                val d = list.getArrayElement(i)
                Dependency(
                    id = d.getMember("id").asString(),
                    resolved = d.getMember("resolved").asBoolean(),
                    denial = d.getMember("denial")?.takeIf { !it.isNull }?.asString(),
                )
            }
        }

    /**
     * Shared by [EXPAND_JS] and [FLATTEN_JS]: the 0.2 resolver over the host
     * resolver, and the warnings of a parse. The dependencies are every target
     * the resolver was asked for, in order, as the 0.1 walk reported them. The engine names
     * the including file in `from`, which is the host resolver's parent id
     * (the 0.1 stack started with the document's own id too).
     */
    private val INCLUDE_HELPERS_JS = """
          var touched = [];
          var seen = {};
          var touch = function (id, resolved, denial) {
            var key = id + '|' + resolved;
            if (seen[key]) return;
            seen[key] = true;
            touched.push({ id: id, resolved: resolved, denial: denial });
          };
          var resolver = function (request) {
            var answer = hostResolve(request.path, request.from === undefined ? null : request.from);
            if (answer.source === null || answer.source === undefined) {
              // An unread target is named by where it would be, the host's id (I11).
              touch(answer.id || request.path, false, answer.denial || null);
              var outside = answer.denial === 'outside-root' || answer.denial === 'absolute-denied' || answer.denial === 'remote-not-allowed';
              return { error: outside ? 'outside_root' : 'not_found' };
            }
            touch(answer.id, true, null);
            return { text: String(answer.source).replace(/\r(?!\n)/g, '\n'), id: answer.id };
          };
          var place = function (byte) {
            var bytes = 0, line = 1, column = 1;
            for (var i = 0; i < source.length && bytes < byte; i++) {
              var code = source.codePointAt(i);
              if (code > 0xffff) i++;
              bytes += code < 0x80 ? 1 : code < 0x800 ? 2 : code < 0x10000 ? 3 : 4;
              if (code === 10) { line++; column = 1; } else { column++; }
            }
            return { line: line, column: column };
          };
          var anchor = function (doc, d) {
            if (d.range && (d.file === undefined || d.file === sourcePath)) return d.range.startOffset;
            var node = doc, at = 0;
            var parts = (d.path || '').split('/').slice(1);
            for (var k = 0; k < parts.length && node; k++) {
              node = node[parts[k]];
              if (node && node.pos && node.pos.file === undefined) at = node.pos.startOffset;
            }
            return at;
          };
          var report = function (parsed) {
            var warnings = parsed.diagnostics
              .filter(function (d) { return d.code.indexOf('include') === 0; })
              .map(function (d) {
                var where = place(anchor(parsed.document, d));
                return { rule: d.code, message: d.message, line: where.line, column: where.column };
              });
            return { warnings: warnings, dependencies: touched, suppressedWarnings: parsed.suppressedDiagnostics };
          };
    """

    /**
     * `(source, options, hostResolve, sourcePath) -> { html, warnings, dependencies, suppressedWarnings }`.
     */
    val EXPAND_JS = """
        (function (source, options, hostResolve, sourcePath) {
        $INCLUDE_HELPERS_JS
          var parsed = carve.parseWithDiagnostics(source, {
            extensions: options.extensions,
            positions: true,
            includes: { resolver: resolver, file: sourcePath }
          });
          var result = report(parsed);
          result.html = carve.renderPreview(carve.decorateLinks(parsed.document), options);
          return result;
        })
    """.trimIndent()

    /**
     * `(source, hostResolve, sourcePath) -> { carve, warnings, dependencies, suppressedWarnings }`.
     *
     * The `carve flatten` shape, through the engine's `flatten`: the children
     * of each expanded include are spliced in place (included frontmatter goes)
     * and the tree is written as canonical Carve, with no extension set, as the
     * CLI does. An include that did not expand keeps its directive.
     */
    val FLATTEN_JS = """
        (function (source, hostResolve, sourcePath) {
        $INCLUDE_HELPERS_JS
          var parsed = carve.parseWithDiagnostics(source, {
            positions: true,
            includes: { resolver: resolver, file: sourcePath }
          });
          var result = report(parsed);
          result.carve = carve.flatten(parsed.document).source;
          return result;
        })
    """.trimIndent()
}
