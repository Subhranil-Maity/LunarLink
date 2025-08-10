package com.subhranil.lunarexplorer.lunarlink.dto

import kotlinx.serialization.Serializable


@Serializable
data class ErrorDto(
    val message: String,
    val code: Int
)