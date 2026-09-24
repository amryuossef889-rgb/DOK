package com.dok.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dok.editor.ui.DokEditorApp
import com.dok.editor.ui.theme.DokEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DokEditorTheme {
                DokEditorApp()
            }
        }
    }
}

