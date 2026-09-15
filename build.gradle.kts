import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    // The IntelliJ Platform Gradle Plugin. Its predecessor, org.jetbrains.intellij 1.x,
    // printed on every single run that it "does not support building plugins against the
    // IntelliJ Platform 2024.2+ (242+)" - which this plugin has targeted since it moved to
    // 243. Everything the 1.x DSL spelled moved: see intellijPlatform {} below and the
    // intellijPlatform block inside dependencies {}.
    // 2.12.0 raised the minimum Gradle to 9.0.0, which is why the wrapper and the Kotlin
    // plugin moved with it.
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "org.markupcarve"
version = "0.1.8"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against IntelliJ IDEA Community so the plugin installs across the whole IDE
        // family. 2025.1 is the floor the plugin claims (see sinceBuild below): the 2023 and
        // 2024 trains are out of support and nobody runs them any more, so the build no
        // longer carries the cost of staying compatible with them.
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")

        // The TextMate Bundles plugin (bundled + enabled in all IntelliJ IDEs) drives
        // editor syntax highlighting; declare it so <depends> resolves and the verifier passes.
        bundledPlugin("org.jetbrains.plugins.textmate")

        // LSP4IJ (RedHat) is the LSP client framework. It maps the bundled carve-lsp's
        // capabilities (diagnostics, completion, folding, document symbols, hover,
        // code actions, rename, formatting, semantic tokens) onto native IDE features.
        // 0.20.1 declares since-build 242.0 and no until-build, so it resolves on 251+.
        plugin("com.redhat.devtools.lsp4ij", "0.20.1")

        // 2.x no longer puts the platform test framework on the test classpath implicitly.
        testFramework(TestFrameworkType.Platform)
    }

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

    // JUnit 4 for the corpus snapshot tests. Declared explicitly because the platform
    // no longer puts JUnit 4 on the plugin test classpath by default.
    testImplementation("junit:junit:4.13.2")
}

// Regression guard for the GraalJS/JDK breakage. The IntelliJ `test` task forks on the
// IDE's bundled JBR, so it only ever exercises that one JDK - which is how a Truffle that
// called the (since-removed) sun.misc.Unsafe.ensureClassInitialized shipped to users with
// a fully green build. This boots a polyglot context under an explicit, newer JDK instead.
// CI runs it across a JDK matrix; see .github/workflows/build.yml.
val javaToolchainService = extensions.getByType<JavaToolchainService>()

// The Kotlin standard library, for graalSmoke and nothing else.
//
// `kotlin.stdlib.default.dependency=false` in gradle.properties keeps the stdlib out of the
// plugin, because at runtime the IDE supplies it. Every other task here runs inside that
// IDE and so never notices. graalSmoke does not: it is a plain `java` process on an
// explicit toolchain, and `GraalSmoke.kt` calls `.use { }`, which is
// `kotlin.jdk7.AutoCloseableKt`. Under the 1.x toolchain the platform jars sat on
// `sourceSets["test"].runtimeClasspath` and carried a stdlib along by accident; 2.x composes
// the platform into the `test` task instead, so that accident is gone and the smoke test
// died with NoClassDefFoundError on a JDK matrix where every other check was green.
//
// Its own configuration rather than `testImplementation` deliberately: adding a stdlib to
// the test classpath would put a second, newer one in front of the 2025.1 platform's for all
// 618 tests, to fix a task that does not run in the IDE at all.
val graalSmokeRuntime = configurations.create("graalSmokeRuntime")

dependencies {
    graalSmokeRuntime(kotlin("stdlib"))
}

