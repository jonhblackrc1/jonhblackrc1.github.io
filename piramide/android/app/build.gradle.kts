plugins {
    id("com.android.application")
}

// IDs de prueba de Google: muestran anuncios de prueba y no generan ingresos.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"

fun config(name: String, default: String): String =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: default

val keystorePath: String? = System.getenv("PIRAMIDE_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.blackstudio.piramide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blackstudio.piramide"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "2.0.0"
        buildConfigField("String", "SUB_PRODUCT_ID", "\"${config("PIRAMIDE_SUB_ID", "piramide_premium")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PIRAMIDE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PIRAMIDE_KEY_ALIAS")
                keyPassword = System.getenv("PIRAMIDE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"$testRewardedId\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = config("ADMOB_APP_ID", testAdmobAppId)
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"${config("ADMOB_REWARDED_ID", testRewardedId)}\"")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.android.gms:play-services-ads:24.0.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
}
