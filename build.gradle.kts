plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply")
}

group = "se.araisan.meme"
version = "1.0.0-SNAPSHOT"

kotlin {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val hostTarget =
        when {
            hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("host")
            hostOs == "Linux" && (arch == "x86_64" || arch == "amd64") -> linuxX64("host")
            hostOs == "Linux" && arch == "aarch64" -> linuxArm64("host")
            hostOs.startsWith("Windows") -> mingwX64("host")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

    hostTarget.apply {
        binaries {
            executable {
                entryPoint = "se.araisan.meme.main"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(ktorLibs.server.core)
                implementation(ktorLibs.server.cio)
                implementation(ktorLibs.serialization.kotlinx.json)
                implementation(ktorLibs.server.contentNegotiation)
                implementation(ktorLibs.server.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(ktorLibs.server.testHost)
                implementation(ktorLibs.client.core)
                implementation(ktorLibs.client.cio)
                implementation(ktorLibs.client.websockets)
            }
        }
    }
}
