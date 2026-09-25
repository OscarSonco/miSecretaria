import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
}

// Build "Interna": token/Chat ID de Telegram pre-rellenados, SOLO para el debug local del
// dueño, nunca para el build público. `secrets.properties` es un archivo local, en
// .gitignore, que jamás se commitea. Sin `-PincludeSecrets=true` (el caso normal, y siempre
// el de release.sh), estos campos quedan vacíos sin importar si el archivo existe en disco.
val secretsProps = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { stream -> load(stream) }
}
val includeSecrets = project.hasProperty("includeSecrets") && project.property("includeSecrets") == "true"

android {
    namespace = "com.sco.misecretaria"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sco.misecretaria"
        minSdk = 26
        targetSdk = 37
        // Esquema: versionCode = major*1000 + minor (soporta minor hasta 999, ej. 2.700 -> 2700)
        versionCode = 2025
        versionName = "2.25"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_BOT_TOKEN", "\"\"")
        buildConfigField("String", "DEFAULT_CHAT_ID", "\"\"")
    }

    buildTypes {
        debug {
            if (includeSecrets) {
                buildConfigField("String", "DEFAULT_BOT_TOKEN", "\"${secretsProps.getProperty("botToken", "")}\"")
                buildConfigField("String", "DEFAULT_CHAT_ID", "\"${secretsProps.getProperty("chatId", "")}\"")
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}