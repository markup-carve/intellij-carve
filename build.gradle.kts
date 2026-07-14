import org.jetbrains.changelog.Changelog

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = "org.markupcarve"
version = "0.1.3"

repositories {
    mavenCentral()
}

dependencies {
    // Server-side Carve rendering for export + preview (runs the bundled carve.iife.js).
    // GraalJS via the modern polyglot coordinates. The old org.graalvm.js:js:23.0.2 line
    // shipped a Truffle that calls sun.misc.Unsafe.ensureClassInitialized, which recent
    // JDKs removed - on a current JBR that threw NoSuchMethodError while building the
    // polyglot Context, so the live preview failed to render at all. js-scriptengine is
    // dropped: we use the Context API directly, never javax.script.
    // Depend on the real jars, NOT the org.graalvm.polyglot:js-community aggregator:
    // that module is pom-packaging, so Gradle puts the .pom itself on the runtime
    // classpath. The IntelliJ test JVM's coroutines javaagent opens every classpath
    // entry as a jar, chokes on the .pom, and aborts the JVM (SIGABRT, "processing of
    // -javaagent failed"). js-language + truffle-runtime pull the same closure
    // (truffle-api, regex, icu4j, truffle-compiler) without any pom artifact.
    implementation("org.graalvm.polyglot:polyglot:24.2.1")
    implementation("org.graalvm.js:js-language:24.2.1")
    implementation("org.graalvm.truffle:truffle-runtime:24.2.1")

    // JUnit 4 for the corpus snapshot tests. Declared explicitly because the 2024.3+
    // platform no longer puts JUnit 4 on the plugin test classpath by default.
    testImplementation("junit:junit:4.13.2")
}

intellij {
    // Build against IntelliJ IDEA Community so the plugin installs across the whole IDE family.
    // 2024.3 is the lowest SDK that exposes the descriptor-first
    // Row.textFieldWithBrowseButton(descriptor, project) overload; the old
    // title-and-descriptor combined overload was deprecated (ERROR level) in that
    // cycle, so the plugin now builds against (and requires) 243+ - see sinceBuild.
    version.set("2024.3")
    type.set("IC")
    // The TextMate Bundles plugin (bundled + enabled in all IntelliJ IDEs) drives
    // editor syntax highlighting; declare it so <depends> resolves and verifyPlugin passes.
    //
    // LSP4IJ (RedHat) is the LSP client framework. It maps the bundled carve-lsp's
    // capabilities (diagnostics, completion, folding, document symbols, hover,
    // code actions, rename, formatting, semantic tokens) onto native IDE features.
    // 0.20.1 supports since-build 242.0+, so it is compatible with 2024.3 (243).
    plugins.set(listOf("org.jetbrains.plugins.textmate", "com.redhat.devtools.lsp4ij:0.20.1"))
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

    test {
        // The shared-corpus snapshot tests read the pinned `spec` submodule from
        // the project root and write goldens under src/test/resources. Pin the
        // working directory so the lookup is stable regardless of how the test
        // JVM is forked, and forward the golden-regeneration switch into it.
        workingDir = rootDir
        System.getProperty("carve.updateGoldens")?.let {
            systemProperty("carve.updateGoldens", it)
        }
    }

    patchPluginXml {
        // 243 (2024.3): the descriptor-first Row.textFieldWithBrowseButton overload
        // we now call is a new platform method that does not exist on the 2024.1/2024.2
        // Row interface, so the plugin can no longer claim compatibility below 243.
        sinceBuild.set("243")

        // Marketplace "What's new" renders <change-notes> from the plugin.xml inside the
        // uploaded ZIP - it does not read GitHub releases. Generate it from CHANGELOG.md
        // so the notes can never drift from the release again (0.1.2 shipped with 0.1.1's
        // notes because the hand-maintained block was never updated).
        changeNotes.set(
            provider {
                with(changelog) {
                    renderItem(
                        (getOrNull(project.version.toString()) ?: getUnreleased())
                            .withHeader(false)
                            .withEmptySections(false),
                        Changelog.OutputType.HTML,
                    )
                }
            },
        )
    }

    runPluginVerifier {
        // Check binary/API compatibility against the IDE versions the plugin claims
        // to support (since-build 243 = 2024.3) plus newer trains, so a Marketplace
        // flag like the 262 build is caught here. Run via `./gradlew runPluginVerifier`.
        // TODO: add "IC-2026.2" once that release train resolves in the verifier's
        // product-releases list (it is not yet available as a stable string).
        ideVersions.set(
            listOf("IC-2024.3", "IC-2025.1", "IC-2025.2"),
        )
    }

    buildSearchableOptions {
        enabled = false
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
