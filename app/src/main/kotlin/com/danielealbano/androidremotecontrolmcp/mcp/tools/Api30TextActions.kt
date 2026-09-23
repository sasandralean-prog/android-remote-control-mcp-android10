package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.annotation.TargetApi
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

@TargetApi(Build.VERSION_CODES.R)
internal object Api30TextActions {
    fun performImeEnter(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
}
