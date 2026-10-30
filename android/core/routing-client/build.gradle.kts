plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.hilt)
}

dependencies {
    // Place and HttpGet are shared with search.
    api(project(":core:search-client"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
