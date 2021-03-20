import com.adarshr.gradle.testlogger.theme.ThemeType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.31"
    application
    id("com.adarshr.test-logger") version "2.1.1"
    id("com.diffplug.spotless") version "5.11.0"
}

group = "com.github.arian"
version = "0.0.1"
val mainClassString = "com.github.arian.gikt.GiktKt"

application {
    mainClass.set(mainClassString)
}

tasks {

    fun JavaExec.cmd(args: String) {
        classpath = sourceSets["main"].runtimeClasspath
        main = mainClassString
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
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

spotless {
    kotlin {
        ktlint("0.41.0")
    }
    kotlinGradle {
        ktlint("0.41.0")
    }
}

repositories {
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testImplementation("com.github.marschall:memoryfilesystem:2.1.0")
}
