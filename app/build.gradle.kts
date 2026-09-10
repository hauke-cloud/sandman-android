import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// Signing is read from keystore.properties (git-ignored) or from the
// environment, so that the key itself never lives in the repository. Every
// build type is signed with the same key: Android refuses to install an APK
// over one signed differently, and swapping a debug build for a release build
// on the same phone should not mean uninstalling first.
val keystoreProperties = Properties().apply {
  val file = rootProject.file("keystore.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, environmentVariable: String): String? =
  (keystoreProperties.getProperty(key) ?: System.getenv(environmentVariable))
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

// A leading ~ is what anyone would write in a properties file, and nothing
// expands it for us here.
fun resolveKeystore(path: String): File {
  val expanded = if (path.startsWith("~/")) System.getProperty("user.home") + path.removePrefix("~") else path
  return File(expanded).let { if (it.isAbsolute) it else rootProject.file(expanded) }
}

// The release tag is the single source of the version: CI passes it in, and a
// local build falls back to the placeholder below.
val releaseVersionName: String =
  System.getenv("SANDMAN_VERSION_NAME")?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }
    ?: "0.1.0"

/**
 * Turns 1.2.3 into 10203.
 *
 * Android will not install an APK whose versionCode is lower than the one
 * already there, so this has to rise with the tag rather than being edited by
 * hand and forgotten. Anything after a hyphen -- 1.2.3-rc1 -- is a label on
 * the same code, and does not change it.
 */
fun versionCodeOf(name: String): Int {
  val parts = name.substringBefore('-').split('.')
  val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
  val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
  val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
  return major * 10_000 + minor * 100 + patch
}

android {
  namespace = "cloud.hauke.sandman"
  compileSdk = 37

  defaultConfig {
    applicationId = "cloud.hauke.sandman"
    minSdk = 26
    targetSdk = 37
    versionName = releaseVersionName
    versionCode = versionCodeOf(releaseVersionName).coerceAtLeast(1)

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("sandman") {
      val storePath = signingValue("storeFile", "SANDMAN_KEYSTORE_FILE")
      if (storePath != null) {
        storeFile = resolveKeystore(storePath)
        storePassword = signingValue("storePassword", "SANDMAN_KEYSTORE_PASSWORD")
        keyAlias = signingValue("keyAlias", "SANDMAN_KEY_ALIAS") ?: "sandman"
        keyPassword = signingValue("keyPassword", "SANDMAN_KEY_PASSWORD")
          ?: signingValue("storePassword", "SANDMAN_KEYSTORE_PASSWORD")
        // v1 is the JAR-signing scheme, which nothing at minSdk 26 reads.
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
      }
    }
  }

  buildTypes {
    // Falls back to the stock debug key when no keystore is configured, so a
    // fresh clone still builds; it just cannot upgrade an existing install.
    val sandmanSigning = signingConfigs.getByName("sandman").takeIf { it.storeFile != null }

    debug {
      // No applicationIdSuffix on purpose: debug and release are the same app
      // on the device, so one replaces the other instead of sitting beside it.
      signingConfig = sandmanSigning ?: signingConfigs.getByName("debug")
    }

    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = sandmanSigning ?: signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    // BuildConfig.DEBUG gates the StrictMode policies in SandmanApplication.
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(platform(libs.androidx.compose.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  debugImplementation(libs.okhttp.logging)

  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
