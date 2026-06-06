package dev.lorenzods.anonimizadorpdf.data.db

import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument

fun PdfDocumentEntity.toDomain() = PdfDocument(
    id = id,
    originalFilename = originalFilename,
    importTimestamp = importTimestamp,
    internalPath = internalPath,
    extractedText = extractedText,
    pageCount = pageCount,
    status = status,
)

fun PdfDocument.toEntity() = PdfDocumentEntity(
    id = id,
    originalFilename = originalFilename,
    importTimestamp = importTimestamp,
    internalPath = internalPath,
    extractedText = extractedText,
    pageCount = pageCount,
    status = status,
)

fun AnonymizedVersionEntity.toDomain() = AnonymizedVersion(
    id = id,
    parentDocumentId = parentDocumentId,
    anonymizedText = anonymizedText,
    redactedTerms = redactedTerms,
    createdTimestamp = createdTimestamp,
)

fun AnonymizedVersion.toEntity() = AnonymizedVersionEntity(
    id = id,
    parentDocumentId = parentDocumentId,
    anonymizedText = anonymizedText,
    redactedTerms = redactedTerms,
    createdTimestamp = createdTimestamp,
)
