import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
}

// Signature release : le keystore et ses mots de passe vivent HORS du
// dépôt (code destiné à être publié). Chemin du fichier de propriétés :
// propriété Gradle `rengaine.keystore`, sinon ~/.rengaine/keystore.properties.
// Sans ce fichier, la release est signée avec la clé debug : suffisant pour
// les essais sur le téléphone de test (vérifier R8), jamais pour le Play Store.
val keystorePropertiesFile = file(
    (findProperty("rengaine.keystore") as String?)
        ?: "${System.getProperty("user.home")}/.rengaine/keystore.properties",
)
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    // Le namespace (paquets Kotlin, classe R) est fixé une fois pour toutes
    // et ne suit pas le domaine : seul applicationId changera (D1).
    namespace = "app.rengaine"
    compileSdk = 36

    defaultConfig {
        // Identifiant DÉFINITIF (D1, décidé le 2026-10-24) :
        // rengaine.org à l'envers. Ne plus jamais le changer une fois publié.
        // rengaine.org est le site vitrine : l'app ne s'y connecte pas.
        applicationId = "org.rengaine.app"
        minSdk = 31
        targetSdk = 36
        // versionName suit SemVer : 1.0.0-rc.1, 1.0.0-rc.2… puis 1.0.0.
        // versionCode augmente de 1 à chaque APK distribué (RC comprises) :
        // Android refuse d'installer un APK dont le versionCode est plus
        // petit que celui déjà installé. Ne jamais le réutiliser.
        versionCode = 3
        versionName = "1.0.0-rc.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Rengaine (debug)")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            resValue("string", "app_name", "Rengaine")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        // Langues livrées : anglais (défaut) et français (Q2). Évite
        // d'embarquer les traductions des bibliothèques dans 80 langues.
        localeFilters += listOf("en", "fr")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("rengaine.captures", project.hasProperty("captures").toString())
                test.systemProperty("rengaine.capturesDir", layout.buildDirectory.dir("captures").get().asFile.path)
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Les annotations Hilt (@ApplicationContext…) sur un paramètre de
        // constructeur s'appliquent aussi à la propriété : comportement
        // futur de Kotlin, adopté dès maintenant.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    implementation(libs.aboutlibraries.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Captures de mise en page (texte à 200 %, écran de 360 dp), à la demande :
    // ./gradlew testDebugUnitTest -Pcaptures --tests '*LayoutCaptures*'
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Écran « Licences » : la liste des bibliothèques est exportée dans
// res/raw/aboutlibraries.json et versionnée (construction reproductible,
// sans réseau, comme F-Droid le demande). À relancer après chaque
// changement de dépendance (la CI vérifie que le fichier est à jour) :
// ./gradlew :app:exportLibraryDefinitions
aboutLibraries {
    offlineMode = true
    collect {
        configPath = rootProject.file("config/aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        outputFile = file("src/main/res/raw/aboutlibraries.json")
        variant = "release"
        prettyPrint = true
        excludeFields.addAll("generated")
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
    }
}
