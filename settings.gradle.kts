plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "ktor-audit"
dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("ktor", "2.3.11")
            version("kotlinx-coroutines-core", "1.7.3")
            version("detekt", "1.23.6")
            version("kotlin", "2.1.2")
            version("test_logger", "3.0.0")
            version("nexus_staging", "2.0.0")
            version("kotlinx-datetime", "0.7.1")

            library("ktor.server.core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("ktor.server.test.host", "io.ktor", "ktor-server-test-host").versionRef("ktor")
            library("ktor.server.netty", "io.ktor", "ktor-server-netty").versionRef("ktor")
            library("ktor.server.auth", "io.ktor", "ktor-server-auth").versionRef("ktor")
            library("ktor.server.double.receive", "io.ktor", "ktor-server-double-receive").versionRef("ktor")
            library("kotlinx.coroutines.core", "org.jetbrains.kotlinx", "kotlinx-coroutines-core").versionRef("kotlinx-coroutines-core")
            library("kotlinx.datetime", "org.jetbrains.kotlinx", "kotlinx-datetime").versionRef("kotlinx-datetime")

            plugin("test.logger", "com.adarshr.test-logger").versionRef("test_logger")
            plugin("detekt", "io.gitlab.arturbosch.detekt").versionRef("detekt")
            plugin("nexus.release", "io.github.gradle-nexus.publish-plugin").versionRef("nexus_staging")
        }
    }
}
