package dev.poddispatcher.engine

import android.content.Context
import dev.poddispatcher.model.PodSchema
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads schemas from the OTA cache (filesDir/schemas) when present, falling
 * back to the copies bundled as assets at build time. [refresh] downloads a
 * fresh set from the hosted repo; the swap is all-or-nothing — every file must
 * download and parse before the cache is replaced.
 */
class SchemaRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val otaDir = File(context.filesDir, "schemas")

    @Volatile
    private var cache: List<PodSchema>? = null

    fun loadAll(): List<PodSchema> =
        cache ?: loadTexts().mapNotNull { text ->
            runCatching { SchemaParser.parse(text) }.getOrNull()
        }.also { cache = it }

    fun sources(): List<PodSchema> = loadAll().filter { it.source != null }

    fun targets(): List<PodSchema> = loadAll().filter { it.target != null }

    /** Targets installable on this device — i.e. with an Android package pin.
     *  Targets without one (iOS-only apps) still ship in the repo for the
     *  future iOS client but aren't offered here. */
    fun androidTargets(): List<PodSchema> = targets().filter { it.target?.android != null }

    fun byId(id: String): PodSchema? = loadAll().firstOrNull { it.id == id }

    private fun loadTexts(): List<String> = loadOta() ?: loadBundled()

    private fun loadOta(): List<String>? {
        val manifest = File(otaDir, MANIFEST)
        if (!manifest.exists()) return null
        return runCatching {
            fileNames(manifest.readText()).map { File(otaDir, it).readText() }
        }.getOrNull()
    }

    private fun loadBundled(): List<String> {
        val assets = context.assets
        val manifest = assets.open(MANIFEST).bufferedReader().use { it.readText() }
        return fileNames(manifest).map { name ->
            assets.open(name).bufferedReader().use { it.readText() }
        }
    }

    private fun fileNames(manifestJson: String): List<String> =
        Json.parseToJsonElement(manifestJson)
            .jsonObject.getValue("files").jsonArray
            .map { it.jsonPrimitive.content }
            .onEach { name ->
                // Manifest content may come from the network — never let a
                // listed filename escape the schemas directory.
                require(SAFE_NAME.matches(name)) { "Unsafe schema filename: $name" }
            }

    /** Downloads the latest schemas. Returns true if the cache was updated. */
    suspend fun refresh(baseUrl: String = DEFAULT_OTA_BASE_URL): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val manifestText = fetch("$baseUrl/$MANIFEST") ?: return@withContext false
            val files = fileNames(manifestText).map { name ->
                name to (fetch("$baseUrl/$name") ?: return@withContext false)
            }
            files.forEach { (_, text) -> SchemaParser.parse(text) } // validate before swapping
            val staging = File(context.filesDir, "schemas.staging")
            staging.deleteRecursively()
            staging.mkdirs()
            files.forEach { (name, text) -> File(staging, name).writeText(text) }
            File(staging, MANIFEST).writeText(manifestText)
            otaDir.deleteRecursively()
            val swapped = staging.renameTo(otaDir)
            if (swapped) cache = null
            swapped
        }.getOrDefault(false)
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    companion object {
        private const val MANIFEST = "manifest.json"
        private val SAFE_NAME = Regex("""^[a-z0-9][a-z0-9.-]*$""")

        // The shared pod-dispatcher-schemas repo (also bundled as the schemas/
        // submodule). TODO: confirm once the repo is published.
        const val DEFAULT_OTA_BASE_URL =
            "https://raw.githubusercontent.com/natepelzel/pod-dispatcher-schemas/main"
    }
}
