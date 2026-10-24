import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    `java-test-fixtures`
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

dependencies {
    api(libs.protobuf.javalite)
    api(libs.protobuf.kotlin.lite)
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

// The reference vehicle simulator (Go, host-only) backs the integration tests. It is built when Go
// is on the PATH; without Go (or with -PskipSimulator) the tests that need it are skipped.
val simulatorDir = rootProject.layout.projectDirectory.dir("tools/vehicle-sim")
val simulatorBinary = simulatorDir.file("build/bin/vehicle-sim")
val goInstalled = providers.environmentVariable("PATH").map { path ->
    path.split(File.pathSeparator).any { File(it, "go").canExecute() || File(it, "go.exe").canExecute() }
}.getOrElse(false)
val buildVehicleSim = tasks.register<Exec>("buildVehicleSim") {
    description = "Builds the reference vehicle simulator used by integration tests."
    enabled = goInstalled && !providers.gradleProperty("skipSimulator").isPresent
    inputs.dir(simulatorDir.dir("sim"))
    inputs.file(simulatorDir.file("build.sh"))
    outputs.file(simulatorBinary)
    commandLine("bash", simulatorDir.file("build.sh").asFile.path)
}

tasks.test {
    dependsOn(buildVehicleSim)
    systemProperty("vehicleSim.binary", simulatorBinary.asFile.path)
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                named("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}
