package dev.lorenzods.anonimizadorpdf.domain.model

/**
 * How sure the offline detector is that a candidate is sensitive. Drives default selection:
 * HIGH/MEDIUM are auto-accepted, LOW is surfaced but left unchecked for the user to confirm.
 */
enum class Confidence { HIGH, MEDIUM, LOW }
