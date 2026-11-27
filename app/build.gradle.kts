import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// App parameters: -P gradle property > environment variable > app.properties > neutral default.
val appFile = Properties().apply {
    rootProject.file("app.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun appParam(key: String, default: String): String =
    (findProperty(key) as String?)
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: appFile.getProperty(key)
        ?: default

// Release signing: environment variables > keystore.properties (git-ignored) > unsigned.
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun signing(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(propName)
val signingStoreFile = signing("SIGNING_KEYSTORE", "storeFile")

android {
    namespace = "app.root_academy"
    compileSdk = 37

    defaultConfig {
        applicationId = appParam("APP_ID", "app.example.app")
        minSdk = 26
        targetSdk = 37
        versionCode = appParam("APP_VERSION_CODE", "1").toInt()
        versionName = appParam("APP_VERSION_NAME", "0.1.0")
        resValue("string", "app_name", appParam("APP_NAME", "App"))
        // Project link (for an About screen). Blank hides it.
        val appUrl = appParam("APP_URL", "https://example.com").replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "APP_URL", "\"$appUrl\"")
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile)
                storePassword = signing("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signing("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signing("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric caches its android-all jars under user.home, which is "?" for the container's uid.
        unitTests.all {
            val home = System.getenv("GRADLE_USER_HOME") ?: layout.buildDirectory.dir("robolectric-home").get().asFile.path
            it.systemProperty("user.home", home)
        }
    }

    // Exported Room schemas are packaged with the debug build only, so MigrationTestHelper (Robolectric) can open every
    // old database version in unit tests. The release build does not contain them.
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// A release build without signing details would silently produce an unsigned artifact; refuse instead.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.path == ":app:assembleRelease" || it.path == ":app:bundleRelease" }
    if (releaseRequested && signingStoreFile == null) {
        throw GradleException(
            "Release signing is not configured: set SIGNING_KEYSTORE (+ SIGNING_STORE_PASSWORD, " +
                "SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD) or create keystore.properties.",
        )
    }
}

// Packs live in /content at the repo root; they ship in the APK as assets (reference solutions stay out).
abstract class SyncContent : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun sync() {
        val dest = out.get().asFile.resolve("content")
        dest.deleteRecursively()
        val src = source.get().asFile
        src.walkTopDown().filter { it.isFile && it.name != "solution.txt" }.forEach {
            it.copyTo(dest.resolve(it.relativeTo(src).path), overwrite = true)
        }
    }
}

val syncContent = tasks.register<SyncContent>("syncContent") {
    source.set(rootProject.layout.projectDirectory.dir("content"))
    out.set(layout.buildDirectory.dir("generated/content"))
}

androidComponents {
    onVariants { it.sources.assets?.addGeneratedSourceDirectory(syncContent, SyncContent::out) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.appcompat)
    implementation(libs.datastore.preferences)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
}
