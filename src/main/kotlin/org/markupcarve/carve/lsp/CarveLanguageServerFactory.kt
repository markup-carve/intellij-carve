package org.markupcarve.carve.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import org.eclipse.lsp4j.services.LanguageServer

/**
 * Registers the bundled carve-lsp server with lsp4ij. lsp4ij then maps the
 * server's capabilities onto native IDE features:
 *
 *  - publishDiagnostics  -> editor inspections / problems
 *  - completion          -> code completion
 *  - foldingRange        -> code folding (D2)
 *  - documentSymbol      -> Structure view + breadcrumbs (D2)
 *  - codeAction          -> intentions / quick fixes (D3)
 *  - hover               -> documentation on hover
 *  - rename / prepare    -> rename refactoring
 *  - formatting          -> Reformat Code
 *  - semanticTokens      -> semantic highlighting
 *  - codeLens            -> inlay code lenses
 *
 * Wired in plugin.xml under com.redhat.devtools.lsp4ij.server and
 * com.redhat.devtools.lsp4ij.languageMapping.
 */
class CarveLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        CarveLspServer(project)

    override fun createLanguageClient(project: Project): LanguageClientImpl =
        LanguageClientImpl(project)

    override fun getServerInterface(): Class<out LanguageServer> =
        LanguageServer::class.java
}
