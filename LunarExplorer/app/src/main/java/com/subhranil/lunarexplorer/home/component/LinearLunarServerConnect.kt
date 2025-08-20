package com.subhranil.lunarexplorer.home.component

import android.graphics.drawable.Icon
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.formatInstant
import com.subhranil.lunarexplorer.home.models.LunarDeviceType
import com.subhranil.lunarexplorer.home.models.LunarHost
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Composable
fun LinearLunarServerConnect(
    modifier: Modifier = Modifier,
    host: LunarHost,
    onClick: () -> Unit
) {
    val (date, time) = formatInstant(host.lastConnected)
    Row(
        modifier = modifier
            .combinedClickable(
                onClick = onClick
            )
            .padding(20.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OsIcon(host.deviceType)
        Column() {
            Text(host.deviceName, style = MaterialTheme.typography.titleLarge)
            Text(
                host.host,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        Column {
            Text(
                "LastConnected On",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text("$date $time", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview
@Composable
private fun Preview(modifier: Modifier = Modifier) {
    val host = LunarHost(
        host = "TODO()",
        username = "USERNAME",
        password = "PADD",
        deviceType = LunarDeviceType.WINDOWS,
        osVersion = "version",
        deviceName = "NAME",
        lastConnected = Clock.System.now()
    )
    LinearLunarServerConnect(host = host) {}
}


@Composable
private fun OsIcon(lunarOsType: LunarDeviceType, size: Dp = 40.dp) {
    when (lunarOsType) {
        LunarDeviceType.WINDOWS -> Icon(
            imageVector = ImageVector.vectorResource(R.drawable.windows_icon),
            contentDescription = null,
            tint = Color.Blue,
            modifier = Modifier.size(size)
        )

        else -> Icon(
            imageVector = ImageVector.vectorResource(R.drawable.unknown_icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size)
        )
    }
}
