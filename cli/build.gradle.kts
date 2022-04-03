import com.akuleshov7.dt.buildutils.configureDetekt
import com.akuleshov7.dt.buildutils.configureDiktat

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    val os = getCurrentOperatingSystem()
    val saveTarget = listOf(when {
        os.isWindows -> mingwX64()
        os.isLinux -> linuxX64()
        os.isMacOsX -> macosX64()
        else -> throw GradleException("Unknown operating system $os")
    })

    configure(saveTarget) {
        binaries {
            val name = "save-${project.version}-${this@configure.name}"
            executable {
                this.baseName = name
                entryPoint = "com.akuleshov7.cli.main"
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val jvmMain by getting

        val commonMain by getting
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(projects.core)
                implementation(projects.common)
                implementation(libs.kotlinx.serialization.properties)
            }
        }
        saveTarget.forEach {
            getByName("${it.name}Main").dependsOn(nativeMain)
        }

        val commonTest by getting

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.engine)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }

    val linkReleaseExecutableTaskProvider = when {
        os.isLinux -> tasks.getByName("linkReleaseExecutableLinuxX64")
        os.isWindows -> tasks.getByName("linkReleaseExecutableMingwX64")
        os.isMacOsX -> tasks.getByName("linkReleaseExecutableMacosX64")
        else -> throw GradleException("Unknown operating system $os")
    }
    project.tasks.register("linkReleaseExecutableMultiplatform") {
        dependsOn(linkReleaseExecutableTaskProvider)
    }

    // Integration test should be able to have access to binary during the execution. Also we use here the debug version,
    // in aim to have ability to run it in CI, which operates only with debug versions
    tasks.getByName("jvmTest").dependsOn(tasks.getByName(
        when {
            os.isLinux -> "linkDebugExecutableLinuxX64"
            os.isWindows -> "linkDebugExecutableMingwX64"
            os.isMacOsX -> "linkDebugExecutableMacosX64"
            else -> throw GradleException("Unknown operating system $os")
        }
    ))

    // disable building of some binaries to speed up build
    // possible values: `all` - build all binaries, `debug` - build only debug binaries
    val enabledExecutables = if (hasProperty("enabledExecutables")) property("enabledExecutables") as String else null
    if (enabledExecutables != null && enabledExecutables != "all" || enabledExecutables == "debug") {
        linkReleaseExecutableTaskProvider.enabled = false
    }
}

configureDiktat()
configureDetekt()

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}
