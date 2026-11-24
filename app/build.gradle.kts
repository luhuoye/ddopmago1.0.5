plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ddopmago"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ddopmago"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.3"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += listOf("ogg") }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // 必须添加，否则 ViewTree* 找不到
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
}
