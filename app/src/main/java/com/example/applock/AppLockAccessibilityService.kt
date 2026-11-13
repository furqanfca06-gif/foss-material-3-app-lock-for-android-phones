package com.example.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {
    private var lastForegroundPackage: String? = null
    private var launchPending = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences(AppSecurity.PREFS, MODE_PRIVATE)
        val ownPackage = packageName == this.packageName

        val unlockedPackage = prefs.getString(AppSecurity.UNLOCKED_PACKAGE, null)
        if (!ownPackage && packageName != lastForegroundPackage && packageName != unlockedPackage) {
            prefs.edit().remove(AppSecurity.UNLOCKED_PACKAGE).apply()
        }
        lastForegroundPackage = packageName
        if (ownPackage) {
            launchPending = false
            return
        }
        if (launchPending) return

        val protected = prefs.getStringSet(AppSecurity.LOCKED_PACKAGES, emptySet()).orEmpty()
        if (packageName !in protected) return
        if (prefs.getString(AppSecurity.UNLOCKED_PACKAGE, null) == packageName) return

        launchPending = true
        runCatching {
            startActivity(Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(LockActivity.EXTRA_PACKAGE, packageName)
            })
        }.onFailure { launchPending = false }
    }

    override fun onInterrupt() = Unit
}
