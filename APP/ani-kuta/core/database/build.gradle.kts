plugins {
    id("anikuta.library")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.confused.anikuta.core.database"
}

sqldelight {
    databases {
        create("AnikutaDatabase") {
            packageName.set("com.confused.anikuta.core.database")
        }
    }
}

dependencies {
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
}
