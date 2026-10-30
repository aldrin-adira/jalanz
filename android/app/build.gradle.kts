plugins {
    alias(libs.plugins.jalanz.android.application)
    alias(libs.plugins.jalanz.android.compose)
    alias(libs.plugins.jalanz.hilt)
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "0.0.1"
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:drive"))
    implementation(libs.androidx.activity.compose)
}
