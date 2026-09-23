package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject

/**
 * API 29-compatible text controller backed by accessibility node actions.
 *
 * Android 13 added AccessibilityService's InputMethod bridge. On older Android
 * releases we preserve the MCP text-tool contract by editing the focused
 * accessibility node with ACTION_SET_TEXT. Because some Android 10 widgets reject
 * ACTION_SET_SELECTION, a compatibility cursor is retained for insert/replace/delete
 * semantics while platform selection synchronization remains best-effort.
 */
class TypeInputControllerImpl
    @Inject
    constructor(
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
    ) : TypeInputController {
        private var selectionNodeKey: String? = null
        private var compatSelectionStart: Int? = null
        private var compatSelectionEnd: Int? = null

        override fun isReady(): Boolean =
            withFocusedEditableNode { node ->
                rememberPlatformSelection(node)
                true
            } ?: false

        override fun commitText(
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val current = editableText(node)
                val (start, end) = normalizedSelection(node, current.length)
                val updated = current.replaceRange(start, end, text)
                if (!setNodeText(node, updated)) return@withFocusedEditableNode false
                val cursor =
                    if (newCursorPosition > 0) {
                        (start + text.length + newCursorPosition - 1).coerceIn(0, updated.length)
                    } else {
                        (start + newCursorPosition).coerceIn(0, updated.length)
                    }
                rememberSelection(cursor, cursor)
                // ACTION_SET_TEXT generally moves the platform cursor to the end. Some API 29
                // widgets (notably Google Keep) reject ACTION_SET_SELECTION even though text
                // replacement itself works, so selection synchronization is best-effort.
                setNodeSelection(node, cursor, cursor)
                true
            } ?: false

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val length = editableText(node).length
                if (start !in 0..length || end !in 0..length) {
                    false
                } else {
                    rememberSelection(start, end)
                    if (!setNodeSelection(node, start, end)) {
                        Log.d(TAG, "Platform rejected ACTION_SET_SELECTION; using API 29 compatibility cursor")
                    }
                    true
                }
            } ?: false

        override fun getSurroundingText(
            beforeLength: Int,
            afterLength: Int,
            flags: Int,
        ): InputSurroundingText? =
            withFocusedEditableNode { node ->
                val current = editableText(node)
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
                val length = editableText(node).length
                rememberSelection(0, length)
                setNodeSelection(node, 0, length)
                true
            } ?: false

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_DEL) return false
            if (event.action == KeyEvent.ACTION_UP) return true
            if (event.action != KeyEvent.ACTION_DOWN) return false
            return withFocusedEditableNode { node ->
                val current = editableText(node)
                val (start, end) = normalizedSelection(node, current.length)
                val deleteStart =
                    if (start != end) {
                        start
                    } else if (start > 0) {
                        Character.offsetByCodePoints(current, start, -1)
                    } else {
                        0
                    }
                val deleteEnd = if (start != end) end else start
                if (deleteStart == deleteEnd) return@withFocusedEditableNode true
                val updated = current.removeRange(deleteStart, deleteEnd)
                if (!setNodeText(node, updated)) return@withFocusedEditableNode false
                rememberSelection(deleteStart, deleteStart)
                setNodeSelection(node, deleteStart, deleteStart)
                true
            } ?: false
        }

        override fun deleteSurroundingText(
            beforeLength: Int,
            afterLength: Int,
        ): Boolean =
            withFocusedEditableNode { node ->
                val current = editableText(node)
                val (start, end) = normalizedSelection(node, current.length)
                val deleteStart = (start - beforeLength.coerceAtLeast(0)).coerceAtLeast(0)
                val deleteEnd = (end + afterLength.coerceAtLeast(0)).coerceAtMost(current.length)
                val updated = current.removeRange(deleteStart, deleteEnd)
                if (!setNodeText(node, updated)) return@withFocusedEditableNode false
                rememberSelection(deleteStart, deleteStart)
                setNodeSelection(node, deleteStart, deleteStart)
                true
            } ?: false

        private fun <T> withFocusedEditableNode(block: (AccessibilityNodeInfo) -> T): T? {
            val root = accessibilityServiceProvider.getRootNode() ?: return null
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.takeIf { it.isFocused }
            if (focused == null || !focused.isEditable) {
                if (focused != null && focused !== root) recycle(focused)
                recycle(root)
                return null
            }
            return try {
                // API 29 keeps a framework-side AccessibilityNodeInfo cache and does not expose
                // AccessibilityService.clearCache(). Refresh the focused node before every read/
                // mutation so per-character verification observes ACTION_SET_TEXT immediately.
                @Suppress("DEPRECATION")
                focused.refresh()
                ensureSelectionTarget(focused)
                block(focused)
            } finally {
                if (focused !== root) recycle(focused)
                recycle(root)
            }
        }

        private fun normalizedSelection(node: AccessibilityNodeInfo, textLength: Int): Pair<Int, Int> {
            val compatStart = compatSelectionStart
            val compatEnd = compatSelectionEnd
            if (compatStart != null && compatEnd != null) {
                val start = compatStart.coerceIn(0, textLength)
                val end = compatEnd.coerceIn(0, textLength)
                return minOf(start, end) to maxOf(start, end)
            }
            return platformSelection(node, textLength)
        }

        private fun platformSelection(node: AccessibilityNodeInfo, textLength: Int): Pair<Int, Int> {
            val rawStart = node.textSelectionStart
            val rawEnd = node.textSelectionEnd
            val start = if (rawStart < 0) textLength else rawStart.coerceIn(0, textLength)
            val end = if (rawEnd < 0) start else rawEnd.coerceIn(0, textLength)
            return minOf(start, end) to maxOf(start, end)
        }

        private fun rememberPlatformSelection(node: AccessibilityNodeInfo) {
            val textLength = editableText(node).length
            val (start, end) = platformSelection(node, textLength)
            rememberSelection(start, end)
        }

        private fun rememberSelection(start: Int, end: Int) {
            compatSelectionStart = start
            compatSelectionEnd = end
        }

        private fun ensureSelectionTarget(node: AccessibilityNodeInfo) {
            val key = nodeKey(node)
            if (selectionNodeKey != key) {
                selectionNodeKey = key
                compatSelectionStart = null
                compatSelectionEnd = null
            }
        }

        private fun nodeKey(node: AccessibilityNodeInfo): String {
            val stableId = node.viewIdResourceName
            if (!stableId.isNullOrBlank()) {
                return "${node.windowId}|${node.packageName}|$stableId"
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            return "${node.windowId}|${node.packageName}|${node.className}|" +
                "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }

        private fun editableText(node: AccessibilityNodeInfo): String =
            if (node.isShowingHintText) "" else node.text?.toString().orEmpty()

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

        companion object {
            private const val TAG = "MCP:TypeInputController"
        }
    }
