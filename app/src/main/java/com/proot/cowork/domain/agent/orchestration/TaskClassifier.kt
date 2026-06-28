package com.proot.cowork.domain.agent.orchestration

object TaskClassifier {

    private val trivialPatterns = listOf(
        Regex("""^(what is|what's|how much is|how many is|define)\b""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s*[\+\-\*/]\s*\d+"""),
        Regex("""^(hi|hello|hey)\b""", RegexOption.IGNORE_CASE),
        Regex("""^(what is|how much is)\s+[\d\s\+\-\*/\(\)\.]+[?\s]*$""", RegexOption.IGNORE_CASE),
    )

    /** Single deliverable file tasks — MODERATE pipeline, not COMPLEX swarm. */
    private val moderateDeliverablePatterns = listOf(
        Regex("""summary\.md""", RegexOption.IGNORE_CASE),
        Regex("""write\s+\w[\w.-]*\.md""", RegexOption.IGNORE_CASE),
        Regex("""write\s+.*\s+to\s+output""", RegexOption.IGNORE_CASE),
    )

    private val complexKeywords = listOf(
        "comprehensive report", "detailed study", "deep research", "full report",
        "build a project", "full stack", "multi-step", "swarm", "architecture",
        "market analysis", "parallel", "multi-angle",
    )

    private val moderateKeywords = listOf(
        "write a", "create a", "research", "install", "setup", "set up", "configure", "deploy",
        "summary", "document", "script", "nginx", "database", "download", "report", "analyze",
    )

    private val actionVerbs = listOf(
        "list", "find", "count", "read", "write", "create", "install", "run", "execute",
        "build", "test", "edit", "update", "research", "analyze", "download",
    )

    fun classify(userTask: String): TaskClassification {
        val text = userTask.trim()
        val lower = text.lowercase()
        val wordCount = text.split(Regex("""\s+""")).count { it.isNotBlank() }

        if (text.length < 30 && trivialPatterns.any { it.containsMatchIn(text) }) {
            return classification(
                TaskComplexity.TRIVIAL,
                text,
                emptyList(),
                "Short factual or greeting query",
            )
        }

        if (moderateDeliverablePatterns.any { it.containsMatchIn(text) } &&
            !complexKeywords.any { lower.contains(it) }
        ) {
            return classification(
                TaskComplexity.MODERATE,
                text,
                listOf(ExecutionStage.RESEARCH, ExecutionStage.EXECUTE, ExecutionStage.INTEGRATE),
                "Research plus single deliverable file",
            )
        }

        if (complexKeywords.any { lower.contains(it) }) {
            return classification(
                TaskComplexity.COMPLEX,
                text,
                listOf(
                    ExecutionStage.RESEARCH,
                    ExecutionStage.EXECUTE,
                    ExecutionStage.VALIDATE,
                    ExecutionStage.INTEGRATE,
                ),
                "Complex multi-stage keywords detected",
            )
        }

        val verbHits = actionVerbs.count { lower.contains(it) }
        if (moderateKeywords.any { lower.contains(it) } || verbHits >= 2) {
            return classification(
                TaskComplexity.MODERATE,
                text,
                listOf(ExecutionStage.RESEARCH, ExecutionStage.EXECUTE, ExecutionStage.INTEGRATE),
                "Multi-step action task",
            )
        }

        if (verbHits >= 1 || wordCount >= 8) {
            return classification(
                TaskComplexity.SIMPLE,
                text,
                listOf(ExecutionStage.EXECUTE, ExecutionStage.INTEGRATE),
                "Single-thread task with explicit action",
            )
        }

        return classification(
            TaskComplexity.TRIVIAL,
            text,
            listOf(ExecutionStage.INTEGRATE),
            "Default lightweight handling",
        )
    }

    private fun classification(
        complexity: TaskComplexity,
        summary: String,
        stages: List<ExecutionStage>,
        rationale: String,
    ): TaskClassification = TaskClassification(
        complexity = complexity,
        summary = summary,
        suggestedStages = stages.ifEmpty {
            when (complexity) {
                TaskComplexity.TRIVIAL -> listOf(ExecutionStage.INTEGRATE)
                TaskComplexity.SIMPLE -> listOf(ExecutionStage.EXECUTE, ExecutionStage.INTEGRATE)
                else -> listOf(
                    ExecutionStage.RESEARCH,
                    ExecutionStage.EXECUTE,
                    ExecutionStage.VALIDATE,
                    ExecutionStage.INTEGRATE,
                )
            }
        },
        rationale = rationale,
    )
}
