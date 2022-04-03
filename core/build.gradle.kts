
import com.akuleshov7.dt.generation.configFilePath
import com.akuleshov7.dt.generation.generateConfigOptions

import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    id("com.akuleshov7.dt.buildutils.kotlin-library")
    id("de.undercouch.download")
}

kotlin {
    sourceSets {
        val commonNonJsMain by getting {
            dependencies {
                api(libs.okio)
                implementation(projects.common)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.ktoml.core)
                implementation(libs.ktoml.file)
            }
        }
        val commonNonJsTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}



