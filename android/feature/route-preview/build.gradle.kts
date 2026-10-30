plugins {
    alias(libs.plugins.jalanz.android.library)
    alias(libs.plugins.jalanz.android.compose)
    alias(libs.plugins.jalanz.hilt)
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:map"))
    implementation(project(":core:routing-client"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
