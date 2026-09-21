package org.markupcarve.carve.includes

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** A child the walk read. [id] is the canonical absolute path the server returned. */
data class WalkedDocument(val id: String, val source: String)

/**
 * A target the walk touched.
 *
 * A resolved target is named by its canonical absolute path. An unresolved one
 * is named by the path as the author wrote it, because a file that is not there
 * has no canonical path to name it by.
 */
data class WalkedDependency(val id: String, val resolved: Boolean)

/** Every file the document reaches, transitively. */
data class IncludeWalk(
    val documents: List<WalkedDocument>,
    val dependencies: List<WalkedDependency>,
)

/**
 * Walks a document's `{{ path }}` includes by asking the bundled language
 * server about each one (spec PART 9 section 19).
 *
 * ## Why the server rather than a scan of the source
 *
 * Two questions have to be answered per candidate and this plugin must not
 * answer either itself. Which `{{ ... }}` is a live directive is the engine's
 * recognizer - one inside a fenced code block is text, and the directive's
 * option slot is spelled with exact-negation lookaheads a hand-written regex
 * gets subtly wrong. Where a directive may read from is the containment root,
 * which is a security boundary; deriving it again here would be a second answer
 * to the one question that must have exactly one.
 *
 * So the plugin enumerates CANDIDATES - every `{{` in the text - and the server
 * arbitrates each. The division is sound in the direction that matters: every
 * real directive contains `{{`, so none can be missed, and a candidate that is
 * not one comes back unresolved and unclaimed. Measured against the vendored
 * server: `{{ not-a-directive.crv }}` inside a fence answers null and raises no
 * diagnostic, `{{ ../../outside/secret.crv }}` answers null and raises
 * `include-denied`, `{{ sub/child.crv }}` answers the child's URI.
 *
 * ## The two answers, and why both are needed
 *
 * `textDocument/definition` names a target it could read. It cannot name one it
 * could not: there is no location to return, so a missing file, a target
 * outside the root and a `{{` that was never a directive all answer null
 * alike. The published `include-unresolved`, `include-denied` or
 * `include-non-text` diagnostic is what separates the first two from the third,
 * and its message carries the path as written. Both
 * are collected, which is why a bundle can report what it could not copy
 * instead of quietly shipping a document whose includes do not resolve.
 *
 * ## Why a private server process
 *
 * The IDE's own carve-lsp instance only answers for documents it has open, and
 * a transitive walk asks about children the user never opened. Sending
 * `didOpen` for them down the shared connection would fight lsp4ij for the
 * document lifecycle, and closing them afterwards would close a file the user
 * did have open. A short-lived private process has neither problem, and it
 * takes the SAME `initializationOptions` the shared one gets, so one gate
 * decides for both.
 */
object CarveIncludeWalk {

    /** Server questions allowed for one walk, so a directive graph cannot spin. */
    const val MAX_REQUESTS = 2000

    /** Transitive depth, matching the engine's own `maxDepth` default. */
    const val MAX_DEPTH = 16

    private const val REQUEST_TIMEOUT_MS = 20_000L

    /**
     * The server's own wording for a target it could not open, as the engine
     * writes it. Read rather than reproduced: a wording change upstream costs
     * the unresolved NAMES, never the count, because an unparsed diagnostic
     * still contributes its directive.
     */
    private val UNRESOLVED_MESSAGE =
        Regex("""^Include "(.+)" (?:could not be resolved|was refused: .+)\.$""")

    /** A miss, and the two refusal codes carve-lsp 0.1.7 splits out of it. */
    private val UNRESOLVED_CODES = setOf("include-unresolved", "include-denied", "include-non-text")

    class WalkFailed(message: String) : Exception(message)

