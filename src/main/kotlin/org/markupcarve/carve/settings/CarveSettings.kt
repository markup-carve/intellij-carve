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

    companion object {
        fun getInstance(project: Project): CarveSettings =
            project.getService(CarveSettings::class.java)
    }
}
