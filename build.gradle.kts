import java.time.Duration

plugins {
    kotlin("jvm") version "2.2.20"
    alias(deps.plugins.nexus.release)
    alias(deps.plugins.test.logger)
    `maven-publish`
    signing

}

group = "com.turbomates"
version = System.getenv("RELEASE_VERSION") ?: "0.1.0"

repositories {
    mavenCentral()
}

dependencies {

    implementation(deps.ktor.server.core)
    implementation(deps.kotlinx.coroutines.core)
    implementation(deps.kotlinx.datetime)
    implementation(deps.ktor.server.auth)

    testImplementation(kotlin("test"))
    testImplementation(deps.ktor.server.test.host)
    testImplementation(deps.ktor.server.netty)
    testImplementation(deps.ktor.server.double.receive)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "ktor-audit"
            groupId = "com.turbomates"
            version = System.getenv("RELEASE_VERSION") ?: "0.1.0"
            from(components["java"])
            pom {
                packaging = "jar"
                name.set("Ktor Audit extensions")
                url.set("https://github.com/turbomates/ktor-audit")
                description.set("Extensions for Hoplite config library")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/turbomates/ktor-audit/blob/main/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:https://github.com/turbomates/ktor-audit.git")
                    developerConnection.set("scm:git@github.com:turbomates/ktor-audit.git")
                    url.set("https://github.com/turbomates/ktor-audit")
                }

                developers {
                    developer {
                        id.set("shustrik")
                        name.set("Vadim Golodko")
                        email.set("vadim.golodko@gmail.com")
                    }
                }
            }
        }
    }
}
nexusPublishing {
    repositories {
        sonatype {
            // Central Portal OSSRH Staging API URLs
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(
                System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME")
                    ?: project.findProperty("centralPortalUsername")?.toString()
            )
            password.set(
                System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD")
                    ?: project.findProperty("centralPortalPassword")?.toString()
            )
        }
    }

    // Настройки тайм-аутов (опционально)
    connectTimeout.set(Duration.ofMinutes(3))
    clientTimeout.set(Duration.ofMinutes(6))

    transitionCheckOptions {
        maxRetries.set(80)
        delayBetween.set(Duration.ofSeconds(10))
    }
}
signing {
    sign(publishing.publications["mavenJava"])
}
