package com.subhranil.lunarexplorer.lunarlink.dto

import com.subhranil.lunarexplorer.models.DiskType
import com.subhranil.lunarexplorer.models.Drive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class DriveDto(
    val name: String,
    @SerialName("used_size")
    val usedSize: Long,
    @SerialName("total_size")
    val totalSize: Long,
    @SerialName("disk_type")
    val diskType: String,
    @SerialName("mount_point")
    val mountPoint: String,
    @SerialName("fs_type")
    val fsType: String
)

fun DriveDto.map(): Drive{
    return Drive(
        name = this.name,
        usedSize = this.usedSize,
        totalSize = this.totalSize,
        diskType = mapDiskType(this.diskType),
        mountPoint = this.mountPoint,
        fsType = this.fsType
    )
}

fun mapDiskType(
    diskType: String
): DiskType = when (diskType){
    "HDD" -> DiskType.HDD
    "SSD" -> DiskType.SSD
    "NVME" -> DiskType.NVME
    "REMOVABLE" -> DiskType.REMOVABLE
    else -> DiskType.UNKNOWN
}

