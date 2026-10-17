import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// URLs del backend y de los agentes: se sobrescriben en local.properties (no se sube a git).
//   tiskz.backendUrl=https://api.midominio.com
//   tiskz.wsUrl=wss://api.midominio.com/ws/calls
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun localProp(key: String, default: String): String = localProps.getProperty(key) ?: default

android {
    namespace = "com.tiskz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tiskz.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 10.0.2.2 es el localhost de la PC visto desde el emulador.
        buildConfigField("String", "BACKEND_URL", "\"${localProp("tiskz.backendUrl", "http://10.0.2.2:8000")}\"")
        buildConfigField("String", "WS_URL", "\"${localProp("tiskz.wsUrl", "ws://10.0.2.2:8000/ws/calls")}\"")
        buildConfigField("boolean", "DEMO_MODE_DEFAULT", localProp("tiskz.demoMode", "true"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.tabler.icons)
    debugImplementation(libs.androidx.ui.tooling)
}
