package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TypeInputControllerImplTest {
    private lateinit var provider: AccessibilityServiceProvider
    private lateinit var root: AccessibilityNodeInfo
    private lateinit var node: AccessibilityNodeInfo
    private lateinit var controller: TypeInputControllerImpl

    @BeforeEach
    fun setUp() {
        provider = mockk()
        root = mockk(relaxed = true)
        node = mockk(relaxed = true)
        every { provider.getRootNode() } returns root
        every { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns node
        every { node.isEditable } returns true
        every { node.isFocused } returns true
        every { node.viewIdResourceName } returns "test.pkg:id/editor"
        every { node.windowId } returns 7
        every { node.packageName } returns "test.pkg"
        every { node.className } returns "android.widget.EditText"
        every { node.textSelectionStart } returns -1
        every { node.textSelectionEnd } returns -1
        @Suppress("DEPRECATION")
        every { root.recycle() } returns Unit
        @Suppress("DEPRECATION")
        every { node.recycle() } returns Unit
        controller = TypeInputControllerImpl(provider)
    }

    @Test
    fun `hint text is not treated as editable content`() {
        every { node.isShowingHintText } returns true
        every { node.text } returns "Nota"

        assertTrue(controller.isReady())
        val surrounding = controller.getSurroundingText(100, 100, 0)

        assertEquals("", surrounding?.text?.toString())
        assertEquals(0, surrounding?.selectionStart)
        assertEquals(0, surrounding?.selectionEnd)
    }

    @Test
    fun `selection falls back to compatibility cursor when platform rejects action`() {
        every { node.isShowingHintText } returns false
        every { node.text } returns "abc"
        every {
            node.performAction(eq(AccessibilityNodeInfo.ACTION_SET_SELECTION), any())
        } returns false

        assertTrue(controller.isReady())
        assertTrue(controller.setSelection(1, 1))

        val surrounding = controller.getSurroundingText(100, 100, 0)
        assertEquals("abc", surrounding?.text?.toString())
        assertEquals(1, surrounding?.selectionStart)
        assertEquals(1, surrounding?.selectionEnd)
    }

    @Test
    fun `commit succeeds when set text works but platform selection is unsupported`() {
        every { node.isShowingHintText } returns true
        every { node.text } returns "Nota"
        every {
            node.performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any())
        } returns true
        every {
            node.performAction(eq(AccessibilityNodeInfo.ACTION_SET_SELECTION), any())
        } returns false

        assertTrue(controller.isReady())
        assertTrue(controller.setSelection(0, 0))
        assertTrue(controller.commitText("H", 1))

        verify(exactly = 1) {
            node.performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any())
        }
    }
}
