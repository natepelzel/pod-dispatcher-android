import org.yaml.snakeyaml.Yaml

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // YAML parsing for the generateManifest task below.
        classpath("org.yaml:snakeyaml:2.3")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.poddispatcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.poddispatcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    sourceSets {
        getByName("main") {
            // The manifest is generated from AndroidManifest.template.xml +
            // the schemas (intent-filter hosts/paths, <queries> packages) so
            // supporting a new app never requires manual XML edits.
            manifest.srcFile("build/generated/manifest/AndroidManifest.xml")
            // Bundle the schema files from the schemas/ submodule as app assets
            // so the engine works offline before the first OTA refresh. Staged
            // through copySchemaAssets because the submodule also carries docs
            // and tooling that must not ship in the APK.
            assets.srcDir(layout.buildDirectory.dir("generated/schemaAssets"))
        }
    }

    signingConfigs {
        // Release signing comes from the environment (CI secrets, or a local
        // keystore kept outside the repo). Without it, release builds are
        // unsigned — fine for CI sanity builds, not for distribution.
        val keystoreFile = System.getenv("POD_KEYSTORE_FILE")
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("POD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POD_KEY_ALIAS") ?: "pod-dispatcher"
                keyPassword = System.getenv("POD_KEY_PASSWORD")
                    ?: System.getenv("POD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val copySchemaAssets by tasks.registering(Sync::class) {
    from(rootProject.file("schemas")) {
        include("*.yml", "*.yaml", "manifest.json")
    }
    into(layout.buildDirectory.dir("generated/schemaAssets"))
}

/**
 * Generates the manifest from AndroidManifest.template.xml and the schemas:
 * one DispatchActivity intent filter per source schema (hosts + android.paths)
 * and one <queries> package per Android target. Schemas are the single source
 * of truth — supporting a new app/platform is a schema-only change.
 */
val generateManifest by tasks.registering {
    val schemasDir = rootProject.file("schemas")
    val template = file("src/main/AndroidManifest.template.xml")
    val output = layout.buildDirectory.file("generated/manifest/AndroidManifest.xml")
    inputs.files(fileTree(schemasDir) { include("*.yml", "*.yaml") })
    inputs.file(template)
    outputs.file(output)

    doLast {
        fun esc(s: String) = s
            .replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")

        val schemas = schemasDir
            .listFiles { f: File -> f.extension == "yml" || f.extension == "yaml" }!!
            .sortedBy { it.name }
            .map { Yaml().load<Map<String, Any>>(it.readText()) }

        val queries = schemas
            .mapNotNull {
                ((it["target"] as? Map<*, *>)?.get("android") as? Map<*, *>)
                    ?.get("package") as String?
            }
            .sorted()
            .joinToString("\n") { """        <package android:name="${esc(it)}" />""" }

        val filters = schemas
            .filter { it["source"] != null }
            .joinToString("\n") { schema ->
                val source = schema["source"] as Map<*, *>
                val paths = ((source["android"] as? Map<*, *>)?.get("paths") as? List<*>).orEmpty()
                buildString {
                    appendLine("            <!-- ${schema["id"]} -->")
                    appendLine("            <intent-filter>")
                    appendLine("""                <action android:name="android.intent.action.VIEW" />""")
                    appendLine("""                <category android:name="android.intent.category.DEFAULT" />""")
                    appendLine("""                <category android:name="android.intent.category.BROWSABLE" />""")
                    appendLine("""                <data android:scheme="http" />""")
                    appendLine("""                <data android:scheme="https" />""")
                    (source["hosts"] as List<*>).forEach {
                        appendLine("""                <data android:host="${esc(it as String)}" />""")
                    }
                    paths.forEach { p ->
                        val path = p as Map<*, *>
                        (path["prefix"] as String?)?.let {
                            appendLine("""                <data android:pathPrefix="${esc(it)}" />""")
                        }
                        (path["pattern"] as String?)?.let {
                            appendLine("""                <data android:pathPattern="${esc(it)}" />""")
                        }
                    }
                    append("            </intent-filter>")
                }
            }

        val out = output.get().asFile
        out.parentFile.mkdirs()
        out.writeText(
            template.readText()
                .replace("<!-- @generated-queries@ -->", queries.trimStart())
                .replace("<!-- @generated-intent-filters@ -->", filters.trimStart()),
        )
    }
}

// preBuild is created by AGP per variant after evaluation, hence matching{}.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(copySchemaAssets, generateManifest)
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
