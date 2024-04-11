package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.shell.KubernetesApi
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class Pod(
    val name: String,
    val namespace: String,
    val status: String,
)

data class PodList(
    val pods: List<Pod>,
)

data class CommandRequest(
    val namespace: String,
    val podName: String,
    val command: String,
)

data class CommandResponse(
    val output: String,
    val error: String,
)

@RestController
@Validated
@RequestMapping("/kubernetes")
@Tag(
    name = "Kubernetes",
    description = "Interact with a kubernetes cluster",
)
class KubernetesController(
    private val kubernetesApi: KubernetesApi,
) {

    @GetMapping("/pods")
    fun getPods(): PodList {
        return PodList(
            pods = kubernetesApi.getActivePods().map {
                Pod(
                    name = it.metadata?.name ?: "",
                    namespace = it.metadata?.namespace ?: "",
                    status = it.status?.phase ?: "",
                )
            },
        )
    }

    @PostMapping("/execute-command")
    fun executeCommand(@RequestBody commandRequest: CommandRequest): CommandResponse {
        kubernetesApi.executeCommandOnPod(
            commandRequest.namespace,
            commandRequest.podName,
            commandRequest.command,
        ).let { (output, error) ->
            return CommandResponse(
                output = output,
                error = error,
            )
        }
    }
}
