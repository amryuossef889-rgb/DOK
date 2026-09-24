package com.dok.editor.history

import com.dok.editor.model.Project
import java.util.ArrayDeque

class UndoRedoManager(
    private val maxHistorySize: Int = 50
) {
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    @Synchronized
    fun pushState(state: Project) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeLast()
        }
        undoStack.push(state)
        redoStack.clear()
    }

    @Synchronized
    fun undo(currentState: Project): Project? {
        if (undoStack.isEmpty()) return null
        redoStack.push(currentState)
        return undoStack.pop()
    }

    @Synchronized
    fun redo(currentState: Project): Project? {
        if (redoStack.isEmpty()) return null
        undoStack.push(currentState)
        return redoStack.pop()
    }

    @Synchronized
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
