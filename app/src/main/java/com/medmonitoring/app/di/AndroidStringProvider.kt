package com.medmonitoring.app.di

import android.content.Context
import com.medmonitoring.core.util.StringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    override fun getString(id: Int): String {
        return context.getString(id)
    }

    override fun getString(id: Int, vararg args: Any): String {
        return context.getString(id, *args)
    }

    override fun getStringByName(name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) fallback else context.getString(id)
    }

    override fun currentLocale(): Locale {
        return context.resources.configuration.locales[0] ?: Locale.getDefault()
    }
}
