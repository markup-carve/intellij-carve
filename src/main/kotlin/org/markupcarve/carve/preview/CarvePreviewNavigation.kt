package org.markupcarve.carve.preview

/**
 * What the preview browser may navigate to.
 *
 * The page's CSP only governs the page it is set on. A navigation away (a link
 * click, a `<meta http-equiv="refresh">` in raw HTML) would land on a document
 * without it, so the main frame stays on the shell the plugin loaded. Web links
 * open in the system browser instead, and only on a user gesture.
 */
object CarvePreviewNavigation {

    enum class Decision { ALLOW, OPEN_EXTERNALLY, BLOCK }

    private val EXTERNAL_SCHEMES = setOf("http", "https", "mailto")

    fun decide(target: String, pageUrl: String?, isMainFrame: Boolean, userGesture: Boolean): Decision {
        if (!isMainFrame) return Decision.BLOCK
        // The empty browser's own start page, before the shell is loaded.
        if (target == "about:blank") return Decision.ALLOW
        if (pageUrl != null && withoutFragment(target) == withoutFragment(pageUrl)) return Decision.ALLOW
        val external = target.substringBefore(':', "").lowercase() in EXTERNAL_SCHEMES
        return if (external && userGesture) Decision.OPEN_EXTERNALLY else Decision.BLOCK
    }

    private fun withoutFragment(url: String): String = url.substringBefore('#')
}
