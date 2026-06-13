package dev.lorenzods.anonimizadorpdf.domain.model

/** A user-created folder for organizing imported documents. */
data class Folder(
    val id: Long = 0,
    val name: String,
    val createdTimestamp: Long,
)
