package com.subhranil.lunarexplorer.lunarlink

import com.subhranil.lunarexplorer.lunarlink.dto.ErrorDto
import com.subhranil.lunarexplorer.result.FileSystemErrors
fun getErrorResults(error: ErrorDto): FileSystemErrors {
    return when (error.code) {
        1001 -> FileSystemErrors.LOGIN_FAIL
        1002 -> FileSystemErrors.UNAUTHORIZED
        1003 -> FileSystemErrors.PERMISSION_DENIED
        1004 -> FileSystemErrors.ACCESS_DENIED_BY_SYSTEM
        1005 -> FileSystemErrors.FILE_OR_FOLDER_NOT_FOUND
        1006 -> FileSystemErrors.FILE_OR_FOLDER_ALREADY_EXISTS
        1007 -> FileSystemErrors.NOT_A_DIRECTORY
        1008 -> FileSystemErrors.NOT_A_VALID_PATH
        1009 -> FileSystemErrors.CONFLICT
        1010 -> FileSystemErrors.IO_ERROR
        1011 -> FileSystemErrors.PARSE_ERROR
        1012 -> FileSystemErrors.INVALID_DATA
        1013 -> FileSystemErrors.NETWORK_ERROR
        1014 -> FileSystemErrors.TIMEOUT
        1015 -> FileSystemErrors.BAD_REQUEST
        1016 -> FileSystemErrors.FAILED_TO_READ_FILE
        1017 -> FileSystemErrors.DELETING_FOLDER_WITHOUT_FLAG
        1018 -> FileSystemErrors.KNOWN_UNKNOWN_ERROR
        1019 -> FileSystemErrors.UNEXPECTED
        1020 -> FileSystemErrors.CUSTOM_ERROR
        1021 -> FileSystemErrors.MISSING_AUTH_HEADER
        1022 -> FileSystemErrors.INVALID_AUTH_HEADER
        1023 -> FileSystemErrors.JWT_ERROR
        1024 -> FileSystemErrors.PATH_NOT_PROVIDED
        1025 -> FileSystemErrors.NEW_PATH_NOT_PROVIDED
        else -> FileSystemErrors.UNKNOWN_ERROR
    }
}
