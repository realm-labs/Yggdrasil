package io.github.realmlabs.yggdrasil.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val LocalLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = LocalLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (default == null) {
            default = Locale.getDefault()
        }
        val newLocale = value?.let(Locale::forLanguageTag) ?: default!!
        Locale.setDefault(newLocale)
        return LocalLocale.provides(newLocale.toLanguageTag())
    }
}
