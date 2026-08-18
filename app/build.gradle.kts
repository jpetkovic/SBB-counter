import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    versionPropsFile.inputStream().use { load(it) }
}
val versionMajor = versionProps.getProperty("VERSION_MAJOR")
val buildNumber = versionProps.getProperty("BUILD_NUMBER").toInt()
val appVersionName = "$versionMajor.${buildNumber.toString().padStart(4, '0')}"

android {
    namespace = "com.example.aiapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.aiapp"
        minSdk = 28
        targetSdk = 37
        versionCode = buildNumber
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

val incrementBuildNumber by tasks.registering {
    val propsFile = versionPropsFile
    val nextBuildNumber = buildNumber + 1
    outputs.upToDateWhen { false }
    doLast {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        props.setProperty("BUILD_NUMBER", nextBuildNumber.toString())
        propsFile.outputStream().use { props.store(it, null) }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn(incrementBuildNumber)
}