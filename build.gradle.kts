plugins {
    kotlin("jvm") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "gallerranking"
version = ""

repositories {
    mavenCentral()
}

dependencies {
    implementation("be.zvz:KotlinInside:1.16.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
    implementation("org.slf4j:slf4j-nop:2.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        manifest {
            attributes (
                "Main-Class" to "com.github.onlaait.gallerranking.MainKt",
                "Multi-Release" to true
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}