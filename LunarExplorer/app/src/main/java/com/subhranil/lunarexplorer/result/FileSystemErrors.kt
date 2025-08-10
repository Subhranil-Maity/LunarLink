package com.subhranil.lunarexplorer.result


typealias FileSystemResult<T> = LunarResult<T, FileSystemErrors>
enum class FileSystemErrors(
    override val message: String?
) : RootError {

    CANNOT_CONNECT("Cannot connect to the file system source"),
    OPERATION_UNAUTHORIZED("Operation is not authorized"),
    LOGIN_FAILED("Login to the file system failed"),
    FILE_OR_FOLDER_ALREADY_EXISTS("A file or folder with the same name already exists"),
    UNKNOWN_ERROR("UNHANDLED ERROR");

    override fun toString(): String = message ?: name
}




//enum class RemoteError(override val message: String? = null) : RootError {
//    UNAUTHORIZED("Unauthorized"),
//    NOT_FOUND("Not found"),
//    SERVER_ERROR("Server error"),
//    NETWORK_ERROR("Network error"),
//    UNKNOWN_ERROR("Unknown error"),
//    TIMEOUT("Request timed out");
//
//    // safe toString - no recursion
//    override fun toString(): String = message?.let { "$name: $it" } ?: name
//}