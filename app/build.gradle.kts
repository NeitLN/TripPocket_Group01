plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "vn.edu.vlu.trippocket"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vn.edu.vlu.trippocket"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-milestone1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
