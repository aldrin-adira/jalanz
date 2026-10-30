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
    implementation(project(":core:map"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:route-preview"))
    implementation(project(":core:search-client"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