tasks.register<JavaExec>("graalSmoke") {
    group = "verification"
    description = "Boot a GraalJS polyglot context under an explicit JDK toolchain."
    mainClass.set("org.markupcarve.carve.GraalSmokeKt")
    classpath = sourceSets["test"].runtimeClasspath + graalSmokeRuntime
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

intellijPlatform {
    // Nothing in this plugin's settings UI is worth an index, and building one boots a
    // headless IDE on every build.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // 251 (2025.1). The 2023 and 2024 trains are out of JetBrains support and the
            // plugin no longer builds against one, so claiming them would be a claim nothing
            // verifies.
            sinceBuild = "251"

            // Deliberately open-ended, as it was under 1.x's updateSinceUntilBuild(false).
            // 2.x writes `until-build` as `MAJOR.*` by default, which would make every new
            // IDE train uninstallable until someone cut a release - and this plugin is a
            // highlighter and a preview, with no reason to break on a platform bump.
            // `provider { null }` is how 2.x spells "emit no until-build attribute"; the
            // Plugin Verifier run below is what backs the open claim up.
            untilBuild = provider { null }
        }

        // Marketplace "What's new" renders <change-notes> from the plugin.xml inside the
        // uploaded ZIP - it does not read GitHub releases. Generate it from CHANGELOG.md
        // so the notes can never drift from the release again (0.1.2 shipped with 0.1.1's
        // notes because the hand-maintained block was never updated).
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    pluginVerification {
        // 2.x fails on COMPATIBILITY_PROBLEMS, INTERNAL_API_USAGES and
        // OVERRIDE_ONLY_API_USAGES. 1.x's runPluginVerifier failed only on the first, so the
        // six internal-API reports below are not new code - they were always there and were
        // never gated.
        //
        // All six are ToolWindowFactory.getAnchor, .getIcon and .manage, and
        // CarvePreviewToolWindowFactory names none of them: it declares isApplicable,
        // createToolWindowContent and shouldBeAvailable and nothing else. They exist because
        // ToolWindowFactory is a KOTLIN interface - `manage` is a suspend function - so the
        // Kotlin compiler emits a delegating override of every default member into each
        // implementing class, and the verifier reads the emitted bytecode, not the source.
        // All three are ApiStatus.Internal, so every Kotlin plugin with a tool window reports
        // them, and there is no source change here that would remove them.
        //
        // ignoredProblemsFile is NOT the lever: it filters compatibility problems, and an
        // internal-API usage is a separate result field the filter never sees. So the level
        // is narrowed instead. OVERRIDE_ONLY_API_USAGES is kept - the category is empty here,
        // and it is the one of the two additions this plugin could actually violate.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )

        ides {
            // Check binary/API compatibility against every 2025 train, so a Marketplace flag
            // is caught here instead. The plugin builds against the first of them and claims
            // all of them plus everything after, so the newest train in the list is the one
            // carrying the open-ended until-build.
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")

            // 2025.3 is IntellijIdea, not IntellijIdeaCommunity: JetBrains stopped publishing
            // a separate Community distribution at 253 and ships one unified IntelliJ IDEA
            // instead. Asking for IC there resolves nothing - "Couldn't resolve
            // IntellijIdeaCommunity download URL for version: '2025.3'" - so the type has to
            // change with the train, not the version string.
            create(IntelliJPlatformType.IntellijIdea, "2025.3")
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        // The 2025.1 platform bundles Kotlin 2.1, and a plugin must not emit metadata newer
        // than the stdlib it runs on (`kotlin.stdlib.default.dependency=false` in
        // gradle.properties means this plugin uses the IDE's, not its own).
        apiVersion = KotlinVersion.KOTLIN_2_1
        languageVersion = KotlinVersion.KOTLIN_2_1
    }
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
//  2. Plugin-only rules. `cross-reference` is highlighted here and not upstream.
//  3. Rule GROUPING. Each side splits some constructs into their own repository
//     rules where the other folds them into a broader one. The constructs are
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
// ---------------------------------------------------------------------------
// Declarations. Reconciled against vscode-carve main on 2026-09-14 (#126).
// ---------------------------------------------------------------------------
// Each list below is a CLAIM about upstream, and upstream moves. Three of the
// four entries this set used to hold - `hard-break`, `include-directive`,
// `thematic-break` - had stopped being true, while six rules that really are
// plugin-only were never declared, and the task threw on both facts in one red
// exit. `checkGrammarDeclarations` now fails on a declaration that has stopped
// being true, so this file cannot rot that way again.

// EVERY DECLARATION BELOW NAMES A FIXTURE, and the fixture is what measures it.
//
// The checks in this file can only ask whether a rule of the same NAME is still there on
// the other side. That is blind to the error these lists are most likely to accumulate -
// upstream highlighting the same CONSTRUCT under a different rule name - because a
// factoring difference is exactly where the names diverge (#135). Four entries were false
// for that reason until #134, and `cross-reference` was false until #135.
//
// So `CarveGrammarDeclarationTest` drives BOTH grammars over the named fixture through the
// same engine and measures the claim itself: upstream highlights nothing a plugin-only
// rule owns, everything a grouped rule owns, and this grammar highlights everything an
// upstream rule declared covered owns. The fixture names travel to it through the
// systemProperty lines in the `test` block.

/** An upstream rule a declaration points at, and the fixture that measures the claim. */
data class GroupedUpstream(val upstreamRule: String, val fixture: String)

/** A local rule a declaration points at, and the fixture that measures the claim. */
data class CoveredLocally(val localRule: String, val fixture: String)

// Local rules upstream neither has nor highlights anywhere, and the fixture that shows it.
//
// EMPTY, and that is a measurement rather than an omission: every local-only rule this
// grammar has is a grouping delta. `cross-reference` sat here until #135 measured it -
// vscode-carve highlights `</#id>` inside its `autolink` rule, so the construct is one
// upstream has, spelled in a rule with another name.
val pluginOnlyGrammarRules = mapOf<String, String>()

// Upstream rule name -> this grammar's name for the SAME construct. A pure
// RENAME: the pair is equated before the name diff and then compared
// structurally like any other shared rule, so an alias can only ever silence a
// naming difference, never a behavioural one. That is why a rename belongs here
// and not in either "covered by a broader rule" map.
val upstreamRuleAliases =
    mapOf(
        // The delimited inline comment `{% ... %}` (spec PART 9 section 21a).
        "braced-comment" to "inline-comment",
    )

// Local rule name -> the upstream rule that carries the same construct. The
// mirror of `upstreamRulesCoveredLocally`, and it exists because the grouping
// delta runs both ways: an entry here says "upstream highlights this too, just
// grouped differently", which is a very different claim from plugin-only and
// must not be made by adding the rule to the set above. Same fixture rule.
// Local rule -> the upstream rule that carries the same construct, and the fixture that
// shows upstream highlighting it.
val localRulesGroupedUpstream =
    mapOf(
        // Upstream highlights the composite figure inside #divs and
        // #caption-behind-a-container-prefix rather than in a rule of its own.
        "figure-group" to GroupedUpstream("divs", "composite-figure.crv"),
        // `</#id>`. Upstream folds it into #autolink, which carries the same
        // `(</#)([^>\s]+)(>)` alternative and scopes it as a cross-reference. It was
        // declared plugin-only until #135 built a check that could see the difference;
        // the entry above it is empty because this was the last one.
        "cross-reference" to GroupedUpstream("autolink", "cross-reference.crv"),
        // The four marker-line rules. Upstream reaches these constructs from
        // `#container-body` with a \G-anchored alternative folded into the shared
        // `-behind-a-container-prefix` rule - `(?:\G(?<=[ \t])|^[ \t]+)`. The IDE's
        // engine runs that alternative fine (measured: upstream's own grammar driven
        // through CarveTextMateTokenizer scopes `- ```php` as a fenced block, which only
        // the \G branch can reach). What it has no home for is the REGION: this grammar's
        // list rules are `match` rules, so a \G rule would sit at the top level and fire
        // after any match that ends on whitespace. So the marker-line half is split into
        // a rule of its own that matches the whole line.
        // They were declared plugin-only until #129 measured the upstream rules:
        // upstream highlights every one of these constructs, which makes them a
        // GROUPING delta, not a construct upstream lacks.
        // Also asserted in CarveMarkerLineBlockOpenerTest.
        "code-fence-on-marker-line" to
            GroupedUpstream("code-block-behind-a-container-prefix", "code-fence-in-list-item.crv"),
        // The OTHER half of that upstream rule: a fence at the item's body column, which
        // upstream reaches from `#container-body` and this grammar reaches from the region
        // `#lists` now opens. Two local rules to upstream's one, which is why the entry in
        // upstreamRulesCoveredLocally names this one and says so.
        // Also asserted in CarveBodyColumnFenceTest.
        "code-fence-at-body-column" to
            GroupedUpstream("code-block-behind-a-container-prefix", "code-fence-in-list-item.crv"),
        "heading-on-marker-line" to
            GroupedUpstream("headings-behind-a-container-prefix", "marker-line-block-openers.crv"),
        "table-row-on-marker-line" to
            GroupedUpstream("table-row-behind-a-container-prefix", "marker-line-block-openers.crv"),
        "thematic-break-on-marker-line" to
            GroupedUpstream("thematic-break-behind-a-container-prefix", "marker-line-block-openers.crv"),
    )

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

// Upstream rule name -> the local rule that already covers it, and the fixture that
// measures it. This is the map a wrong entry would use to silence a real gap, so the
// fixture is not a comment here either: CarveGrammarDeclarationTest takes the spans the
// UPSTREAM rule owns in the fixture and asserts this grammar highlights every one of
// them. An entry pointing at an unrelated but well-covered local rule fails, because the
// spans come from upstream's rule rather than from the local one.
val upstreamRulesCoveredLocally = mapOf(
    // `braced-emphasis.crv` carries `{^x^}`, `{,x,}`, `{*x*}`, `{/x/}`, `{_x_}`, `{~x~}`.
    "forced-emphasis" to CoveredLocally("emphasis", "braced-emphasis.crv"),
    "sup-sub" to CoveredLocally("emphasis", "braced-emphasis.crv"),
    // A block quote opened on a list item's own marker line. Upstream needs a
    // separate \G-anchored rule because its `#block-quotes` begin is anchored on
    // ^ and the container has already consumed the marker; this grammar's
    // `#block-quotes` carries a second begin alternative that matches the marker
    // prefix and the `>` in one go, so every marker spelling reaches it.
    // Also asserted in CarveMarkerLineQuoteTest.
    "block-quote-on-marker-line" to CoveredLocally("block-quotes", "quote-on-list-marker-line.crv"),
    // A definition at a container's content column. Upstream's `#definitions` is
    // anchored flush-left, so the indented form needs a rule of its own; this
    // grammar's `#definitions` is anchored `^\s*` and takes both forms through
    // one rule, tokenizing them identically. Both upstream patterns are covered:
    // the abbreviation definition and the link reference definition.
    // CarveDefinitionInContainerTest asserts the indented form tokenizes as the
    // flush-left form does.
    // The attribute-block capture upstream carries on a link reference
    // definition is missing on BOTH local forms, so that delta belongs to the
    // shared `definitions` rule and is already reported in the structural diff.
    "definitions-in-container" to CoveredLocally("definitions", "definition-in-container.crv"),
    // Upstream's rule has TWO patterns and this grammar splits them: a fence on the item's
    // own marker line is `code-fence-on-marker-line`, a fence at the item's body column is
    // `code-fence-at-body-column`. The entry names the second because it was the missing
    // half - the first was ported long ago and the second landed with the list region that
    // makes it reachable. `code-fence-in-list-item.crv` carries both spellings AND the
    // document-level indented fence that must NOT become a block.
    "code-block-behind-a-container-prefix" to
        CoveredLocally("code-fence-at-body-column", "code-fence-in-list-item.crv"),
)

// `rule=fixture;rule=fixture` - the one shape a system property can carry, and the shape
// CarveGrammarDeclarationTest parses.
fun encodeDeclarations(declarations: Map<String, String>): String =
    declarations.entries.joinToString(";") { (rule, fixture) -> "$rule=$fixture" }

val localGrammarFile = file("$textmateDir/carve.tmLanguage.json")
val upstreamGrammarScratch = layout.buildDirectory.file("grammar-drift/upstream.tmLanguage.json")

@Suppress("UNCHECKED_CAST")
fun parseGrammarRepository(f: File): Map<String, Any?> {
    val root = groovy.json.JsonSlurper().parse(f, "UTF-8") as Map<String, Any?>
    return (root["repository"] as? Map<String, Any?>).orEmpty()
}

// `comment` keys are prose for humans and have ZERO effect on tokenization,
// so a rule whose only difference is its comment is not drift. Stripping them
// on BOTH sides is what stops the report flagging rules that behave identically.
fun stripGrammarComments(node: Any?): Any? =
    when (node) {
        is Map<*, *> ->
            node.entries
                .filterNot { (k, _) -> k == "comment" }
                .associate { (k, v) -> k to stripGrammarComments(v) }
        is List<*> -> node.map { stripGrammarComments(it) }
        else -> node
    }

// Rewrite upstream's scope-name conventions to this plugin's, so the comparison
// is apples-to-apples instead of a hundred-odd false differences.
fun normalizeUpstreamScopes(node: Any?): Any? =
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
                        normalizeUpstreamScopes(v)
                    }
            }
        is List<*> -> node.map { normalizeUpstreamScopes(it) }
        else -> node
    }

