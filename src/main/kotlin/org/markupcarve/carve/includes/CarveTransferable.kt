package org.markupcarve.carve.includes

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * Carve on the clipboard, under its own type and as plain text at once.
 *
 * The type is `text/x-carve` (markup-carve/carve#2050; a browser API that
 * demands the prefix spells it `web text/x-carve`, which no JVM clipboard does).
 * Carrying both flavors is what lets one copy serve two destinations: a Carve
 * editor takes the typed flavor and knows it is markup, a web box or a chat
 * window takes `text/plain` and gets the same characters. Neither destination
 * has to be asked which it wanted.
 *
 * `text/x-carve` is FIRST in the flavor list because a reader that takes the
 * first supported one should get the typed answer.
 */
class CarveTransferable(private val text: String) : Transferable {

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(CARVE_FLAVOR, DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == CARVE_FLAVOR || flavor == DataFlavor.stringFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return text
    }

    companion object {
        /** The settled clipboard type, carried as a String rather than a stream. */
        val CARVE_FLAVOR: DataFlavor = DataFlavor("text/x-carve; class=java.lang.String", "Carve")
    }
}
