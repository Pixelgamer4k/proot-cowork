package com.proot.cowork.domain.agent.tools

import android.content.Context
import android.util.Base64
import com.proot.cowork.data.proot.ProotGuestShellExecutor
import com.proot.cowork.data.proot.ShellResult
import com.proot.cowork.data.skills.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolInvocation(
    val name: String,
    val arguments: JSONObject,
)

class AgentToolRegistry(context: Context) {
    private val shell = ProotGuestShellExecutor(context)
    private val skills = SkillRepository(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun execute(invocation: ToolInvocation): String = when (invocation.name) {
        ProotShellTool.NAME -> ProotShellTool.execute(shell, invocation.arguments)
        FileSystemTool.NAME_READ -> FileSystemTool.read(shell, invocation.arguments)
        FileSystemTool.NAME_WRITE -> FileSystemTool.write(shell, invocation.arguments)
        WebTool.NAME -> WebTool.execute(shell, http, invocation.arguments)
        CodeTool.NAME -> CodeTool.execute(shell, invocation.arguments)
        SkillTools.NAME_LIST -> SkillTools.list(skills, invocation.arguments)
        SkillTools.NAME_VIEW -> SkillTools.view(skills, invocation.arguments)
        SkillTools.NAME_MANAGE -> SkillTools.manage(skills, invocation.arguments)
        else -> "Unknown tool: ${invocation.name}"
    }

    fun openAiToolDefinitions(): org.json.JSONArray = org.json.JSONArray().apply {
        put(ProotShellTool.definition())
        put(FileSystemTool.readDefinition())
        put(FileSystemTool.writeDefinition())
        put(WebTool.definition())
        put(CodeTool.definition())
        put(SkillTools.listDefinition())
        put(SkillTools.viewDefinition())
        put(SkillTools.manageDefinition())
    }

    fun toolsForAgent(agent: com.proot.cowork.domain.agent.SwarmAgentType): org.json.JSONArray {
        val all = openAiToolDefinitions()
        val allowed = when (agent) {
            com.proot.cowork.domain.agent.SwarmAgentType.Planner ->
                setOf(FileSystemTool.NAME_READ, SkillTools.NAME_LIST, SkillTools.NAME_VIEW)
            com.proot.cowork.domain.agent.SwarmAgentType.Researcher ->
                setOf(WebTool.NAME, FileSystemTool.NAME_READ, ProotShellTool.NAME, SkillTools.NAME_LIST, SkillTools.NAME_VIEW)
            com.proot.cowork.domain.agent.SwarmAgentType.Executor ->
                setOf(ProotShellTool.NAME, FileSystemTool.NAME_READ, FileSystemTool.NAME_WRITE, SkillTools.NAME_LIST, SkillTools.NAME_VIEW, SkillTools.NAME_MANAGE)
            com.proot.cowork.domain.agent.SwarmAgentType.Coder ->
                setOf(ProotShellTool.NAME, FileSystemTool.NAME_READ, FileSystemTool.NAME_WRITE, CodeTool.NAME, SkillTools.NAME_LIST, SkillTools.NAME_VIEW, SkillTools.NAME_MANAGE)
            com.proot.cowork.domain.agent.SwarmAgentType.Validator ->
                setOf(ProotShellTool.NAME, FileSystemTool.NAME_READ, SkillTools.NAME_LIST, SkillTools.NAME_VIEW)
            com.proot.cowork.domain.agent.SwarmAgentType.Slack ->
                setOf(ProotShellTool.NAME, SkillTools.NAME_LIST)
        }
        return org.json.JSONArray().apply {
            for (i in 0 until all.length()) {
                val tool = all.getJSONObject(i)
                val name = tool.getJSONObject("function").getString("name")
                if (name in allowed) put(tool)
            }
        }
    }
}

object ProotShellTool {
    const val NAME = "proot_shell"

