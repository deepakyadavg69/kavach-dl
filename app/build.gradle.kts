// 🛡️ KAVACH App module
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kavach.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kavach.app"
        minSdk = 23            // Android 6.0 — purane se naye sab phones 📱
        targetSdk = 34         // Android 14 rules handle
        versionCode = 55
        versionName = "5.3.0"

        // 📡 Bootstrap config — gradle.properties se EK BAAR, har APK mein embed
        buildConfigField("String", "KAVACH_MAIN_BOT_TOKEN",
            "\"${findProperty("KAVACH_MAIN_BOT_TOKEN") ?: ""}\"")
        buildConfigField("String", "KAVACH_DRIVE_LINK",
            "\"${project.findProperty("KAVACH_DRIVE_LINK") ?: ""}\"")
        buildConfigField("long", "KAVACH_OWNER_CHAT_ID",
            "${findProperty("KAVACH_OWNER_CHAT_ID") ?: "0"}")
        buildConfigField("String", "KAVACH_SOS_FILE_ID",
            "\"${findProperty("KAVACH_SOS_FILE_ID") ?: ""}\"")   // 🪂 Drive-SOS (v3.7.1)
    }

    buildFeatures { buildConfig = true }

    // 🔐 STABLE SIGN (v2.7.0+) — tools/kavach.jks (Drive-backup me bhi hai) →
    // har update seedha install-over hoga, cert kabhi nahi badlega (sandbox reset se bhi nahi!)
    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("tools/kavach.jks")
            storePassword = "kavach123"
            keyAlias = "kavach"
            keyPassword = "kavach123"
        }
    }

    buildTypes {
        debug {                                  // 🪶 v3.9.4 HALKA: hum debug hi ship karte hain → yahan bhi minify!
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true      // code obfuscate — aur bhi stealthy 🎭
            isShrinkResources = true    // 🗜 dead resources bhi kaat (size control)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")

    // 🌐 Telegram Bot API ke liye HTTP (long polling ke saath best)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ⚡ Background work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // 🔐 Token/secrets ka encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 🔳 QR generate (HQ mode mein /app command ke liye)
    implementation("com.google.zxing:core:3.5.3")

    // 🧠 ML Kit face detection (AI intruder detect ke liye — model Play Services se)
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")
}
