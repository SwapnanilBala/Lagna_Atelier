/*
 * The web OAuth client id the app asks Google to mint sign-in tokens for
 * (Credential Manager's serverClientId; SIGN_IN.md 2.2). It is public, not a
 * secret, but it is per-deployment, so it is not written here: set
 * `lagna.googleWebClientId` in ~/.gradle/gradle.properties, pass
 * -Plagna.googleWebClientId=..., or set LAGNA_GOOGLE_WEB_CLIENT_ID. Without
 * it the app builds and its Account tab says sign-in is not set up yet.
 */
val googleWebClientId: String = providers.gradleProperty("lagna.googleWebClientId")
    .orElse(providers.environmentVariable("LAGNA_GOOGLE_WEB_CLIENT_ID"))
    .orElse("")
    .get()
    .trim()
    .also { id ->
        require(id.isEmpty() || Regex("""[0-9A-Za-z-]+\.apps\.googleusercontent\.com""").matches(id)) {
            "lagna.googleWebClientId must be a web client id ending in .apps.googleusercontent.com"
        }
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "site.lagnaatelier.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "site.lagnaatelier.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}