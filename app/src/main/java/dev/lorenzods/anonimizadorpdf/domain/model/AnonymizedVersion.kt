package dev.lorenzods.anonimizadorpdf.domain.model

data class AnonymizedVersion(
    val id: Long = 0,
    val parentDocumentId: Long,
    val anonymizedText: String,
    val redactedTerms: List<String>,
    val createdTimestamp: Long,
)
