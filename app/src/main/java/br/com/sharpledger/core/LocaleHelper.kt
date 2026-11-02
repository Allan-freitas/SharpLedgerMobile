package br.com.sharpledger.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun apply(context: Context, localeTag: String): Context {
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }
}
