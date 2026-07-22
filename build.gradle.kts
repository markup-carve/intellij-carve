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

// Regression guard for the GraalJS/JDK breakage. The IntelliJ `test` task forks on the
// IDE's bundled JBR, so it only ever exercises that one JDK - which is how a Truffle that
// called the (since-removed) sun.misc.Unsafe.ensureClassInitialized shipped to users with
// a fully green build. This boots a polyglot context under an explicit, newer JDK instead.
// CI runs it across a JDK matrix; see .github/workflows/build.yml.
val javaToolchainService = extensions.getByType<JavaToolchainService>()

val graalSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Boot a GraalJS polyglot context under an explicit JDK toolchain."
    mainClass.set("org.markupcarve.carve.GraalSmokeKt")
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher.set(
        javaToolchainService.launcherFor {
            languageVersion.set(
                JavaLanguageVersion.of(
                    providers.gradleProperty("graalSmokeJdk").getOrElse("21").toInt(),
                ),
            )
        },
    )
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

// ---------------------------------------------------------------------------
// Grammar drift: intended, permanent deltas from the vscode-carve upstream copy
// ---------------------------------------------------------------------------
// This plugin carries its OWN copy of the Carve TextMate grammar. It is NOT a
// mirror of vscode-carve's and must never be overwritten with it. There used to
// be a `downloadGrammar` task that streamed upstream straight over the committed
// file; running it would have (measured against upstream on 2026-07-22):
//
//   * rewritten all 111 `keyword.control.*` scope names to `punctuation.definition.*`
//   * DELETED the 3 rules only this plugin has (cross-reference, hard-break,
//     thematic-break)
//   * clobbered 13 of the 28 shared rules, which have structurally diverged
//
// Two deltas are BY DESIGN and are encoded below:
//
//  1. Scope-name convention. The IDE's TextMate bridge colours `keyword.control.*`
//     out of the box, so this grammar uses that prefix where vscode-carve uses
//     `punctuation.definition.*`. The suffix after the prefix is kept identical,
//     which is what makes an automated comparison possible at all.
//  2. Plugin-only rules. Three constructs are highlighted here and not upstream.
//  3. Rule GROUPING. Upstream splits some constructs into their own repository
//     rules where this grammar folds them into a broader one. The constructs are
//     highlighted identically; only the rule name differs. A name-based diff would
//     otherwise report these as missing features forever, which would make the
//     actionable category permanently non-empty and train everyone to ignore it.
//
// Anything else is genuine drift and should be reconciled by HAND, preserving the
// three deltas above. `checkGrammarDrift` reports it; it never edits the grammar.
// Scope-name convention deltas, applied to UPSTREAM before comparing. Each entry is
// a whole-family rename where the suffix after the prefix is identical on both sides,
// so a mapping can only ever equate scopes that already mean the same thing: if the
// suffixes differ at all, the rule still reports as diverged. A one-off scope with no
// family pattern is deliberately NOT mapped - that is where a mapping could mask a
// real scope-name bug rather than silence a naming convention.
val upstreamScopeConventions =
    listOf(
        // The IDE's TextMate bridge colours `keyword.control.*` out of the box, so this
        // grammar uses it wherever upstream reaches for a punctuation/operator category.
        "punctuation.definition." to "keyword.control.",
        "punctuation.separator." to "keyword.control.separator.",
        "keyword.operator." to "keyword.control.",
        "constant.language.task-list." to "keyword.control.task-list.",
        // Named things: this grammar prefers the entity/variable families the IDE themes.
        "constant.other.reference." to "entity.name.reference.",
        "variable.other." to "variable.parameter.",
        "constant.character.typography." to "constant.character.entity.typography.",
    )
val intellijScopePrefix = "keyword.control."
val pluginOnlyGrammarRules = setOf("cross-reference", "hard-break", "thematic-break")

