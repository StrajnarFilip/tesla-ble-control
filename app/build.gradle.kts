plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.teslablecontrol"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.teslablecontrol"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release builds are signed with a private key that never enters the repository; its location
    // and passwords come from Gradle properties such as ~/.gradle/gradle.properties (see README).
    // Without them, assembleRelease builds an unsigned APK. Debug builds use the machine's own
    // debug key, as usual.
    val releaseStore = providers.gradleProperty("teslaBle.releaseStoreFile")
    signingConfigs {
        if (releaseStore.isPresent) {
            create("release") {
                storeFile = file(releaseStore.get())
                storePassword = providers.gradleProperty("teslaBle.releaseStorePassword").get()
                keyAlias = providers.gradleProperty("teslaBle.releaseKeyAlias").get()
                keyPassword = providers.gradleProperty("teslaBle.releaseKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Emulator testing against tools/vehicle-sim (see src/sim). The only variant with INTERNET,
        // which the simulator's socket needs; installs beside the real app.
        create("sim") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sim"
            versionNameSuffix = "-sim"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // The sim build's simulator link (DebugLinks) reuses the protocol tests' TCP transport.
    "simImplementation"(testFixtures(project(":protocol")))
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
