package org.markupcarve.carve

import org.graalvm.polyglot.Context

/**
 * Boots a GraalJS polyglot context outside the IntelliJ test harness, so it can be run
 * under an explicit JDK toolchain via the `graalSmoke` Gradle task.
 *
 * Why this exists: the live preview renders through GraalJS, but the IntelliJ `test` task
 * forks on the IDE's bundled JBR, so the suite only ever exercises that one JDK. The old
 * GraalJS line (org.graalvm.js:js:23.0.2) shipped a Truffle that calls
 * `sun.misc.Unsafe.ensureClassInitialized`, which newer JDKs removed - so on a current JBR
 * the preview died with NoSuchMethodError while every test stayed green. Booting a context
 * under a pinned, newer JDK turns that class of breakage into a build failure.
 */
fun main() {
    Context.newBuilder("js")
        .allowAllAccess(false)
        .option("engine.WarnInterpreterOnly", "false")
        .build()
        .use { context ->
            val result = context.eval("js", "1 + 1").asInt()
            check(result == 2) { "GraalJS evaluated 1 + 1 as $result" }
        }

    println("GraalJS polyglot context booted OK on JDK ${System.getProperty("java.version")}")
}