// Shared rules whose divergence from upstream is BY DESIGN. Same fixture rule as
// `upstreamRulesCoveredLocally`: every entry must be pinned by a fixture, so declaring
// a rule here can never hide a later regression in it.
val divergedByDesign =
    mapOf(
        "frontmatter" to
            "IntelliJ's TextMate engine treats the document-start anchor \\A like ^, so upstream's " +
                "\\A-anchored bare `---` open fence would fire mid-document and swallow the rest of the " +
                "file. This grammar requires a typed format token (`---toml`) instead. Cost: a BARE `---` " +
                "frontmatter fence is highlighted as a thematic break. Pinned by frontmatter-typed.crv.",
    )

// Upstream rule name -> the local rule that already covers it. Every entry here
// MUST be backed by a fixture in src/test/resources/fixtures/ that pins the
// construct's scopes, so this map cannot be used to silence a real gap: if the
// coverage ever regresses, the fixture test fails. `braced-emphasis.crv` pins
// both entries below (`{^x^}`, `{,x,}`, `{*x*}`, `{/x/}`, `{_x_}`, `{~x~}`).
val upstreamRulesCoveredLocally = mapOf(
    "forced-emphasis" to "emphasis",
    "sup-sub" to "emphasis",
)

tasks {
    // Read-only drift report against vscode-carve's grammar. This task CANNOT modify
    // the committed grammar: it writes the fetched copy to the build directory only.
    // Deliberately not wired into `check`/CI - it needs network, and upstream moving
    // is not a reason for this build to fail.
    register("checkGrammarDrift") {
        description = "Reports drift between the committed TextMate grammar and vscode-carve (read-only)"
        group = "verification"

        val localFile = file("$textmateDir/carve.tmLanguage.json")
        val scratchFile = layout.buildDirectory.file("grammar-drift/upstream.tmLanguage.json")
        inputs.file(localFile)
        outputs.file(scratchFile)

        doLast {
            val scratch = scratchFile.get().asFile
            scratch.parentFile.mkdirs()

            // Fail loudly on a fetch problem. A drift checker that silently reports
            // "no drift" because it could not reach the network is worse than none.
            try {
                uri(grammarUrl).toURL().openStream().use { input ->
                    scratch.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw GradleException(
                    "Could not fetch the upstream grammar from $grammarUrl: ${e.message}. " +
                        "Drift was NOT checked (this is a hard failure, not a pass).",
                )
            }
            if (scratch.length() == 0L) {
                throw GradleException("Upstream grammar fetched empty from $grammarUrl; drift was NOT checked.")
            }

            @Suppress("UNCHECKED_CAST")
            fun parseRepository(f: File): Map<String, Any?> {
                val root = groovy.json.JsonSlurper().parse(f, "UTF-8") as Map<String, Any?>
                return (root["repository"] as? Map<String, Any?>).orEmpty()
            }

            // `comment` keys are prose for humans and have ZERO effect on tokenization,
            // so a rule whose only difference is its comment is not drift. Stripping them
            // on BOTH sides is what stops the report flagging rules that behave identically.
            fun stripComments(node: Any?): Any? =
                when (node) {
                    is Map<*, *> ->
                        node.entries
                            .filterNot { (k, _) -> k == "comment" }
                            .associate { (k, v) -> k to stripComments(v) }
                    is List<*> -> node.map { stripComments(it) }
                    else -> node
                }

            // Rewrite upstream's scope-name conventions to this plugin's, so the comparison
            // is apples-to-apples instead of a hundred-odd false differences.
            fun normalize(node: Any?): Any? =
                when (node) {
                    is Map<*, *> ->
                        node.entries.associate { (k, v) ->
                            k to
                                if (k == "name" && v is String) {
                                    upstreamScopeConventions
                                        .firstOrNull { (from, _) -> v.startsWith(from) }
                                        ?.let { (from, to) -> to + v.removePrefix(from) }
                                        ?: v
                                } else {
                                    normalize(v)
                                }
                        }
                    is List<*> -> node.map { normalize(it) }
                    else -> node
                }

            val local = parseRepository(localFile).mapValues { (_, v) -> stripComments(v) }
            val upstream = parseRepository(scratch).mapValues { (_, v) -> stripComments(normalize(v)) }

            val upstreamOnly = (upstream.keys - local.keys).sorted()
            val pluginOnly = (local.keys - upstream.keys).sorted()
            val shared = (local.keys intersect upstream.keys).sorted()
            val diverged =
                shared.filter { rule ->
                    groovy.json.JsonOutput.toJson(local[rule]) != groovy.json.JsonOutput.toJson(upstream[rule])
                }

            println("Grammar drift vs vscode-carve (read-only - this task never edits the committed grammar)")
            println("  This plugin's grammar intentionally diverges from upstream; reconcile by hand.")
            println("  local rules: ${local.size}   upstream rules: ${upstream.size}   shared: ${shared.size}")

            println("\n  Plugin-only rules (expected - this plugin highlights these, upstream does not):")
            pluginOnly.forEach { r ->
                val expected = if (r in pluginOnlyGrammarRules) "by design" else "UNDECLARED - add to pluginOnlyGrammarRules or remove"
                println("    - $r ($expected)")
            }
            if (pluginOnly.isEmpty()) println("    (none)")

            val divergedByChoice = diverged.filter { it in divergedByDesign }
            val divergedUnexplained = diverged.filterNot { it in divergedByDesign }

            println("\n  Shared rules diverged BY DESIGN (expected - engine constraint, pinned by a fixture):")
            divergedByChoice.forEach { println("    = $it: ${divergedByDesign[it]}") }
            if (divergedByChoice.isEmpty()) println("    (none)")

            println("\n  Structurally diverged shared rules (needs human judgement, not auto-fixable):")
            divergedUnexplained.forEach { println("    ~ $it") }
            if (divergedUnexplained.isEmpty()) println("    (none)")

            val coveredElsewhere = upstreamOnly.filter { it in upstreamRulesCoveredLocally }
            val trulyMissing = upstreamOnly.filterNot { it in upstreamRulesCoveredLocally }

            println("\n  Upstream rules folded into a broader local rule (expected - same constructs, different grouping):")
            coveredElsewhere.forEach { println("    = $it (covered by '${upstreamRulesCoveredLocally[it]}', pinned by a fixture)") }
            if (coveredElsewhere.isEmpty()) println("    (none)")

            println("\n  Upstream-only rules (ACTIONABLE - features this plugin is missing):")
            trulyMissing.forEach { println("    + $it") }
            if (trulyMissing.isEmpty()) println("    (none)")

            val undeclared = pluginOnly.filterNot { it in pluginOnlyGrammarRules }
            if (trulyMissing.isNotEmpty() || undeclared.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        append("Actionable grammar drift. ")
                        if (trulyMissing.isNotEmpty()) {
                            append("Missing upstream rules: ${trulyMissing.joinToString(", ")}. ")
                        }
                        if (undeclared.isNotEmpty()) {
                            append("Undeclared plugin-only rules: ${undeclared.joinToString(", ")}. ")
                        }
                        append(
                            "Port them BY HAND into $localFile, keeping the $intellijScopePrefix " +
                                "scope convention. Never copy the upstream file over it.",
                        )
                    },
                )
            }
        }
    }

    // Kept so existing muscle memory and docs do not resurrect the old behaviour.
    // It now runs the SAME read-only check - it never overwrites the grammar.
    register("downloadGrammar") {
        description = "Deprecated alias for checkGrammarDrift (no longer overwrites the grammar)"
        group = "verification"
        // The safety message lives in checkGrammarDrift's own header, because a
        // dependency runs to completion first: if it fails on actionable drift, no
        // action defined here would ever execute.
        dependsOn("checkGrammarDrift")
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
