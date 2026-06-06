package dev.lorenzods.anonimizadorpdf.domain.model

/** Lifecycle of an imported document. */
enum class DocumentStatus {
    /** Imported, text not yet extracted (or extraction failed). */
    RAW,

    /** Text extracted and stored. */
    PROCESSED,

    /** An anonymized version has been produced. */
    ANONYMIZED,
}
