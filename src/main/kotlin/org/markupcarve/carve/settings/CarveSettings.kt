package org.markupcarve.carve.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

enum class CarveRenderer {
    CARVE_JS,
    CARVE_PHP,
}

/**
 * Whether the language server may resolve `{{ path }}` includes (spec PART 9
 * section 19). The three values are the server's own, and [AUTO] is its default:
 * resolve in a trusted project, refuse in an untrusted one. Section 19 makes the
 * capability opt-in, so the plugin has to say which one - sending nothing reads
 * as a refusal.
 */
enum class CarveIncludeMode(val wireValue: String) {
    AUTO("auto"),
    ON("on"),
    OFF("off"),
}

@State(
    name = "CarveSettings",
    storages = [Storage("carve.xml")],
)
@Service(Service.Level.PROJECT)
class CarveSettings : PersistentStateComponent<CarveSettings.State> {

    data class State(
        var renderer: CarveRenderer = CarveRenderer.CARVE_JS,
        var phpPath: String = "php",
        var phpCarveScript: String = "",
        var customCssPath: String = "",
        var nodePath: String = "",
        var includeMode: CarveIncludeMode = CarveIncludeMode.AUTO,
        var includeRoot: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var renderer: CarveRenderer
        get() = state.renderer
        set(value) { state.renderer = value }

    var phpPath: String
        get() = state.phpPath
        set(value) { state.phpPath = value }

    var phpCarveScript: String
        get() = state.phpCarveScript
        set(value) { state.phpCarveScript = value }

    var customCssPath: String
        get() = state.customCssPath
        set(value) { state.customCssPath = value }

    var nodePath: String
        get() = state.nodePath
        set(value) { state.nodePath = value }

    var includeMode: CarveIncludeMode
        get() = state.includeMode
        set(value) { state.includeMode = value }

    /**
     * Containment root for include resolution. EMPTY MEANS UNSET, and unset is
     * not the same as "the current directory" - see
     * `CarveLspInitializationOptions`, which omits a blank value rather than
     * forwarding it. Left unset the server uses the project root, falling back
     * to the document's own directory.
     */
    var includeRoot: String
        get() = state.includeRoot
        set(value) { state.includeRoot = value }

    companion object {
        fun getInstance(project: Project): CarveSettings =
            project.getService(CarveSettings::class.java)
    }
}
