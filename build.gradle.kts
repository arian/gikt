import com.adarshr.gradle.testlogger.theme.ThemeType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.72"
    application
    id("com.adarshr.test-logger") version "2.0.0"
    id("com.diffplug.gradle.spotless") version "3.28.1"
}

group = "com.github.arian"
version = "0.0.1"
val mainClass = "com.github.arian.gikt.GiktKt"

application {
    mainClassName = mainClass
}

tasks {

    fun JavaExec.cmd(args: String) {
        classpath = sourceSets["main"].runtimeClasspath
        main = mainClass
        setArgsString(args)
    }

    register<JavaExec>("status") {
        cmd("status")
    }

    register<JavaExec>("add") {
        cmd("add .")
    }

    register<JavaExec>("commit") {
        dependsOn("check")
        (project.properties["message"] as String?)?.let { standardInput = it.byteInputStream(Charsets.UTF_8) }
        environment("GIT_AUTHOR_NAME", "Arian")
        environment("GIT_AUTHOR_EMAIL", "stolwijk.arian@gmail.com")
        cmd("commit")
    }

    test {
        useJUnitPlatform()
        testlogger {
            theme = ThemeType.MOCHA
        }
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }
}

spotless {
    kotlin {
        ktlint("0.36.0")
    }
    kotlinGradle {
        ktlint("0.36.0")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    testImplementation("com.github.marschall:memoryfilesystem:2.1.0")
}