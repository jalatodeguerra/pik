package com.pik.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pik.app.lock.AppLock
import com.pik.app.lock.LockHost
import com.pik.app.ui.nav.PikRoot
import com.pik.app.ui.theme.PikTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A FragmentActivity (a ComponentActivity subclass) because BiometricPrompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLock

    /** Incremented each time the "Add expense" app shortcut is used. */
    private var quickAddToken by mutableIntStateOf(0)

    /** Incremented each time a reminder notification is tapped. */
    private var openRecurringToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the same launch intent is still attached: only handle a fresh launch.
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // While the lock is on, hide the screens from Recent apps and screenshots.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLock.enabled.collect { enabled ->
                    if (enabled == true) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else if (enabled == false) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        setContent {
            PikTheme {
                LockHost(appLock) {
                    PikRoot(quickAddToken = quickAddToken, openRecurringToken = openRecurringToken)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForegrounded(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        appLock.onBackgrounded(SystemClock.elapsedRealtime())
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_EXPENSE -> quickAddToken++
            ACTION_OPEN_RECURRING -> openRecurringToken++
        }
    }

    companion object {
        /** Must match the intent action in res/xml/shortcuts.xml. */
        const val ACTION_ADD_EXPENSE = "com.pik.app.action.ADD_EXPENSE"

        /** Set on the intent of the reminder notification (see ReminderWorker). */
        const val ACTION_OPEN_RECURRING = "com.pik.app.action.OPEN_RECURRING"
    }
}
