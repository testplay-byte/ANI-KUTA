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
    // ROUND 127 (D-789/D-798): the schema-migration lock tests run the REAL
    // migration (1.sqm) on an in-memory SQLite — the user's requirement that a
    // schema update converts the old database in place is TESTED, not assumed.
    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.sqlite.driver)
}
