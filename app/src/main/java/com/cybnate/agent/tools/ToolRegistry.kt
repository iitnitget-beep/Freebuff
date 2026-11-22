package com.cybnate.agent.tools

import com.cybnate.agent.provider.ToolDef
import java.io.File

/** Tool registry. All file tools are jailed to [projectRoot]; destructive tools need approval. */
class ToolRegistry(private val projectRoot: File) {

    private val readOnly = setOf("read_file", "list_files", "search_files")

    fun isReadOnly(name: String): Boolean = name in readOnly

    fun definitions(): List<ToolDef> = listOf(
        ToolDef("read_file", "Read a text file in the project", """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""),
        ToolDef("write_file", "Create or overwrite a file (backup first)", """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""),
        ToolDef("list_files", "List project files", """{"type":"object","properties":{"path":{"type":"string"}}}"""),
        ToolDef("search_files", "Regex search across project files", """{"type":"object","properties":{"pattern":{"type":"string"}},"required":["pattern"]}"""),
        ToolDef("run_command", "Run a command on the paired worker (never unrestricted on-device)", """{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}"""),
    )

    private fun jailed(path: String): File {
        val f = File(projectRoot, path).canonicalFile
        require(f.path.startsWith(projectRoot.canonicalPath)) { "Path outside project: $path" }
        return f
    }

    suspend fun execute(name: String, argsJson: String): String {
        // Minimal JSON field extraction (full parser wired with kotlinx-serialization in provider layer).
        fun field(key: String): String =
            Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(argsJson)?.groupValues?.get(1)
                ?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
        return try {
            when (name) {
                "read_file" -> jailed(field("path")).readText().take(100_000)
                "write_file" -> {
                    val f = jailed(field("path"))
                    if (f.exists()) f.copyTo(File(f.path + ".bak"), overwrite = true)
                    f.parentFile?.mkdirs(); f.writeText(field("content")); "Wrote ${f.name}"
                }
                "list_files" -> jailed(field("path").ifBlank { "." }).walkTopDown()
                    .take(500).joinToString("\n") { it.relativeTo(projectRoot).path }
                "search_files" -> {
                    val rx = Regex(field("pattern"))
                    projectRoot.walkTopDown().filter { it.isFile && it.length() < 1_000_000 }
                        .flatMap { f -> f.readLines().mapIndexedNotNull { i, l ->
                            if (rx.containsMatchIn(l)) "${f.relativeTo(projectRoot).path}:${i + 1}: $l" else null } }
                        .take(200).joinToString("\n")
                }
                "run_command" -> "Worker not paired. Pair a worker in Settings to run builds/tests."
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
