package org.markupcarve.carve.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import org.markupcarve.carve.lsp.CarveLspRestart
import javax.swing.event.HyperlinkEvent

class CarveSettingsConfigurable(private val project: Project) : BoundConfigurable("Carve") {

    private val settings get() = CarveSettings.getInstance(project)

    /**
     * carve-lsp reads its settings once, in `initialize`, so a changed include
     * setting reaches a running server only across a restart. Without this the
     * radio button appears to do nothing until the IDE is restarted.
     */
    override fun apply() {
        val before = settings.includeMode to settings.includeRoot
        super.apply()
        if (before != settings.includeMode to settings.includeRoot) {
            CarveLspRestart.restart(project)
        }
    }

    override fun createPanel(): DialogPanel {
        return panel {
            buttonsGroup("Renderer:") {
                row {
                    radioButton("carve-js (JavaScript via GraalJS)", CarveRenderer.CARVE_JS)
                        .comment("Default renderer, bundled with the plugin - no dependencies required")
                }
                row {
                    radioButton("carve-php (PHP CLI)", CarveRenderer.CARVE_PHP)
                        .comment("Requires markup-carve/carve-php installed in your project")
                }
            }.bind(settings::renderer)

            group("PHP Settings") {
                row {
                    text(
                        "Requires <a href=\"https://github.com/markup-carve/carve-php\">markup-carve/carve-php</a> " +
                            "installed via Composer in your project:",
                    ).applyToComponent {
                        addHyperlinkListener { e ->
                            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                                BrowserUtil.browse(e.url)
                            }
                        }
                    }
                }
                row {
                    text("<code>composer require markup-carve/carve-php</code>")
                }
                row("PHP executable:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
                            .withTitle("Select PHP Executable"),
                        project,
                    ).columns(COLUMNS_LARGE)
                        .bindText(settings::phpPath)
                        .comment("Path to PHP binary (default: php)")
                }
                row("Converter script:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFileDescriptor("php")
                            .withTitle("Select Carve Script"),
                        project,
                    ).columns(COLUMNS_LARGE)
                        .bindText(settings::phpCarveScript)
                        .comment(
                            "Optional PHP script that reads stdin and outputs HTML. " +
                                "Leave empty to use vendor/bin/carve or the built-in one-liner.",
                        )
                }
            }

            group("Language Server") {
                row("Node.js executable:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
                            .withTitle("Select Node.js Executable"),
                        project,
                    ).columns(COLUMNS_LARGE)
                        .bindText(settings::nodePath)
                        .comment(
                            "Path to the Node.js binary that runs the bundled Carve language " +
                                "server (diagnostics, completion, folding, outline, code actions). " +
                                "Leave empty to use <code>node</code> from your PATH.",
                        )
                }
            }

            group("Includes") {
                row {
                    text(
                        "A <code>{{ path }}</code> directive pulls another Carve file into this one " +
                            "(spec PART 9 &sect; 19). With resolution on, the language server offers " +
                            "go-to-definition into the included file, path completion, the child's " +
                            "headings in the Structure view, and a warning where a target does not resolve.",
                    )
                }
                buttonsGroup("Resolve includes:") {
                    row {
                        radioButton("In trusted projects", CarveIncludeMode.AUTO)
                            .comment("Recommended. Follows the IDE's project trust, which is what &sect; 19 asks for.")
                    }
                    row {
                        radioButton("Always", CarveIncludeMode.ON)
                    }
                    row {
                        radioButton("Never", CarveIncludeMode.OFF)
                    }
                }.bind(settings::includeMode)
                row("Containment root:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor()
                            .withTitle("Select Include Root"),
                        project,
                    ).columns(COLUMNS_LARGE)
                        .bindText(settings::includeRoot)
                        .comment(
                            "No include may resolve outside this folder. Leave empty to use the " +
                                "project root, falling back to the document's own directory.",
                        )
                }
            }

            group("Preview Styling") {
                row("Custom CSS file:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFileDescriptor("css")
                            .withTitle("Select CSS File"),
                        project,
                    ).columns(COLUMNS_LARGE)
                        .bindText(settings::customCssPath)
                        .comment("Injected after the built-in styles, so your rules override them.")
                }
                row {
                    comment(
                        "A <code>carve-preview.css</code> next to the file or in the project root " +
                            "(or <code>.carve/preview.css</code>) is picked up automatically. " +
                            "Order: built-in &rarr; project file &rarr; this setting (last wins).",
                    )
                }
            }
        }
    }
}
