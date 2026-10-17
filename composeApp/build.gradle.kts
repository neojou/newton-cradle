import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.neojou.newtoncradle"
version = "0.2.0"

// kool 0.19.0. JVM target name is `jvm` (source set jvmMain): that is the desktop app.
// Web is Kotlin/JS, not wasmJs — kool 0.19 publishes no wasm artifact.
// Tasks: :composeApp:runJvm (alias :composeApp:run), :composeApp:jsBrowserDevelopmentRun,
// :composeApp:jsBrowserDistribution. `jvmRun` also exists but is the IDE carrier.
kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass.set("com.neojou.newtoncradle.MainKt")
                applicationDefaultJvmArgs = buildList {
                    add("--add-opens=java.base/java.lang=ALL-UNNAMED")
                    add("--enable-native-access=ALL-UNNAMED")
                    if (OperatingSystem.current().isMacOsX) {
                        add("-XstartOnFirstThread")
                    }
                }
            }
        }
    }
    jvmToolchain(25)

    js {
        binaries.executable()
        browser {
            @OptIn(ExperimentalDistributionDsl::class)
            distribution {
                outputDirectory.set(layout.buildDirectory.dir("dist/js"))
            }
            commonWebpackConfig {
                mode = KotlinWebpackConfig.Mode.DEVELOPMENT
            }
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(rootProject.file("shared/src"))
            dependencies {
                implementation(libs.kool.core)
                implementation(libs.kool.physics)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jvmMain by getting
        val jsMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.register("run") {
    group = "application"
    description = "Run the desktop app (same as runJvm)"
    dependsOn("runJvm")
}
