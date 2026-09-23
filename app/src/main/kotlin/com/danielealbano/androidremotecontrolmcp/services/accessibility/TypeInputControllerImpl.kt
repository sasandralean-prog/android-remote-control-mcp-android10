package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject

/**
 * API 29-compatible text controller backed by accessibility node actions.
 *
 * Android 13 added AccessibilityService's InputMethod bridge. On older Android
 * releases we preserve the MCP text-tool contract by editing the focused
 * accessibility node with ACTION_SET_TEXT and ACTION_SET_SELECTION.
 */
class TypeInputControllerImpl
    @Inject
    constructor() : TypeInputController {
        override fun isReady(): Boolean =
            withFocusedEditableNode { true } ?: false

        override fun commitText(
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val current = node.text?.toString().orEmpty()
                val (start, end) = normalizedSelection(node, current.length)
                val updated = current.replaceRange(start, end, text)
                if (!setNodeText(node, updated)) return@withFocusedEditableNode false
                val cursor =
                    if (newCursorPosition > 0) {
                        (start + text.length + newCursorPosition - 1).coerceIn(0, updated.length)
                    } else {
                        (start + newCursorPosition).coerceIn(0, updated.length)
                    }
                setNodeSelection(node, cursor, cursor)
            } ?: false

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val length = node.text?.length ?: 0
                if (start !in 0..length || end !in 0..length) {
                    false
                } else {
                    setNodeSelection(node, start, end)
                }
            } ?: false

        override fun getSurroundingText(
            beforeLength: Int,
            afterLength: Int,
            flags: Int,
        ): InputSurroundingText? =
            withFocusedEditableNode { node ->
                val current = node.text?.toString().orEmpty()
                val (selectionStart, selectionEnd) = normalizedSelection(node, current.length)
                val cursor = selectionEnd
                val sliceStart = (cursor - beforeLength.coerceAtLeast(0)).coerceAtLeast(0)
                val sliceEnd = (cursor + afterLength.coerceAtLeast(0)).coerceAtMost(current.length)
                InputSurroundingText(
                    text = current.substring(sliceStart, sliceEnd),
                    selectionStart = (selectionStart - sliceStart).coerceIn(0, sliceEnd - sliceStart),
                    selectionEnd = (selectionEnd - sliceStart).coerceIn(0, sliceEnd - sliceStart),
                    offset = sliceStart,
                )
            }

        override fun performContextMenuAction(id: Int): Boolean =
            withFocusedEditableNode { node ->
                if (id != android.R.id.selectAll) return@withFocusedEditableNode false
                val length = node.text?.length ?: 0
                setNodeSelection(node, 0, length)
            } ?: false

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_DEL) return false
            if (event.action == KeyEvent.ACTION_UP) return true
            if (event.action != KeyEvent.ACTION_DOWN) return false
            return withFocusedEditableNode { node ->
                val current = node.text?.toString().orEmpty()
                val (start, end) = normalizedSelection(node, current.length)
                val deleteStart = if (start != end) start else (start - 1).coerceAtLeast(0)
                val deleteEnd = if (start != end) end else start
                if (deleteStart == deleteEnd) return@withFocusedEditableNode true
                val updated = current.removeRange(deleteStart, deleteEnd)
                setNodeText(node, updated) && setNodeSelection(node, deleteStart, deleteStart)
            } ?: false
        }

        override fun deleteSurroundingText(
            beforeLength: Int,
            afterLength: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val current = node.text?.toString().orEmpty()
                val (start, end) = normalizedSelection(node, current.length)
                val deleteStart = (start - beforeLength.coerceAtLeast(0)).coerceAtLeast(0)
                val deleteEnd = (end + afterLength.coerceAtLeast(0)).coerceAtMost(current.length)
                val updated = current.removeRange(deleteStart, deleteEnd)
                setNodeText(node, updated) && setNodeSelection(node, deleteStart, deleteStart)
            } ?: false

        private fun <T> withFocusedEditableNode(block: (AccessibilityNodeInfo) -> T): T? {
            val root = McpAccessibilityService.instance?.getRootNode() ?: return null
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.takeIf { it.isFocused }
            if (focused == null || !focused.isEditable) {
                if (focused != null && focused !== root) recycle(focused)
                recycle(root)
                return null
            }
            return try {
                block(focused)
            } finally {
                if (focused !== root) recycle(focused)
                recycle(root)
            }
        }

        private fun normalizedSelection(node: AccessibilityNodeInfo, textLength: Int): Pair<Int, Int> {
            val rawStart = node.textSelectionStart
            val rawEnd = node.textSelectionEnd
            val start = if (rawStart < 0) textLength else rawStart.coerceIn(0, textLength)
            val end = if (rawEnd < 0) start else rawEnd.coerceIn(0, textLength)
            return minOf(start, end) to maxOf(start, end)
        }

        private fun setNodeText(node: AccessibilityNodeInfo, text: CharSequence): Boolean {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        private fun setNodeSelection(
            node: AccessibilityNodeInfo,
            start: Int,
            end: Int,
        ): Boolean {
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        }

        @Suppress("DEPRECATION")
        private fun recycle(node: AccessibilityNodeInfo) {
            node.recycle()
        }
    }
