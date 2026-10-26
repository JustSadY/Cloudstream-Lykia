package com.lykia

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.mapper

internal inline fun <reified T> jsonOku(metin: String?): T? {
    return try {
        mapper.readValue(metin ?: return null, object : TypeReference<T>() {})
    } catch (_: Exception) {
        null
    }
}

internal inline fun <reified T> jsonCevir(metin: String): T =
    mapper.readValue(metin, object : TypeReference<T>() {})
