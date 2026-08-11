import java.nio.file.Files
import java.nio.file.LinkOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

// Windowsでは同期・展開されたbuild生成ディレクトリにReadOnly属性が付く場合がある。
// Android Gradle Pluginが前回出力を削除する前に属性を解除し、増分ビルドを安定させる。
val clearReadOnlyBuildOutputs = tasks.register("clearReadOnlyBuildOutputs") {
    group = "build setup"
    description = "Clears Windows read-only attributes from generated build outputs."

    doLast {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            return@doLast
        }

        val outputRoot = layout.buildDirectory.get().asFile
        if (!outputRoot.exists()) return@doLast

        var failures = 0
        outputRoot.walkTopDown().forEach { output ->
            try {
                Files.setAttribute(
                    output.toPath(),
                    "dos:readonly",
                    false,
                    LinkOption.NOFOLLOW_LINKS
                )
            } catch (_: Exception) {
                failures += 1
            }
        }
        if (failures > 0) {
            logger.warn("Could not clear ReadOnly from $failures generated build entries.")
        }
    }
}

tasks.configureEach {
    if (name != clearReadOnlyBuildOutputs.name) {
        dependsOn(clearReadOnlyBuildOutputs)
    }
}

android {
    namespace = "com.example.timerecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.timerecorder"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        compose = true
    }
}

// Room のスキーマを出力
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    // Room 2.7.0+ and Kotlin 2.x work better with room.generateKotlin = true
    arg("room.generateKotlin", "true")
}

dependencies {
    // Compose BOM で統一
    implementation(platform(libs.androidx.compose.bom))

    // 基本
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.runtime.saveable)

    // Material Design
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Room（KSP）
    // Update to 2.7.0-alpha11 to fix "unexpected jvm signature V" with Kotlin 2.x
    val roomVersion = "2.7.0-alpha11"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation(libs.androidx.foundation.layout)
    ksp("androidx.room:room-compiler:$roomVersion")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Compose で Flow/StateFlow を安全に collect
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // テスト
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // デバッグツール
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
}
