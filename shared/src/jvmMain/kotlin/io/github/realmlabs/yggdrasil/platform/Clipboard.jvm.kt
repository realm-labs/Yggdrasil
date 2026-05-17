package io.github.realmlabs.yggdrasil.platform

import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun copyPlainTextToClipboard(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (_: HeadlessException) {
        // No system clipboard is available in headless environments.
    } catch (_: IllegalStateException) {
        // Clipboard is temporarily unavailable because another app owns it.
    }
}
