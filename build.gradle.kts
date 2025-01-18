plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

repositories {
    maven("https://repo.nexomc.com/releases")
    maven("https://maven.mcbrawls.net/releases")
    maven("https://repo.papermc.io/repository/maven-public")
    mavenCentral()
}

dependencies {
    compileOnly("com.nexomc:nexo:0.7.0")
    compileOnly("io.netty:netty-all:4.1.97.Final")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("team.unnamed:creative-api:1.7.3")

    implementation("net.mcbrawls.inject:api:3.1.2")
    implementation("net.mcbrawls.inject:http:3.1.2")
    implementation("net.mcbrawls.inject:spigot:3.1.2")
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        relocate("net.mcbrawls.inject", "net.radstevee.inject_nexo.inject")
    }
}

kotlin {
    explicitApi()
}