// A repository rule carrying no scope name anywhere highlights NOTHING on its
// own: it is a pattern list other rules include. Upstream factors its container
// body into one (`container-body`); this grammar's containers are `match` rules,
// so the document's own pattern list applies and no such rule is needed. A rule
// like that present on one side only is a FACTORING difference, never a missing
// feature - and it is read off the rule's shape rather than declared, so it is
// one less claim that can go stale. Where both sides have one it stays in the
// structural diff: an include list that diverges is real drift.
fun grammarRuleIsStructuralOnly(node: Any?): Boolean =
    when (node) {
        is Map<*, *> ->
            node.entries.none { (k, v) -> (k == "name" || k == "contentName") && v is String } &&
                node.values.all { grammarRuleIsStructuralOnly(it) }
        is List<*> -> node.all { grammarRuleIsStructuralOnly(it) }
        else -> true
    }

data class GrammarComparison(
    val local: Map<String, Any?>,
    val upstream: Map<String, Any?>,
    val upstreamRawNames: Set<String>,
    val shared: List<String>,
    val pluginOnly: List<String>,
    // Upstream rules with no local counterpart, minus the pure factoring rules -
    // see grammarRuleIsStructuralOnly.
    val upstreamOnly: List<String>,
    val structuralOnly: List<String>,
    val diverged: List<String>,
)

