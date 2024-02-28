plugins {
    id("com.android.application")
}

android {
    namespace = "com.nulllab.ble.coding.central"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nulllab.ble.coding.central"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        outputs.all {
            when (this) {
                is com.android.build.gradle.internal.api.ApkVariantOutputImpl -> {
                    if (name.endsWith("debug")) {
                        outputFileName =
                            "arduino_ble_flash_demo-debug-v" + defaultConfig.versionName + ".apk"
                    }
                    if (name.endsWith("release")) {
                        outputFileName =
                            "arduino_ble_flash_demo-release-v" + defaultConfig.versionName + ".apk"
                    }
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}