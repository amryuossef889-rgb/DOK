package com.dok.editor

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun editorRootIsVisibleOnDevice() {
        composeRule.onNodeWithTag("dok_editor_root").assertExists()
    }
}
