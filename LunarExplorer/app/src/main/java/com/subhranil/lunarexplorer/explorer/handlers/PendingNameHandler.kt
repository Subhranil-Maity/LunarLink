package com.subhranil.lunarexplorer.explorer.handlers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.subhranil.lunarexplorer.explorer.NameRequestAction
import com.subhranil.lunarexplorer.explorer.NameRequestType
import com.subhranil.lunarexplorer.explorer.PendingNameRequest

@Composable
fun PendingNameHandler(
    request: PendingNameRequest?,
    dismissNameRequest: () -> Unit,
    confirmName: (String) -> Unit
) {
    if (request != null) {
        // Prefill with currentName on first composition, or empty string otherwise
        var input by remember(request.currentName) { mutableStateOf(request.currentName ?: "") }

        AlertDialog(
            onDismissRequest = { dismissNameRequest() },
            title = {
                Text(
                    when (request.action) {
                        NameRequestAction.CREATE -> if (request.type == NameRequestType.FILE) "Enter file name" else "Enter folder name"
                        NameRequestAction.RENAME -> if (request.type == NameRequestType.FILE) "Rename file" else "Rename folder"
                    }
                )
            },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            when (request.action) {
                                NameRequestAction.CREATE -> if (request.type == NameRequestType.FILE) "example.txt" else "New Folder"
                                NameRequestAction.RENAME -> ""  // No placeholder when renaming, since we prefill
                            }
                        )
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (input.isNotBlank()) {
                        confirmName(input.trim())
                    }
                }) {
                    Text(
                        when (request.action) {
                            NameRequestAction.CREATE -> "Create"
                            NameRequestAction.RENAME -> "Rename"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissNameRequest() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
