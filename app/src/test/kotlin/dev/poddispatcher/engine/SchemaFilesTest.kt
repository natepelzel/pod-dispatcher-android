package dev.poddispatcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sanity checks over every schema file shipped in schemas/. */
class SchemaFilesTest {

    @Test
    fun `all schema files parse`() {
        val files = TestSchemas.files()
        assertTrue("expected at least 2 schema files", files.size >= 2)
        files.forEach { file ->
            SchemaParser.parse(file.readText()) // throws on failure
        }
    }

    @Test
    fun `schema id matches filename`() {
        TestSchemas.files().forEach { file ->
            val schema = SchemaParser.parse(file.readText())
            assertEquals(file.nameWithoutExtension, schema.id)
        }
    }

    @Test
    fun `source patterns compile as Kotlin regexes`() {
        TestSchemas.files().forEach { file ->
            val schema = SchemaParser.parse(file.readText())
            schema.source?.patterns?.forEach { pattern ->
                Regex(pattern.path) // throws on invalid syntax
                pattern.query.values.forEach { Regex(it) }
            }
        }
    }
}
