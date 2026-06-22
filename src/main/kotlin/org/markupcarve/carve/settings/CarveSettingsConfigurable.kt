package org.markupcarve.carve.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.event.HyperlinkEvent

class CarveSettingsConfigurable(private val project: Project) : BoundConfigurable("Carve") {

    private val settings get() = CarveSettings.getInstance(project)

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
