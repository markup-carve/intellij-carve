package org.markupcarve.carve

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders Carve to HTML by shelling out to `markup-carve/carve-php`.
 *
 * Resolution order for the command:
 *   1. an explicit converter script ([scriptPath]) if set and present;
 *   2. the project's `vendor/bin/carve` CLI if installed;
 *   3. an inline one-liner using `Carve\CarveConverter` against the project autoloader.
 *
 * `safe` adds `--safe` to the CLI and turns on safe mode in the one-liner. A custom
 * script takes no flags and gets `CARVE_SAFE=1` in its environment instead; whether it
 * honors that is up to the script, and the preview's CSP still blocks script execution.
 */
object CarvePhpConverter {

    private val LOG = Logger.getInstance(CarvePhpConverter::class.java)

    // carve-php moved from `Carve\` to `MarkupCarve\Carve\`; both are tried.
    private fun inlineScript(safe: Boolean) = """
        require_once 'vendor/autoload.php';
        ${'$'}input = file_get_contents('php://stdin');
        ${'$'}cls = class_exists('\\MarkupCarve\\Carve\\CarveConverter') ? '\\MarkupCarve\\Carve\\CarveConverter' : '\\Carve\\CarveConverter';
        ${'$'}converter = new ${'$'}cls();
        if (${if (safe) "true" else "false"}) {
            if (!method_exists(${'$'}converter, 'setSafeMode')) {
                fwrite(STDERR, 'This carve-php has no safe mode; raw HTML cannot be suppressed.');
                exit(3);
            }
            ${'$'}converter->setSafeMode(true);
        }
        echo ${'$'}converter->convert(${'$'}input);
    """.trimIndent()

    fun toHtml(
        carve: String,
        phpPath: String = "php",
        scriptPath: String = "",
        workingDir: String? = null,
        safe: Boolean = false,
    ): Result<String> {
        return try {
            val effectivePhpPath = phpPath.ifBlank { "php" }
            val vendorCli = workingDir?.let { File(it, "vendor/bin/carve") }

            val command = when {
                scriptPath.isNotBlank() && File(scriptPath).exists() ->
                    listOf(effectivePhpPath, scriptPath)
                vendorCli != null && vendorCli.exists() ->
                    listOfNotNull(effectivePhpPath, vendorCli.absolutePath, "--safe".takeIf { safe })
                else ->
                    listOf(effectivePhpPath, "-r", inlineScript(safe))
            }

            val processBuilder = ProcessBuilder(command).redirectErrorStream(false)
            if (safe) processBuilder.environment()["CARVE_SAFE"] = "1"
            if (workingDir != null) {
                val dir = File(workingDir)
                if (!dir.exists()) {
                    return Result.failure(Exception("Working directory does not exist: $workingDir"))
                }
                processBuilder.directory(dir)
            }

            val process = processBuilder.start()
            process.outputStream.bufferedWriter().use { it.write(carve) }

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return Result.failure(Exception("PHP process timed out"))
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                LOG.warn("carve-php failed: $error")
                return Result.failure(Exception("PHP exited with code $exitCode: $error"))
            }

            Result.success(process.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            LOG.warn("carve-php exception", e)
            Result.failure(e)
        }
    }
}
