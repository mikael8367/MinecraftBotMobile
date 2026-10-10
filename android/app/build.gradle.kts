plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "br.com.mikael.minecraftbot"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.mikael.minecraftbot"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
