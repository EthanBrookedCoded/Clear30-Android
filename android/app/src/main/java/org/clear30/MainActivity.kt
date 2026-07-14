package org.clear30

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.clear30.views.theme.Clear30Theme
import org.clear30.views.theme.Haptics

/**
 * Single-activity host — ported from `Clear30App` (@main App + WindowGroup) in
 * Clear30App.swift. `WindowGroup { ContentView() }` becomes
 * `setContent { Clear30Theme { AppRoot() } }`.
 *
 * Deep links (`.onOpenURL`) arrive as VIEW intents and are forwarded to
 * [AppState.pendingUrl]; foreground/background transitions (`scenePhase`) are
 * observed via the lifecycle.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLink(intent)
        observeScenePhase()

        setContent {
            Clear30Theme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { AppState.setPendingUrl(it) }
    }

    private fun observeScenePhase() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // scenePhase == .active
                Haptics.prepHaptics()
                // Refresh the long-press quick actions every foreground —
                // cheap (single setDynamicShortcuts) and recovers from a user
                // who side-loaded a previous build that left stale entries.
                runCatching {
                    org.clear30.data.ShortcutHandler.installDefaults(this@MainActivity)
                }
                // Session/openedApp + background flush live in AppRootViewModel
                // (it owns the loaded models); this just relays the transition.
                AppState.setForegrounded(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                // scenePhase == .background
                AppState.setForegrounded(false)
            }
        })
    }
}
