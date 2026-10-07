package com.subhranil.lunarexplorer.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.LunarError

/** Text produced by view models without needing a Context. Args may themselves be [UiText]. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    data class Raw(val text: String) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun pluralText(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
    UiText.Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.map { (it as? UiText)?.asString() ?: it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { (it as? UiText)?.asString() ?: it }.toTypedArray())
}

/** Resolves outside of composition, e.g. for snackbars. */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> context.getString(id, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(
        id, count, *args.map { (it as? UiText)?.resolve(context) ?: it }.toTypedArray()
    )
}

/** A user-facing message for an error. Falls back to the server's own message. */
fun LunarError.toUiText(): UiText = when (this) {
    LunarError.Unreachable -> uiText(R.string.error_unreachable)
    LunarError.Timeout -> uiText(R.string.error_timeout)
    is LunarError.Unexpected -> uiText(R.string.error_unexpected, detail)
    is LunarError.Api -> when (code) {
        ErrorCode.LOGIN_FAILED -> uiText(R.string.error_login_failed)
        ErrorCode.TOO_MANY_ATTEMPTS -> uiText(R.string.error_too_many_attempts, retryAfterSeconds ?: 60L)
        ErrorCode.PERMISSION_DENIED -> uiText(R.string.error_permission_denied)
        ErrorCode.PATH_OUTSIDE_ROOTS -> uiText(R.string.error_outside_roots)
        ErrorCode.SYSTEM_ACCESS_DENIED -> uiText(R.string.error_system_denied)
        ErrorCode.NOT_FOUND -> uiText(R.string.error_not_found)
        ErrorCode.ALREADY_EXISTS -> uiText(R.string.error_already_exists)
        ErrorCode.TRASH_FAILED -> uiText(R.string.error_trash_failed)
        ErrorCode.TOKEN_EXPIRED, ErrorCode.INVALID_TOKEN, ErrorCode.UNAUTHENTICATED ->
            uiText(R.string.error_session_expired)
        else -> UiText.Raw(message)
    }
}
