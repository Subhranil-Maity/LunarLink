package com.subhranil.lunarexplorer.lunarlink.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseDto (
    val token: String
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)