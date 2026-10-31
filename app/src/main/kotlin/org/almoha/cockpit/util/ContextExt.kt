package org.almoha.cockpit.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun Activity.isPinned(): Boolean {
    val manager = getSystemService(ActivityManager::class.java) ?: return false
    return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
}
