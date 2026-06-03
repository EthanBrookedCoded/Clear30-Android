plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Uncomment once google-services.json is added (see README in app/):
    // alias(libs.plugins.google.services)
    // alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "org.clear30"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.clear30"
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
        buildConfigField("String", "SUPABASE_URL", "\"https://quluipmdicjsolnsopkg.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${props.getProperty("SUPABASE_ANON_KEY", "")}\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${props.getProperty("REVENUECAT_API_KEY", "")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}
