plugins {
    id("com.android.application")
}

android {
    namespace = "dev.cycy.jdhook"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.cycy.jdhook"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // META-INF/xposed/* 必须原样进 APK，否则 LSPosed 认不出这是模块
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("androidx.annotation:annotation:1.9.1")
}
