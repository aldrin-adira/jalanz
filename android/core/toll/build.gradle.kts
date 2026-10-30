// Pure Kotlin/JVM toll engine, shared with the backend (spec/04-toll-estimator.md §1).
plugins {
    alias(libs.plugins.jalanz.jvm.library)
}

dependencies {
    testImplementation(libs.kotlinx.serialization.json)
}

// Fixtures F1–F10 live at the repo root (/fixtures/toll) so every engine reads the same files (04 §9).
val tollFixtures = layout.projectDirectory.dir("../../../fixtures/toll")

tasks.test {
    inputs.dir(tollFixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("jalanz.tollFixtures", tollFixtures.asFile.absolutePath)
}
