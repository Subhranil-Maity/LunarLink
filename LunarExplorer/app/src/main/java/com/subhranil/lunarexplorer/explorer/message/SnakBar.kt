package com.subhranil.lunarexplorer.explorer.message

import androidx.compose.ui.graphics.vector.ImageVector

data class SnackbarMessage(
    val text: String,
    val icon: ImageVector? = null // optional icon
)