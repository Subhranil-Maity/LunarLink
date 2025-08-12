package com.subhranil.lunarexplorer.explorer.clipboard

data class NameConflictRequest(
    val originalName: String,
    val originalPath: String,
    val destinationPath: String,
    val onReplace: (applyToAll: Boolean) -> Unit,
    val onKeepBoth: (applyToAll: Boolean) -> Unit,
    val onSkip: (applyToAll: Boolean) -> Unit
)
