package com.subhranil.lunarexplorer.lunarlink.dto

import com.subhranil.lunarexplorer.models.Mounts
import kotlinx.serialization.Serializable


@Serializable
data class MountsDto(
    val drives: List<DriveDto>,
    val mounts: List<MetaDataDto>
)

fun MountsDto.map(): Mounts{
    return Mounts(
        drives = this.drives.map { it.map() },
        mounts = this.mounts.map { it.map() }
    )
}