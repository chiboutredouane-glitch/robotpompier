plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "fr.telecommande.universelle"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.telecommande.universelle"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
        resValue("string", "app_version", "1.0.3")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
