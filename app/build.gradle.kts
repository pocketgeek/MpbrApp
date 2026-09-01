import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.mpbr"
    compileSdk = 37

    defaultConfig {
        applicationId = "us.pgnet.mpbr"
        minSdk = 24
        targetSdk = 37
        versionCode = 112
        versionName = "2.12"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/MINE.jks")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 shrink + optimize + obfuscate. The app has no reflection-based
            // serialization (SessionData JSON is hand-written field by field),
            // so no custom -keep rules are needed. Crash deobfuscation: the
            // mapping file is embedded in the AAB and Play Console applies it
            // automatically. Debug builds never minify — verify release-only
            // behavior on an installed assembleRelease APK, not assembleDebug.
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.print:print:1.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Ballistics.kt is pure Kotlin with no Android deps — plain JVM unit tests.
    testImplementation("junit:junit:4.13.2")
}
