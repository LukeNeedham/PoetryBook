package com.lukeneedham.poetrybook.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule in docs/database.md ("Migrations: never destructive"): the app's source must
 * never wipe the user's poems to get past a schema change. This fails the build if main code
 * calls any destructive-migration fallback, or deletes the database or clears all its tables.
 *
 * It only reads code, not comments, so the rule can still be written about in KDoc. It cannot
 * prove a migration keeps every row; that needs a MigrationTestHelper test per migration.
 */
class NoDestructiveMigrationTest {
    private val forbidden = Regex(
        """\b(fallbackToDestructiveMigration\w*|clearAllTables|deleteDatabase)\b""",
    )

    @Test
    fun `main source never wipes the database`() {
        val sources = mainSourceFiles()
        // If the layout changes and nothing is found, the guard would silently pass; refuse that.
        assertTrue("No main source files found to check from ${File(".").absolutePath}", sources.isNotEmpty())

        val offences = sources.flatMap { file ->
            forbidden.findAll(withoutComments(file.readText())).map { "${file.path}: ${it.value}" }.toList()
        }
        assertTrue(
            "Destructive database calls are forbidden (see docs/database.md, \"Migrations: never destructive\"):\n" +
                offences.joinToString("\n"),
            offences.isEmpty(),
        )
    }

    @Test
    fun `comments are ignored but code is not`() {
        val source = """
            /** Never call fallbackToDestructiveMigration() here. */
            // also not clearAllTables()
            val ok = 1
            builder.fallbackToDestructiveMigrationOnDowngrade()
        """.trimIndent()
        val found = forbidden.findAll(withoutComments(source)).map { it.value }.toList()
        assertEquals(listOf("fallbackToDestructiveMigrationOnDowngrade"), found)
    }

    private fun mainSourceFiles(): List<File> {
        // Gradle runs unit tests from the module directory; allow running from the repo root too.
        val root = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory } ?: return emptyList()
        return root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    }

    private fun withoutComments(source: String) = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")
}
