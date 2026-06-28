package com.proot.cowork.domain.agent

/** Per-run context for tools and orchestration (cleared after each agent run). */
object AgentRunContext {
    var threadId: String? = null
    val filesReadThisRun: MutableSet<String> = mutableSetOf()
    var planWritten: Boolean = false
    var todosInitialized: Boolean = false
    var webFetchCount: Int = 0
    var researchNotes: String = ""
    var targetDeliverablePath: String? = null

    const val MAX_WEB_FETCH_PER_RUN = 2

    fun reset(threadId: String?) {
        this.threadId = threadId
        filesReadThisRun.clear()
        planWritten = false
        todosInitialized = false
        webFetchCount = 0
        researchNotes = ""
        targetDeliverablePath = null
    }
}
