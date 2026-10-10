import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.terminallylazy.a0android"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.terminallylazy.a0android"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        providers.gradleProperty("a0Server").orNull?.let { testInstrumentationRunnerArguments["a0Server"] = it }
    }
    val signingFile = providers.environmentVariable("A0_SIGNING_PROPERTIES").orNull?.let(::file)
    val signing = Properties().apply {
        signingFile?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (signingFile != null) create("distribution") {
            storeFile = file(requireNotNull(signing.getProperty("storeFile")))
            storePassword = requireNotNull(signing.getProperty("storePassword"))
            keyAlias = requireNotNull(signing.getProperty("keyAlias"))
            keyPassword = requireNotNull(signing.getProperty("keyPassword"))
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            if (signingFile != null) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.webkit)
    implementation(libs.datastore)
    implementation(libs.okhttp)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
    androidTestImplementation(libs.uiautomator)
    debugImplementation(libs.compose.test.manifest)
}
