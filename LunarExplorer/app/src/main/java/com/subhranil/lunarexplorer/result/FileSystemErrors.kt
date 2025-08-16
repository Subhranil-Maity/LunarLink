package com.subhranil.lunarexplorer.result


typealias FileSystemResult<T> = LunarResult<T, FileSystemErrors>
//typealias FileSystemResult<T> = LunarResult<T, RootError>
enum class FileSystemErrors(
    override val message: String?
) : RootError {

    CANNOT_CONNECT("Cannot connect to the file system source"),
    OPERATION_UNAUTHORIZED("Operation is not authorized"),
    LOGIN_FAILED("Login to the file system failed"),
    FILE_OR_FOLDER_ALREADY_EXISTS("A file or folder with the same name already exists"),
    UNKNOWN_ERROR("UNHANDLED ERROR"),
    UNAUTHORISED("Currently UnAuthorised"),

    // Added from LunarError mapping
    LOGIN_FAIL("Login failed. Please check your credentials."),
    UNAUTHORIZED("Unauthorized access."),
    PERMISSION_DENIED("You do not have the required permissions."),
    ACCESS_DENIED_BY_SYSTEM("Access denied by system policy."),
    FILE_OR_FOLDER_NOT_FOUND("File or folder not found."),
    NOT_A_DIRECTORY("The specified path is not a directory."),
    NOT_A_VALID_PATH("The specified path is invalid."),
    CONFLICT("Conflict with existing resource."),
    IO_ERROR("I/O error occurred."),
    PARSE_ERROR("Failed to parse data."),
    INVALID_DATA("Invalid data format."),
    NETWORK_ERROR("Network error occurred."),
    TIMEOUT("Operation timed out."),
    BAD_REQUEST("Bad request."),
    FAILED_TO_READ_FILE("Failed to read file."),
    DELETING_FOLDER_WITHOUT_FLAG("Deleting folder without required flag."),
    KNOWN_UNKNOWN_ERROR("An internal error occurred."),
    UNEXPECTED("Unexpected error."),
    CUSTOM_ERROR("Custom error."),
    MISSING_AUTH_HEADER("Missing authentication header."),
    INVALID_AUTH_HEADER("Invalid authentication header."),
    JWT_ERROR("JWT token error."),
    PATH_NOT_PROVIDED("Path parameter not provided."),
    NEW_PATH_NOT_PROVIDED("New path parameter not provided."),
    SERIALISATION_ERROR("Cannot convert the Response To Appropriate Object ");

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