plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.clivena.tonight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clivena.tonight"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        // 设置页要显示版本号，读 BuildConfig.VERSION_NAME。
        // AGP 8 起这个开关默认是关的，不显式打开就编译不过。
        buildConfig = true
    }
}

/**
 * Room 的 schema 导出目录。
 *
 * 每编译一次，就生成一份「数据库此刻应该长什么样」的机器可读基准
 * （app/schemas/com.clivena.tonight.data.local.AppDatabase/2.json）。
 * 手写迁移 SQL 时必须逐字对照它 —— 这是在没有模拟器的机器上
 * 唯一能自证「迁移结果与 Entity 期望一致」的手段。
 *
 * 该目录要提交进版本库：将来 v3、v4 的迁移都要拿它当基准。
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)

    // ── 测试（不进 APK）──────────────────────────────────────
    // 备份的写入与读取都在 JVM 上能跑（只用到 java.util.zip 和 org.json），
    // 所以「导出一份、再读回来、字段都对得上」这件事可以在编译阶段自证，
    // 而不是等装到手机上才发现少了一列。
    testImplementation(libs.junit)
    testImplementation(libs.json.impl)
}
