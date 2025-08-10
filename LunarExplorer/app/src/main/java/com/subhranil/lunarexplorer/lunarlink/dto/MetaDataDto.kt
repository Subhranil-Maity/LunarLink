package com.subhranil.lunarexplorer.lunarlink.dto

import com.subhranil.lunarexplorer.models.EntryType
import com.subhranil.lunarexplorer.models.LunarMetaData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class MetaDataDto(
    val name: String,
    val size: Long,

    @SerialName("entry_type")
    val entryType: String,

    @SerialName("symlink_location")
    val symlinkLocation: String? = null,

    @SerialName("absolute_path")
    val absolutePath: String,

    @SerialName("last_modified")
    val lastModified: Long,

    @SerialName("mime_type")
    val mimeType: String? = null
)



@OptIn(ExperimentalTime::class)
fun MetaDataDto.map(): LunarMetaData{
    return LunarMetaData(
        name = this.name,
        size = this.size,
        entryType = mapEntryType(this.entryType),
        symlinkLocation = this.symlinkLocation,
        absolutePath = this.absolutePath,
        lastModified = Instant.fromEpochSeconds(this.lastModified)
    )
}

fun mapEntryType(entryType: String): EntryType{
    return when(entryType){
        "File" -> EntryType.FILE
        "Directory" -> EntryType.DIRECTORY
        "Symlink" -> EntryType.SYMLINK
        else -> EntryType.OTHER
    }
}