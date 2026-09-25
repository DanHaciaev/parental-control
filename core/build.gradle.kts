plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.teo.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.core.ktx)
    api(libs.kotlinx.coroutines.play.services)

    api(libs.stream.webrtc.android)
}
