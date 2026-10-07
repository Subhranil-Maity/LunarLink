package com.subhranil.lunarexplorer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.subhranil.lunarexplorer.ui.Feedback
import com.subhranil.lunarexplorer.ui.rememberHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.ui.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditorSheet(
    state: EditorState,
    onDismiss: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (state.editingId == null) R.string.editor_add_title else R.string.editor_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    label = { Text(stringResource(R.string.editor_host)) },
                    placeholder = { Text(stringResource(R.string.editor_host_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    label = { Text(stringResource(R.string.editor_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.width(104.dp),
                )
            }
            val addressHint = when {
                state.host.isBlank() -> null
                state.baseUrl == null -> stringResource(R.string.editor_invalid_address)
                else -> stringResource(R.string.editor_resolved, state.baseUrl!!)
            }
            addressHint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.baseUrl == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.editor_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.editor_password)) },
                singleLine = true,
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canTest) onTest() }),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            painterResource(if (state.passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = stringResource(
                                if (state.passwordVisible) R.string.editor_hide_password else R.string.editor_show_password
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            TestResult(state.test)
            state.error?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onTest, enabled = state.canTest) {
                    if (state.test == TestState.Testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.editor_testing), Modifier.padding(start = 8.dp))
                    } else {
                        Text(stringResource(R.string.editor_test))
                    }
                }
                Button(onClick = onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun TestResult(test: TestState) {
    val haptics = rememberHaptics()
    // Confirm the outcome of a connection test, once per result.
    LaunchedEffect(test) {
        when (test) {
            is TestState.Success -> haptics.result(Feedback.SUCCESS)
            is TestState.Failed -> haptics.result(Feedback.ERROR)
            else -> Unit
        }
    }
    val (icon, color, text) = when (test) {
        is TestState.Success -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.editor_test_ok, test.info.name, osDisplayName(test.info.osType)),
        )
        is TestState.Failed -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, test.message.asString())
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}
