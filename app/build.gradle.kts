import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// google-services.json is shared privately and never committed. Without it the app
// still builds and every unit test runs; only Firebase calls fail at runtime.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Release signing material lives outside the repo (keystore.properties is git-ignored).
// A machine without that file — CI, a teammate, a fresh clone — must still build: the release
// APK then stays unsigned instead of failing the build.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
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
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Milestone 4: base URL of the team's MockAPI.io project (Trip Ideas CRUD), from
        // gradle.properties; override it in ~/.gradle/gradle.properties to point at another project.
        val mockApiBaseUrl = providers.gradleProperty("trippocket.mockApiBaseUrl").get()
        buildConfigField("String", "MOCKAPI_BASE_URL", "\"$mockApiBaseUrl\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // M5 spike (18/11): in AGP 9.4.1 `optimization.enable`, `isMinifyEnabled` and both
            // together produce the same APK (4.52 MB) and the same mapping.txt, so the two DSLs
            // drive one pipeline. Keep the explicit pair below: it names exactly what the
            // milestone requires (R8 + resource shrinking) and avoids two switches for one thing.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null when keystore.properties is missing, so the build still succeeds.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // Robolectric needs the merged resources and manifest.
        unitTests.isIncludeAndroidResources = true
        // Robolectric cannot open its jar cache under a home folder with spaces or accents
        // (e.g. C:\Users\Viet Tien), so such machines use a plain ASCII cache folder.
        val home = System.getProperty("user.home")
        if (home.any { it == ' ' || it.code > 127 }) {
            unitTests.all { it.systemProperty("maven.repo.local", "C:/Users/Public/robolectric-m2") }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.glide)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
