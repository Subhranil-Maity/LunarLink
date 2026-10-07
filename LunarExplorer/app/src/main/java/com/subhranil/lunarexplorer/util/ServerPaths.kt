package com.subhranil.lunarexplorer.util

/**
 * Path helpers for paths on the *server*, which may be POSIX (`/home/me`) or Windows (`C:\Users`).
 * The server always returns canonical absolute paths, so these helpers only need to handle those.
 */
enum class PathStyle {
    POSIX,
    WINDOWS;

    val separator: Char get() = if (this == WINDOWS) '\\' else '/'

    companion object {
        private val DRIVE = Regex("^[A-Za-z]:([\\\\/]|$)")

        fun of(path: String): PathStyle =
            if (DRIVE.containsMatchIn(path) || path.startsWith("\\\\")) WINDOWS else POSIX
    }
}

data class Crumb(val name: String, val path: String)

object ServerPaths {
    private fun isSep(c: Char) = c == '/' || c == '\\'

    /** Removes trailing separators but keeps roots (`/`, `C:\`) intact. */
    fun trim(path: String): String {
        val style = PathStyle.of(path)
        var end = path.length
        while (end > 1 && isSep(path[end - 1])) end--
        val trimmed = path.substring(0, end)
        return when {
            style == PathStyle.WINDOWS && trimmed.length == 2 && trimmed[1] == ':' -> trimmed + '\\'
            else -> trimmed
        }
    }

    fun join(parent: String, name: String): String {
        val base = trim(parent)
        val sep = PathStyle.of(base).separator
        return if (base.isNotEmpty() && isSep(base.last())) base + name else base + sep + name
    }

    fun name(path: String): String {
        val t = trim(path)
        val idx = t.indexOfLast(::isSep)
        return if (idx < 0 || idx == t.length - 1) t else t.substring(idx + 1)
    }

    /** The parent folder, or null for a filesystem root. */
    fun parent(path: String): String? {
        val t = trim(path)
        val idx = t.indexOfLast(::isSep)
        if (idx < 0 || idx == t.length - 1) return null
        return trim(if (idx == 0) t.substring(0, 1) else t.substring(0, idx))
    }

    /** True if [path] is [ancestor] or lies inside it. */
    fun isSameOrInside(path: String, ancestor: String): Boolean {
        val p = trim(path)
        val a = trim(ancestor)
        if (p == a) return true
        val prefix = if (isSep(a.last())) a else a + PathStyle.of(a).separator
        return p.startsWith(prefix)
    }

    /** Breadcrumbs from [rootPath] (shown as [rootName]) down to [path]. */
    fun crumbs(rootPath: String, rootName: String, path: String): List<Crumb> {
        val root = trim(rootPath)
        val target = trim(path)
        if (!isSameOrInside(target, root)) return listOf(Crumb(name(target), target))
        val result = mutableListOf(Crumb(rootName, root))
        var current = root
        target.removePrefix(root).split('/', '\\').filter { it.isNotEmpty() }.forEach { part ->
            current = join(current, part)
            result += Crumb(part, current)
        }
        return result
    }
}

enum class NameError { EMPTY, RESERVED, INVALID_CHARACTER, TOO_LONG }

private val WINDOWS_INVALID = setOf('<', '>', ':', '"', '|', '?', '*')

/** Validates a single file or folder name for the given server path style. */
fun validateName(name: String, style: PathStyle): NameError? = when {
    name.isBlank() -> NameError.EMPTY
    name == "." || name == ".." -> NameError.RESERVED
    name.any { it == '/' || it == '\\' || it == '\u0000' } -> NameError.INVALID_CHARACTER
    style == PathStyle.WINDOWS && (name.any { it in WINDOWS_INVALID } || name.endsWith('.') || name.endsWith(' ')) ->
        NameError.INVALID_CHARACTER
    name.toByteArray(Charsets.UTF_8).size > 255 -> NameError.TOO_LONG
    else -> null
}

/** "report.pdf" -> "report (1).pdf", "report (2).pdf", ... until it isn't in [taken]. */
fun uniqueName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val dot = name.lastIndexOf('.')
    val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var i = 1
    while ("$base ($i)$ext" in taken) i++
    return "$base ($i)$ext"
}
