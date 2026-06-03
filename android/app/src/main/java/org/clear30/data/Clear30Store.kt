package org.clear30.data

import org.clear30.data.model.AchievementData
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.JournalEntries
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.SocialFeed
import org.clear30.data.model.SymptomInfos
import org.clear30.data.model.UserInfo

/**
 * Repository for the persisted singleton models — the Android equivalent of the
 * `Model.getX(context)` / `context.save()` pattern SwiftData used, and of
 * `ContentView.loadStorage()`.
 *
 * Each `load*` reads the single stored instance (creating + patching a default
 * if absent), and `save*` flushes it back. Loaded instances are cached in
 * memory so callers mutate the same object then persist, mirroring SwiftData's
 * mutate-in-place reference semantics.
 *
 * Program / OnboardingSetup are added once the assessment engine is ported
 * (they route through ProgramAssessmentResponse).
 */
object Clear30Store {

    private var cachedUserInfo: UserInfo? = null

    suspend fun loadUserInfo(): UserInfo {
        cachedUserInfo?.let { return it }
        val loaded = (LocalStore.load(UserInfo.STORE_KEY, UserInfo.serializer()) ?: UserInfo())
            .also { it.patch() }
        cachedUserInfo = loaded
        return loaded
    }

    suspend fun save(userInfo: UserInfo) {
        cachedUserInfo = userInfo
        LocalStore.save(UserInfo.STORE_KEY, UserInfo.serializer(), userInfo)
    }

    suspend fun loadJournalEntries(): JournalEntries =
        LocalStore.load(JournalEntries.STORE_KEY, JournalEntries.serializer()) ?: JournalEntries()

    suspend fun save(journalEntries: JournalEntries) =
        LocalStore.save(JournalEntries.STORE_KEY, JournalEntries.serializer(), journalEntries)

    suspend fun loadSocialFeed(): SocialFeed =
        LocalStore.load(SocialFeed.STORE_KEY, SocialFeed.serializer()) ?: SocialFeed()

    suspend fun save(socialFeed: SocialFeed) =
        LocalStore.save(SocialFeed.STORE_KEY, SocialFeed.serializer(), socialFeed)

    suspend fun loadSymptomInfos(): SymptomInfos =
        LocalStore.load(SymptomInfos.STORE_KEY, SymptomInfos.serializer()) ?: SymptomInfos()

    suspend fun save(symptomInfos: SymptomInfos) =
        LocalStore.save(SymptomInfos.STORE_KEY, SymptomInfos.serializer(), symptomInfos)

    suspend fun loadAchievementData(): AchievementData =
        LocalStore.load(AchievementData.STORE_KEY, AchievementData.serializer()) ?: AchievementData()

    suspend fun save(achievementData: AchievementData) =
        LocalStore.save(AchievementData.STORE_KEY, AchievementData.serializer(), achievementData)

    suspend fun loadProgram(): Program =
        LocalStore.load(Program.STORE_KEY, Program.serializer()) ?: Program()

    suspend fun save(program: Program) =
        LocalStore.save(Program.STORE_KEY, Program.serializer(), program)

    suspend fun loadOnboardingSetup(): OnboardingSetup =
        LocalStore.load(OnboardingSetup.STORE_KEY, OnboardingSetup.serializer()) ?: OnboardingSetup()

    suspend fun save(onboardingSetup: OnboardingSetup) =
        LocalStore.save(OnboardingSetup.STORE_KEY, OnboardingSetup.serializer(), onboardingSetup)

    suspend fun loadExperimentController(): ExperimentController =
        LocalStore.load(ExperimentController.STORE_KEY, ExperimentController.serializer())
            ?: ExperimentController()

    suspend fun save(experimentController: ExperimentController) =
        LocalStore.save(ExperimentController.STORE_KEY, ExperimentController.serializer(), experimentController)

    /** Sign-out: delete all SwiftData models (ContentView.signOut). */
    suspend fun wipeAll() {
        cachedUserInfo = null
        LocalStore.clearAll()
    }
}
