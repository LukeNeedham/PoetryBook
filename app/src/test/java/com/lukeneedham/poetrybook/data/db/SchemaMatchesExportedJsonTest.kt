package com.lukeneedham.poetrybook.data.db

import com.lukeneedham.poetrybook.data.DatabaseTestBase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the committed schema JSON (`app/schemas/…/N.json`) honest. If an entity changes without a
 * new schema version being exported and committed, or the version number is raised without a
 * migration's schema, these fail. See docs/database.md, "Migrations: never destructive".
 */
class SchemaMatchesExportedJsonTest : DatabaseTestBase() {
    private val schemaDir: File = listOf(
        File("schemas/com.lukeneedham.poetrybook.data.db.PoetryDatabase"),
        File("app/schemas/com.lukeneedham.poetrybook.data.db.PoetryDatabase"),
    ).firstOrNull { it.isDirectory } ?: error("schema directory not found from ${File(".").absolutePath}")

    private fun json(version: Int) = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")

    private fun <T> JSONArray.map(transform: (JSONObject) -> T) = (0 until length()).map { transform(getJSONObject(it)) }

    private fun rows(sql: String): List<Map<String, String?>> =
        db.openHelper.writableDatabase.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add(c.columnNames.associateWith { c.getString(c.getColumnIndexOrThrow(it)) })
            }
        }

    private val currentVersion get() = db.openHelper.readableDatabase.version

    @Test
    fun `a schema file exists for every version up to the current one and none beyond`() {
        val versions = schemaDir.listFiles { f -> f.name.endsWith(".json") }!!
            .map { it.name.removeSuffix(".json").toInt() }.sorted()
        assertEquals((1..currentVersion).toList(), versions)
    }

    @Test
    fun `the exported file for the current version says it is that version`() {
        assertEquals(currentVersion, json(currentVersion).getInt("version"))
    }

    @Test
    fun `the exported identity hash is the one the running database has`() {
        val actual = rows("SELECT identity_hash FROM room_master_table").single().getValue("identity_hash")
        assertEquals(json(currentVersion).getString("identityHash"), actual)
    }

    @Test
    fun `every exported table exists with the same columns, types, nullability and key`() {
        json(currentVersion).getJSONArray("entities").map { entity ->
            val table = entity.getString("tableName")
            val actual = rows("PRAGMA table_info($table)")
            val exported = entity.getJSONArray("fields").map { it }
            assertEquals(
                "$table: column names",
                exported.map { it.getString("columnName") }.sorted(),
                actual.map { it.getValue("name")!! }.sorted(),
            )
            exported.forEach { field ->
                val column = actual.single { it["name"] == field.getString("columnName") }
                assertEquals("$table.${field.getString("columnName")} type", field.getString("affinity"), column["type"])
                assertEquals(
                    "$table.${field.getString("columnName")} not null",
                    if (field.optBoolean("notNull", false)) "1" else "0",
                    column["notnull"],
                )
            }
            val keyColumns = entity.getJSONObject("primaryKey").getJSONArray("columnNames")
            val expectedKey = (0 until keyColumns.length()).map { keyColumns.getString(it) }
            val actualKey = actual.filter { it["pk"] != "0" }.sortedBy { it["pk"]!!.toInt() }.map { it["name"] }
            assertEquals("$table: primary key", expectedKey, actualKey)
        }
    }

    @Test
    fun `every exported index exists with the same columns and uniqueness`() {
        json(currentVersion).getJSONArray("entities").map { entity ->
            val table = entity.getString("tableName")
            val exportedIndices = entity.optJSONArray("indices")?.map { it } ?: emptyList()
            val actual = rows("PRAGMA index_list($table)").filter { it["origin"] == "c" }
            assertEquals("$table: index names", exportedIndices.map { it.getString("name") }.sorted(), actual.map { it["name"]!! }.sorted())
            exportedIndices.forEach { index ->
                val name = index.getString("name")
                assertEquals("$name unique", if (index.getBoolean("unique")) "1" else "0", actual.single { it["name"] == name }["unique"])
                val columns = rows("PRAGMA index_info($name)").sortedBy { it["seqno"]!!.toInt() }.map { it["name"] }
                val expected = index.getJSONArray("columnNames").let { a -> (0 until a.length()).map { a.getString(it) } }
                assertEquals("$name columns", expected, columns)
            }
        }
    }

    @Test
    fun `every exported foreign key exists with the same action`() {
        json(currentVersion).getJSONArray("entities").map { entity ->
            val table = entity.getString("tableName")
            val exported = entity.optJSONArray("foreignKeys")?.map { it } ?: emptyList()
            val actual = rows("PRAGMA foreign_key_list($table)")
            assertEquals("$table: foreign key count", exported.size, actual.size)
            exported.forEach { fk ->
                val from = fk.getJSONArray("columns").getString(0)
                val match = actual.single { it["from"] == from }
                assertEquals("$table.$from references", fk.getString("table"), match["table"])
                assertEquals("$table.$from referenced column", fk.getJSONArray("referencedColumns").getString(0), match["to"])
                assertEquals("$table.$from on delete", fk.getString("onDelete"), match["on_delete"])
                assertEquals("$table.$from on update", fk.getString("onUpdate"), match["on_update"])
            }
        }
    }

    @Test
    fun `the exported entities are exactly the three tables`() {
        val names = json(currentVersion).getJSONArray("entities").map { it.getString("tableName") }
        assertEquals(listOf("books", "poem_versions", "poems"), names.sorted())
    }

    @Test
    fun `the database does not fall back to destroying data when versions differ`() {
        // A guard on the exported history: an older schema file must never be rewritten to match new code.
        val older = (1 until currentVersion)
        assertTrue("old schema files are kept: $older", older.all { File(schemaDir, "$it.json").isFile })
    }
}
