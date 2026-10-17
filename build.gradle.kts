// Root build — plugins applied by subprojects only.
// :composeApp is the kool 0.19.0 app (desktop JVM + browser JS). There is no root package.json.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
}
