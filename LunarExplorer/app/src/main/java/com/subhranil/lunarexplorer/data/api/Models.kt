package com.subhranil.lunarexplorer.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors the LunarLink server API (see LunarLinkServer/API.md).

@Serializable
enum class Permission {
    @SerialName("read") READ,
    @SerialName("write") WRITE,
    @SerialName("delete") DELETE,
}

@Serializable
enum class EntryKind {
    @SerialName("file") FILE,
    @SerialName("dir") DIR,
    @SerialName("symlink") SYMLINK,
    @SerialName("other") OTHER,
}

@Serializable
data class Entry(
    val name: String,
    val path: String,
    val kind: EntryKind,
    val size: Long = 0,
    val modified: Long? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val hidden: Boolean = false,
    @SerialName("symlink_target") val symlinkTarget: String? = null,
    @SerialName("target_kind") val targetKind: EntryKind? = null,
) {
    /** A folder, or a symlink that points to a folder. */
    val isDirLike: Boolean get() = kind == EntryKind.DIR || targetKind == EntryKind.DIR
}

@Serializable
enum class RootKind {
    @SerialName("mount") MOUNT,
    @SerialName("drive") DRIVE,
}

@Serializable
enum class DiskKind {
    @SerialName("hdd") HDD,
    @SerialName("ssd") SSD,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
data class Disk(
    val name: String,
    @SerialName("mount_point") val mountPoint: String,
    @SerialName("fs_type") val fsType: String,
    @SerialName("disk_kind") val diskKind: DiskKind = DiskKind.UNKNOWN,
    val removable: Boolean = false,
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("available_bytes") val availableBytes: Long,
)

@Serializable
data class Root(
    val name: String,
    val path: String,
    val kind: RootKind,
    val permissions: Set<Permission>,
    val disk: Disk? = null,
) {
    fun can(permission: Permission) = permission in permissions
}

@Serializable
data class RootsResponse(val roots: List<Root>)

@Serializable
data class Listing(
    val entry: Entry,
    val items: List<Entry>,
    val total: Int,
    val offset: Int,
    val limit: Int? = null,
)

@Serializable
data class ServerInfo(
    val name: String,
    @SerialName("os_type") val osType: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("server_version") val serverVersion: String,
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("auth_required") val authRequired: Boolean,
)

@Serializable
data class UserInfo(val username: String, val permissions: Set<Permission>)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_at") val expiresAt: Long,
    val user: UserInfo,
)

@Serializable
enum class SortKey {
    @SerialName("name") NAME,
    @SerialName("modified") MODIFIED,
    @SerialName("size") SIZE,
    @SerialName("kind") KIND,
}

@Serializable
enum class SortOrder {
    @SerialName("asc") ASC,
    @SerialName("desc") DESC,
}

data class ListQuery(
    val offset: Int = 0,
    val limit: Int? = null,
    val sort: SortKey = SortKey.NAME,
    val order: SortOrder = SortOrder.ASC,
    val dirsFirst: Boolean = true,
    val showHidden: Boolean = true,
)

// ---------- request bodies ----------

@Serializable
internal data class LoginRequest(val username: String, val password: String)

@Serializable
internal data class PathBody(val path: String)

@Serializable
internal data class DirBody(val path: String, val parents: Boolean)

@Serializable
internal data class TransferBody(val from: String, val to: String, val overwrite: Boolean)

@Serializable
internal data class DeleteBody(val path: String, val permanent: Boolean, val recursive: Boolean)

@Serializable
internal data class ErrorEnvelope(val error: ErrorBody)

@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)
