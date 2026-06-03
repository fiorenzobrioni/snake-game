// Top-level build file — configuration common to all sub-projects/modules
// lives here. Plugins are declared but not applied at the root level.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
