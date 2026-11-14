plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val liveKitTokenEndpoint = providers.gradleProperty("livekitTokenEndpoint")
    .orElse(providers.environmentVariable("LIVEKIT_TOKEN_ENDPOINT"))
    .getOrElse("")
val liveKitDevTokenServerId = providers.gradleProperty("livekitDevTokenServerId")
    .orElse(providers.environmentVariable("LIVEKIT_DEV_TOKEN_SERVER_ID"))
    .getOrElse("")

android {
    namespace = "com.rurnima.purnima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rurnima.purnima"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "LIVEKIT_TOKEN_ENDPOINT", "\"$liveKitTokenEndpoint\"")
        buildConfigField("String", "LIVEKIT_DEV_TOKEN_SERVER_ID", "\"$liveKitDevTokenServerId\"")
        buildConfigField("String", "LIVEKIT_AGENT_NAME", "\"purnima-voice-agent\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.livekit:livekit-android:2.30.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
