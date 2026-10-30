plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.hilt)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
