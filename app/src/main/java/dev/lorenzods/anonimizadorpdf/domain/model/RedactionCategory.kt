package dev.lorenzods.anonimizadorpdf.domain.model

/** Cosmetic grouping for redaction terms. See RedactionClassifier. */
enum class RedactionCategory {
    NAME,
    DOCUMENT,
    CONTACT,
    DATE,
    ADDRESS,
    ORGANIZATION,
    OTHER,
}
