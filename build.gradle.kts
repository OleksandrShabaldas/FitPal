plugins {
    alias(libs.plugins.android.application) apply false
    // Declared here (once) so the :shared and :wear library modules resolve the same AGP version as
    // :app instead of hitting "already on the classpath with an unknown version".
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
