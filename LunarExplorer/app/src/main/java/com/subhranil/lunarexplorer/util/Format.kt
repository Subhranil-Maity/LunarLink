package com.subhranil.lunarexplorer.util

import java.util.Locale

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

/** 1536 -> "1.5 KB". Bytes are shown without decimals. */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(locale, "%.1f %s", value, UNITS[unit])
}

enum class FileCategory { IMAGE, VIDEO, AUDIO, DOCUMENT, PDF, SPREADSHEET, PRESENTATION, ARCHIVE, CODE, TEXT, CAD, OTHER }

private val CATEGORY_BY_EXTENSION: Map<String, FileCategory> = buildMap {
    fun add(category: FileCategory, vararg ext: String) = ext.forEach { put(it, category) }
    add(FileCategory.IMAGE, "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg", "ico", "heic", "avif")
    add(FileCategory.VIDEO, "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
    add(FileCategory.AUDIO, "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus")
    add(FileCategory.DOCUMENT, "doc", "docx", "odt", "rtf")
    add(FileCategory.PDF, "pdf")
    add(FileCategory.SPREADSHEET, "xls", "xlsx", "ods", "csv")
    add(FileCategory.PRESENTATION, "ppt", "pptx", "odp", "key")
    add(FileCategory.ARCHIVE, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst")
    add(FileCategory.CODE, "kt", "java", "rs", "py", "js", "ts", "c", "cpp", "h", "go", "sh", "json", "xml", "html", "css", "toml", "yaml", "yml")
    add(FileCategory.TEXT, "txt", "md", "log", "cfg", "ini", "conf")
    add(FileCategory.CAD, "dwg", "dxf")
}

fun fileCategory(name: String): FileCategory {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.length - 1) return FileCategory.OTHER
    return CATEGORY_BY_EXTENSION[name.substring(dot + 1).lowercase(Locale.ROOT)] ?: FileCategory.OTHER
}
