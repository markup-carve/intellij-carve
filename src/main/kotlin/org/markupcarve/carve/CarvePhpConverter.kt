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
 */
object CarvePhpConverter {

    private val LOG = Logger.getInstance(CarvePhpConverter::class.java)

    private val inlineScript = """
        require_once 'vendor/autoload.php';
        ${'$'}input = file_get_contents('php://stdin');
        echo (new \Carve\CarveConverter())->convert(${'$'}input);
    """.trimIndent()

    fun toHtml(
        carve: String,
        phpPath: String = "php",
        scriptPath: String = "",
        workingDir: String? = null,
    ): Result<String> {
        return try {
            val effectivePhpPath = phpPath.ifBlank { "php" }
            val vendorCli = workingDir?.let { File(it, "vendor/bin/carve") }

            val command = when {
                scriptPath.isNotBlank() && File(scriptPath).exists() ->
                    listOf(effectivePhpPath, scriptPath)
                vendorCli != null && vendorCli.exists() ->
                    listOf(effectivePhpPath, vendorCli.absolutePath)
                else ->
                    listOf(effectivePhpPath, "-r", inlineScript)
            }

            val processBuilder = ProcessBuilder(command).redirectErrorStream(false)
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
