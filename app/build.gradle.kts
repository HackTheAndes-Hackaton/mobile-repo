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
        versionCode = 14
        versionName = "0.4.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // La voz usa Maritime por defecto; la URL se puede cambiar en Ajustes.
        buildConfigField("String", "BACKEND_URL", "\"${localProp("tiskz.backendUrl", "https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755")}\"")
        buildConfigField("String", "WS_URL", "\"${localProp("tiskz.wsUrl", "ws://10.0.2.2:8000/ws/calls")}\"")
        buildConfigField("boolean", "DEMO_MODE_DEFAULT", localProp("tiskz.demoMode", "true"))
        // Panel web donde se guarda la conversación de cada llamada. La API key NO va aquí:
        // se ingresa una vez en Ajustes y queda cifrada en el teléfono.
        //   tiskz.panelUrl=https://web-repo-xi.vercel.app/api/v1
        //   tiskz.panelOrg=org_uav
        buildConfigField("String", "PANEL_URL", "\"${localProp("tiskz.panelUrl", "https://web-repo-xi.vercel.app/api/v1")}\"")
        buildConfigField("String", "PANEL_ORG", "\"${localProp("tiskz.panelOrg", "org_uav")}\"")
        // Configuración del equipo para la app final (solo en local.properties, nunca en Git):
        //   tiskz.preprovisioned=true  oculta los ajustes técnicos y aplica estos valores al arrancar
        //   tiskz.voiceAccessCode=…    código privado del canal de voz
        //   tiskz.panelApiKey=tkz_…    clave del panel para guardar llamadas y consultar reputación
        // Ojo: quedan dentro del APK. Usar claves revocables y no publicar la APK.
        buildConfigField("boolean", "PREPROVISIONED", localProp("tiskz.preprovisioned", "false"))
        buildConfigField("String", "VOICE_ACCESS_CODE", "\"${localProp("tiskz.voiceAccessCode", "")}\"")
        buildConfigField("String", "PANEL_API_KEY", "\"${localProp("tiskz.panelApiKey", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
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
    implementation("io.github.webrtc-sdk:android:150.7871.01")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    implementation(libs.tabler.icons)
    debugImplementation(libs.androidx.ui.tooling)
}
