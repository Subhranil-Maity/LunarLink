package com.subhranil.lunarexplorer.settings.settingui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.subhranil.lunarexplorer.settings.SettingObject

@Composable
fun <T : Enum<T>> SettingOptions(
    modifier: Modifier = Modifier,
    setting: SettingObject.OptionSettingItem<T>,
    onValueChange: (T) -> Unit
) {
    SettingIndividual(
        modifier = modifier,
        name = setting.title,
        description = setting.description
    ){
        DropDown(
            allItems = setting.options,
            selected = setting.value
        ) { item ->
            onValueChange(item)

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Enum<T>> DropDown(
    modifier: Modifier = Modifier,
    allItems: List<T>,
    selected: T,
    onItemValueChanged: (T) -> Unit
) {
    var isExpanded by remember {
        mutableStateOf(false)
    }
    Column (
        modifier = modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = {
                isExpanded = !isExpanded
            }
        ) {
            TextField(
                modifier = modifier.menuAnchor(),
                value = selected.toString(),
                onValueChange = {
                },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                allItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.toString()) },
                        onClick = {
                            onItemValueChanged(item)
                            isExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

//@Composable
//fun <T> LargeDropdownMenu(
//    modifier: Modifier = Modifier,
//    enabled: Boolean = true,
//    notSetLabel: String? = null,
//    items: List<T>,
//    selectedIndex: Int = -1,
//    onItemSelected: (index: Int, item: T) -> Unit,
//    selectedItemToString: (T) -> String = { it.toString() },
//    drawItem: @Composable (T, Boolean, Boolean, () -> Unit) -> Unit = { item, selected, itemEnabled, onClick ->
//        LargeDropdownMenuItem(
//            text = item.toString(),
//            selected = selected,
//            enabled = itemEnabled,
//            onClick = onClick,
//        )
//    },
//) {
//    var expanded by remember { mutableStateOf(false) }
//
//    Box(modifier = modifier.height(IntrinsicSize.Min)) {
//        OutlinedTextField(
////            label = { Text(label) },
//            value = items.getOrNull(selectedIndex)?.let { selectedItemToString(it) } ?: "",
//            enabled = enabled,
//            modifier = Modifier.fillMaxWidth(),
//            trailingIcon = {
//                val icon = if (expanded) {Icons.Filled.Home} else{ Icons.Filled.ArrowDropDown}
//                Icon(icon, "")
//            },
//            onValueChange = { },
//            readOnly = true,
//        )
//
//        // Transparent clickable surface on top of OutlinedTextField
//        Surface(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(top = 8.dp)
//                .clip(MaterialTheme.shapes.extraSmall)
//                .clickable(enabled = enabled) { expanded = true },
//            color = Color.Transparent,
//        ) {}
//    }
//
//    if (expanded) {
//        Dialog(
//            onDismissRequest = { expanded = false },
//        ) {
//                Surface(
//                    shape = RoundedCornerShape(12.dp),
//                ) {
//                    val listState = rememberLazyListState()
//                    if (selectedIndex > -1) {
//                        LaunchedEffect("ScrollToSelected") {
//                            listState.scrollToItem(index = selectedIndex)
//                        }
//                    }
//
//                    LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
//                        if (notSetLabel != null) {
//                            item {
//                                LargeDropdownMenuItem(
//                                    text = notSetLabel,
//                                    selected = false,
//                                    enabled = false,
//                                    onClick = { },
//                                )
//                            }
//                        }
//                        itemsIndexed(items) { index, item ->
//                            val selectedItem = index == selectedIndex
//                            drawItem(
//                                item,
//                                selectedItem,
//                                true
//                            ) {
//                                onItemSelected(index, item)
//                                expanded = false
//                            }
//
//                            if (index < items.lastIndex) {
//                                HorizontalDivider(
//                                    modifier = Modifier.padding(horizontal = 16.dp),
//                                    thickness = DividerDefaults.Thickness,
//                                    color = DividerDefaults.color
//                                )
//                            }
//                        }
//                    }
//                }
//            }
//
//    }
//}
//
//@Composable
//fun LargeDropdownMenuItem(
//    text: String,
//    selected: Boolean,
//    enabled: Boolean,
//    onClick: () -> Unit,
//) {
//    val contentColor = when {
//        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
//        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.87f)
//        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
//    }
//
//    CompositionLocalProvider(LocalContentColor provides contentColor) {
//        Box(modifier = Modifier
//            .clickable(enabled) { onClick() }
//            .fillMaxWidth()
//            .padding(16.dp)) {
//            Text(
//                text = text,
//                style = MaterialTheme.typography.titleSmall,
//            )
//        }
//    }
//}