    fun definition(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", NAME)
            put("description", "Run a bash command inside the Ubuntu proot container and return stdout/stderr.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Bash command to run in guest"))
                })
                put("required", org.json.JSONArray().put("command"))
            })
        })
    }

    suspend fun execute(shell: ProotGuestShellExecutor, args: JSONObject): String {
        val command = args.optString("command").trim()
        if (command.isBlank()) return "Error: command is required"
        return formatResult(shell.run(command))
    }
}

object FileSystemTool {
    const val NAME_READ = "read_file"
    const val NAME_WRITE = "write_file"

    fun readDefinition(): JSONObject = toolDef(
        NAME_READ,
        "Read a text file inside the proot container.",
        JSONObject().apply {
            put("path", JSONObject().put("type", "string"))
        },
        listOf("path"),
    )

    fun writeDefinition(): JSONObject = toolDef(
        NAME_WRITE,
        "Write text content to a file inside the proot container (creates parent dirs).",
        JSONObject().apply {
            put("path", JSONObject().put("type", "string"))
            put("content", JSONObject().put("type", "string"))
        },
        listOf("path", "content"),
    )

    suspend fun read(shell: ProotGuestShellExecutor, args: JSONObject): String {
        val path = args.optString("path").trim()
        if (path.isBlank()) return "Error: path is required"
        val quoted = "'" + path.replace("'", "'\\''") + "'"
        return formatResult(shell.run("cat $quoted 2>&1 | head -c 32000"))
    }

    suspend fun write(shell: ProotGuestShellExecutor, args: JSONObject): String {
        val path = args.optString("path").trim()
        val content = args.optString("content")
        if (path.isBlank()) return "Error: path is required"
        val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val quoted = "'" + path.replace("'", "'\\''") + "'"
        val cmd = "mkdir -p \$(dirname $quoted) && echo '$b64' | base64 -d > $quoted"
        return formatResult(shell.run(cmd))
    }

    private fun toolDef(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject =
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", props)
                    put("required", org.json.JSONArray(required))
                })
            })
        }
}

object WebTool {
    const val NAME = "web_fetch"

    fun definition(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", NAME)
            put("description", "Fetch a URL for research. Uses in-guest curl when URL is local, otherwise fetches from device.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().put("type", "string"))
                })
                put("required", org.json.JSONArray().put("url"))
            })
        })
    }

    suspend fun execute(
        shell: ProotGuestShellExecutor,
        http: OkHttpClient,
        args: JSONObject,
    ): String = withContext(Dispatchers.IO) {
        val url = args.optString("url").trim()
        if (url.isBlank()) return@withContext "Error: url is required"
        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
            val quoted = "'" + url.replace("'", "'\\''") + "'"
            return@withContext formatResult(shell.run("curl -fsSL --max-time 30 $quoted 2>&1 | head -c 24000"))
        }
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string().orEmpty().take(24_000)
                if (!response.isSuccessful) "HTTP ${response.code}: ${body.take(500)}" else body
            }
        }.getOrElse { "Fetch failed: ${it.message}" }
    }
}

object CodeTool {
    const val NAME = "edit_and_test_code"

    fun definition(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", NAME)
            put("description", "Write code to a file in proot and run a test/shell command.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string"))
                    put("content", JSONObject().put("type", "string"))
                    put("test_command", JSONObject().put("type", "string"))
                })
                put("required", org.json.JSONArray().put("path").put("content").put("test_command"))
            })
        })
    }

    suspend fun execute(shell: ProotGuestShellExecutor, args: JSONObject): String {
        val writeResult = FileSystemTool.write(shell, args)
        val test = args.optString("test_command").trim()
        if (test.isBlank()) return writeResult
        val testResult = ProotShellTool.execute(shell, JSONObject().put("command", test))
        return "$writeResult\n\nTest:\n$testResult"
    }
}

private fun formatResult(result: ShellResult): String = when {
    result.error != null -> "Error: ${result.error}"
    result.output.isBlank() -> "OK (exit ${result.exitCode}, no output)"
    else -> "exit ${result.exitCode}\n${result.output}"
}
