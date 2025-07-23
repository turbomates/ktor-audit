plugins {
    kotlin("jvm") version "2.1.21"
    alias(deps.plugins.nexus.release)
    alias(deps.plugins.test.logger)
    `maven-publish`
    signing
}

group = "com.turbomates.ktor-audit"
version = System.getenv("RELEASE_VERSION") ?: "0.1.0"

repositories {
    mavenCentral()
}

dependencies {

    implementation(deps.ktor.server.core)
    implementation(deps.kotlinx.coroutines.core)
    implementation(deps.kotlinx.datetime)

    testImplementation(kotlin("test"))
    testImplementation(deps.ktor.server.test.host)
    testImplementation(deps.ktor.server.netty)
    testImplementation(deps.ktor.server.auth)
    testImplementation(deps.ktor.server.double.receive)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
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
            from(components["java"])
            pom {
                packaging = "jar"
                name.set("Ktor Audit extensions")
                url.set("https://github.com/turbomates/ktor-audit")
                description.set("Extensions for Hoplite config library")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/turbomates/hoplite/blob/main/LICENSE")
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
    repositories {
        maven {
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME") ?: project.properties["ossrhUsername"].toString()
                password = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD") ?: project.properties["ossrhPassword"].toString()
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    username = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME") ?: project.properties["ossrhUsername"].toString()
    password = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD") ?: project.properties["ossrhPassword"].toString()
}
