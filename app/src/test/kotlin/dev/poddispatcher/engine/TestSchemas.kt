package dev.poddispatcher.engine

import dev.poddispatcher.model.PodSchema
import java.io.File

/** Loads the real schema files from the schemas/ submodule. */
object TestSchemas {
    private val dir: File = sequenceOf("../schemas", "schemas")
        .map { File(it).absoluteFile.normalize() }
        .firstOrNull { it.isDirectory }
        ?: error("schemas/ directory not found relative to ${File(".").absolutePath}")

    fun load(name: String): PodSchema = SchemaParser.parse(File(dir, name).readText())

    fun files(): List<File> =
        dir.listFiles { f -> f.extension == "yml" || f.extension == "yaml" }!!.sorted()
}
