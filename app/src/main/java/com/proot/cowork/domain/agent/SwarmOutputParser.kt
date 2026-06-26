package com.proot.cowork.domain.agent

object SwarmOutputParser {

    data class ParsedOutput(
        val fileRows: List<FileListingRow>,
        val chips: List<SummaryChip>,
        val terminals: List<TerminalBlock>,
        val narrative: String?,
        val resultType: SwarmResultType,
    )

    fun parse(messages: List<AgentMessage>): ParsedOutput {
        val fileRows = mutableListOf<FileListingRow>()
        val chips = mutableListOf<SummaryChip>()
        val terminals = mutableListOf<TerminalBlock>()
        val narratives = mutableListOf<String>()

        messages.filter { it.role == MessageRole.TOOL }.forEach { msg ->
            val body = msg.content.trim()
            if (msg.toolName == "proot_shell" || msg.toolName == "read_file") {
                fileRows += parseLsOutput(body)
                terminals += terminalFrom(msg, body)
            }
            if (msg.toolName == "web_fetch") {
                chips += SummaryChip("🌐", "Web research (${body.length.coerceAtMost(999)} chars)")
            }
            narratives += extractNarrative(body)
        }

        messages.filter { it.role == MessageRole.ASSISTANT }.forEach { msg ->
            val text = msg.content.trim()
            if (text.isNotBlank() && !looksLikeJson(text)) {
                narratives += text
                chips += parseBulletChips(text)
            }
        }

        val distinctFiles = fileRows.distinctBy { it.name }
        val distinctChips = chips.distinctBy { it.label }.take(8)
        val resultType = when {
            distinctFiles.isNotEmpty() && distinctChips.isNotEmpty() -> SwarmResultType.MIXED
            distinctFiles.isNotEmpty() -> SwarmResultType.FILE_LISTING
            terminals.isNotEmpty() -> SwarmResultType.TERMINAL
            distinctChips.isNotEmpty() || narratives.isNotEmpty() -> SwarmResultType.SUMMARY
            else -> SwarmResultType.NONE
        }

        return ParsedOutput(
            fileRows = distinctFiles,
            chips = distinctChips,
            terminals = terminals,
            narrative = narratives.lastOrNull { it.length > 20 && !it.startsWith("exit ") },
            resultType = resultType,
        )
    }

    fun thinkingLine(message: AgentMessage): String = when (message.toolStatus) {
        ToolCallStatus.RUNNING -> "▸ ${message.agentName ?: "Agent"} running ${message.toolName ?: "tool"}…"
        ToolCallStatus.FAILED -> "✗ ${message.agentName ?: "Agent"} · ${message.toolName}: failed"
        else -> "✓ ${message.agentName ?: "Agent"} · ${message.toolName ?: "tool"}"
    }

    private fun terminalFrom(message: AgentMessage, body: String): TerminalBlock {
        val (stdout, stderr, code) = splitTerminalBody(body)
        return TerminalBlock(
            id = message.id,
            agentName = message.agentName ?: "Agent",
            toolName = message.toolName ?: "shell",
            stdout = stdout,
            stderr = stderr,
            exitCode = code,
        )
    }

    private fun splitTerminalBody(body: String): Triple<String, String, Int?> {
        val code = Regex("""exit\s+(-?\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val lines = body.lines().filter { !it.startsWith("exit ") }
        val err = lines.filter { it.contains("error", ignoreCase = true) || it.contains("denied", ignoreCase = true) }
        val out = lines.filterNot { it in err }
        return Triple(out.joinToString("\n"), err.joinToString("\n"), code)
    }

    private fun parseLsOutput(body: String): List<FileListingRow> {
        val rows = mutableListOf<FileListingRow>()
        val linePattern = Regex(
            """^([dl-][rwx-]{9})\s+\d+\s+\S+\s+\S+\s+(\d+)\s+(\w+\s+\d+\s+[\d:]+|\w+\s+\d+\s+\d+)\s+(.+)$""",
        )
        body.lines().forEach { line ->
            val m = linePattern.find(line.trim()) ?: return@forEach
            val typeChar = m.groupValues[1].first()
            val type = when (typeChar) {
                'd' -> "Dir"
                'l' -> "Link"
                '-' -> "File"
                else -> "Other"
            }
            val size = formatSize(m.groupValues[2].toLongOrNull() ?: 0L)
            rows += FileListingRow(
                type = type,
                name = m.groupValues[4].trim(),
                size = size,
                modified = m.groupValues[3].trim(),
            )
        }
        return rows
    }

    private fun parseBulletChips(text: String): List<SummaryChip> {
        return Regex("""^[-•*]\s+(.+)$""", RegexOption.MULTILINE).findAll(text).map { match ->
            val line = match.groupValues[1].trim()
            val icon = when {
                line.contains("socket", ignoreCase = true) -> "🔌"
                line.contains("screenshot", ignoreCase = true) || line.contains("png", ignoreCase = true) -> "📁"
                line.contains("session", ignoreCase = true) -> "📂"
                line.contains("file", ignoreCase = true) -> "📄"
                else -> "•"
            }
            SummaryChip(icon, line.take(64))
        }.toList()
    }

    private fun extractNarrative(body: String): String? {
        val cleaned = body.lines()
            .filterNot { it.startsWith("exit ") }
            .filterNot { it.matches(Regex("""^[dl-][rwx-]{9}\s+.*""")) }
            .joinToString("\n")
            .trim()
        return cleaned.takeIf { it.length > 40 && !looksLikeJson(cleaned) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun looksLikeJson(text: String): Boolean =
        text.trimStart().startsWith("{") && text.contains("\"subtasks\"")
}
