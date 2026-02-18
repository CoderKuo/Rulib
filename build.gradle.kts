import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.MinecraftChat


plugins {
    java
    id("io.izzel.taboolib") version "2.0.31"
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    `maven-publish`
}

taboolib {
    env {
        install(Basic)
        install(Bukkit)
        install(BukkitUtil)
        install(MinecraftChat)
        install(Database)
        install(BukkitUI)
        install(BukkitNMS)
        install(BukkitNMSItemTag)
        install(BukkitNMSUtil)
    }
    description {
        name = "Rulib"
    }
    version { taboolib = "6.2.4-86dd2bf" }

    isSubproject = true

    relocate("com.google.code.regexp.","com.dakuo.rulib.common.regex.")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))

    taboo("com.github.tony19:named-regexp:1.0.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            from(components["java"])
        }
    }
}
