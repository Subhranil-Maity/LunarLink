package com.subhranil.lunarexplorer.result



interface RootError {
    val message: String?
}


sealed interface LunarResult<out D, out E : RootError> {
    data class Success<out D, out E : RootError>(val data: D) : LunarResult<D, E>
    data class Error<out D, out E : RootError>(val error: E) : LunarResult<D, E>

    fun successOrNull(): D? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun isError(): Boolean = this is Error
    fun isSuccess(): Boolean = this is Success
    fun getErrorOrNull(): E? = when (this) {
        is Success -> null
        is Error -> error
    }

    fun let(block: (D) -> Unit) {
        if (this is Success) {
            block(data)
        }
    }
    fun onSuccess(block: (D) -> Unit): LunarResult<D, E> {
        if (this is Success) {
            block(data)
        }
        return this
    }
    fun onError(block: (E) -> Unit): LunarResult<D, E> {
        if (this is Error) {
            block(error)
        }
        return this
    }
}