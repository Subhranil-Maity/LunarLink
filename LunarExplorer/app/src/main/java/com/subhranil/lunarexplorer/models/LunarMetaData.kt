@file:OptIn(ExperimentalTime::class)

package com.subhranil.lunarexplorer.models

import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class LunarMetaData constructor(
    val name: String,
    val size: Long,
    val entryType: EntryType,
    val symlinkLocation: String? = null,
    val absolutePath: String,
    val lastModified: Instant,
)

enum class EntryType{
    DIRECTORY,
    FILE,
    SYMLINK,
    OTHER
}

