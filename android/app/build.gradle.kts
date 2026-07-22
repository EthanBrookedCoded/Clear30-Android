plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "org.clear30"
    compileSdk = 35

    defaultConfig {
        // Must stay "org.clear30.Clear30v1": the new app ships as an UPDATE to the
        // old RN app's Play listing (same package = old users auto-update + access
        // to the old app's on-device data for migration). Play identity is immutable.
        applicationId = "org.clear30.Clear30v1"
        minSdk = 26
        targetSdk = 35
        versionCode = 21601
        versionName = "2.16.1"
        vectorDrawables { useSupportLibrary = true }

        // Secrets (iOS used ConfidentialKit/confidential.yml). Put real values in
        // local.properties (git-ignored): SUPABASE_ANON_KEY=..., REVENUECAT_API_KEY=...
        val props = org.jetbrains.kotlin.konan.properties.Properties().apply {
            val f = rootProject.file("local.properties"); if (f.exists()) f.inputStream().use { load(it) }
        }
        // Supabase env switch. Defaults to LOCAL for dev. Flip to prod by adding
        // `SUPABASE_LOCAL=false` to local.properties (no rebuild config needed
        // beyond a Gradle sync). See CLAUDE.md "Local vs prod Supabase".
        val useLocalSupabase = props.getProperty("SUPABASE_LOCAL", "true").toBoolean()

        // Local Supabase (supabase CLI). `10.0.2.2` is the host-machine loopback as
        // seen from the Android EMULATOR (127.0.0.1 there = the emulator itself).
        // On a physical device set SUPABASE_LOCAL_URL to your machine's LAN IP,
        // e.g. http://192.168.1.50:54321 . The key is the CLI publishable key.
        val localSupabaseUrl = props.getProperty("SUPABASE_LOCAL_URL", "http://10.0.2.2:54321")
        // Legacy local anon JWT (role=anon) from `supabase status` — the standard
        // CLI demo key. Preferred over the new `sb_publishable_…` key for
        // supabase-kt 3.0.3 (Auth treats the Bearer token as a JWT).
        val localSupabaseKey = props.getProperty(
            "SUPABASE_LOCAL_ANON_KEY",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0",
        )

        val prodSupabaseUrl = "https://quluipmdicjsolnsopkg.supabase.co"
        val prodSupabaseKey = props.getProperty("SUPABASE_ANON_KEY", "")

        buildConfigField("boolean", "SUPABASE_LOCAL", "$useLocalSupabase")
        buildConfigField("String", "SUPABASE_URL", "\"${if (useLocalSupabase) localSupabaseUrl else prodSupabaseUrl}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${if (useLocalSupabase) localSupabaseKey else prodSupabaseKey}\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${props.getProperty("REVENUECAT_API_KEY", "")}\"")
        // Helium paywall SDK key (app.tryhelium.com → Profile). Blank = Helium
        // disabled → PaywallController falls back to the native RevenueCat paywall,
        // exactly like a blank REVENUECAT_API_KEY no-ops purchases.
        buildConfigField("String", "HELIUM_API_KEY", "\"${props.getProperty("HELIUM_API_KEY", "")}\"")
    }

    signingConfigs {
        create("release") {
            // Upload key (EAS-managed for the live app). Keystore lives at
            // app/upload-keystore.jks (git-ignored); passwords in local.properties
            // (git-ignored). Absent on machines/CI without the secrets — release
            // builds there fail with a clear "keystore not set", which is intended.
            val ksProps = org.jetbrains.kotlin.konan.properties.Properties().apply {
                val f = rootProject.file("local.properties"); if (f.exists()) f.inputStream().use { load(it) }
            }
            val ksFile = rootProject.file("app/upload-keystore.jks")
            if (ksFile.exists() && ksProps.getProperty("RELEASE_STORE_PASSWORD") != null) {
                storeFile = ksFile
                storePassword = ksProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = ksProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = ksProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Local persistence (SwiftData replacement)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Home-screen widgets (WidgetKit -> Glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Local notification scheduling (iOS UNUserNotificationCenter -> WorkManager)
    implementation(libs.androidx.work.runtime.ktx)

    // Media / images (AsyncImage, video players)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    // YouTube embeds — the maintained community player (iOS YouTubePlayerKit
    // equivalent). Handles the IFrame origin + WebView video-surface quirks that
    // a hand-rolled WebView embed hits (error 152 / black video).
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Serialization / dates / coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // RevenueCat
    implementation(libs.revenuecat)

    // Helium paywall SDK (remote/A-B-tested paywalls) + its RevenueCat purchase
    // bridge — iOS uses Helium + HeliumRevenueCat the same way.
    implementation(libs.helium.core)
    implementation(libs.helium.revenuecat)

    // Play In-App Review (iOS requestReview parity — O9)
    implementation(libs.play.review)
}
