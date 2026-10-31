plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.14.10"
    id("maven-publish")
    java
}

version = "1.1"
group = "com.mossbot_addon"

base {
    archivesName.set("moss-boss")
}

repositories {
    // Tells Loom to treat your local 'libs' folder as a real Maven repo so it remaps the jar properly!
    flatDir {
        dirs("libs")
    }
    maven { url = uri("https://maven.meteordev.org/releases") }
    maven { url = uri("https://maven.meteordev.org/snapshots") }
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings("net.fabricmc:yarn:1.21.11+build.6:v2")
    modImplementation("net.fabricmc:fabric-loader:0.18.2")

    // Orbit dependency for Meteor Client event handlers
    implementation("meteordevelopment:orbit:0.2.3")

    // Starscript dependency required by Meteor Client
    implementation("org.meteordev:starscript:0.2.5")

    // Netty proxy handler required by Meteor's network mixins
    implementation("io.netty:netty-handler-proxy:4.1.107.Final")

    // Reflections library required by Meteor's ReflectInit loader
    implementation("org.reflections:reflections:0.10.2")

    // Discord IPC library required by Meteor's Rich Presence module
    implementation("meteordevelopment:discord-ipc:1.1")

    // Loaded via flatDir so Fabric Loom remaps Minecraft classes inside Meteor properly
    modImplementation("meteordevelopment:meteor-client:1.21.11-86")

    // Baritone API/Mod dependency (use modCompileOnly if you don't have Baritone installed in your live mods folder!)
    modCompileOnly("com.github.cabaletta:baritone:1.10.1")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
