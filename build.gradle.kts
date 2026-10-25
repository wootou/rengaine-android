// Plugins déclarés ici, appliqués dans app/.
// AGP 9 intègre Kotlin : ne jamais appliquer "org.jetbrains.kotlin.android"
// (AGP le refuse). Les plugins du compilateur Compose et de la
// sérialisation restent à appliquer explicitement.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.aboutlibraries) apply false
}
