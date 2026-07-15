package org.markupcarve.carve.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.markupcarve.carve.lang.CarvePsiFile

/**
 * Colours Carve's structural markers, layered on top of the TextMate base highlighting.
 *
 * Runs once per file (on the [CarvePsiFile] root), scans the text with [CarveMarkerScanner],
 * and emits a silent, informational annotation per marker carrying that marker's
 * [CarveColors] attribute key. Annotations paint above the syntax-highlighter layer, so these
 * override TextMate's single keyword colour for the markers while leaving everything else -
 * code, strings, comments, emphasis, links - exactly as TextMate rendered it.
 */
class CarveAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is CarvePsiFile) return
        for (span in CarveMarkerScanner.scan(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(span.range)
                .textAttributes(span.key)
                .create()
        }
    }
}
