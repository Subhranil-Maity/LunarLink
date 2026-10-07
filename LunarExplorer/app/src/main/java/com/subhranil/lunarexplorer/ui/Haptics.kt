package com.subhranil.lunarexplorer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether the user wants haptic feedback. Provided from the app settings in MainActivity. */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Outcome of an action, so the screen can confirm it with a matching haptic. */
enum class Feedback { SUCCESS, ERROR }

/** Plays haptic feedback, unless the user turned it off in Settings. */
@Stable
class Haptics internal constructor(private val feedback: HapticFeedback, private val enabled: Boolean) {
    fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }

    /** Long-press that starts a selection or opens a menu. */
    fun longPress() = perform(HapticFeedbackType.LongPress)

    /** Small tick, e.g. adding or removing an item from a selection. */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    /** A gesture crossed the point where it will take effect, e.g. pull to refresh. */
    fun threshold() = perform(HapticFeedbackType.GestureThresholdActivate)

    fun contextClick() = perform(HapticFeedbackType.ContextClick)

    /** Something was switched on or resumed (`true`), or switched off or paused (`false`). */
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun result(result: Feedback) = perform(
        when (result) {
            Feedback.SUCCESS -> HapticFeedbackType.Confirm
            Feedback.ERROR -> HapticFeedbackType.Reject
        }
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
