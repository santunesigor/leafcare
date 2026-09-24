import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Supabase publishable key comes from android-app/local.properties (never committed),
// with environment fallback for CI. project.findProperty does NOT read local.properties,
// so the file is loaded explicitly here.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val supabasePublishableKey =
    localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY")
        ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
        ?: "YOUR_PUBLISHABLE_KEY_HERE"

android {
    namespace = "br.com.leafcare"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.leafcare"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // BuildConfig fields for Supabase configuration (from local.properties)
        buildConfigField("String", "SUPABASE_URL", "\"https://nhkqfanjfcivcbndivav.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        // Legacy alias for backward compatibility
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabasePublishableKey\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.ai.edge.litert:litert:1.4.0")
    implementation("com.google.ai.edge.litert:litert-api:1.4.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Supabase dependencies for Auth (Phase 3) and sync (Phase 4)
    // Using supabase-kt 2.1.0 with gotrue-kt (compatible with Kotlin 2.0.21)
    implementation("io.github.jan-tennert.supabase:supabase-kt:2.1.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.1.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.1.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.1.0")

    // WorkManager for offline-first sync queue (Phase 4)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Ktor client engine required by supabase-kt 2.1.0 (Ktor 2.3.7).
    // Without an engine, HttpClient creation crashes the app at startup.
    implementation("io.ktor:ktor-client-okhttp:2.3.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(composeBom)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val verifySupabaseConfig by tasks.registering {
    group = "verification"
    description = "Impede gerar APK com placeholder de chave Supabase."
    doLast {
        check(supabasePublishableKey.isNotBlank() && supabasePublishableKey != "YOUR_PUBLISHABLE_KEY_HERE") {
            "SUPABASE_PUBLISHABLE_KEY não configurada em local.properties"
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifySupabaseConfig) }

val verifyModelAssets by tasks.registering {
    group = "verification"
    description = "Bloqueia release sem modelo treinado e contrato consistente."
    doLast {
        val assets = file("src/main/assets")
        val model = assets.resolve("leafcare.tflite")
        check(model.isFile) { "Modelo ausente: execute o pipeline Python e export_tflite.py." }
        val meta = JsonSlurper().parse(assets.resolve("model_metadata.json")) as Map<*, *>
        val classes = JsonSlurper().parse(assets.resolve("classes.json")) as List<*>
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(meta["status"] == "trained" && meta["classes"] == classes && classes.size >= 3)
        check(meta["model_sha256"] == hash(model.readBytes())) { "Hash do modelo divergente." }
        check(meta["classes_sha256"] == hash(classes.joinToString("\n").toByteArray(Charsets.UTF_8)))
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyModelAssets) }
