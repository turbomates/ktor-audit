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
            version("nexus_staging", "0.30.0")
            version("kotlinx-datetime", "0.5.0")

            library("ktor_server_core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("ktor_server_test_host", "io.ktor", "ktor-server-test-host").versionRef("ktor")
            library("ktor_server_netty", "io.ktor", "ktor-server-netty").versionRef("ktor")
            library("ktor_server_auth", "io.ktor", "ktor-server-auth").versionRef("ktor")
            library("ktor_server_double_receive", "io.ktor", "ktor-server-double-receive").versionRef("ktor")
            library(
                "kotlinx_coroutines_core",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-core"
            ).versionRef("kotlinx-coroutines-core")
            library("kotlinx_datetime", "org.jetbrains.kotlinx", "kotlinx-datetime").versionRef("kotlinx-datetime")

            plugin("test_logger", "com.adarshr.test-logger").versionRef("test_logger")
            plugin("detekt", "io.gitlab.arturbosch.detekt").versionRef("detekt")
            plugin("nexus_release", "io.codearte.nexus-staging").versionRef("nexus_staging")
        }
    }
}
