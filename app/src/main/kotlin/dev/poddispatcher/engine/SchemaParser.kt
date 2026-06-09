package dev.poddispatcher.engine

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.poddispatcher.model.PodSchema

object SchemaParser {
    // strictMode=false so OTA schemas with fields from a newer format version
    // still load on older app builds.
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    fun parse(text: String): PodSchema = yaml.decodeFromString(PodSchema.serializer(), text)
}
