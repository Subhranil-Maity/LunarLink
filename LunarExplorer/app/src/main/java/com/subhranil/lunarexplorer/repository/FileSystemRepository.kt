package com.subhranil.lunarexplorer.repository

import com.subhranil.lunarexplorer.models.FolderInfo
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.FileSystemResult
import com.subhranil.lunarexplorer.result.LunarResult


interface FileSystemRepository {
    suspend fun connect(): FileSystemResult<Unit>
    suspend fun getMounts(): FileSystemResult<Mounts>

    suspend fun getMetaData(path: String): FileSystemResult<LunarMetaData>
    suspend fun getFolderContent(path: String): FileSystemResult<FolderInfo>
    suspend fun createFile(path: String): FileSystemResult<LunarMetaData>
    suspend fun createFolder(path: String): FileSystemResult<LunarMetaData>

//    // ======== Navigation ========
//    fun listFiles(path: String): List<FileItem>
//    fun listDirectories(path: String): List<FileItem>
//    fun exists(path: String): Boolean
//
//    // ======== File operations ========
//    fun createFile(path: String, data: ByteArray? = null): Boolean
//    fun createDirectory(path: String): Boolean
//    fun rename(path: String, newName: String): Boolean
//    fun move(sourcePath: String, destinationPath: String): Boolean
//    fun copy(sourcePath: String, destinationPath: String): Boolean
//    fun delete(path: String, recursive: Boolean = false): Boolean
//
//    // ======== Reading & Writing ========
//    fun readFile(path: String): ByteArray
////    fun readFileStream(path: String): InputStream
//    fun writeFile(path: String, data: ByteArray, append: Boolean = false): Boolean
//
//    // ======== Metadata & Thumbnails ========
//    fun getMetadata(path: String): FileMetadata
//    fun getThumbnail(path: String, size: Int): ByteArray?
//
//    // ======== Search ========
//    fun searchFiles(query: String, startPath: String): List<FileItem>
}
