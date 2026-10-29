plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.leerumax.huiltje"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.leerumax.huiltje"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.5.1"
        // Alleen arm64: alle gangbare telefoons; scheelt de TFLite-bibliotheken voor drie andere ABI's.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Officiële releases worden ondertekend met een sleutel die niet in de repo staat:
    //   -PhuiltjeKeystore=/pad/huiltje-release.keystore -PhuiltjeWachtwoordBestand=/pad/wachtwoord
    // Zonder die twee krijg je een release met de debug-sleutel: installeerbaar, maar het is dan jouw eigen build
    // (hij kan niet over de officiële app heen updaten en andersom).
    val sleutel = providers.gradleProperty("huiltjeKeystore").orNull
    signingConfigs {
        if (sleutel != null) create("eigen") {
            val wachtwoord = file(providers.gradleProperty("huiltjeWachtwoordBestand").get()).readText().trim()
            storeFile = file(sleutel)
            storePassword = wachtwoord
            keyAlias = "huiltje"
            keyPassword = wachtwoord
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (sleutel != null) "eigen" else "debug")
        }
    }

    buildFeatures { compose = true }
    androidResources { noCompress += "tflite" }
    // Gecomprimeerde .so's: APK ±10 MB kleiner (WebRTC); Android pakt ze bij installatie uit.
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

tasks.withType<Test> { maxHeapSize = "1200m" }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // Babyfoon: WebRTC (BSD), voorgecompileerd door webrtc-sdk.
    implementation("io.github.webrtc-sdk:android:144.7559.15")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Alleen voor tests (niet in de APK): echte SQLite, sockets en schermafdrukken op de JVM.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
