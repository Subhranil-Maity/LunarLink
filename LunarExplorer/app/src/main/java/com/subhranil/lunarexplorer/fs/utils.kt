package com.subhranil.lunarexplorer.fs

import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.isDir

enum class CollisionType {
    NONE,                 // No problem
    NAME_CONFLICT,        // Same name & same type exists
    PASTE_INTO_SELF,      // Trying to paste a folder into itself or a subfolder
}

fun findCollisions(
    metaData: LunarMetaData,
    currentPath: String,
    currentFolderContents: List<LunarMetaData>
): CollisionType {

    fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }

    val sourcePath = normalizePath(metaData.absolutePath)
    val targetPath = normalizePath(currentPath)

    // 1. PASTE_INTO_SELF check (only makes sense for folders)
    if (metaData.isDir() && targetPath.startsWith("$sourcePath/")) {
        return CollisionType.PASTE_INTO_SELF
    }

    // 2. NAME_CONFLICT check (same name & type already in target folder)
    val conflict = currentFolderContents.any { existing ->
        existing.name.equals(metaData.name, ignoreCase = true) &&
                existing.isDir() == metaData.isDir()
    }
    if (conflict) {
        return CollisionType.NAME_CONFLICT
    }

    // 3. No problems found
    return CollisionType.NONE
}

fun getNewPath(
    metaData: LunarMetaData,
    currentPath: String
): String {
    // Normalize to forward slashes
    fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }

    val normalizedCurrent = normalizePath(currentPath)
    val itemName = metaData.name

    // Construct the new path
    return "$normalizedCurrent/$itemName"
}

fun generateNonConflictingPath(
    baseDir: String,
    originalName: String,
    existingItems: List<LunarMetaData>
): String {
    // Split extension for files, leave folder names intact
    val dotIndex = originalName.lastIndexOf('.')
    val nameWithoutExt: String
    val extension: String
    if (dotIndex != -1 && dotIndex != 0 && dotIndex != originalName.length - 1) {
        nameWithoutExt = originalName.substring(0, dotIndex)
        extension = originalName.substring(dotIndex)
    } else {
        nameWithoutExt = originalName
        extension = ""
    }

    // Set starting candidate
    var candidateName = originalName
    var counter = 1

    val existingNames = existingItems.map { it.name }.toSet()

    // Loop until we find a unique name
    while (existingNames.contains(candidateName)) {
        candidateName = "$nameWithoutExt ($counter)$extension"
        counter++
    }

    return "$baseDir/$candidateName"
}

fun getBasePath(metaData: LunarMetaData): String {
    val path = metaData.absolutePath.trimEnd('/', '\\') // remove trailing slash or backslash
    val name = metaData.name

    return if (path.endsWith(name)) {
        path.substring(0, path.length - name.length).trimEnd('/', '\\')
    } else {
        path
    }
}
