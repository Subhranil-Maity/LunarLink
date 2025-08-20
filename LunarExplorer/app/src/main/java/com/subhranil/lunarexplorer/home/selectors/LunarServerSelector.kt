package com.subhranil.lunarexplorer.home.selectors

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.subhranil.lunarexplorer.home.HomeActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.lunarlink.isLunarHostAlive
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext.get

/**
 * Composable IP address input.
 *
 * @param value current serialised representation "a.b.c.d" where any segment may be empty ""
 *              Example valid shapes: "", "192", "192.168.0.1", ".1.2.", "192.0..1"
 *              For ease-of-use, it's recommended to pass a canonical "a.b.c.d" (with three dots),
 *              but the component tolerates missing dots and will interpret appropriately.
 * @param onValueChange called with updated representation after any change. Representation will
 *                      always be 0..3 dots separating up to 4 segments (if user hasn't typed any
 *                      dot input it will try its best to map).
 * @param segmentWidthDp width for each octet field (default 72dp)
 */
@Composable
fun IpAddressInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    segmentWidthDp: Int = 72
) {
    // Parse incoming value into exactly 4 segments (empty strings allowed)
    fun toSegments(s: String): List<String> {
        // If the user provided fewer than 3 dots, attempt to split intelligently:
        val dots = s.count { it == '.' }
        return when {
            dots >= 3 -> s.split(".", limit = 4).map { it.trim() }
            s.isBlank() -> List(4) { "" }
            else -> {
                // If no or fewer dots, try splitting by any dots then fill remaining with empty
                val parts = s.split(".").filter { it.isNotEmpty() }
                // If there are zero dots but the user typed digits like "192168001001" we won't auto-chop.
                // Keep provided parts and fill remaining slots with ""
                val result = parts.toMutableList()
                while (result.size < 4) result.add("")
                result.take(4)
            }
        }
    }

    val incomingSegments = remember(value) { toSegments(value) }

    // 4 states for fields
    val segStates = List(4) { index ->
        remember { mutableStateOf(incomingSegments.getOrNull(index) ?: "") }
    }

    // Keep the fields synchronized when external `value` changes
    LaunchedEffect(value) {
        val segs = toSegments(value)
        segStates.forEachIndexed { i, state ->
            if (state.value != (segs.getOrNull(i) ?: "")) {
                state.value = segs.getOrNull(i) ?: ""
            }
        }
    }

    // Focus requesters for keyboard navigation
    val focusRequesters = List(4) { remember { FocusRequester() } }

    // Combined update helper: constructs canonical "a.b.c.d" (always 3 dots)
    fun emitCombined() {
        val combined = segStates.joinToString(separator = ".") { it.value }
        onValueChange(combined)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0..3) {
            // local alias to avoid capturing loop var
            val idx = i
            val state = segStates[idx]

            OutlinedTextField(
                value = state.value,
                onValueChange = { raw ->
                    // Process the raw input:
                    // - allow only digits and dots
                    // - accept dot to move to next
                    // - handle paste of a whole IP
                    val filtered = raw.filter { it.isDigit() || it == '.' }

                    // If user pasted a full IP or typed dots, attempt to parse:
                    if (filtered.contains('.')) {
                        // split and apply to current and following fields
                        val pieces = filtered.split('.').filter { it.isNotEmpty() }
                        // If the paste content looks like full ip (4 pieces), apply across fields
                        if (pieces.size >= 1) {
                            var writeIndex = idx
                            for (p in pieces) {
                                if (writeIndex > 3) break
                                val newText = p.take(3) // limit each segment to 3 chars
                                segStates[writeIndex].value = newText
                                writeIndex++
                            }
                            emitCombined()
                            // move focus to next appropriate
                            if (idx < 3 && pieces.size >= 1) {
                                focusRequesters[minOf(3, idx + pieces.size - 1)].requestFocus()
                            }
                            return@OutlinedTextField
                        }
                    }

                    // Normal typing flow (no dot in new input)
                    var nextText = filtered.take(3) // restrict to 3 digits
                    // If user typed dot at end in their raw entry (e.g., "12.") we detected earlier
                    // and returned; so here we just set the text
                    state.value = nextText
                    emitCombined()

                    // If user filled 3 digits automatically advance focus
                    if (nextText.length == 3 && idx < 3) {
                        focusRequesters[idx + 1].requestFocus()
                    }
                },

                modifier = Modifier
                    .size(width = segmentWidthDp.dp, height = 56.dp)
                    .focusRequester(focusRequesters[idx]),

                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (idx == 3) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (idx < 3) focusRequesters[idx + 1].requestFocus()
                    },
                    onDone = {
                        // nothing special
                    }
                ),
                label = { /* optional label per segment */ },
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Validate an IPv4 string.
 * Accepts canonical "a.b.c.d" or values with fewer dots; returns true only when:
 * - There are exactly 4 segments
 * - Every segment is non-empty, contains only digits
 * - Each numeric value is in 0..255
 */
fun isValidIpv4(input: String): Boolean {
    val segments = input.split(".")
    if (segments.size != 4) return false
    return segments.all { seg ->
        if (seg.isEmpty()) return false
        if (seg.length > 3) return false
        // only digits
        if (!seg.all { it.isDigit() }) return false
        // numeric range
        val v = try {
            seg.toInt()
        } catch (e: NumberFormatException) {
            return false
        }
        v in 0..255
    }
}

@Preview
@Composable
private fun LunarServerSelectorPreview(modifier: Modifier = Modifier) {
    LunarServerSelector(shouldShow = true) { }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunarServerSelector(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    onAction: (HomeActions) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8000") }
    var username by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val maxPort = 65535
    var isServerReachable by remember { mutableStateOf(false) }
//    var server by remember { mutableStateOf<LunarHost?>(null) }
    val client = koinInject<HttpClient>()
    if (shouldShow) {
        ModalBottomSheet(
            modifier = modifier,
            sheetState = sheetState,
            onDismissRequest = {
                onAction(HomeActions.OnDismissAddNewServerDialog)
                ip = ""
            },
        ) {
            Column {
                Text("Add New Server", style = MaterialTheme.typography.titleLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IpAddressInput(
                        value = ip,
                        onValueChange = { ip = it }
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { newText ->
                            // Allow only numbers
                            if (newText.all { it.isDigit() }) {
                                val newPort = newText.toIntOrNull()
                                if (newPort != null && newPort in 0..maxPort) {
                                    port = newText
                                } else if (newText.isEmpty()) {
                                    port = ""
                                }
                            }
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = {
                            pass = it
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (!isServerReachable) {
                    ConnectButton {
                        val reach = isLunarHostAlive(
                            host = LunarHostConnectionConfig(
                                host = "http://$ip:$port",
                                username = username,
                                password = pass,
                                lastConnected = 0L
                            ),
                            client = client
                        )
                        if (reach !=null ){
                            isServerReachable = true
                        }
//                        Toast.makeText(
//                            get().get(),
//                            reach?.username ?: "Not Recable, \"http://$ip:$port\"",
//                            Toast.LENGTH_SHORT
//                        ).show()
                    }
                } else {
                    Button(
                        onClick = {
                            onAction(HomeActions.OnAddServer(host = "http://$ip:$port", username = username, password = pass))
                        }
                    ) {
                        Text("Save And Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(
    onConnect: suspend () -> Unit
) {
    var inConnecting by remember { mutableStateOf(false) }
    var isDisabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            scope.launch {
                inConnecting = true
                isDisabled = true
                onConnect()
                inConnecting = false
                isDisabled = false
            }
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        enabled = !isDisabled
    ) {
        if (!inConnecting) {
            Text("Connect")
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.width(12.dp))
                Text("Connecting")
            }
        }
    }
}