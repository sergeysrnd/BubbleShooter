plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.kilocade.bubbleshooter"
version = "1.0.0"

java {
    modularity.inferModulePath.set(true)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjfx:javafx-controls:21.0.2")
    implementation("org.openjfx:javafx-graphics:21.0.2")
    implementation("org.openjfx:javafx-base:21.0.2")
}

application {
    mainModule.set("com.kilocade.bubbleshooter")
    mainClass.set("com.kilocade.bubbleshooter.Main")
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.base", "javafx.controls", "javafx.graphics")
}

tasks.withType<org.gradle.api.tasks.JavaExec>().configureEach {
    modularity.inferModulePath.set(true)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.kilocade.bubbleshooter.Main"
    }
}