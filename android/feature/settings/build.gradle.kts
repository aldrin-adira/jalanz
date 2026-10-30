plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.android.compose)
}

dependencies {
    implementation(project(":core:designsystem"))
}
