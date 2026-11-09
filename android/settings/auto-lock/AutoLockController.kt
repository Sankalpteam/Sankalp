package com.indoone.settings.autolock

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.indoone.settings.applock.AppLockStore
import com.indoone.settings.biometric.BiometricAuthenticator
import com.indoone.settings.biometric.BiometricUnlockStore
import com.indoone.settings.unlock.AutoLockUnlockScreen

class AutoLockController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val autoLockStore = AutoLockStore(context)
    private val appLockStore = AppLockStore(context)
    private val biometricStore = BiometricUnlockStore(context)
    private var activity: ComponentActivity? = null
    private var lockOverlay: View? = null
    private var pendingBackgroundDurationMs: Long? = null
    private var watchingInactivity = false
    private val inactivityRunnable = Runnable { showLock() }

    fun onResumed(activity: ComponentActivity, backgroundDurationMs: Long? = null) {
        this.activity = activity
        configureActivitySystemBars(activity)

        val backgroundDuration = backgroundDurationMs ?: pendingBackgroundDurationMs
        pendingBackgroundDurationMs = null
        if (
            backgroundDuration != null &&
            shouldLock() &&
            backgroundDuration >= autoLockStore.minutes() * 60_000L
        ) {
            handler.post {
                if (
                    this.activity === activity &&
                    !activity.isFinishing &&
                    (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed) &&
                    activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    showLock()
                }
            }
        }
    }

    /**
     * The Auto-Lock setting promises "after a period of inactivity", so a timer runs
     * on the screen the user is actually using and resets on every touch or key.
     * Background time is a separate check, made once on resume.
     */
    fun startInactivityWatch() {
        watchingInactivity = true
        armInactivityWatch()
    }

    fun onUserInteraction() {
        if (watchingInactivity) armInactivityWatch()
    }

    fun onPaused() {
        // The activity pauses during normal in-app navigation and when another
        // Sankalp screen is shown. The watch is re-armed on the next resume, and the
        // background-time check covers the app being left.
        watchingInactivity = false
        handler.removeCallbacks(inactivityRunnable)
    }

    fun onDestroyed() {
        watchingInactivity = false
        handler.removeCallbacksAndMessages(null)
        removeLockOverlay()
        activity = null
        pendingBackgroundDurationMs = null
    }

    private fun armInactivityWatch() {
        handler.removeCallbacks(inactivityRunnable)
        if (!watchingInactivity || !shouldLock()) return
        val current = activity ?: return
        if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        handler.postDelayed(inactivityRunnable, autoLockStore.minutes() * 60_000L)
    }

    private fun shouldLock(): Boolean = appLockStore.isEnabled() || biometricStore.isEnabled()

    private fun configureActivitySystemBars(activity: ComponentActivity) {
        val window = activity.window
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * The lock is a view added over the activity, not a dialog. A dialog has its own
     * window, which is not part of the activity's view tree, so Compose cannot find
     * the activity's owners from there and throws "ViewTreeLifecycleOwner not found".
     * Added to the activity's own content, everything Compose needs is already there.
     */
    private fun showLock() {
        val currentActivity = activity ?: return
        if (lockOverlay != null || !shouldLock()) return

        val overlay = ComposeView(currentActivity).apply {
            setContent {
                MaterialTheme {
                    AutoLockUnlockScreen(
                        biometricEnabled = biometricStore.isEnabled(),
                        onUnlockWithPin = { pin ->
                            if (appLockStore.verifyPin(pin)) {
                                dismissLock()
                                true
                            } else false
                        },
                        onUnlockWithBiometric = {
                            val authenticator = BiometricAuthenticator(currentActivity)
                            if (!authenticator.canAuthenticate()) {
                                false
                            } else {
                                authenticator.authenticateForUnlock(
                                    onSuccess = { dismissLock() },
                                    onError = { },
                                )
                                true
                            }
                        },
                    )
                }
            }
        }

        lockOverlay = overlay
        currentActivity.addContentView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun removeLockOverlay() {
        val overlay = lockOverlay
        lockOverlay = null
        if (overlay != null) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
    }

    private fun dismissLock() {
        removeLockOverlay()
        pendingBackgroundDurationMs = null
        armInactivityWatch()
    }
}
