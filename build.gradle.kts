// Plugins are declared here and applied in the modules that need them, so that
// every module agrees on one version of each.
//
// There is no kotlin-android plugin: AGP 9 compiles Kotlin itself. The two
// Kotlin compiler plugins below still have to be asked for by name, and the
// version they carry is the Kotlin the whole build ends up using.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
