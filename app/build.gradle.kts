plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.cards"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cards"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    //dataBinding
    implementation(libs.lifecycle.compiler)

    //supabase
    //鏍稿績SDK
    implementation(libs.postgrest.kt.v250)
    implementation(libs.realtime.kt.v250)
    //鍔ㄥ姏寮曟搸(Ktor)
    implementation(libs.ktor.client.android.v2311)
    //搴忓垪鍖?瑙ｆ瀽JSON)
    implementation(libs.kotlinx.serialization.json.v162)
    //鍗忕▼鏀寔
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
