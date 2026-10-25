plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- release 签名信息 ----
// 优先取构建时传入的值：-P 参数或 ORG_GRADLE_PROJECT_* 环境变量；
// 其次才读项目根目录的 keystore.properties（可选，不存在也不影响）。
// 四项齐全才算配置完成，否则 release 产出未签名包。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps: java.util.Properties? = if (keystorePropsFile.exists()) {
    java.util.Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
} else {
    null
}

fun signingValue(key: String): String? {
    val fromFile = keystoreProps?.getProperty(key)
    if (!fromFile.isNullOrBlank()) return fromFile
    return (rootProject.findProperty(key) as String?)?.takeIf { it.isNotBlank() }
}

val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { signingValue(it) != null }

android {
    namespace = "lap.shynome.com"
    compileSdk = 35

    defaultConfig {
        applicationId = "lap.shynome.com"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingValue("storeFile")!!)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
