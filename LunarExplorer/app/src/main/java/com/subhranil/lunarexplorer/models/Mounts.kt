package com.subhranil.lunarexplorer.models

data class Mounts(
    val drives: List<Drive>,
    val mounts: List<LunarMetaData>
)

fun defaultMounts(): Mounts{
    return Mounts(
        drives = listOf(),
        mounts = listOf()
    )
}
