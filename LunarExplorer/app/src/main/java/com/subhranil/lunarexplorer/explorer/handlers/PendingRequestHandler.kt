package com.subhranil.lunarexplorer.explorer.handlers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.explorer.ExplorerState

@Composable
fun PendingRequestHandler(
    modifier: Modifier = Modifier,
    state: ExplorerState
) {
    val pendingConflict = state.pendingNameConflict ?: return

    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { pendingConflict.onSkip(false) }, // Default to skip on outside tap
        title = { Text("Name Conflict") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "The item \"${pendingConflict.originalName}\" already exists in this folder.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Do this for all conflicts")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { pendingConflict.onReplace(applyToAll) }) {
                Text("Replace")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pendingConflict.onKeepBoth(applyToAll) }) {
                    Text("Keep Both")
                }
                TextButton(onClick = { pendingConflict.onSkip(applyToAll) }) {
                    Text("Skip")
                }
            }
        }
    )
}
