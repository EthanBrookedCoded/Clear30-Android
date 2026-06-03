package org.clear30.data

import kotlinx.coroutines.launch
import org.clear30.data.model.UserInfo

/**
 * TutorialController — ported from `TutorialController` / the per-screen
 * `.tutorial(...)` overlay system in the iOS app.
 *
 * Each tutorial is a short series of [TutorialStep]s scoped to a screen
 * (Today, Profile, Community). When the user enters a screen we call
 * [maybeShow] with the screen key; if it hasn't been completed yet, we
 * enqueue the steps into [PopupManager] as `TutorialStep` payloads and mark
 * the screen completed once the last one is acknowledged.
 *
 * Completion is stored as a comma-separated list in `UserInfo.cache` under
 * [CACHE_KEY] so we don't need a schema migration for the field.
 */
object TutorialController {
    private const val CACHE_KEY = "tutorials_completed"

    /** A single highlight step within a tutorial. */
    data class TutorialStep(val captionKey: String)

    /** Tutorial flows by screen key — extend as new flows ship. */
    private val flows: Map<String, List<TutorialStep>> = mapOf(
        "today" to listOf(
            TutorialStep("Tap a day to check in."),
            TutorialStep("Pull up the calendar for the full month."),
            TutorialStep("Your daily message lives here."),
        ),
        "profile" to listOf(
            TutorialStep("Your streak and stats update with every check-in."),
            TutorialStep("Journal a quick note — text or video."),
            TutorialStep("Earn achievements as you build the habit."),
        ),
        "community" to listOf(
            TutorialStep("Share a win. The community is anonymous by default."),
            TutorialStep("React, comment, or just listen — both count."),
        ),
        "groups" to listOf(
            TutorialStep("Invite up to 5 friends with the code."),
            TutorialStep("The group calendar shows everyone's progress."),
        ),
    )

    /** True iff the user has already completed [screen]'s tutorial. */
    fun isCompleted(userInfo: UserInfo, screen: String): Boolean =
        completed(userInfo).contains(screen)

    /**
     * Enqueue [screen]'s tutorial in [PopupManager] if it hasn't already been
     * completed. Each step is acknowledged through the regular popup
     * dismissal; the *last* one also marks the screen completed and logs the
     * tutorial-completed analytic.
     */
    fun maybeShow(userInfo: UserInfo, screen: String) {
        if (isCompleted(userInfo, screen)) return
        val steps = flows[screen] ?: return
        steps.forEachIndexed { index, step ->
            val isLast = index == steps.lastIndex
            // PopupManager dedupes identical TutorialStep payloads within an
            // 800ms window; injecting the index into screen identifies each
            // step distinctly so all four enqueue in order.
            PopupManager.request(
                PopupManager.Payload.TutorialStep(
                    screen = "$screen (${index + 1}/${steps.size})",
                    captionKey = step.captionKey,
                ),
            )
            if (isLast) markCompleted(userInfo, screen)
        }
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedTutorialScreen,
            mapOf(LogEventExtraDataType.TUTORIAL_SCREEN to screen),
        )
    }

    /** Mark [screen] completed and persist. Idempotent. */
    fun markCompleted(userInfo: UserInfo, screen: String) {
        val current = completed(userInfo)
        if (screen in current) return
        userInfo.cache[CACHE_KEY] = (current + screen).joinToString(",")
        // Persist on a background scope so the call site (a Compose
        // LaunchedEffect) doesn't block on disk I/O.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            Clear30Store.save(userInfo)
        }
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.completedTutorial,
            mapOf(LogEventExtraDataType.TUTORIAL_SCREEN to screen),
        )
    }

    /** Reset (debug / sign-out path). */
    fun reset(userInfo: UserInfo) {
        userInfo.cache.remove(CACHE_KEY)
    }

    private fun completed(userInfo: UserInfo): Set<String> =
        userInfo.cache[CACHE_KEY]?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
}
