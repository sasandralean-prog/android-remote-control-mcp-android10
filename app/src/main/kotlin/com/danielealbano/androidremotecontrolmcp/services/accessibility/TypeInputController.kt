package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.KeyEvent

/**
 * Platform-neutral text editing abstraction used by MCP text tools.
 *
 * On Android 13+ this contract can be implemented with the accessibility input-method
 * bridge. The Android 10 port implements the same semantics with focused
 * [android.view.accessibility.AccessibilityNodeInfo] actions such as ACTION_SET_TEXT
 * and ACTION_SET_SELECTION.
 *
 * **Concurrency**: Text-tool mutations are serialized by `typeOperationMutex` in
 * TextInputTools.kt. Implementations may therefore keep short-lived cursor/selection
 * compatibility state for widgets that expose editable text but reject selection actions.
 *
 * **Return values**: `true` means the controller accepted/dispatched the requested
 * operation. Implementations may emulate an operation when the platform lacks a direct
 * equivalent, but must return `false` when no editable target is available or the text
 * mutation itself could not be performed.
 */
data class InputSurroundingText(
    val text: CharSequence,
    val selectionStart: Int,
    val selectionEnd: Int,
    val offset: Int,
)

interface TypeInputController {
    /**
     * Returns true when an editable accessibility target is focused and ready for text operations.
     */
    fun isReady(): Boolean

    /**
     * Commits a single character or text to the focused text field.
     *
     * @param text The text to commit.
     * @param newCursorPosition Cursor position relative to the committed text.
     *   1 = after the text (most common for typing).
     * @return true if the edit was dispatched/emulated, false if no editable target is available.
     */
    fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
    ): Boolean

    /**
     * Sets the selection/cursor position in the focused text field.
     * If start == end, positions the cursor without selecting.
     *
     * @param start Selection start (0-based character index).
     * @param end Selection end (0-based character index).
     * @return true if the selection was applied or emulated, false if unavailable/invalid.
     */
    fun setSelection(
        start: Int,
        end: Int,
    ): Boolean

    /**
     * Gets the text surrounding the cursor in the focused text field.
     *
     * @param beforeLength Characters to retrieve before cursor.
     * @param afterLength Characters to retrieve after cursor.
     * @param flags Compatibility flags; implementations may ignore unsupported styling flags.
     * @return Current surrounding text snapshot, or null if unavailable.
     */
    fun getSurroundingText(
        beforeLength: Int,
        afterLength: Int,
        flags: Int,
    ): InputSurroundingText?

    /**
     * Performs a context menu action on the focused text field.
     * Used for select-all (android.R.id.selectAll).
     *
     * @param id The context menu action ID (e.g., android.R.id.selectAll).
     * @return true if the action was applied or emulated, false if unavailable/unsupported.
     */
    fun performContextMenuAction(id: Int): Boolean

    /**
     * Sends a key event to the focused text field.
     * Used for DELETE key after selection.
     *
     * @param event The KeyEvent to send.
     * @return true if the key action was applied/emulated, false if unavailable/unsupported.
     */
    fun sendKeyEvent(event: KeyEvent): Boolean

    /**
     * Deletes text surrounding the cursor.
     * Included for future extensibility — not currently used by any tool.
     *
     * @param beforeLength Characters to delete before cursor.
     * @param afterLength Characters to delete after cursor.
     * @return true if the deletion was applied/emulated, false if unavailable.
     */
    fun deleteSurroundingText(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean
}
