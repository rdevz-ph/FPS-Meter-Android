import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rdevzph.fpsmeter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rdevzph.fpsmeter"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            var keystorePath = System.getenv("KEYSTORE_FILE")
            var keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            var keyAlias = System.getenv("KEY_ALIAS") ?: "fpsmeter"
            var keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword

            val envFile = rootProject.file(".env")
            if (envFile.exists()) {
                val envProps = Properties()
                FileInputStream(envFile).use { envProps.load(it) }
                keystorePath = keystorePath ?: envProps.getProperty("KEYSTORE_FILE")
                keystorePassword = keystorePassword ?: envProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: envProps.getProperty("KEY_ALIAS") ?: "fpsmeter"
                keyPassword = keyPassword ?: envProps.getProperty("KEY_PASSWORD") ?: keystorePassword
            }

            if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { releaseSigning ->
                if (releaseSigning.storeFile != null) {
                    signingConfig = releaseSigning
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("installAppIcon") {
    doLast {
        copy {
            from("../icon/icon.png")
            into("src/main/res/mipmap-xxxhdpi")
            rename { "ic_launcher_foreground.png" }
        }
    }
}
