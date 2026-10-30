plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.android.compose)
}

dependencies {
    api(libs.maplibre.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
