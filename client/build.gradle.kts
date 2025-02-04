import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-network:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
//    implementation(libs.androidx.lifecycle.viewmodel)
//    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(compose.desktop.currentOs)
//    implementation(libs.kotlinx.coroutines.swing)

    implementation(project(":common"))
    implementation(project(":server"))
}

compose.desktop {
    application {
        mainClass = "client.ClientMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
        }
    }
}

kotlin {
    jvmToolchain(21)
}