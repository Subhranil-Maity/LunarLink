package com.subhranil.lunarexplorer.lunarlink.dto

import com.subhranil.lunarexplorer.models.FolderInfo
import kotlinx.serialization.Serializable


@Serializable
data class FolderContent(
    val metadata: MetaDataDto,
    val entries: List<MetaDataDto>
)

fun FolderContent.map(): FolderInfo{
    return FolderInfo(
        metaData = this.metadata.map(),
        entries = this.entries.map { it.map() }
    )
}
