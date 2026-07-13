package dev.lorenzods.anonimizadorpdf.data.db

import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument

fun PdfDocumentEntity.toDomain() = PdfDocument(
    id = id,
    originalFilename = originalFilename,
    importTimestamp = importTimestamp,
    internalPath = internalPath,
    extractedText = extractedText,
    pageCount = pageCount,
    status = status,
    customName = customName,
    folderId = folderId,
    isFavorite = isFavorite,
)

/** List projection: text is intentionally empty — fetch the document by id when text is needed. */
fun PdfDocumentMetaRow.toDomain() = PdfDocument(
    id = id,
    originalFilename = originalFilename,
    importTimestamp = importTimestamp,
    internalPath = internalPath,
    extractedText = "",
    pageCount = pageCount,
    status = status,
    customName = customName,
    folderId = folderId,
    isFavorite = isFavorite,
)

fun PdfDocument.toEntity() = PdfDocumentEntity(
    id = id,
    originalFilename = originalFilename,
    importTimestamp = importTimestamp,
    internalPath = internalPath,
    extractedText = extractedText,
    pageCount = pageCount,
    status = status,
    customName = customName,
    folderId = folderId,
    isFavorite = isFavorite,
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    createdTimestamp = createdTimestamp,
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    createdTimestamp = createdTimestamp,
)

fun AnonymizedVersionEntity.toDomain() = AnonymizedVersion(
    id = id,
    parentDocumentId = parentDocumentId,
    anonymizedText = anonymizedText,
    redactedTerms = redactedTerms,
    createdTimestamp = createdTimestamp,
)

/** Dashboard projection: anonymized text is intentionally empty (only counts/terms are needed). */
fun AnonymizedVersionMetaRow.toDomain() = AnonymizedVersion(
    id = id,
    parentDocumentId = parentDocumentId,
    anonymizedText = "",
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
