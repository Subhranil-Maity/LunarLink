package com.subhranil.lunarexplorer.models

data class FolderInfo(
    val metaData: LunarMetaData,
    val entries: List<LunarMetaData>
)
