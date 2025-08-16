package com.subhranil.lunarexplorer.explorer.options

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.explorer.ExplorerActions
import com.subhranil.lunarexplorer.explorer.ExplorerState
import com.subhranil.lunarexplorer.icons.getIcon
import com.subhranil.lunarexplorer.models.EntryType

@Composable
fun OptionPage(
    modifier: Modifier = Modifier,
    state: ExplorerState,
    onAction: (ExplorerActions) -> Unit,
    options: List<ExplorerOptions>
) {
    state.showOptions?.let { selected ->
        val options = options.toMutableList()
        // Hot Fix
        if (selected.entryType == EntryType.FILE){
            options.add(ExplorerOptions(
                label = "Download ALl At Once",
                icon = ImageVector.vectorResource(R.drawable.download_icon),
                type = ExplorerOptionType.DOWNLOAD
            ))
        }
        val (icon, tint) = getIcon(selected)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Header row with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    tint = tint,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = selected.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onAction(ExplorerActions.OnOptionsDismiss) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Thin divider after header
            HorizontalDivider(thickness = 0.5.dp, color = DividerDefaults.color)

            // Option list
            options.forEachIndexed { index, option ->
                ThisOption(
                    explorerOptions = option,
                    onClickListener = {
                        onAction(ExplorerActions.OnOptionClicked(option, selected))
                    }
                )

                // Even thinner divider for separation
                if (index != options.lastIndex) {
                    HorizontalDivider(thickness = 0.3.dp, color = DividerDefaults.color)
                }
            }
        }
    }
}

@Composable
private fun ThisOption(
    modifier: Modifier = Modifier,
    explorerOptions: ExplorerOptions,
    onClickListener: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickListener() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = explorerOptions.icon,
            contentDescription = explorerOptions.label,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = explorerOptions.label,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
