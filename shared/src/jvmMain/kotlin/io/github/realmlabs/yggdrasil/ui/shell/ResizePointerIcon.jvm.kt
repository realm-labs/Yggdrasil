package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun resizePointerIcon(verticalDivider: Boolean): PointerIcon =
    PointerIcon(Cursor(if (verticalDivider) Cursor.E_RESIZE_CURSOR else Cursor.S_RESIZE_CURSOR))
