package com.confused.anikuta.core.providerapi

/**
 * Future sub-interfaces for IMAGE (manga) and TEXT (novels) content.
 * Not implemented in Phase 3 — defined here so the architecture is ready.
 *
 * Architecture plan §8 (C1 fix): per-content-type sub-interfaces.
 */

/**
 * Extension provider for IMAGE content (manga).
 * Adds methods for fetching chapter lists and page lists.
 */
interface ImageExtensionProvider : ExtensionProvider {
    fun fetchContentList(source: Source, page: Int, query: String? = null): kotlinx.coroutines.flow.Flow<List<SourceContent>>
    fun fetchContentDetails(content: SourceContent): kotlinx.coroutines.flow.Flow<SourceContentDetails>
    fun fetchChapterList(content: SourceContent): kotlinx.coroutines.flow.Flow<List<SourceChapter>>
    fun fetchPageList(chapter: SourceChapter): kotlinx.coroutines.flow.Flow<List<SourcePage>>
}

/**
 * Extension provider for TEXT content (novels).
 * Adds methods for fetching chapter lists and text content.
 */
interface TextExtensionProvider : ExtensionProvider {
    fun fetchContentList(source: Source, page: Int, query: String? = null): kotlinx.coroutines.flow.Flow<List<SourceContent>>
    fun fetchContentDetails(content: SourceContent): kotlinx.coroutines.flow.Flow<SourceContentDetails>
    fun fetchChapterList(content: SourceContent): kotlinx.coroutines.flow.Flow<List<SourceChapter>>
    fun fetchTextContent(chapter: SourceChapter): kotlinx.coroutines.flow.Flow<String>
}

// Future data classes (not used in Phase 3 — defined for architecture completeness)

data class SourceChapter(
    val contentKey: String,
    val externalId: String,
    val number: Double,
    val name: String,
    val url: String? = null,
    val dateUpload: Long? = null,
)

data class SourcePage(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
)
