package dev.lorenzods.anonimizadorpdf.domain.model

/** User-selectable appearance mode. Persisted in [AppPreferences]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
