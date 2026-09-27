import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// ---- Ad IDs: single source of truth is /admob.properties -------------------
val admob = Properties().apply {
    rootProject.file("admob.properties").inputStream().use { load(it) }
}
fun admobValue(key: String): String =
    requireNotNull(admob.getProperty(key)) { "Missing $key in admob.properties" }.trim()

// ---- Release signing: env vars (CI) or keystore.properties (local, git-ignored)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_FILE", "storeFile")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "com.offerpk.photoresizer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.offerpk.photoresizer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders["admobAppId"] = admobValue("ADMOB_APP_ID")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobValue("ADMOB_BANNER_ID")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobValue("ADMOB_INTERSTITIAL_ID")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${admobValue("ADMOB_REWARDED_ID")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Store screenshots (Robolectric + Roborazzi) render the real Compose UI on the JVM.
// They are slow-ish and need sample images, so they only run when requested:
//   ./gradlew :app:recordRoborazziDebug -Pscreenshots
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    systemProperty("samples.dir", rootProject.file("tools/samples").absolutePath)
    systemProperty("screenshots.out", rootProject.file("store/screenshots/raw").absolutePath)
    if (!project.hasProperty("screenshots")) exclude("**/StoreScreenshots*")
    maxHeapSize = "3g"
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

android {
    lint {
        // compileSdk/targetSdk are pinned to 36 on purpose (store requirement); newer
        // library versions that need compileSdk 37 are intentionally not used yet.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        abortOnError = true
    }
}
