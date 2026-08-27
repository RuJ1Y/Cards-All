plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    //允许 Kotlin 的 data class 被自动转换成 JSON
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.cards.community"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cards.community"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
    //让协程跟随 Activity 生命周期运行
    implementation(libs.androidx.lifecycle.runtime.ktx)

    //Ktor Client 的 Android 网络引擎
    implementation(libs.ktor.client.android.v2311)
    //让 Ktor Client 知道怎么处理不同格式的数据
    implementation(libs.ktor.client.content.negotiation.v2311)
    //让 Ktor Client 能在 JSON 和 Kotlin 对象之间转换
    implementation(libs.ktor.serialization.kotlinx.json.jvm)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
