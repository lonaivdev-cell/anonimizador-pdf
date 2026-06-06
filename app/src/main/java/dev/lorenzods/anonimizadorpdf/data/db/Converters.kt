package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.TypeConverter
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun statusToString(status: DocumentStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): DocumentStatus =
        runCatching { DocumentStatus.valueOf(value) }.getOrDefault(DocumentStatus.RAW)

    @TypeConverter
    fun termsToString(terms: List<String>): String = Json.encodeToString(terms)

    @TypeConverter
    fun stringToTerms(value: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
