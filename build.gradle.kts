plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"

    `java-library`
    `maven-publish`
}

group = "dev.emortal.minestom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven("https://repo.emortal.dev/snapshots")
    maven("https://repo.emortal.dev/releases")
    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    compileOnly("dev.emortal.minestom:core:7c5e374") { // should be provided by any user
        exclude(group = "dev.emortal.api", module = "common-proto-sdk")
    }
    compileOnly("dev.emortal.api:common-proto-sdk:e7bee22")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        mergeServiceFiles()
    }
}

publishing {
    repositories {
        maven {
            name = "development"
            url = uri("https://repo.emortal.dev/snapshots")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
        maven {
            name = "release"
            url = uri("https://repo.emortal.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_SECRET")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.emortal.minestom"
            artifactId = "game-sdk"

            val commitHash = System.getenv("COMMIT_HASH_SHORT")
            val releaseVersion = System.getenv("RELEASE_VERSION")
            version = commitHash ?: releaseVersion ?: "local"

            from(components["java"])
        }
    }
}