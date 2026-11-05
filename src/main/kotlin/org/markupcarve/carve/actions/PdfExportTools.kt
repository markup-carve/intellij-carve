package org.markupcarve.carve.actions

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

/** Locates PDF tools without asking a shell to interpret file names or settings. */
internal object PdfExportTools {
    enum class Kind { CRV2PDF, CHROMIUM }
    data class Tool(val kind: Kind, val executable: String)

    fun find(
        pdfPath: String,
        browserPath: String,
        os: String = System.getProperty("os.name").orEmpty(),
        path: String = System.getenv("PATH").orEmpty(),
        exists: (File) -> Boolean = { it.isFile && it.canExecute() },
    ): Tool? {
        val windows = os.lowercase().contains("win")
        fun locate(explicit: String, names: List<String>, defaults: List<String>): String? {
            if (explicit.isNotBlank()) {
                val candidate = File(explicit.trim())
                return candidate.absolutePath.takeIf { exists(candidate) }
            }
            for (directory in path.split(File.pathSeparatorChar).filter { it.isNotBlank() }) {
                for (name in names) {
                    val candidate = File(directory, name)
                    if (exists(candidate)) return candidate.absolutePath
                }
            }
            for (name in defaults) {
                val candidate = File(name)
                if (exists(candidate)) return candidate.absolutePath
            }
            return null
        }
        val pdf = locate(pdfPath, if (windows) listOf("crv2pdf.exe", "crv2pdf.cmd", "crv2pdf") else listOf("crv2pdf"), emptyList())
        if (pdf != null) return Tool(Kind.CRV2PDF, pdf)
        val names = when {
            windows -> listOf("chrome.exe", "msedge.exe", "chromium.exe")
            os.lowercase().contains("mac") -> listOf("Google Chrome", "Chromium", "Microsoft Edge")
            else -> listOf("google-chrome", "chromium", "chromium-browser", "microsoft-edge", "google-chrome-stable")
        }
        val defaults = when {
            windows -> listOf(
                "C:/Program Files/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            )
            os.lowercase().contains("mac") -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            )
            else -> listOf("/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/microsoft-edge")
        }
        return locate(browserPath, names, defaults)?.let { Tool(Kind.CHROMIUM, it) }
    }

    fun command(tool: Tool, input: File, output: File, includeRoot: File? = null): GeneralCommandLine =
        GeneralCommandLine(tool.executable).apply {
            when (tool.kind) {
                Kind.CRV2PDF -> {
                    addParameters(input.absolutePath, output.absolutePath)
                    if (includeRoot != null) addParameters("--include-root", includeRoot.absolutePath)
                }
                Kind.CHROMIUM -> addParameters(
                    "--headless", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                    "--print-to-pdf=${output.absolutePath}", input.toURI().toASCIIString(),
                )
            }
        }
}
