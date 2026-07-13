plugins {
    //去掉library标记
    alias(libs.plugins.kotlin.jvm)
    //允许 Kotlin 的 data class 被自动转换成 JSON
    alias(libs.plugins.kotlin.serialization)

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

    //让 Ktor 知道怎么返回不同格式的数据
    implementation(libs.ktor.server.content.negotiation.jvm)
    //让 Ktor 能把 Kotlin 对象转成 JSON
    implementation(libs.ktor.serialization.kotlinx.json.jvm)
    //后端日志
    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)
}
//标记入口文件
application {
    mainClass.set("com.example.server.ApplicationKt")
}