fun compareGrammars(localFile: File, upstreamFile: File): GrammarComparison {
    val localRaw = parseGrammarRepository(localFile)
    val upstreamRaw = parseGrammarRepository(upstreamFile)
    val local = localRaw.mapValues { (_, v) -> stripGrammarComments(v) }
    val upstream =
        upstreamRaw
            .mapKeys { (k, _) -> upstreamRuleAliases[k] ?: k }
            .mapValues { (_, v) -> stripGrammarComments(normalizeUpstreamScopes(v)) }

    val shared = (local.keys intersect upstream.keys).sorted()
    val unmatched = (upstream.keys - local.keys).sorted()
    val structuralOnly = unmatched.filter { grammarRuleIsStructuralOnly(upstream[it]) }
    return GrammarComparison(
        local = local,
        upstream = upstream,
        upstreamRawNames = upstreamRaw.keys,
        shared = shared,
        pluginOnly = (local.keys - upstream.keys).sorted(),
        upstreamOnly = unmatched - structuralOnly.toSet(),
        structuralOnly = structuralOnly,
        diverged =
            shared.filter { rule ->
                groovy.json.JsonOutput.toJson(local[rule]) != groovy.json.JsonOutput.toJson(upstream[rule])
            },
    )
}

tasks {
    // The network half, shared by the two checks below so a run of both fetches
    // once. Never up to date: the whole point is what upstream looks like NOW.
    register("fetchUpstreamGrammar") {
        description = "Fetches vscode-carve's TextMate grammar into the build directory (read-only)"
        group = "verification"
        val scratchFile = upstreamGrammarScratch
        outputs.file(scratchFile)
        outputs.upToDateWhen { false }

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
        }
    }

    // ACTIONABLE drift: upstream constructs this plugin does not highlight. This
    // task CANNOT modify the committed grammar - it only reads the copy
    // fetchUpstreamGrammar wrote to the build directory.
    //
    // It used to throw on `trulyMissing || undeclared`, which put the one category
    // worth acting on in the same red exit as bookkeeping that had gone stale, and
    // the comment above predicted exactly what that does to a category: it trains
    // everyone to ignore it. The bookkeeping half now lives in
    // checkGrammarDeclarations and fails separately.
    //
    // Out of `check` and off `pull_request` deliberately - it needs network, and an
    // upstream rule landing this afternoon is not a reason for an unrelated pull
    // request to go red. .github/workflows/grammar-drift.yml runs it on a schedule,
    // where it is REPORTED; the declarations check beside it is GATED.
    register("checkGrammarDrift") {
        description = "Reports upstream grammar constructs this plugin is missing (read-only)"
        group = "verification"
        dependsOn("fetchUpstreamGrammar")

        val local = localGrammarFile
        val scratchFile = upstreamGrammarScratch

        doLast {
            val cmp = compareGrammars(local, scratchFile.get().asFile)

            println("Grammar drift vs vscode-carve (read-only - this task never edits the committed grammar)")
            println("  This plugin's grammar intentionally diverges from upstream; reconcile by hand.")
            println("  local rules: ${cmp.local.size}   upstream rules: ${cmp.upstream.size}   shared: ${cmp.shared.size}")

            val divergedByChoice = cmp.diverged.filter { it in divergedByDesign }
            val divergedUnexplained = cmp.diverged.filterNot { it in divergedByDesign }

            println("\n  Shared rules diverged BY DESIGN (expected - engine constraint, pinned by a fixture):")
            divergedByChoice.forEach { println("    = $it: ${divergedByDesign[it]}") }
            if (divergedByChoice.isEmpty()) println("    (none)")

            println("\n  Structurally diverged shared rules (needs human judgement, not auto-fixable):")
            divergedUnexplained.forEach { println("    ~ $it") }
            if (divergedUnexplained.isEmpty()) println("    (none)")

            println("\n  Upstream rules folded into a broader local rule (expected - same constructs, different grouping):")
            val coveredElsewhere = cmp.upstreamOnly.filter { it in upstreamRulesCoveredLocally }
            coveredElsewhere.forEach {
                val d = upstreamRulesCoveredLocally.getValue(it)
                println("    = $it (covered by '${d.localRule}', measured on ${d.fixture})")
            }
            if (coveredElsewhere.isEmpty()) println("    (none)")

            println("\n  Upstream rules that are pattern lists only (expected - a factoring difference, they highlight nothing):")
            cmp.structuralOnly.forEach { println("    = $it") }
            if (cmp.structuralOnly.isEmpty()) println("    (none)")

            val trulyMissing = cmp.upstreamOnly.filterNot { it in upstreamRulesCoveredLocally }
            println("\n  Upstream-only rules (ACTIONABLE - features this plugin is missing):")
            trulyMissing.forEach { println("    + $it") }
            if (trulyMissing.isEmpty()) println("    (none)")

            println("\n  ACTIONABLE: ${trulyMissing.size}")

            if (trulyMissing.isNotEmpty()) {
                throw GradleException(
                    "Actionable grammar drift. Missing upstream rules: ${trulyMissing.joinToString(", ")}. " +
                        "Port them BY HAND into $local, keeping the $intellijScopePrefix scope convention. " +
                        "Never copy the upstream file over it. If one of them is already covered by a broader " +
                        "local rule, declare it in upstreamRulesCoveredLocally and pin it with a fixture.",
                )
            }
        }
    }

    // BOOKKEEPING: every declaration above is a claim about upstream, and upstream
    // moves. This is the guard that keeps them honest - it fails when a claim has
    // stopped being true, which is the failure that would have caught `thematic-break`
    // the day vscode-carve grew it instead of six rules later.
    //
    // Deliberately a SEPARATE task from checkGrammarDrift, not a second throw inside
    // it: the two categories want different responses (edit a list here, port a
    // grammar rule there) and, in the scheduled workflow, different severities.
    register("checkGrammarDeclarations") {
        description = "Fails when a plugin-only/covered/diverged grammar declaration has gone stale"
        group = "verification"
        dependsOn("fetchUpstreamGrammar")

        val local = localGrammarFile
        val scratchFile = upstreamGrammarScratch

        doLast {
            val cmp = compareGrammars(local, scratchFile.get().asFile)
            val problems = mutableListOf<String>()

            val undeclared =
                cmp.pluginOnly.filterNot { it in pluginOnlyGrammarRules.keys || it in localRulesGroupedUpstream }
            println("Grammar declarations vs vscode-carve")
            println("\n  Local-only rules and how they are declared:")
            cmp.pluginOnly.forEach { r ->
                val how =
                    when {
                        r in pluginOnlyGrammarRules.keys -> "plugin-only, by design"
                        r in localRulesGroupedUpstream ->
                            "grouped upstream into '${localRulesGroupedUpstream.getValue(r).upstreamRule}'"
                        else -> "UNDECLARED"
                    }
                println("    - $r ($how)")
            }
            if (cmp.pluginOnly.isEmpty()) println("    (none)")
            undeclared.forEach {
                problems += "$it is local-only and UNDECLARED - add it to pluginOnlyGrammarRules " +
                    "(upstream does not highlight the construct at all) or to localRulesGroupedUpstream " +
                    "(upstream highlights it inside a broader rule), and pin it with a fixture"
            }

            (pluginOnlyGrammarRules.keys - cmp.pluginOnly.toSet()).sorted().forEach {
                problems += "$it is declared in pluginOnlyGrammarRules but is NOT local-only any more - " +
                    "vscode-carve has grown the rule, so drop the entry and read the shared diff for it"
            }
            (localRulesGroupedUpstream.keys - cmp.pluginOnly.toSet()).sorted().forEach {
                problems += "$it is declared in localRulesGroupedUpstream but is NOT local-only any more - drop the entry"
            }
            localRulesGroupedUpstream.forEach { (rule, declaration) ->
                if (declaration.upstreamRule !in cmp.upstream.keys) {
                    problems += "$rule is declared as grouped into upstream's '${declaration.upstreamRule}', " +
                        "which upstream no longer has"
                }
            }
            upstreamRuleAliases.forEach { (upstreamName, localName) ->
                if (upstreamName !in cmp.upstreamRawNames) {
                    problems += "the alias '$upstreamName' -> '$localName' names an upstream rule that no longer exists"
                }
                if (localName !in cmp.local.keys) {
                    problems += "the alias '$upstreamName' -> '$localName' names a local rule that no longer exists"
                }
            }
            (upstreamRulesCoveredLocally.keys - cmp.upstreamOnly.toSet()).sorted().forEach {
                problems += "$it is declared in upstreamRulesCoveredLocally but is not an upstream-only rule any more - drop the entry"
            }
            upstreamRulesCoveredLocally.forEach { (rule, declaration) ->
                if (declaration.localRule !in cmp.local.keys) {
                    problems += "$rule is declared as covered by local '${declaration.localRule}', " +
                        "which this grammar no longer has"
                }
            }

            // THE FIXTURE IS THE MEASUREMENT, so a declaration naming one that does not
            // exist is a claim nothing can check. CarveGrammarDeclarationTest measures the
            // claim itself; this only keeps the two halves from drifting apart silently.
            val declaredFixtures =
                pluginOnlyGrammarRules.map { (rule, fixture) -> rule to fixture } +
                    localRulesGroupedUpstream.map { (rule, d) -> rule to d.fixture } +
                    upstreamRulesCoveredLocally.map { (rule, d) -> rule to d.fixture }
            declaredFixtures.forEach { (rule, fixture) ->
                if (!file("src/test/resources/fixtures/$fixture").isFile) {
                    problems += "$rule names the fixture '$fixture', which does not exist in " +
                        "src/test/resources/fixtures - the declaration cannot be measured"
                }
            }
            (divergedByDesign.keys - cmp.diverged.toSet()).sorted().forEach {
                problems += "$it is declared in divergedByDesign but no longer diverges from upstream - drop the entry"
            }

            println("\n  Stale or missing declarations:")
            problems.forEach { println("    ! $it") }
            if (problems.isEmpty()) println("    (none)")
            println("\n  STALE DECLARATIONS: ${problems.size}")

            if (problems.isNotEmpty()) {
                throw GradleException(
                    "Grammar declarations are out of date with vscode-carve (${problems.size}): " +
                        problems.joinToString("; ") + ". This is BOOKKEEPING in build.gradle.kts, " +
                        "not a grammar change - fix the declarations, do not touch the .tmLanguage.json.",
                )
            }
        }
    }

    // The declarations measured against the CONSTRUCT rather than the rule name. The
    // assertions live in CarveGrammarDeclarationTest, because driving a grammar needs the
    // IDE's TextMate engine and that is only on the `test` task's classpath - a JavaExec on
    // sourceSets["test"].runtimeClasspath does NOT carry the platform under the 2.x plugin
    // (see the graalSmoke note above).
    //
    // So this task is the fetch plus the test task, and the test skips its upstream arms
    // when no grammar has been fetched. Network, therefore out of `check` and off
    // `pull_request` with the two beside it.
    register("checkGrammarConstructs") {
        description = "Measures each grammar declaration against vscode-carve over its fixture"
        group = "verification"
        dependsOn("fetchUpstreamGrammar", "test")
    }

    named("test") { mustRunAfter("fetchUpstreamGrammar") }

    // Kept so existing muscle memory and docs do not resurrect the old behaviour.
    // It now runs the SAME read-only checks - it never overwrites the grammar.
    register("downloadGrammar") {
        description = "Deprecated alias for checkGrammarDrift (no longer overwrites the grammar)"
        group = "verification"
        // The safety message lives in checkGrammarDrift's own header, because a
        // dependency runs to completion first: if it fails on actionable drift, no
        // action defined here would ever execute.
        dependsOn("checkGrammarDrift", "checkGrammarDeclarations", "checkGrammarConstructs")
    }

    test {
        // The shared-corpus snapshot tests read the pinned `spec` submodule from
        // the project root and write goldens under src/test/resources. Pin the
        // working directory so the lookup is stable regardless of how the test
        // JVM is forked, and forward the golden-regeneration switch into it.
        workingDir = rootDir

        // THE CORPUS IS AN INPUT, AND GRADLE HAD NO WAY TO KNOW.
        //
        // These tests reach the corpus by FILE PATH from `workingDir`, not
        // through the classpath, so none of it was a declared input to this
        // task. Bumping the `spec` submodule therefore changed nothing Gradle
        // tracks, and `./gradlew test` reported BUILD SUCCESSFUL with
        // `> Task :test UP-TO-DATE` - having run no test at all.
        //
        // Measured on this repository: with the pin moved from bbd7d8e to
        // carve 287b4b8, a 108-document corpus change, `./gradlew test`
        // succeeded in 9 seconds. Forced with --rerun-tasks the same tree
        // fails twice, on 23 documents the vendored bundle renders
        // differently and on 17 unclassified categories.
        //
        // CI never saw this because every run is a fresh checkout with no
        // build cache, so the task always executes. A human bumping the pin
        // locally sees the opposite, and spec-drift.yml prints exactly that
        // command as its remediation - `git -C spec checkout origin/main &&
        // git add spec && ./gradlew test` - so the instruction for fixing a
        // stale pin was an instruction to run nothing.
        //
        // fileTree rather than inputs.dir: the submodule may not be checked
        // out, and a missing declared input directory is a hard failure with
        // a worse message than CarveCorpus.MISSING_MESSAGE, which is written
        // for exactly that case.
        inputs.files(fileTree("spec/tests/corpus"))
            .withPropertyName("sharedCorpus")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        // The corpus's own source. CarveBundleCorpusTest counts the `:::
        // compare` blocks on these pages to decide how many pairs there
        // should be, so they decide the outcome as much as the corpus does.
        inputs.files(fileTree("spec/resources/examples"))
            .withPropertyName("corpusSourcePages")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        System.getProperty("carve.updateGoldens")?.let {
            systemProperty("carve.updateGoldens", it)
        }

        // The grammar declarations, forwarded to CarveGrammarDeclarationTest. They live in
        // this file because the two checks above read them too, and a second copy in the
        // test sources is a second thing to keep in step.
        systemProperty("carve.declarations.pluginOnly", encodeDeclarations(pluginOnlyGrammarRules))
        systemProperty(
            "carve.declarations.groupedUpstream",
            encodeDeclarations(localRulesGroupedUpstream.mapValues { (_, d) -> d.fixture }),
        )
        systemProperty(
            "carve.declarations.coveredLocally",
            encodeDeclarations(upstreamRulesCoveredLocally.mapValues { (_, d) -> d.fixture }),
        )

        // The upstream copy, when one has been fetched. OPTIONAL: `test` runs on every pull
        // request and fetching needs network, so the arms that compare against upstream
        // SKIP without it and `checkGrammarConstructs` is what makes them run. Declared as
        // an input so the task is not UP-TO-DATE the first time the file appears.
        val upstreamScratch = upstreamGrammarScratch.get().asFile
        systemProperty("carve.upstreamGrammar", upstreamScratch.absolutePath)
        inputs.files(files(upstreamScratch))
            .withPropertyName("upstreamGrammar")
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .optional(true)
    }
}
