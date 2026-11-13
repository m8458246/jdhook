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
        versionCode = 6
        versionName = "0.5.1"
    }

    // 固定签名：CI 每次用同一把 key，装更新时 pm install -r 不会因签名变化失败，
    // 也就不会触发卸载 → LSPosed 里已勾选的启用状态和作用域不会丢。
    signingConfigs {
        create("fixed") {
            storeFile = file("../keystore/jdhook.jks")
            storePassword = "jdhook123456"
            keyAlias = "jdhook"
            keyPassword = "jdhook123456"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
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
