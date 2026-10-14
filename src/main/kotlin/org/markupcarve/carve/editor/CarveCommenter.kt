package org.markupcarve.carve.editor

import com.intellij.lang.Commenter

/**
 * Line comments are `%%` lines; block comments use the braced inline form `{% ... %}`,
 * because a `%%%` fence must sit on lines of its own and cannot wrap a selection.
 *
 * The line prefix carries a space so commenting a line that starts with `%` cannot
 * produce a `%%%` fence opener.
 */
class CarveCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "%% "
    override fun getBlockCommentPrefix(): String = "{%"
    override fun getBlockCommentSuffix(): String = "%}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
