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


application {
    mainModule.set("com.kilocade.bubbleshooter")
    mainClass.set("com.kilocade.bubbleshooter.Main")
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.base")
}


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.kilocade.bubbleshooter.Main"
    }
}