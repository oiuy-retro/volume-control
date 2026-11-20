import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.oiuy.volumecontrol"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.oiuy.volumecontrol"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Selawik fonts (Microsoft, SIL OFL 1.1). The font files are third-party binaries, so
// they are not in git: this task fetches the pinned upstream release, checks SHA-256
// hashes, and writes them into a generated resource folder under app/build.
// The verified zip is cached in the Gradle user home, so later builds (including
// --rerun-tasks release builds) work offline. See docs/BUILDING.md.
abstract class FetchSelawikFonts : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val zipSha256: Property<String>
    /** Zip entry name to Android resource file name. */
    @get:Input abstract val fontNames: MapProperty<String, String>
    /** Zip entry name to SHA-256 of the extracted font file. */
    @get:Input abstract val fontSha256: MapProperty<String, String>
    @get:Internal abstract val cacheFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @TaskAction
    fun fetch() {
        val cache = cacheFile.get().asFile
        val expectedZip = zipSha256.get()
        var zipBytes = if (cache.isFile) cache.readBytes() else null
        if (zipBytes == null || sha256(zipBytes) != expectedZip) {
            logger.lifecycle("Downloading Selawik fonts from ${url.get()}")
            val downloaded = URI(url.get()).toURL().openStream().use { it.readBytes() }
            val actual = sha256(downloaded)
            if (actual != expectedZip) {
                throw GradleException("Selawik download SHA-256 mismatch: expected $expectedZip, got $actual")
            }
            cache.parentFile.mkdirs()
            val tmp = File(cache.parentFile, cache.name + ".tmp")
            tmp.writeBytes(downloaded)
            if (!tmp.renameTo(cache)) {
                cache.delete()
                if (!tmp.renameTo(cache)) throw GradleException("Cannot store Selawik cache at $cache")
            }
            zipBytes = downloaded
        }
        val names = fontNames.get()
        val hashes = fontSha256.get()
        val fontDir = outputDir.get().asFile.resolve("font")
        outputDir.get().asFile.deleteRecursively()
        fontDir.mkdirs()
        val found = mutableSetOf<String>()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val resName = names[entry.name] ?: continue
                val bytes = zip.readBytes()
                val actual = sha256(bytes)
                if (actual != hashes[entry.name]) {
                    throw GradleException("SHA-256 mismatch for ${entry.name}: got $actual")
                }
                fontDir.resolve(resName).writeBytes(bytes)
                found += entry.name
            }
        }
        val missing = names.keys - found
        if (missing.isNotEmpty()) throw GradleException("Selawik zip is missing $missing")
    }
}

val fetchSelawikFonts = tasks.register<FetchSelawikFonts>("fetchSelawikFonts") {
    // Tag 1.01 of https://github.com/microsoft/Selawik (the only release).
    url.set("https://github.com/microsoft/Selawik/releases/download/1.01/Selawik_Release.zip")
    zipSha256.set("3f62c51e05e3b5a1e6241cf92a371f0be2ea1183aa87b30718bbd40832a8d423")
    fontNames.set(
        mapOf(
            "selawkl.ttf" to "selawik_light.ttf",
            "selawksl.ttf" to "selawik_semilight.ttf",
            "selawk.ttf" to "selawik_regular.ttf",
            "selawksb.ttf" to "selawik_semibold.ttf",
            "selawkb.ttf" to "selawik_bold.ttf",
        ),
    )
    fontSha256.set(
        mapOf(
            "selawkl.ttf" to "8e19d073091a1e869b5b0d48b925e605c0a4c6ece7df7b22a364fb065314c4c5",
            "selawksl.ttf" to "8620960344f12093482cded984a1aafa5a57d24cbf7e9299da125ccf0e9d4102",
            "selawk.ttf" to "e9d98518d8ac2817782a9a382430463a2e0793ea68350b695bb727d9a830ee1c",
            "selawksb.ttf" to "0a9e9d0549a10f24bef9b3a29e06fe6e0b5c21e7a784c503b048a307841a7783",
            "selawkb.ttf" to "f0db5e174a90e0956ad7d2844bdca1d5e6da92ec65b2c04e57ba9b180668c904",
        ),
    )
    cacheFile.set(
        File(gradle.gradleUserHomeDir, "caches/volume-control/selawik-1.01/Selawik_Release.zip"),
    )
    outputDir.set(layout.buildDirectory.dir("generated/selawik/res"))
}

// Ships the Selawik license text inside the APK (assets/licenses/) next to the fonts.
abstract class SelawikLicenseAsset : DefaultTask() {
    @get:InputFile abstract val license: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("licenses")
        outputDir.get().asFile.deleteRecursively()
        dir.mkdirs()
        license.get().asFile.copyTo(dir.resolve("Selawik-OFL.txt"))
    }
}

val selawikLicenseAsset = tasks.register<SelawikLicenseAsset>("selawikLicenseAsset") {
    license.set(rootProject.layout.projectDirectory.file("docs/licenses/Selawik-OFL.txt"))
    outputDir.set(layout.buildDirectory.dir("generated/selawik/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(fetchSelawikFonts, FetchSelawikFonts::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(selawikLicenseAsset, SelawikLicenseAsset::outputDir)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