    /**
     * @param initializationOptions the payload
     *   [org.markupcarve.carve.lsp.CarveLspInitializationOptions] builds for the
     *   IDE's own server, passed through verbatim so the containment root the
     *   bundle obeys is the one the editor already enforces.
     * @param workspaceRoot the folder sent as `rootUri`, or null for a document
     *   with no project behind it - the server then roots containment at the
     *   document's own directory.
     */
    fun walk(
        nodePath: String,
        serverPath: Path,
        documentPath: Path,
        source: String,
        workspaceRoot: Path?,
        initializationOptions: JsonObject,
        maxDepth: Int = MAX_DEPTH,
        maxRequests: Int = MAX_REQUESTS,
    ): IncludeWalk {
        val process = ProcessBuilder(nodePath, serverPath.toString(), "--stdio")
            .redirectErrorStream(false)
            .start()
        // Drained on a thread of its own, for two reasons. A server that says
        // why it died says it here, and a failure message that repeats it beats
        // one that only reports the silence; and an undrained stderr pipe
        // eventually fills and blocks the process that is writing to it.
        val diagnosis = StringBuilder()
        val stderr = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(diagnosis) { diagnosis.append(line).append('\n') }
                }
            }
        }
        stderr.isDaemon = true
        stderr.start()
        return try {
            Session(process.outputStream, process.inputStream).use { session ->
                session.initialize(workspaceRoot, initializationOptions)
                session.walkFrom(documentPath, source, maxDepth, maxRequests)
            }
        } catch (e: WalkFailed) {
            val said = synchronized(diagnosis) { diagnosis.toString() }.trim().takeLast(600)
            throw if (said.isEmpty()) e else WalkFailed("${e.message}\n\n$said")
        } finally {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    private class Session(private val writer: OutputStream, private val input: InputStream) : AutoCloseable {
        // `serializeNulls`, because a JSON-RPC response MUST carry `result`
        // even when the result is nothing. Gson drops a null member by default,
        // so the reply to `client/registerCapability` went out as
        // `{"jsonrpc":"2.0","id":0}` and vscode-jsonrpc killed the connection
        // with "The received response has neither a result nor an error
        // property" - which reached the walk as an unexplained closed pipe.
        private val gson = GsonBuilder().serializeNulls().create()
        private val line = StringBuilder()
        private var nextId = 0

        /** Unresolved or refused include paths seen so far, per document URI. */
        private val unresolved = LinkedHashMap<String, MutableSet<String>>()

        fun initialize(workspaceRoot: Path?, initializationOptions: JsonObject) {
            val params = JsonObject()
            params.addProperty("processId", ProcessHandle.current().pid())
            params.add("capabilities", JsonObject())
            params.add("initializationOptions", initializationOptions)
            if (workspaceRoot != null) {
                val uri = workspaceRoot.toUri().toString()
                params.addProperty("rootUri", uri)
                val folder = JsonObject()
                folder.addProperty("uri", uri)
                folder.addProperty("name", workspaceRoot.fileName?.toString() ?: "root")
                params.add("workspaceFolders", gson.toJsonTree(listOf(folder)))
            }
            request("initialize", params)
            notify("initialized", JsonObject())
        }

        fun walkFrom(documentPath: Path, source: String, maxDepth: Int, maxRequests: Int): IncludeWalk {
            val root = documentPath.toAbsolutePath().normalize().toString()
            val documents = LinkedHashMap<String, String>()
            // First encounter fixes the order and a later successful read
            // upgrades an entry first seen unresolved - the rule the engine's
            // own dependency list follows, so a bundle and a `carve flatten` of
            // the same document name the same files in the same order.
            val dependencies = LinkedHashMap<String, Boolean>()
            // Files already walked, the ROOT included. Without the root in it a
            // cycle (a child that includes its parent) walks the parent a
            // second time before settling; with it, every file is asked about
            // once and a cycle costs nothing.
            val walked = mutableSetOf(root)
            var requests = 0

            var frontier = listOf(root to source)
            for (depth in 0 until maxDepth) {
                if (frontier.isEmpty()) break
                val next = mutableListOf<Pair<String, String>>()
                for ((path, text) in frontier) {
                    open(path, text)
                    for (candidate in directiveCandidates(text)) {
                        if (requests >= maxRequests) {
                            throw WalkFailed(
                                "This document reaches more than $maxRequests include candidates, " +
                                    "so the walk stopped rather than reading without bound.",
                            )
                        }
                        requests++
                        val target = definitionAt(path, candidate.line, candidate.character) ?: continue
                        if (!walked.add(target)) continue
                        val read = runCatching { Files.readString(Path.of(target)) }.getOrNull()
                        dependencies[target] = read != null
                        if (read == null) continue
                        documents[target] = read
                        next += target to read
                    }
                }
                frontier = next
            }
            // Loudly, like the request bound. A frontier left over means files
            // the walk never asked about, and returning the partial set would
            // write a bundle whose deepest directives point at nothing while
            // the summary reports a clean export.
            if (frontier.isNotEmpty()) {
                throw WalkFailed(
                    "This document's includes nest more than $maxDepth deep, so the walk stopped " +
                        "rather than exporting a bundle missing the files below that.",
                )
            }

            // One more round trip before reading the diagnostics off. Nothing
            // in the protocol promises a publish precedes the response to the
            // last request, and the walk reads its unresolved targets out of
            // publishes. Not load-bearing against the fixtures here - removing
            // it leaves every row green - so it is a deliberate guard against a
            // server that batches or debounces its validation, not a fix.
            drain()
            for (path in walked) {
                for (name in unresolved[uriOf(path)].orEmpty()) {
                    dependencies.putIfAbsent(name, false)
                }
            }

            return IncludeWalk(
                documents = documents.map { (id, text) -> WalkedDocument(id, text) },
                dependencies = dependencies.map { (id, resolved) -> WalkedDependency(id, resolved) },
            )
        }

        private fun drain() {
            request("shutdown", JsonObject())
        }

        /**
         * `file:///abs/path`, the authority form every language client sends
         * and the form the server echoes in its own diagnostics, so the URI a
         * diagnostic is keyed by compares equal to the one sent in `didOpen`.
         *
         * `Path.toUri()` rather than `java.io.File.toURI()`, which writes
         * `file:/abs/path` - one slash, same file, different string. The
         * vendored server accepts both (measured: the walk is unchanged when
         * this is switched), so this is the convention rather than a fix, and
         * it is spelled once here so a future URI comparison cannot be the
         * thing that discovers the difference.
         */
        private fun uriOf(path: String): String = Path.of(path).toUri().toString()

        private fun open(path: String, text: String) {
            val item = JsonObject()
            item.addProperty("uri", uriOf(path))
            item.addProperty("languageId", "carve")
            item.addProperty("version", 1)
            item.addProperty("text", text)
            val params = JsonObject()
            params.add("textDocument", item)
            notify("textDocument/didOpen", params)
        }

        private fun definitionAt(path: String, line: Int, character: Int): String? {
            val identifier = JsonObject()
            identifier.addProperty("uri", uriOf(path))
            val position = JsonObject()
            position.addProperty("line", line)
            position.addProperty("character", character)
            val params = JsonObject()
            params.add("textDocument", identifier)
            params.add("position", position)
            val result = request("textDocument/definition", params) ?: return null
            val location = when {
                result.isJsonObject -> result.asJsonObject
                result.isJsonArray && result.asJsonArray.size() > 0 -> result.asJsonArray[0].asJsonObject
                else -> return null
            }
            val uri = location.get("uri")?.asString ?: location.get("targetUri")?.asString ?: return null
            return runCatching { Path.of(java.net.URI(uri)).toString() }.getOrNull()
        }

        private fun request(method: String, params: JsonObject): JsonElement? {
            val id = ++nextId
            val message = JsonObject()
            message.addProperty("jsonrpc", "2.0")
            message.addProperty("id", id)
            message.addProperty("method", method)
            message.add("params", params)
            write(message)
            val deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    throw WalkFailed("The Carve language server did not answer '$method' in time.")
                }
                val incoming = read() ?: throw WalkFailed(
                    "The Carve language server closed the connection while answering '$method'.",
                )
                // A message carrying `method` is something the SERVER is
                // asking or telling, never an answer to this request - and its
                // id space is its own. carve-lsp sends `client/registerCapability`
                // with ids 0, 1, 2 during startup, so matching on the id alone
                // reads the server's third registration as the reply to this
                // walk's second question, finds no `result`, and returns null:
                // every include silently unresolved, no error anywhere.
                if (incoming.has("method")) {
                    collect(incoming)
                    answer(incoming)
                    continue
                }
                if (incoming.get("id")?.takeIf { it.isJsonPrimitive }?.asInt == id) {
                    val result = incoming.get("result")
                    return if (result == null || result.isJsonNull) null else result
                }
            }
        }

        /**
         * Answer a server-to-client REQUEST with an empty result.
         *
         * The walk registers for nothing and watches nothing, so there is
         * nothing to say - but a request left unanswered keeps the server
         * waiting, and carve-lsp registers three capabilities before it will
         * settle. A notification (no `id`) needs no answer.
         */
        private fun answer(incoming: JsonObject) {
            val id = incoming.get("id") ?: return
            val message = JsonObject()
            message.addProperty("jsonrpc", "2.0")
            message.add("id", id)
            message.add("result", com.google.gson.JsonNull.INSTANCE)
            write(message)
        }

        /** Keep the unresolved-include diagnostics; ignore every other notification. */
        private fun collect(message: JsonObject) {
            if (message.get("method")?.asString != "textDocument/publishDiagnostics") return
            val params = message.getAsJsonObject("params") ?: return
            val uri = params.get("uri")?.asString ?: return
            val into = unresolved.getOrPut(uri) { linkedSetOf() }
            val published = params.getAsJsonArray("diagnostics") ?: return
            for (entry in published) {
                val diagnostic = entry.asJsonObject
                if (diagnostic.get("code")?.asString !in UNRESOLVED_CODES) continue
                val text = diagnostic.get("message")?.asString ?: continue
                into += UNRESOLVED_MESSAGE.find(text)?.groupValues?.get(1) ?: text
            }
        }

        private fun notify(method: String, params: JsonObject) {
            val message = JsonObject()
            message.addProperty("jsonrpc", "2.0")
            message.addProperty("method", method)
            message.add("params", params)
            write(message)
        }

        private fun write(message: JsonObject) {
            val body = gson.toJson(message).toByteArray(StandardCharsets.UTF_8)
            writer.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            writer.write(body)
            writer.flush()
        }

        /**
         * One framed message, or null at end of stream.
         *
         * `Content-Length` counts BYTES, so the payload is read as bytes and
         * decoded once. Reading characters and counting them would truncate
         * every message carrying a non-ASCII path or heading.
         */
        private fun read(): JsonObject? {
            var length = -1
            while (true) {
                val header = readHeaderLine() ?: return null
                if (header.isEmpty()) break
                val separator = header.indexOf(':')
                if (separator > 0 && header.substring(0, separator).trim().equals("Content-Length", true)) {
                    length = header.substring(separator + 1).trim().toIntOrNull() ?: -1
                }
            }
            if (length < 0) return null
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) return null
                read += n
            }
            return JsonParser.parseString(String(body, StandardCharsets.UTF_8)).asJsonObject
        }

        private fun readHeaderLine(): String? {
            line.setLength(0)
            while (true) {
                val c = input.read()
                if (c < 0) return null
                if (c == '\n'.code) return line.toString().removeSuffix("\r")
                line.append(c.toChar())
            }
        }

        override fun close() {
            runCatching { writer.close() }
        }
    }

    /** A position the walk will ask the server about. */
    data class Candidate(val line: Int, val character: Int)

    /**
     * Every `{{` in the text, as a zero-based LSP position just inside it.
     *
     * A deliberate OVER-approximation, and its direction is what makes it safe:
     * it may name something that is not a directive, because the server rejects
     * those, but it can never miss one - a directive opens with a literal `{{`
     * (spec I1: `\{\{\s+`). Two on one line are both offered; an `indexOf`
     * that stopped at the first would silently drop the second, and the second
     * is a whole file missing from the bundle.
     *
     * The column has to fall INSIDE the directive, and `at + 2` always does
     * because the span starts at `at`. Measured against the vendored server by
     * probing every column of a line: for `Text {{ child.crv }} tail` the five
     * leading and five trailing columns answer null and the fifteen between
     * them all answer the child, and on `{{ a.crv }} and {{ b.crv }}` the two
     * spans answer their own targets with the five columns between them null.
     * So the position is span-sensitive but not token-sensitive - there is
     * nothing to be gained by aiming at the path itself.
     *
     * Columns are UTF-16 code units, which is what LSP positions count and what
     * `indexOf` on a Kotlin string returns. Counting codepoints instead would
     * drift left by one per astral character earlier in the line, and far
     * enough left the probe leaves the span.
     */
    fun directiveCandidates(text: String): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        text.split("\n").forEachIndexed { number, content ->
            var at = content.indexOf("{{")
            while (at >= 0) {
                candidates += Candidate(number, at + 2)
                at = content.indexOf("{{", at + 2)
            }
        }
        return candidates
    }
}
