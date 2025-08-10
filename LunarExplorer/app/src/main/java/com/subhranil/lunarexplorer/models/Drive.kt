package com.subhranil.lunarexplorer.models

import kotlinx.serialization.SerialName

data class Drive(
    val name: String,
    val usedSize: Long,
    val totalSize: Long,
    val diskType: DiskType,
    val mountPoint: String,
    val fsType: String
)

enum class DiskType{
    HDD,
    SSD,
    NVME,
    REMOVABLE,
    UNKNOWN;
}
