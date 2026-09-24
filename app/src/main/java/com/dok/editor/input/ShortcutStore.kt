package com.dok.editor.input

import android.content.Context
import com.dok.editor.command.EditorCommand

object ShortcutStore {
    private const val PREFS = "dok_shortcuts"
    private val defaults = linkedMapOf(
        "Play / Pause" to "Space",
        "Reverse" to "J",
        "Stop Shuttle" to "K",
        "Forward" to "L",
        "Split" to "B",
        "Undo" to "Ctrl+Z",
        "Redo" to "Ctrl+Shift+Z"
    )

    fun all(context: Context): LinkedHashMap<String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LinkedHashMap<String, String>().apply {
            defaults.forEach { (name, value) -> put(name, prefs.getString(name, value) ?: value) }
        }
    }

    fun save(context: Context, values: Map<String, String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            values.forEach { (name, key) -> putString(name, key.trim()) }
            apply()
        }
    }

    fun commandFor(context: Context, chord: String): EditorCommand? {
        val normalized = chord.trim().replace(" ", "")
        return all(context).entries.firstOrNull { it.value.replace(" ", "").equals(normalized, true) }?.let {
            when (it.key) {
                "Play / Pause" -> EditorCommand.TogglePlayPause
                "Reverse" -> EditorCommand.ShuttleReverse
                "Stop Shuttle" -> EditorCommand.ShuttleStop
                "Forward" -> EditorCommand.ShuttleForward
                "Split" -> EditorCommand.SplitClipAtPlayhead
                "Undo" -> EditorCommand.Undo
                "Redo" -> EditorCommand.Redo
                else -> null
            }
        }
    }
}
