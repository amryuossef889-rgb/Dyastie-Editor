package com.example.dyastie.project

import com.example.dyastie.model.Project
import java.util.Stack

class UndoRedoManager(private val maxHistory: Int = 40) {
    private val undoStack = Stack<Project>()
    private val redoStack = Stack<Project>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushState(project: Project) {
        if (undoStack.isNotEmpty() && undoStack.peek() == project) return
        undoStack.push(project)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(currentProject: Project): Project? {
        if (undoStack.isEmpty()) return null
        redoStack.push(currentProject)
        return undoStack.pop()
    }

    fun redo(currentProject: Project): Project? {
        if (redoStack.isEmpty()) return null
        undoStack.push(currentProject)
        return redoStack.pop()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
