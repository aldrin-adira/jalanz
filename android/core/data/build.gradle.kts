plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.hilt)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed so future versions can write tested migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:search-client"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
}
