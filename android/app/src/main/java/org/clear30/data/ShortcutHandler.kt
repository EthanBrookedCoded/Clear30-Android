package org.clear30.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * ShortcutHandler — ported from `ShortcutHandler` (Data/General/
 * ShortcutHandler.swift). iOS used `UIApplication.shortcutItems` for the long-
 * press app-icon quick actions ("Check in", "Today", "Talk to Claire", "Sign
 * up", "Manage subscription"); Android uses dynamic [ShortcutManagerCompat]
 * shortcuts to the same effect.
 *
 * Each shortcut launches MainActivity with a `clear30://` deep link the existing
 * [URLManager] dispatcher already understands — so adding/removing a quick
 * action here is enough; routing for free.
 */
object ShortcutHandler {

    /** Ids match the iOS quick-action keys so analytics line up. */
    const val ID_CHECK_IN = "check_in"
    const val ID_TODAY = "today"
    const val ID_CLAIRE = "claire"
    const val ID_MANAGE_SUB_STRIPE = "manage_sub_stripe"

    /** Install the default existing-user quick actions. */
    fun installDefaults(context: Context) {
        val shortcuts = listOf(
            shortcut(context, ID_CHECK_IN, "Check in", "clear30://today"),
            shortcut(context, ID_TODAY, "Today", "clear30://today"),
            shortcut(context, ID_CLAIRE, "Talk to Claire", "clear30://chat/claire"),
        )
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /** Add a single quick action without disturbing the others (RC `addQuickAction`). */
    fun addQuickAction(context: Context, id: String, label: String, deepLink: String) {
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(context, id, label, deepLink))
    }

    /** Remove a quick action by id (e.g. once a Stripe sub is cleaned up). */
    fun removeQuickAction(context: Context, id: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id))
    }

    /** Tear everything down (sign-out). */
    fun removeAll(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    private fun shortcut(context: Context, id: String, label: String, deepLink: String): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .setPackage(context.packageName)
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_view))
            .setIntent(intent)
            .build()
    }
}
