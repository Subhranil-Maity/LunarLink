package com.subhranil.lunarexplorer.repository

import com.subhranil.lunarexplorer.models.FolderInfo
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.result.FileSystemResult


interface FileSystemRepository {
    suspend fun connect(): FileSystemResult<Unit>
    suspend fun getMounts(): FileSystemResult<Mounts>

    suspend fun getMetaData(path: String): FileSystemResult<LunarMetaData>
    suspend fun getFolderContent(path: String): FileSystemResult<FolderInfo>
    suspend fun createFile(path: String): FileSystemResult<LunarMetaData>
    suspend fun createFolder(path: String): FileSystemResult<LunarMetaData>
    suspend fun delete(path: String, permanently: Boolean): FileSystemResult<Unit>
    suspend fun rename(path: String, newPath: String, shouldOverride: Boolean): FileSystemResult<Unit>
    suspend fun copy(path: String, newPath: String, shouldOverride: Boolean): FileSystemResult<Unit>
    suspend fun move(path: String, newPath: String, shouldOverride: Boolean): FileSystemResult<Unit>
}
