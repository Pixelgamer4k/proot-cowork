package com.proot.cowork.userland

sealed class ExecutionResult
data class MissingExecutionAsset(val asset: String) : ExecutionResult()
object SuccessfulExecution : ExecutionResult()
data class FailedExecution(val reason: String) : ExecutionResult()
data class OngoingExecution(val process: Process) : ExecutionResult()

enum class ServiceType {
    Ssh,
    Vnc,
    Xsdl,
    Unhandled,
}

data class CoworkSession(
    val filesystemId: Long = UserlandConfig.FILESYSTEM_ID,
    val username: String = UserlandConfig.DEFAULT_USERNAME,
    val vncPassword: String = UserlandConfig.DEFAULT_VNC_PASSWORD,
    val geometry: String = UserlandConfig.DEFAULT_GEOMETRY,
    val serviceType: ServiceType = ServiceType.Vnc,
    var pid: Long = 0,
    var active: Boolean = false,
)
