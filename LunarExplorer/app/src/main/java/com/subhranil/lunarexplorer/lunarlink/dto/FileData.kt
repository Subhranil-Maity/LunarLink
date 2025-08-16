package com.subhranil.lunarexplorer.lunarlink.dto

import kotlinx.serialization.Serializable

@Serializable
data class FileData(
    val metadata: MetaDataDto,
    val data: List<Int>
)

fun List<Int>.toByteArrayUnsigned(): ByteArray =
    ByteArray(size) { i -> this[i].toByte() } // preserves raw bits

