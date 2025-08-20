package com.subhranil.lunarexplorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant


@OptIn(ExperimentalTime::class)
@Composable
fun formatInstant(instant: Instant): Pair<String, String> {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
    }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    val javaInstant = instant.toJavaInstant()
    val dateStr = dateFormatter.format(javaInstant)
    val timeStr = timeFormatter.format(javaInstant)

    return dateStr to timeStr
}

