import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    dependsOn(":protocol:buildVehicleSim")
    systemProperty(
        "vehicleSim.binary",
        rootProject.layout.projectDirectory.file("tools/vehicle-sim/build/bin/vehicle-sim").asFile.path,
    )
}

dependencies {
    api(project(":protocol"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":protocol")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
