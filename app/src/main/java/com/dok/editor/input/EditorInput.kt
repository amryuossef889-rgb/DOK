package com.dok.editor.input

import com.dok.editor.command.EditorCommand

enum class InputDevice { TOUCH, MOUSE, KEYBOARD, STYLUS }

data class KeyChord(
    val key: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
)

class EditorInputMapper(
    private val keymap: Map<KeyChord, EditorCommand> = defaultKeymap()
) {
    fun commandFor(chord: KeyChord): EditorCommand? = keymap[chord]

    companion object {
        fun defaultKeymap(): Map<KeyChord, EditorCommand> = mapOf(
            KeyChord("Space") to EditorCommand.TogglePlayPause,
            KeyChord("J") to EditorCommand.ShuttleReverse,
            KeyChord("K") to EditorCommand.ShuttleStop,
            KeyChord("L") to EditorCommand.ShuttleForward,
            KeyChord("I") to EditorCommand.SetInPointAtPlayhead,
            KeyChord("O") to EditorCommand.SetOutPointAtPlayhead,
            KeyChord("B") to EditorCommand.SplitClipAtPlayhead,
            KeyChord("A") to EditorCommand.ClearTool,
            KeyChord("M") to EditorCommand.AddMarkerAtPlayhead,
            KeyChord("Z", ctrl = true) to EditorCommand.Undo,
            KeyChord("Z", ctrl = true, shift = true) to EditorCommand.Redo,
            KeyChord("S", ctrl = true) to EditorCommand.SaveProject
        )
    }
}
