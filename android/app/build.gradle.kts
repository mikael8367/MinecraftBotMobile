plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "br.com.mikael.minecraftbot"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.mikael.minecraftbot"
        minSdk = 26
        targetSdk = 28
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "API_BASE_URL", "\"https://minecraftbotmobile-1.onrender.com\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
