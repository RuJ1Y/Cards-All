plugins {
    //去掉library标记
    alias(libs.plugins.kotlin.jvm)

    //标记为app
    application
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
//依赖
dependencies {
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
}
//标记入口文件
application {
    mainClass.set("com.example.server.ApplicationKt")
}
