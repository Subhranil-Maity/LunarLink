package com.subhranil.lunarexplorer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.ui.explorer.DeleteDialog
import com.subhranil.lunarexplorer.ui.explorer.NameDialog
import com.subhranil.lunarexplorer.ui.theme.LunarTheme
import com.subhranil.lunarexplorer.util.PathStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nameDialogValidatesAsYouType() {
        var confirmed: String? = null
        compose.setContent {
            LunarTheme {
                NameDialog(
                    title = "New folder",
                    confirmLabel = "Create",
                    initial = "",
                    style = PathStyle.POSIX,
                    existingNames = setOf("taken"),
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Create").assertIsNotEnabled()

        compose.onNodeWithText("Name").performTextReplacement("a/b")
        compose.onNodeWithText("The name contains characters that aren't allowed").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsNotEnabled()

        compose.onNodeWithText("a/b").performTextReplacement("taken")
        compose.onNodeWithText("An item with that name already exists.").assertIsDisplayed()

        compose.onNodeWithText("taken").performTextReplacement("photos")
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals("photos", confirmed)
    }

    @Test
    fun deleteDialogExplainsPermanentDeletion() {
        var permanent: Boolean? = null
        val folder = Entry(name = "docs", path = "/share/docs", kind = EntryKind.DIR)
        compose.setContent {
            LunarTheme { DeleteDialog(listOf(folder), onConfirm = { permanent = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Items will be moved to the server's trash.").assertIsDisplayed()
        compose.onNodeWithText("Folders are deleted with everything inside them.").assertIsDisplayed()

        compose.onNodeWithText("Delete permanently").performClick()
        compose.onNodeWithText("Items will be deleted permanently. This can't be undone.").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(true, permanent)
    }
}
