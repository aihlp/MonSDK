package com.medmonitoring.core.util

import java.util.Locale

interface StringProvider {
    fun getString(id: Int): String
    fun getString(id: Int, vararg args: Any): String
    fun getStringByName(name: String, fallback: String): String
    fun currentLocale(): Locale = Locale.getDefault()
}
