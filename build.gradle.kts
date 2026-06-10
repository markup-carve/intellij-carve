plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.markupcarve"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Server-side Carve rendering for export + preview (runs the bundled carve.iife.js).
    implementation("org.graalvm.js:js:23.0.2")
    implementation("org.graalvm.js:js-scriptengine:23.0.2")
}

intellij {
    // Build against IntelliJ IDEA Community so the plugin installs across the whole IDE family.
    version.set("2024.1")
    type.set("IC")
    // The TextMate Bundles plugin (bundled + enabled in all IntelliJ IDEs) drives
    // editor syntax highlighting; declare it so <depends> resolves and verifyPlugin passes.
    plugins.set(listOf("org.jetbrains.plugins.textmate"))
    updateSinceUntilBuild.set(false)
}

kotlin {
    jvmToolchain(17)
}

val textmateDir = "src/main/resources/textmate"
val grammarUrl =
    "https://raw.githubusercontent.com/markup-carve/vscode-carve/main/syntaxes/carve.tmLanguage.json"

tasks {
    // Manual refresh task: pulls the latest grammar from vscode-carve and overwrites
    // the committed copy. Not wired into the build so a network hiccup never breaks it;
    // run `./gradlew downloadGrammar` and commit the result to stay in lockstep.
    register("downloadGrammar") {
        description = "Refreshes the committed TextMate grammar from vscode-carve"
        group = "build"

        val outputFile = file("$textmateDir/carve.tmLanguage.json")
        outputs.file(outputFile)

        doLast {
            uri(grammarUrl).toURL().openStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("Downloaded grammar to $outputFile")
        }
    }

    patchPluginXml {
        sinceBuild.set("241")
    }

    buildSearchableOptions {
        enabled = false
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
